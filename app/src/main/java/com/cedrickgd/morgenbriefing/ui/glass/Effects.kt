package com.cedrickgd.morgenbriefing.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.cedrickgd.morgenbriefing.perf.Effects

/**
 * The effects the UI draws now – [Effects.FULL] or [Effects.LIGHT] – resolved by `AppRoot` from
 * the "Effekte" setting and the phone's tier. Changes rarely (setting, battery saver), so a
 * change may recompose everything. Previews and tests without a provider get the full look.
 */
val LocalEffects = staticCompositionLocalOf { Effects.FULL }

/**
 * Real blur behind glass: full effects on Android 12+ (RenderEffect). Otherwise the app uses no
 * Haze at all (no blur source, no recording layers) and glass is a frosted tint.
 */
@Composable
@ReadOnlyComposable
fun glassBlurEnabled(): Boolean = LocalEffects.current.blursGlass(Build.VERSION.SDK_INT)

/** The sky drifts at 10 fps and its particles stand still (light effects, Android 7). */
@Composable
@ReadOnlyComposable
fun gentleSky(): Boolean = LocalEffects.current.gentleSky(Build.VERSION.SDK_INT)

/**
 * Decorative loops (the seal's pulse, the bobbing weather emoji) run; with light effects they
 * stand still. The system "remove animations" setting is checked separately, as before.
 */
@Composable
@ReadOnlyComposable
fun decorativeMotion(): Boolean = LocalEffects.current.decorativeMotion
