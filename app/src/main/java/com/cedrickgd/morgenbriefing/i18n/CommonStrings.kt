package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/*
 * PATTERN for every feature strings file (copy it):
 *
 *  - one file per feature: `i18n/<Feature>Strings.kt`
 *  - `interface <Feature>Strings` with `val` for fixed texts and `fun` for texts with values
 *    (counts, names). Plurals are handled inside the `fun` per language.
 *  - one `private object` per [AppLanguage] entry (De, En, Fr, Es, It) implementing it, so a
 *    missing translation is a compile error
 *  - `companion object { fun of(lang: AppLanguage): <Feature>Strings }` for non-Compose code
 *    (workers, notifications, view models, pure domain code, tests)
 *  - a `@Composable @ReadOnlyComposable fun <feature>Strings()` reading [LocalAppLanguage]
 *
 * Texts are written natively per language (not word-by-word): short, friendly, the same tone
 * as the German original. No hard-coded user-visible text outside these files.
 */

/** Texts used all over the app. */
interface CommonStrings {
    val appName: String
    val back: String
    val close: String
    val cancel: String
    val save: String
    val done: String
    val delete: String
    val retry: String
    val share: String
    val copyLink: String
    val openInBrowser: String
    val more: String
    val search: String
    val loading: String
    val on: String
    val off: String

    companion object {
        fun of(lang: AppLanguage): CommonStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : CommonStrings {
        override val appName = "Daily Briefing"
        override val back = "Zurück"
        override val close = "Schließen"
        override val cancel = "Abbrechen"
        override val save = "Speichern"
        override val done = "Fertig"
        override val delete = "Löschen"
        override val retry = "Nochmal versuchen"
        override val share = "Teilen"
        override val copyLink = "Link kopieren"
        override val openInBrowser = "Im Browser öffnen"
        override val more = "Mehr"
        override val search = "Suchen"
        override val loading = "Lädt …"
        override val on = "An"
        override val off = "Aus"
    }

    private object En : CommonStrings {
        override val appName = "Daily Briefing"
        override val back = "Back"
        override val close = "Close"
        override val cancel = "Cancel"
        override val save = "Save"
        override val done = "Done"
        override val delete = "Delete"
        override val retry = "Try again"
        override val share = "Share"
        override val copyLink = "Copy link"
        override val openInBrowser = "Open in browser"
        override val more = "More"
        override val search = "Search"
        override val loading = "Loading …"
        override val on = "On"
        override val off = "Off"
    }

    private object Fr : CommonStrings {
        override val appName = "Daily Briefing"
        override val back = "Retour"
        override val close = "Fermer"
        override val cancel = "Annuler"
        override val save = "Enregistrer"
        override val done = "Terminé"
        override val delete = "Supprimer"
        override val retry = "Réessayer"
        override val share = "Partager"
        override val copyLink = "Copier le lien"
        override val openInBrowser = "Ouvrir dans le navigateur"
        override val more = "Plus"
        override val search = "Rechercher"
        override val loading = "Chargement …"
        override val on = "Activé"
        override val off = "Désactivé"
    }

    private object Es : CommonStrings {
        override val appName = "Daily Briefing"
        override val back = "Atrás"
        override val close = "Cerrar"
        override val cancel = "Cancelar"
        override val save = "Guardar"
        override val done = "Listo"
        override val delete = "Eliminar"
        override val retry = "Reintentar"
        override val share = "Compartir"
        override val copyLink = "Copiar enlace"
        override val openInBrowser = "Abrir en el navegador"
        override val more = "Más"
        override val search = "Buscar"
        override val loading = "Cargando …"
        override val on = "Activado"
        override val off = "Desactivado"
    }

    private object It : CommonStrings {
        override val appName = "Daily Briefing"
        override val back = "Indietro"
        override val close = "Chiudi"
        override val cancel = "Annulla"
        override val save = "Salva"
        override val done = "Fatto"
        override val delete = "Elimina"
        override val retry = "Riprova"
        override val share = "Condividi"
        override val copyLink = "Copia link"
        override val openInBrowser = "Apri nel browser"
        override val more = "Altro"
        override val search = "Cerca"
        override val loading = "Caricamento …"
        override val on = "Attivo"
        override val off = "Disattivo"
    }
}

@Composable
@ReadOnlyComposable
fun commonStrings(): CommonStrings = CommonStrings.of(LocalAppLanguage.current)
