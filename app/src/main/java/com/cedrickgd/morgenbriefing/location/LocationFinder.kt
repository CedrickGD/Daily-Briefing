package com.cedrickgd.morgenbriefing.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.RegionStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/** Why "Mein Standort" could not produce a place. */
enum class LocationIssue {
    /** ACCESS_COARSE_LOCATION is not granted. */
    NO_PERMISSION,

    /** Location is switched off on the device, or no provider answered in time. */
    UNAVAILABLE,

    /**
     * The position is known but no town could be named for it (no geocoder on the device, or
     * offline). Fine for weather, but local news need a real place name to search for.
     */
    NO_TOWN,
}

class LocationException(val issue: LocationIssue) : Exception(issue.name)

/**
 * "Mein Standort" for the place picker: one coarse fix from the system (a recent last-known
 * location is good enough, else a single fresh one) and a reverse lookup of the town name
 * with the platform [Geocoder], in the interface language. No Play Services, nothing leaves the
 * device but the geocoder request Android itself makes. Needs ACCESS_COARSE_LOCATION at runtime.
 */
class LocationFinder(context: Context) {
    private val app = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * The current place, named in [lang] after the town the geocoder finds ("My location" in
     * [lang] when it finds none; with [needsTown] that is [LocationIssue.NO_TOWN] instead – a
     * local-news topic cannot search for "My location").
     * @throws LocationException when the permission is missing, no location can be obtained or
     *   [needsTown] and no town was found.
     */
    suspend fun currentPlace(lang: AppLanguage, needsTown: Boolean): Place {
        if (!hasPermission()) throw LocationException(LocationIssue.NO_PERMISSION)
        val manager = ContextCompat.getSystemService(app, LocationManager::class.java)
            ?: throw LocationException(LocationIssue.UNAVAILABLE)
        val location = lastKnownFresh(manager)
            ?: withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { freshFix(manager) }
            ?: lastKnownAny(manager)
            ?: throw LocationException(LocationIssue.UNAVAILABLE)
        val place = withContext(Dispatchers.IO) { placeFor(location, lang) }
        if (needsTown && place == null) throw LocationException(LocationIssue.NO_TOWN)
        return place ?: unnamedPlace(location.latitude, location.longitude, lang)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFresh(manager: LocationManager): Location? =
        lastKnownAny(manager)?.takeIf { System.currentTimeMillis() - it.time < MAX_AGE_MS }

    /**
     * The providers this grant may use: before Android 12 the GPS and passive providers need
     * ACCESS_FINE_LOCATION (the system throws otherwise); from 12 on the platform coarsens every
     * provider's fix itself.
     */
    private fun allowedProviders(vararg preferred: String): List<String> {
        val fineOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return preferred.filter { fineOk || it == LocationManager.NETWORK_PROVIDER }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownAny(manager: LocationManager): Location? {
        val providers = allowedProviders(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        return providers.mapNotNull { provider ->
            try {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            } catch (e: Exception) {
                Log.w(TAG, "Last known location of $provider unavailable", e)
                null
            }
        }.maxByOrNull { it.time }
    }

    /** One fresh coarse fix: the network provider is fast and good enough for weather. */
    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager): Location? {
        val provider = allowedProviders(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        return suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(app)) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fresh location failed", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /**
     * Reverse geocoding on the IO thread (the pre-33 API blocks); never throws. The town and its
     * region are named in [lang]; when the country speaks another language, a second lookup
     * names the town in that language too ([Place.localName], for the local-news search).
     * Null when no town can be named.
     */
    private fun placeFor(location: Location, lang: AppLanguage): Place? {
        val lat = location.latitude
        val lon = location.longitude
        if (!Geocoder.isPresent()) return null
        val address = lookup(lat, lon, lang.locale) ?: return null
        val name = PlaceNaming.townName(address.locality, address.subAdminArea, address.adminArea, address.featureName)
            ?: return null
        val countryCode = address.countryCode?.trim()?.uppercase().orEmpty()
        val localName = NewsEdition.forCountry(countryCode)?.takeIf { it.language != lang.code }?.let { edition ->
            lookup(lat, lon, Locale.forLanguageTag(edition.language))?.let { local ->
                PlaceNaming.townName(local.locality, local.subAdminArea, local.adminArea, local.featureName)
            }
        }
        return Place(
            name = name,
            lat = lat,
            lon = lon,
            region = PlaceNaming.region(name, address.adminArea, address.countryName),
            countryCode = countryCode,
            localName = PlaceNaming.localName(name, localName),
        )
    }

    /** One address for the spot, named in [locale]; null when none or the geocoder fails. */
    private fun lookup(lat: Double, lon: Double, locale: Locale): android.location.Address? = try {
        @Suppress("DEPRECATION")
        Geocoder(app, locale).getFromLocation(lat, lon, 1)?.firstOrNull()
    } catch (e: IOException) {
        Log.i(TAG, "Reverse geocoding unavailable", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Reverse geocoding failed", e)
        null
    }

    /** The spot without a town: "My location" in [lang], the coordinates as its label. */
    private fun unnamedPlace(lat: Double, lon: Double, lang: AppLanguage): Place =
        Place(name = RegionStrings.of(lang).myLocation, lat = lat, lon = lon, region = PlaceNaming.coordinatesLabel(lat, lon))

    private companion object {
        const val TAG = "LocationFinder"
        const val MAX_AGE_MS = 10L * 60 * 1000
        const val FRESH_FIX_TIMEOUT_MS = 12_000L
    }
}
