package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.cedrickgd.morgenbriefing.update.UpdateError

/** Texts of the in-app updater (settings card and the "update available" notification). */
interface UpdateStrings {
    /** Friendly explanation of [error]; [debugBuild] tailors the wrong-package case. */
    fun error(error: UpdateError, debugBuild: Boolean): String

    val neverChecked: String
    val checkedJustNow: String
    /** "Zuletzt geprüft: heute um 14:32"; [day] is "heute", "gestern" or a short date. */
    fun checkedAt(day: String, time: String): String

    /** "Update verfügbar: Daily Briefing 1.2.0" */
    fun availableTitle(version: String): String
    val availableText: String

    companion object {
        fun of(lang: AppLanguage): UpdateStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : UpdateStrings {
        override fun error(error: UpdateError, debugBuild: Boolean) = when (error) {
            UpdateError.UNAUTHORIZED -> "GitHub lässt die Anfrage gerade nicht durch. Versuch's in ein paar Minuten nochmal."
            UpdateError.NOT_FOUND -> "Noch kein Release auf GitHub gefunden. Sobald eins veröffentlicht ist, taucht es hier auf."
            UpdateError.RATE_LIMITED -> "GitHub bremst gerade. Versuch's in ein paar Minuten nochmal."
            UpdateError.NETWORK -> "Keine Verbindung zu GitHub – bist du online?"
            UpdateError.SERVER -> "GitHub hat unerwartet geantwortet. Versuch's gleich nochmal."
            UpdateError.INVALID_RELEASE -> "Das neueste Release hat keine gültige Versionsnummer."
            UpdateError.NO_APK -> "Das neueste Release enthält keine APK zum Installieren."
            UpdateError.INCOMPLETE_DOWNLOAD -> "Der Download kam unvollständig an. Versuch's nochmal."
            UpdateError.STORAGE -> "Nicht genug freier Speicher für das Update."
            UpdateError.APK_UNREADABLE -> "Die heruntergeladene Datei ist keine gültige App."
            UpdateError.WRONG_PACKAGE -> if (debugBuild) {
                "Das ist die Debug-Version – sie kann keine Release-Updates installieren."
            } else {
                "Die Datei gehört zu einer anderen App. Sie wird nicht installiert."
            }
            UpdateError.WRONG_SIGNATURE ->
                "Das Update ist nicht mit deinem Schlüssel signiert. Aus Sicherheitsgründen wird es nicht installiert."
            UpdateError.NOT_NEWER -> "Die heruntergeladene Version ist nicht neuer als deine."
            UpdateError.INSTALLER_MISSING -> "Der Paket-Installer von Android ließ sich nicht öffnen."
        }
        override val neverChecked = "Noch nicht geprüft"
        override val checkedJustNow = "Zuletzt geprüft: gerade eben"
        override fun checkedAt(day: String, time: String) = "Zuletzt geprüft: $day um $time"
        override fun availableTitle(version: String) = "Update verfügbar: Daily Briefing $version"
        override val availableText = "Tippe, um es unter Einstellungen → App zu installieren."
    }

    private object En : UpdateStrings {
        override fun error(error: UpdateError, debugBuild: Boolean) = when (error) {
            UpdateError.UNAUTHORIZED -> "GitHub isn't letting the request through right now. Try again in a few minutes."
            UpdateError.NOT_FOUND -> "No release found on GitHub yet. It will show up here as soon as one is published."
            UpdateError.RATE_LIMITED -> "GitHub is slowing things down right now. Try again in a few minutes."
            UpdateError.NETWORK -> "Can't reach GitHub – are you online?"
            UpdateError.SERVER -> "GitHub sent an unexpected answer. Try again shortly."
            UpdateError.INVALID_RELEASE -> "The latest release has no valid version number."
            UpdateError.NO_APK -> "The latest release contains no APK to install."
            UpdateError.INCOMPLETE_DOWNLOAD -> "The download arrived incomplete. Please try again."
            UpdateError.STORAGE -> "Not enough free storage for the update."
            UpdateError.APK_UNREADABLE -> "The downloaded file is not a valid app."
            UpdateError.WRONG_PACKAGE -> if (debugBuild) {
                "This is the debug version – it can't install release updates."
            } else {
                "The file belongs to a different app. It won't be installed."
            }
            UpdateError.WRONG_SIGNATURE -> "The update isn't signed with your key. For your safety it won't be installed."
            UpdateError.NOT_NEWER -> "The downloaded version isn't newer than yours."
            UpdateError.INSTALLER_MISSING -> "Android's package installer couldn't be opened."
        }
        override val neverChecked = "Not checked yet"
        override val checkedJustNow = "Last checked: just now"
        override fun checkedAt(day: String, time: String) = "Last checked: $day at $time"
        override fun availableTitle(version: String) = "Update available: Daily Briefing $version"
        override val availableText = "Tap to install it under Settings → App."
    }

    private object Fr : UpdateStrings {
        override fun error(error: UpdateError, debugBuild: Boolean) = when (error) {
            UpdateError.UNAUTHORIZED -> "GitHub refuse la demande pour le moment. Réessaie dans quelques minutes."
            UpdateError.NOT_FOUND -> "Aucune version trouvée sur GitHub pour l'instant. Elle apparaîtra ici dès sa publication."
            UpdateError.RATE_LIMITED -> "GitHub ralentit les demandes. Réessaie dans quelques minutes."
            UpdateError.NETWORK -> "Impossible de joindre GitHub – es-tu en ligne ?"
            UpdateError.SERVER -> "GitHub a répondu de façon inattendue. Réessaie dans un instant."
            UpdateError.INVALID_RELEASE -> "La dernière version n'a pas de numéro valide."
            UpdateError.NO_APK -> "La dernière version ne contient aucun APK à installer."
            UpdateError.INCOMPLETE_DOWNLOAD -> "Le téléchargement est incomplet. Réessaie."
            UpdateError.STORAGE -> "Pas assez d'espace libre pour la mise à jour."
            UpdateError.APK_UNREADABLE -> "Le fichier téléchargé n'est pas une appli valide."
            UpdateError.WRONG_PACKAGE -> if (debugBuild) {
                "C'est la version de débogage – elle ne peut pas installer les mises à jour officielles."
            } else {
                "Le fichier appartient à une autre appli. Il ne sera pas installé."
            }
            UpdateError.WRONG_SIGNATURE -> "La mise à jour n'est pas signée avec ta clé. Par sécurité, elle ne sera pas installée."
            UpdateError.NOT_NEWER -> "La version téléchargée n'est pas plus récente que la tienne."
            UpdateError.INSTALLER_MISSING -> "Impossible d'ouvrir l'installateur de paquets d'Android."
        }
        override val neverChecked = "Pas encore vérifié"
        override val checkedJustNow = "Dernière vérification : à l'instant"
        override fun checkedAt(day: String, time: String) = "Dernière vérification : $day à $time"
        override fun availableTitle(version: String) = "Mise à jour disponible : Daily Briefing $version"
        override val availableText = "Touche pour l'installer sous Réglages → Appli."
    }

    private object Es : UpdateStrings {
        override fun error(error: UpdateError, debugBuild: Boolean) = when (error) {
            UpdateError.UNAUTHORIZED -> "GitHub no deja pasar la solicitud ahora mismo. Inténtalo de nuevo en unos minutos."
            UpdateError.NOT_FOUND -> "Aún no hay ninguna versión en GitHub. Aparecerá aquí en cuanto se publique."
            UpdateError.RATE_LIMITED -> "GitHub está frenando las solicitudes. Inténtalo de nuevo en unos minutos."
            UpdateError.NETWORK -> "No hay conexión con GitHub – ¿estás conectado?"
            UpdateError.SERVER -> "GitHub respondió de forma inesperada. Inténtalo de nuevo enseguida."
            UpdateError.INVALID_RELEASE -> "La última versión no tiene un número de versión válido."
            UpdateError.NO_APK -> "La última versión no incluye ningún APK para instalar."
            UpdateError.INCOMPLETE_DOWNLOAD -> "La descarga llegó incompleta. Inténtalo de nuevo."
            UpdateError.STORAGE -> "No hay suficiente espacio libre para la actualización."
            UpdateError.APK_UNREADABLE -> "El archivo descargado no es una app válida."
            UpdateError.WRONG_PACKAGE -> if (debugBuild) {
                "Esta es la versión de depuración – no puede instalar actualizaciones oficiales."
            } else {
                "El archivo pertenece a otra app. No se instalará."
            }
            UpdateError.WRONG_SIGNATURE -> "La actualización no está firmada con tu clave. Por seguridad, no se instalará."
            UpdateError.NOT_NEWER -> "La versión descargada no es más nueva que la tuya."
            UpdateError.INSTALLER_MISSING -> "No se pudo abrir el instalador de paquetes de Android."
        }
        override val neverChecked = "Aún sin comprobar"
        override val checkedJustNow = "Última comprobación: ahora mismo"
        override fun checkedAt(day: String, time: String) = "Última comprobación: $day a las $time"
        override fun availableTitle(version: String) = "Actualización disponible: Daily Briefing $version"
        override val availableText = "Toca para instalarla en Ajustes → App."
    }

    private object It : UpdateStrings {
        override fun error(error: UpdateError, debugBuild: Boolean) = when (error) {
            UpdateError.UNAUTHORIZED -> "GitHub non fa passare la richiesta al momento. Riprova tra qualche minuto."
            UpdateError.NOT_FOUND -> "Ancora nessuna versione su GitHub. Apparirà qui appena pubblicata."
            UpdateError.RATE_LIMITED -> "GitHub sta rallentando le richieste. Riprova tra qualche minuto."
            UpdateError.NETWORK -> "Nessuna connessione a GitHub – sei online?"
            UpdateError.SERVER -> "GitHub ha risposto in modo inatteso. Riprova tra poco."
            UpdateError.INVALID_RELEASE -> "L'ultima versione non ha un numero di versione valido."
            UpdateError.NO_APK -> "L'ultima versione non contiene un APK da installare."
            UpdateError.INCOMPLETE_DOWNLOAD -> "Il download è arrivato incompleto. Riprova."
            UpdateError.STORAGE -> "Spazio libero insufficiente per l'aggiornamento."
            UpdateError.APK_UNREADABLE -> "Il file scaricato non è un'app valida."
            UpdateError.WRONG_PACKAGE -> if (debugBuild) {
                "Questa è la versione di debug – non può installare gli aggiornamenti ufficiali."
            } else {
                "Il file appartiene a un'altra app. Non verrà installato."
            }
            UpdateError.WRONG_SIGNATURE -> "L'aggiornamento non è firmato con la tua chiave. Per sicurezza non verrà installato."
            UpdateError.NOT_NEWER -> "La versione scaricata non è più recente della tua."
            UpdateError.INSTALLER_MISSING -> "Impossibile aprire il programma di installazione di Android."
        }
        override val neverChecked = "Non ancora controllato"
        override val checkedJustNow = "Ultimo controllo: adesso"
        override fun checkedAt(day: String, time: String) = "Ultimo controllo: $day alle $time"
        override fun availableTitle(version: String) = "Aggiornamento disponibile: Daily Briefing $version"
        override val availableText = "Tocca per installarlo in Impostazioni → App."
    }
}

@Composable
@ReadOnlyComposable
fun updateStrings(): UpdateStrings = UpdateStrings.of(LocalAppLanguage.current)
