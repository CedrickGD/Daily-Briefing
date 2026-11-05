package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** The first-start sheet: language, news edition, notifications, place. */
interface OnboardingStrings {
    val overline: String
    val title: String
    val intro: String
    fun stepOf(step: Int, count: Int): String
    val stepDone: String

    val languageTitle: String
    val languageText: String
    val phoneLanguage: String

    val editionTitle: String
    val editionText: String
    val change: String

    val notificationsTitle: String
    val notificationsText: String
    val notificationsBlockedTitle: String
    val notificationsBlockedText: String
    val allowed: String
    val allow: String
    val openSettings: String

    val placeTitle: String
    val placeText: String
    val choosePlace: String

    val start: String

    companion object {
        fun of(lang: AppLanguage): OnboardingStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : OnboardingStrings {
        override val overline = "Willkommen"
        override val title = "Schön, dass du da bist"
        override val intro = "Das Wetter vor deiner Tür, dein Tag auf einen Blick und die Nachrichten aus deinen Themen – frisch aus den Redaktionen, direkt auf dein Handy. Ein paar kurze Schritte, dann läuft's."
        override fun stepOf(step: Int, count: Int) = "Schritt $step von $count"
        override val stepDone = "Erledigt"

        override val languageTitle = "Deine Sprache"
        override val languageText = "So spricht die App mit dir. Du kannst das jederzeit in den Einstellungen ändern."
        override val phoneLanguage = "Handy-Sprache"

        override val editionTitle = "Deine Nachrichten"
        override val editionText = "Woher deine Schlagzeilen kommen. Orte überall auf der Welt kannst du später dazunehmen."
        override val change = "Ändern"

        override val notificationsTitle = "Benachrichtigungen erlauben"
        override val notificationsText = "Damit dich dein Briefing morgens auch wirklich erreicht."
        override val notificationsBlockedTitle = "Benachrichtigungen sind blockiert"
        override val notificationsBlockedText = "Schalte sie in den Systemeinstellungen ein – sonst erreicht dich dein Briefing morgens nicht."
        override val allowed = "Erlaubt"
        override val allow = "Erlauben"
        override val openSettings = "Einstellungen öffnen"

        override val placeTitle = "Deinen Ort festlegen"
        override val placeText = "Wetter und Regionalnachrichten richten sich nach deinem Ort. Suche ihn oder nimm deinen Standort."
        override val choosePlace = "Ort wählen"

        override val start = "Los geht's"
    }

    private object En : OnboardingStrings {
        override val overline = "Welcome"
        override val title = "Good to have you here"
        override val intro = "The weather outside your door, your day at a glance and the news on your topics – fresh from the newsrooms, straight to your phone. A few quick steps and you're set."
        override fun stepOf(step: Int, count: Int) = "Step $step of $count"
        override val stepDone = "Done"

        override val languageTitle = "Your language"
        override val languageText = "How the app talks to you. You can change it any time in the settings."
        override val phoneLanguage = "Phone language"

        override val editionTitle = "Your news"
        override val editionText = "Where your headlines come from. You can add places anywhere in the world later."
        override val change = "Change"

        override val notificationsTitle = "Allow notifications"
        override val notificationsText = "So your briefing really reaches you in the morning."
        override val notificationsBlockedTitle = "Notifications are blocked"
        override val notificationsBlockedText = "Switch them on in the system settings – otherwise your briefing can't reach you in the morning."
        override val allowed = "Allowed"
        override val allow = "Allow"
        override val openSettings = "Open settings"

        override val placeTitle = "Set your place"
        override val placeText = "Weather and regional news follow your place. Search for it or use your location."
        override val choosePlace = "Choose place"

        override val start = "Let's go"
    }

    private object Fr : OnboardingStrings {
        override val overline = "Bienvenue"
        override val title = "Ravi de te voir ici"
        override val intro = "La météo devant ta porte, ta journée d'un coup d'œil et l'actualité de tes thèmes – tout droit des rédactions, sur ton téléphone. Quelques étapes rapides et c'est parti."
        override fun stepOf(step: Int, count: Int) = "Étape $step sur $count"
        override val stepDone = "Fait"

        override val languageTitle = "Ta langue"
        override val languageText = "La langue dans laquelle l'appli te parle. Tu peux la changer à tout moment dans les réglages."
        override val phoneLanguage = "Langue du téléphone"

        override val editionTitle = "Tes actualités"
        override val editionText = "D'où viennent tes titres. Tu pourras ajouter plus tard des lieux partout dans le monde."
        override val change = "Changer"

        override val notificationsTitle = "Autoriser les notifications"
        override val notificationsText = "Pour que ton briefing t'arrive vraiment le matin."
        override val notificationsBlockedTitle = "Les notifications sont bloquées"
        override val notificationsBlockedText = "Active-les dans les réglages du système – sinon ton briefing ne t'arrivera pas le matin."
        override val allowed = "Autorisées"
        override val allow = "Autoriser"
        override val openSettings = "Ouvrir les réglages"

        override val placeTitle = "Choisir ton lieu"
        override val placeText = "La météo et l'actualité régionale dépendent de ton lieu. Cherche-le ou utilise ta position."
        override val choosePlace = "Choisir le lieu"

        override val start = "C'est parti"
    }

    private object Es : OnboardingStrings {
        override val overline = "Bienvenida"
        override val title = "Qué bien que estés aquí"
        override val intro = "El tiempo en tu puerta, tu día de un vistazo y las noticias de tus temas – recién salidas de las redacciones, directas a tu móvil. Unos pasos rápidos y listo."
        override fun stepOf(step: Int, count: Int) = "Paso $step de $count"
        override val stepDone = "Hecho"

        override val languageTitle = "Tu idioma"
        override val languageText = "Así te habla la app. Puedes cambiarlo cuando quieras en los ajustes."
        override val phoneLanguage = "Idioma del móvil"

        override val editionTitle = "Tus noticias"
        override val editionText = "De dónde vienen tus titulares. Más adelante puedes añadir lugares de todo el mundo."
        override val change = "Cambiar"

        override val notificationsTitle = "Permitir notificaciones"
        override val notificationsText = "Para que tu resumen te llegue de verdad por la mañana."
        override val notificationsBlockedTitle = "Las notificaciones están bloqueadas"
        override val notificationsBlockedText = "Actívalas en los ajustes del sistema – si no, tu resumen no te llegará por la mañana."
        override val allowed = "Permitidas"
        override val allow = "Permitir"
        override val openSettings = "Abrir ajustes"

        override val placeTitle = "Elige tu lugar"
        override val placeText = "El tiempo y las noticias regionales dependen de tu lugar. Búscalo o usa tu ubicación."
        override val choosePlace = "Elegir lugar"

        override val start = "Empezar"
    }

    private object It : OnboardingStrings {
        override val overline = "Benvenuto"
        override val title = "Che bello averti qui"
        override val intro = "Il meteo fuori dalla porta, la tua giornata a colpo d'occhio e le notizie dei tuoi temi – fresche dalle redazioni, dritte sul tuo telefono. Pochi passi veloci e ci sei."
        override fun stepOf(step: Int, count: Int) = "Passo $step di $count"
        override val stepDone = "Fatto"

        override val languageTitle = "La tua lingua"
        override val languageText = "Così l'app ti parla. Puoi cambiarla quando vuoi nelle impostazioni."
        override val phoneLanguage = "Lingua del telefono"

        override val editionTitle = "Le tue notizie"
        override val editionText = "Da dove arrivano i tuoi titoli. Più avanti puoi aggiungere luoghi in tutto il mondo."
        override val change = "Cambia"

        override val notificationsTitle = "Consenti le notifiche"
        override val notificationsText = "Così il tuo briefing ti raggiunge davvero al mattino."
        override val notificationsBlockedTitle = "Le notifiche sono bloccate"
        override val notificationsBlockedText = "Attivale nelle impostazioni di sistema – altrimenti il briefing non ti raggiunge al mattino."
        override val allowed = "Consentite"
        override val allow = "Consenti"
        override val openSettings = "Apri impostazioni"

        override val placeTitle = "Scegli il tuo luogo"
        override val placeText = "Meteo e notizie regionali seguono il tuo luogo. Cercalo o usa la tua posizione."
        override val choosePlace = "Scegli luogo"

        override val start = "Iniziamo"
    }
}

@Composable
@ReadOnlyComposable
fun onboardingStrings(): OnboardingStrings = OnboardingStrings.of(LocalAppLanguage.current)
