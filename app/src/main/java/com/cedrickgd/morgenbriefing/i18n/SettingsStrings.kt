package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.cedrickgd.morgenbriefing.data.model.AccentId
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.perf.DeviceTier
import com.cedrickgd.morgenbriefing.perf.TierReason

/**
 * The settings page itself: page and section titles, the status hero, the morning briefing,
 * the feed card, the weather blocks, the look, the app card with the updater and the snackbars.
 * Language & region, places and the place picker are in [RegionStrings]; topics and sources in
 * [SourcesStrings].
 */
interface SettingsStrings {
    // Page
    val pageOverline: String
    val pageTitle: String

    // Section headings: overline + title
    val languageOverline: String
    val languageTitle: String
    val briefingOverline: String
    val briefingTitle: String
    val topicsOverline: String
    val topicsTitle: String
    val sourcesOverline: String
    val sourcesTitle: String
    val feedOverline: String
    val feedTitle: String
    val lookOverline: String
    val lookTitle: String
    val placesOverline: String
    val placesTitle: String
    val appOverline: String
    val appTitle: String

    // Status hero
    val statusNotifications: String
    val statusExactAlarm: String
    val statusBattery: String
    val stateAllowed: String
    val stateNotAllowed: String
    val stateChannelOff: String
    val stateUnrestricted: String
    val stateOptimized: String
    val heroOffTitle: String
    fun heroOffSubtitle(time: String): String
    fun heroReadyTitle(day: String, time: String): String
    val heroReadySubtitle: String
    val heroNothingArrives: String
    fun heroAlmostReady(day: String, time: String): String
    fun heroProblems(count: Int): String
    val hintNotifications: String
    val hintExactAlarm: String
    val hintBattery: String
    val sealOverlineReady: String
    val sealOverlineAttention: String
    val sealOverlineOff: String
    val sealReady: String
    val sealAttention: String
    val sealOff: String
    val pillFix: String
    val pillGoToSection: String
    val samsungHint: String
    val openAppInfo: String

    // Days and countdowns around the next briefing ("heute"/"morgen" come from FormatStrings)
    val dayAfterTomorrow: String
    /** "am Fr, 9. Okt." for a later day; [date] is [FormatStrings.shortDate]. */
    fun onDate(date: String): String
    val countdownNow: String
    fun countdownMinutes(minutes: Long): String
    fun countdownHours(hours: Long): String
    fun countdownHoursMinutes(hours: Long, minutes: Long): String
    fun nextBriefingCountdown(day: String, countdown: String): String
    fun nextBriefingAt(day: String, time: String): String

    // Briefing card
    val everyMorningAt: String
    val briefingSwitch: String
    val changeTime: String
    /** Spoken time, "Uhrzeit 06:30 Uhr". */
    fun timeDescription(time: String): String
    /** Written after the big time ("Uhr"); empty where a language writes none. */
    val clockSuffix: String
    val briefingIsOff: String
    val testNotification: String
    val testNeedsPermission: String
    val allow: String
    val testQueued: String
    val testFailed: String
    val settingOpenFailed: String

    // Time dialog
    val timeDialogTitle: String
    val timeDialogDial: String
    val timeDialogKeyboard: String
    val apply: String

    // Feed card
    val inTheFeed: String
    val whileReading: String
    val perTopic: String
    val perTopicHint: String
    val perTopicDescription: String
    val newsAgeOverline: String
    fun newsAge(age: NewsAge): String
    val newsAgeHint: String
    val showImages: String
    val showImagesOn: String
    val showImagesOff: String
    val readInApp: String
    val readInAppOn: String
    val readInAppOff: String
    val readableOnly: String
    val readableOnlyOn: String
    val readableOnlyOff: String

    /** "Gelesene Artikel ausblenden" and the row to the archive of read and hidden stories below it. */
    val hideRead: String
    val hideReadOn: String
    val hideReadOff: String
    val historyTitle: String
    fun historySummary(read: Int, hidden: Int): String

    /** Under "Artikel vorladen" while [readableOnly] is on: it checks and loads ahead anyway. */
    val prefetchWhileReadableOnly: String
    val prefetch: String
    fun prefetchMode(mode: PrefetchMode): String
    fun prefetchHint(mode: PrefetchMode): String

    // Weather blocks of "Heute"
    val weatherCardsOverline: String
    val weatherCardsHint: String
    fun weatherCardTitle(card: WeatherCard): String
    fun weatherCardText(card: WeatherCard): String

    // Look
    val modeOverline: String
    fun themeMode(mode: ThemeMode): String
    val accentOverline: String
    fun accentName(accent: AccentId): String

    // Effects (glass blur, the living sky): automatic by the phone, full or light
    val effectsOverline: String
    fun effectsMode(mode: EffectsMode): String

    /**
     * What "Automatisch" picks for this phone ([tier]) and why ([reason]), in plain words.
     * [chosen]: automatic is the selected mode ("Für dieses Handy: …"); otherwise the line says
     * what automatic would do.
     */
    fun effectsHint(tier: DeviceTier, reason: TierReason, chosen: Boolean): String

    // App card, links, footer
    fun version(version: String): String
    val testBuild: String
    val aboutApp: String
    val linkWeather: String
    val linkWeatherText: String
    val linkNews: String
    val linkNewsText: String
    val linkCode: String
    val openWebsite: String
    val footer: String

    // Updater card (errors and "last checked" are in UpdateStrings)
    val updateOverline: String
    val updateIdle: String
    val updateChecking: String
    val updateUpToDate: String
    fun updateAvailable(version: String): String
    fun updateDownloading(version: String): String
    val updateVerifying: String
    val updateNeedsPermission: String
    fun updateReady(version: String): String
    val updateFailed: String
    val updateCheck: String
    val updateCheckAgain: String
    fun updateIsCurrent(app: String, version: String): String
    val updateDownload: String
    val updatePermissionText: String
    val updateConfirmInDialog: String
    val updateVerified: String
    val updateReopenInstaller: String
    val updateInstallNow: String
    fun releasedOn(date: String): String
    val connecting: String
    fun downloadedOf(received: String, total: String): String

    // Snackbars
    fun briefingTimeSet(time: String): String
    val keepOneTopic: String
    fun topicCreatedAddFeed(title: String): String
    fun topicCreatedSearch(title: String): String
    val topicDeleted: String
    fun feedAdded(name: String): String
    val feedRemoved: String
    val saveFailed: String
    fun placeAdded(name: String): String
    fun placeRemoved(name: String): String

    companion object {
        fun of(lang: AppLanguage): SettingsStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : SettingsStrings {
        override val pageOverline = "Dein Morgen"
        override val pageTitle = "Einstellungen"

        override val languageOverline = "Woher & wie"
        override val languageTitle = "Sprache & Region"
        override val briefingOverline = "Dein Wecker"
        override val briefingTitle = "Morgenbriefing"
        override val topicsOverline = "Dein Feed"
        override val topicsTitle = "Deine Themen"
        override val sourcesOverline = "Woher die News kommen"
        override val sourcesTitle = "Quellen"
        override val feedOverline = "So liest sich dein Feed"
        override val feedTitle = "Feed"
        override val lookOverline = "Dein Look"
        override val lookTitle = "Darstellung"
        override val placesOverline = "Dein Wetter"
        override val placesTitle = "Orte & Zeitfenster"
        override val appOverline = "Über"
        override val appTitle = "App"

        override val statusNotifications = "Benachrichtigungen"
        override val statusExactAlarm = "Exakte Uhrzeit"
        override val statusBattery = "Akku"
        override val stateAllowed = "erlaubt"
        override val stateNotAllowed = "nicht erlaubt"
        override val stateChannelOff = "Kanal ausgeschaltet"
        override val stateUnrestricted = "nicht eingeschränkt"
        override val stateOptimized = "Optimierung an"
        override val heroOffTitle = "Dein Morgenbriefing ist aus"
        override fun heroOffSubtitle(time: String) = "Schalte es unten wieder ein – dann kommt es jeden Morgen um $time."
        override fun heroReadyTitle(day: String, time: String) = "Alles bereit für $day, $time"
        override val heroReadySubtitle = "Wetter und News kommen pünktlich – du musst nichts weiter tun."
        override val heroNothingArrives = "Noch kommt morgens nichts an"
        override fun heroAlmostReady(day: String, time: String) = "Fast bereit für $day, $time"
        override fun heroProblems(count: Int) = "$count Kleinigkeiten fehlen noch – tippe auf die gelben Punkte."
        override val hintNotifications = "Erlaube Benachrichtigungen – tippe unten auf den gelben Punkt."
        override val hintExactAlarm = "Mit „Exakte Uhrzeit“ kommt dein Briefing auf die Minute."
        override val hintBattery = "Nimm die App aus der Akku-Optimierung, damit Android sie morgens nicht bremst."
        override val sealOverlineReady = "Status · alles gut"
        override val sealOverlineAttention = "Status · kurz prüfen"
        override val sealOverlineOff = "Status · pausiert"
        override val sealReady = "Alles bereit"
        override val sealAttention = "Braucht deine Aufmerksamkeit"
        override val sealOff = "Ausgeschaltet"
        override val pillFix = "Beheben"
        override val pillGoToSection = "Zum Abschnitt"
        override val samsungHint = "Bei Samsung zusätzlich: App-Info → Akku → „Nicht eingeschränkt“."
        override val openAppInfo = "App-Info öffnen"

        override val dayAfterTomorrow = "übermorgen"
        override fun onDate(date: String) = "am $date"
        override val countdownNow = "gleich"
        override fun countdownMinutes(minutes: Long) = "in $minutes Min."
        override fun countdownHours(hours: Long) = "in $hours Std."
        override fun countdownHoursMinutes(hours: Long, minutes: Long) = "in $hours Std. $minutes Min."
        override fun nextBriefingCountdown(day: String, countdown: String) = "Nächstes Briefing: $day · $countdown"
        override fun nextBriefingAt(day: String, time: String) = "Nächstes Briefing: $day um $time"

        override val everyMorningAt = "Jeden Morgen um"
        override val briefingSwitch = "Morgenbriefing"
        override val changeTime = "Uhrzeit ändern"
        override fun timeDescription(time: String) = "Uhrzeit $time Uhr"
        override val clockSuffix = "Uhr"
        override val briefingIsOff = "Aus – morgens kommt keine Benachrichtigung."
        override val testNotification = "Test-Benachrichtigung"
        override val testNeedsPermission = "Erlaube zuerst Benachrichtigungen."
        override val allow = "Erlauben"
        override val testQueued = "Kommt gleich – die Test-Benachrichtigung ist unterwegs."
        override val testFailed = "Die Test-Benachrichtigung konnte nicht gestartet werden."
        override val settingOpenFailed = "Die Einstellung ließ sich nicht öffnen."

        override val timeDialogTitle = "Wann soll dein Briefing kommen?"
        override val timeDialogDial = "Uhr-Ansicht"
        override val timeDialogKeyboard = "Uhrzeit eintippen"
        override val apply = "Übernehmen"

        override val inTheFeed = "Im Feed"
        override val whileReading = "Beim Lesen"
        override val perTopic = "Meldungen pro Thema in „Für dich“"
        override val perTopicHint = "Danach führt „Alle Meldungen zu …“ in die endlose Liste des Themas."
        override val perTopicDescription = "Meldungen pro Thema"
        override val newsAgeOverline = "Zeitraum"
        override fun newsAge(age: NewsAge) = when (age) {
            NewsAge.DAY -> "Heute"
            NewsAge.TWO_DAYS -> "2 Tage"
            NewsAge.WEEK -> "Eine Woche"
            NewsAge.ALL -> "Alles, was die Quellen liefern"
        }
        override val newsAgeHint = "Ein Thema zeigt immer mindestens seine drei neuesten Meldungen."
        override val showImages = "Bilder anzeigen"
        override val showImagesOn = "Die Bilder der Redaktionen im Feed"
        override val showImagesOff = "Nur ruhige Cover in Themenfarbe – spart Daten"
        override val readInApp = "Artikel in der App lesen"
        override val readInAppOn = "Volltext mit Bildern – ohne Werbung und Cookie-Banner"
        override val readInAppOff = "Jeder Artikel öffnet im Browser"
        override val readableOnly = "Nur vollständige Artikel"
        override val readableOnlyOn =
            "Die App prüft jeden Artikel, bevor sie ihn zeigt: Du siehst nur Meldungen, die komplett in der App lesbar sind – keine Bezahlschranke, kein bloßer Anriss, keine Cookie-Wand. Dafür lädt sie Artikel vorab, auch über mobile Daten (etwas Datenvolumen); offline zeigt sie nur schon geprüfte."
        override val readableOnlyOff = "Alle Meldungen – manche lassen sich nur bei der Redaktion ganz lesen"
        override val hideRead = "Gelesene Artikel ausblenden"
        override val hideReadOn = "Was du gelesen oder angehört hast, verschwindet aus den Listen – beim nächsten Aktualisieren, nie unter dem Finger."
        override val hideReadOff = "Gelesenes bleibt in den Listen, nur ruhiger dargestellt."
        override val historyTitle = "Gelesen & ausgeblendet"
        override fun historySummary(read: Int, hidden: Int) =
            if (read == 0 && hidden == 0) "Noch leer – hier findest du Gelesenes wieder" else "$read gelesen · $hidden ausgeblendet"
        override val prefetchWhileReadableOnly =
            "„Nur vollständige Artikel“ ist an: Die App prüft und lädt die Artikel ohnehin vorab. Diese Wahl gilt, wenn du das ausschaltest."
        override val prefetch = "Artikel vorladen"
        override fun prefetchMode(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Aus"
            PrefetchMode.WIFI_ONLY -> "Nur im WLAN"
            PrefetchMode.ALWAYS -> "Immer"
        }
        override fun prefetchHint(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Artikel laden erst, wenn du sie öffnest."
            PrefetchMode.WIFI_ONLY -> "Im WLAN lädt die App die Artikel auf dem Bildschirm schon vor – sie öffnen sofort, auch offline."
            PrefetchMode.ALWAYS -> "Lädt die Artikel auf dem Bildschirm auch über mobile Daten vor."
        }

        override val weatherCardsOverline = "Auf „Heute“ zeigen"
        override val weatherCardsHint = "„Vor der Tür“ mit der Temperatur ist immer da."
        override fun weatherCardTitle(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Warnungen"
            WeatherCard.CURVE -> "Tagesverlauf"
            WeatherCard.CLOTHING -> "Anziehen"
        }
        override fun weatherCardText(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Glätte, Sturm, Hitze – wenn etwas ansteht"
            WeatherCard.CURVE -> "Temperatur und Regen über den Tag"
            WeatherCard.CLOTHING -> "Was du heute anziehen solltest"
        }

        override val modeOverline = "Modus"
        override fun themeMode(mode: ThemeMode) = when (mode) {
            ThemeMode.SYSTEM -> "System"
            ThemeMode.LIGHT -> "Hell"
            ThemeMode.DARK -> "Dunkel"
        }
        override val accentOverline = "Akzentfarbe"
        override fun accentName(accent: AccentId) = when (accent) {
            AccentId.DYNAMIC -> "Material You"
            AccentId.MORGENROT -> "Morgenrot"
            AccentId.SONNE -> "Sonne"
            AccentId.OZEAN -> "Ozean"
            AccentId.WALD -> "Wald"
            AccentId.LAVENDEL -> "Lavendel"
            AccentId.BEERE -> "Beere"
            AccentId.GRAPHIT -> "Graphit"
        }

        override val effectsOverline = "Effekte"
        override fun effectsMode(mode: EffectsMode) = when (mode) {
            EffectsMode.AUTO -> "Automatisch"
            EffectsMode.FULL -> "Volle Effekte"
            EffectsMode.LIGHT -> "Sparsam"
        }
        override fun effectsHint(tier: DeviceTier, reason: TierReason, chosen: Boolean): String {
            val verdict = if (tier == DeviceTier.FULL) "volle Effekte" else "sparsam"
            val why = when (reason) {
                TierReason.LOW_RAM_DEVICE, TierReason.LITTLE_MEMORY, TierReason.SLOW_CPU -> "flüssiger auf einfacher Hardware"
                TierReason.OLD_ANDROID -> "flüssiger auf älterem Android"
                TierReason.POWER_SAVER -> "der Energiesparmodus ist an"
                TierReason.CAPABLE -> "genug Leistung für Glas und lebendigen Himmel"
            }
            val lead = when {
                !chosen -> "Automatisch wäre hier"
                reason == TierReason.POWER_SAVER -> "Gerade"
                else -> "Für dieses Handy"
            }
            return "$lead: $verdict – $why"
        }

        override fun version(version: String) = "Version $version"
        override val testBuild = "Testversion"
        override val aboutApp = "Läuft komplett auf deinem Handy: Wetter von Open-Meteo, Nachrichten direkt aus den Feeds der Redaktionen und aus Google News. Kein Konto, keine Cloud, keine Werbung."
        override val linkWeather = "Wetterdaten"
        override val linkWeatherText = "Open-Meteo.com · CC BY 4.0"
        override val linkNews = "Nachrichten"
        override val linkNewsText = "Feeds der Redaktionen und Google News · alle Rechte bei den Verlagen"
        override val linkCode = "Quellcode"
        override val openWebsite = "Website öffnen"
        override val footer = "Gemacht für ruhige Morgen"

        override val updateOverline = "App-Update"
        override val updateIdle = "Nach neuen Versionen suchen"
        override val updateChecking = "Suche nach Updates …"
        override val updateUpToDate = "Du bist auf dem neuesten Stand"
        override fun updateAvailable(version: String) = "Version $version ist da"
        override fun updateDownloading(version: String) = "Lade Version $version …"
        override val updateVerifying = "Prüfe Paket und Signatur …"
        override val updateNeedsPermission = "Einmal erlauben, dann geht's weiter"
        override fun updateReady(version: String) = "Version $version ist bereit"
        override val updateFailed = "Das hat nicht geklappt"
        override val updateCheck = "Nach Updates suchen"
        override val updateCheckAgain = "Nochmal suchen"
        override fun updateIsCurrent(app: String, version: String) = "$app $version ist aktuell."
        override val updateDownload = "Herunterladen & installieren"
        override val updatePermissionText = "Android fragt dafür einmal nach: Schalte „Aus dieser Quelle zulassen“ ein und komm zurück – die Installation geht dann direkt weiter."
        override val updateConfirmInDialog = "Geprüft – bestätige die Installation im Android-Dialog."
        override val updateVerified = "Geladen und geprüft."
        override val updateReopenInstaller = "Installation erneut öffnen"
        override val updateInstallNow = "Jetzt installieren"
        override fun releasedOn(date: String) = "vom $date"
        override val connecting = "Verbinde …"
        override fun downloadedOf(received: String, total: String) = "$received von $total"

        override fun briefingTimeSet(time: String) = "Dein Briefing kommt jetzt um $time Uhr."
        override val keepOneTopic = "Mindestens ein Thema bleibt in deinem Feed."
        override fun topicCreatedAddFeed(title: String) = "„$title“ angelegt – füge jetzt eine Quelle hinzu."
        override fun topicCreatedSearch(title: String) = "„$title“ ist jetzt in deinem Feed."
        override val topicDeleted = "Thema gelöscht."
        override fun feedAdded(name: String) = "„$name“ hinzugefügt."
        override val feedRemoved = "Quelle entfernt."
        override val saveFailed = "Das konnte gerade nicht gespeichert werden."
        override fun placeAdded(name: String) = "Du folgst jetzt den Nachrichten aus $name."
        override fun placeRemoved(name: String) = "$name ist nicht mehr in deinem Feed."
    }

    private object En : SettingsStrings {
        override val pageOverline = "Your morning"
        override val pageTitle = "Settings"

        override val languageOverline = "Where & how"
        override val languageTitle = "Language & region"
        override val briefingOverline = "Your alarm"
        override val briefingTitle = "Morning briefing"
        override val topicsOverline = "Your feed"
        override val topicsTitle = "Your topics"
        override val sourcesOverline = "Where the news comes from"
        override val sourcesTitle = "Sources"
        override val feedOverline = "How your feed reads"
        override val feedTitle = "Feed"
        override val lookOverline = "Your look"
        override val lookTitle = "Appearance"
        override val placesOverline = "Your weather"
        override val placesTitle = "Places & time window"
        override val appOverline = "About"
        override val appTitle = "App"

        override val statusNotifications = "Notifications"
        override val statusExactAlarm = "Exact time"
        override val statusBattery = "Battery"
        override val stateAllowed = "allowed"
        override val stateNotAllowed = "not allowed"
        override val stateChannelOff = "channel switched off"
        override val stateUnrestricted = "unrestricted"
        override val stateOptimized = "optimization on"
        override val heroOffTitle = "Your morning briefing is off"
        override fun heroOffSubtitle(time: String) = "Switch it back on below and it arrives every morning at $time."
        override fun heroReadyTitle(day: String, time: String) = "All set for $day, $time"
        override val heroReadySubtitle = "Weather and news arrive on time – nothing else to do."
        override val heroNothingArrives = "Nothing reaches you in the morning yet"
        override fun heroAlmostReady(day: String, time: String) = "Almost set for $day, $time"
        override fun heroProblems(count: Int) = "$count little things are still missing – tap the yellow dots."
        override val hintNotifications = "Allow notifications – tap the yellow dot below."
        override val hintExactAlarm = "With “Exact time” your briefing arrives on the minute."
        override val hintBattery = "Take the app out of battery optimization so Android doesn't hold it back in the morning."
        override val sealOverlineReady = "Status · all good"
        override val sealOverlineAttention = "Status · quick check"
        override val sealOverlineOff = "Status · paused"
        override val sealReady = "All set"
        override val sealAttention = "Needs your attention"
        override val sealOff = "Switched off"
        override val pillFix = "Fix"
        override val pillGoToSection = "Go to section"
        override val samsungHint = "On Samsung also: App info → Battery → “Unrestricted”."
        override val openAppInfo = "Open app info"

        override val dayAfterTomorrow = "the day after tomorrow"
        override fun onDate(date: String) = "on $date"
        override val countdownNow = "any moment"
        override fun countdownMinutes(minutes: Long) = "in $minutes min"
        override fun countdownHours(hours: Long) = if (hours == 1L) "in 1 hr" else "in $hours hrs"
        override fun countdownHoursMinutes(hours: Long, minutes: Long) = "in $hours hr $minutes min"
        override fun nextBriefingCountdown(day: String, countdown: String) = "Next briefing: $day · $countdown"
        override fun nextBriefingAt(day: String, time: String) = "Next briefing: $day at $time"

        override val everyMorningAt = "Every morning at"
        override val briefingSwitch = "Morning briefing"
        override val changeTime = "Change time"
        override fun timeDescription(time: String) = "Time $time"
        override val clockSuffix = ""
        override val briefingIsOff = "Off – no notification in the morning."
        override val testNotification = "Test notification"
        override val testNeedsPermission = "Allow notifications first."
        override val allow = "Allow"
        override val testQueued = "Coming right up – the test notification is on its way."
        override val testFailed = "The test notification couldn't be started."
        override val settingOpenFailed = "That setting couldn't be opened."

        override val timeDialogTitle = "When should your briefing arrive?"
        override val timeDialogDial = "Clock view"
        override val timeDialogKeyboard = "Type the time"
        override val apply = "Apply"

        override val inTheFeed = "In the feed"
        override val whileReading = "While reading"
        override val perTopic = "Stories per topic in “For you”"
        override val perTopicHint = "After that, “All stories on …” opens the topic's endless list."
        override val perTopicDescription = "Stories per topic"
        override val newsAgeOverline = "Time range"
        override fun newsAge(age: NewsAge) = when (age) {
            NewsAge.DAY -> "Today"
            NewsAge.TWO_DAYS -> "2 days"
            NewsAge.WEEK -> "One week"
            NewsAge.ALL -> "Everything the sources deliver"
        }
        override val newsAgeHint = "A topic always shows at least its three newest stories."
        override val showImages = "Show pictures"
        override val showImagesOn = "The newsrooms' pictures in the feed"
        override val showImagesOff = "Calm covers in the topic color only – saves data"
        override val readInApp = "Read articles in the app"
        override val readInAppOn = "Full text with pictures – no ads, no cookie banners"
        override val readInAppOff = "Every article opens in the browser"
        override val readableOnly = "Only complete articles"
        override val readableOnlyOn =
            "The app checks every article before showing it: you only see stories that can be read in full in the app – no paywall, no mere teaser, no cookie wall. For that it loads articles in advance, on mobile data too (uses some data); offline it shows only articles checked before."
        override val readableOnlyOff = "All stories – some can only be read in full on the publisher's site"
        override val hideRead = "Hide read articles"
        override val hideReadOn = "Stories you read or listened to leave the lists the next time they refresh – never under your finger."
        override val hideReadOff = "Read stories stay in the lists, just quieter."
        override val historyTitle = "Read & hidden"
        override fun historySummary(read: Int, hidden: Int) =
            if (read == 0 && hidden == 0) "Empty for now – find what you read here again" else "$read read · $hidden hidden"
        override val prefetchWhileReadableOnly =
            "“Only complete articles” is on: the app checks and loads articles in advance anyway. This choice applies when you switch that off."
        override val prefetch = "Preload articles"
        override fun prefetchMode(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Off"
            PrefetchMode.WIFI_ONLY -> "Wi-Fi only"
            PrefetchMode.ALWAYS -> "Always"
        }
        override fun prefetchHint(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Articles load only when you open them."
            PrefetchMode.WIFI_ONLY -> "On Wi-Fi the app loads the articles on screen ahead of time – they open instantly, even offline."
            PrefetchMode.ALWAYS -> "Loads the articles on screen ahead of time on mobile data too."
        }

        override val weatherCardsOverline = "Show on “Today”"
        override val weatherCardsHint = "“Outside” with the temperature is always there."
        override fun weatherCardTitle(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Warnings"
            WeatherCard.CURVE -> "Day at a glance"
            WeatherCard.CLOTHING -> "What to wear"
        }
        override fun weatherCardText(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Ice, storms, heat – when something's coming"
            WeatherCard.CURVE -> "Temperature and rain through the day"
            WeatherCard.CLOTHING -> "What you should wear today"
        }

        override val modeOverline = "Mode"
        override fun themeMode(mode: ThemeMode) = when (mode) {
            ThemeMode.SYSTEM -> "System"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
        }
        override val accentOverline = "Accent color"
        override fun accentName(accent: AccentId) = when (accent) {
            AccentId.DYNAMIC -> "Material You"
            AccentId.MORGENROT -> "Sunrise"
            AccentId.SONNE -> "Sun"
            AccentId.OZEAN -> "Ocean"
            AccentId.WALD -> "Forest"
            AccentId.LAVENDEL -> "Lavender"
            AccentId.BEERE -> "Berry"
            AccentId.GRAPHIT -> "Graphite"
        }

        override val effectsOverline = "Effects"
        override fun effectsMode(mode: EffectsMode) = when (mode) {
            EffectsMode.AUTO -> "Automatic"
            EffectsMode.FULL -> "Full effects"
            EffectsMode.LIGHT -> "Light"
        }
        override fun effectsHint(tier: DeviceTier, reason: TierReason, chosen: Boolean): String {
            val verdict = if (tier == DeviceTier.FULL) "full effects" else "light"
            val why = when (reason) {
                TierReason.LOW_RAM_DEVICE, TierReason.LITTLE_MEMORY, TierReason.SLOW_CPU -> "smoother on simple hardware"
                TierReason.OLD_ANDROID -> "smoother on older Android"
                TierReason.POWER_SAVER -> "battery saver is on"
                TierReason.CAPABLE -> "enough power for glass and a lively sky"
            }
            val lead = when {
                !chosen -> "Automatic would pick"
                reason == TierReason.POWER_SAVER -> "Right now"
                else -> "For this phone"
            }
            return "$lead: $verdict – $why"
        }

        override fun version(version: String) = "Version $version"
        override val testBuild = "Test build"
        override val aboutApp = "Runs entirely on your phone: weather from Open-Meteo, news straight from the newsrooms' feeds and Google News. No account, no cloud, no ads."
        override val linkWeather = "Weather data"
        override val linkWeatherText = "Open-Meteo.com · CC BY 4.0"
        override val linkNews = "News"
        override val linkNewsText = "Newsroom feeds and Google News · all rights with the publishers"
        override val linkCode = "Source code"
        override val openWebsite = "Open website"
        override val footer = "Made for calm mornings"

        override val updateOverline = "App update"
        override val updateIdle = "Look for new versions"
        override val updateChecking = "Looking for updates …"
        override val updateUpToDate = "You're up to date"
        override fun updateAvailable(version: String) = "Version $version is here"
        override fun updateDownloading(version: String) = "Downloading version $version …"
        override val updateVerifying = "Checking package and signature …"
        override val updateNeedsPermission = "Allow it once, then it carries on"
        override fun updateReady(version: String) = "Version $version is ready"
        override val updateFailed = "That didn't work"
        override val updateCheck = "Check for updates"
        override val updateCheckAgain = "Check again"
        override fun updateIsCurrent(app: String, version: String) = "$app $version is the latest."
        override val updateDownload = "Download & install"
        override val updatePermissionText = "Android asks once: switch on “Allow from this source” and come back – the installation then continues right away."
        override val updateConfirmInDialog = "Verified – confirm the installation in the Android dialog."
        override val updateVerified = "Downloaded and verified."
        override val updateReopenInstaller = "Open installation again"
        override val updateInstallNow = "Install now"
        override fun releasedOn(date: String) = "from $date"
        override val connecting = "Connecting …"
        override fun downloadedOf(received: String, total: String) = "$received of $total"

        override fun briefingTimeSet(time: String) = "Your briefing now arrives at $time."
        override val keepOneTopic = "At least one topic stays in your feed."
        override fun topicCreatedAddFeed(title: String) = "“$title” created – now add a source."
        override fun topicCreatedSearch(title: String) = "“$title” is now in your feed."
        override val topicDeleted = "Topic deleted."
        override fun feedAdded(name: String) = "“$name” added."
        override val feedRemoved = "Source removed."
        override val saveFailed = "That couldn't be saved just now."
        override fun placeAdded(name: String) = "You now follow the news from $name."
        override fun placeRemoved(name: String) = "$name is no longer in your feed."
    }

    private object Fr : SettingsStrings {
        override val pageOverline = "Ton matin"
        override val pageTitle = "Réglages"

        override val languageOverline = "D'où & comment"
        override val languageTitle = "Langue & région"
        override val briefingOverline = "Ton réveil"
        override val briefingTitle = "Briefing du matin"
        override val topicsOverline = "Ton fil"
        override val topicsTitle = "Tes thèmes"
        override val sourcesOverline = "D'où viennent les infos"
        override val sourcesTitle = "Sources"
        override val feedOverline = "Comment se lit ton fil"
        override val feedTitle = "Fil"
        override val lookOverline = "Ton style"
        override val lookTitle = "Apparence"
        override val placesOverline = "Ta météo"
        override val placesTitle = "Lieux & plage horaire"
        override val appOverline = "À propos"
        override val appTitle = "Appli"

        override val statusNotifications = "Notifications"
        override val statusExactAlarm = "Heure exacte"
        override val statusBattery = "Batterie"
        override val stateAllowed = "autorisées"
        override val stateNotAllowed = "non autorisées"
        override val stateChannelOff = "canal désactivé"
        override val stateUnrestricted = "sans restriction"
        override val stateOptimized = "optimisation active"
        override val heroOffTitle = "Ton briefing du matin est désactivé"
        override fun heroOffSubtitle(time: String) = "Réactive-le ci-dessous : il arrivera chaque matin à $time."
        override fun heroReadyTitle(day: String, time: String) = "Tout est prêt pour $day, $time"
        override val heroReadySubtitle = "Météo et infos arrivent à l'heure – tu n'as rien d'autre à faire."
        override val heroNothingArrives = "Pour l'instant, rien ne t'arrive le matin"
        override fun heroAlmostReady(day: String, time: String) = "Presque prêt pour $day, $time"
        override fun heroProblems(count: Int) = "Il manque encore $count petites choses – touche les points jaunes."
        override val hintNotifications = "Autorise les notifications – touche le point jaune ci-dessous."
        override val hintExactAlarm = "Avec « Heure exacte », ton briefing arrive à la minute près."
        override val hintBattery = "Retire l'appli de l'optimisation de batterie pour qu'Android ne la freine pas le matin."
        override val sealOverlineReady = "État · tout va bien"
        override val sealOverlineAttention = "État · à vérifier"
        override val sealOverlineOff = "État · en pause"
        override val sealReady = "Tout est prêt"
        override val sealAttention = "Demande ton attention"
        override val sealOff = "Désactivé"
        override val pillFix = "Corriger"
        override val pillGoToSection = "Aller à la section"
        override val samsungHint = "Sur Samsung, en plus : Infos sur l'appli → Batterie → « Sans restriction »."
        override val openAppInfo = "Ouvrir les infos de l'appli"

        override val dayAfterTomorrow = "après-demain"
        override fun onDate(date: String) = "le $date"
        override val countdownNow = "dans un instant"
        override fun countdownMinutes(minutes: Long) = "dans $minutes min"
        override fun countdownHours(hours: Long) = "dans $hours h"
        override fun countdownHoursMinutes(hours: Long, minutes: Long) = "dans $hours h $minutes min"
        override fun nextBriefingCountdown(day: String, countdown: String) = "Prochain briefing : $day · $countdown"
        override fun nextBriefingAt(day: String, time: String) = "Prochain briefing : $day à $time"

        override val everyMorningAt = "Chaque matin à"
        override val briefingSwitch = "Briefing du matin"
        override val changeTime = "Changer l'heure"
        override fun timeDescription(time: String) = "Heure $time"
        override val clockSuffix = ""
        override val briefingIsOff = "Désactivé – aucune notification le matin."
        override val testNotification = "Notification test"
        override val testNeedsPermission = "Autorise d'abord les notifications."
        override val allow = "Autoriser"
        override val testQueued = "Ça arrive – la notification test est en route."
        override val testFailed = "Impossible de lancer la notification test."
        override val settingOpenFailed = "Impossible d'ouvrir ce réglage."

        override val timeDialogTitle = "À quelle heure veux-tu ton briefing ?"
        override val timeDialogDial = "Vue cadran"
        override val timeDialogKeyboard = "Saisir l'heure"
        override val apply = "Appliquer"

        override val inTheFeed = "Dans le fil"
        override val whileReading = "À la lecture"
        override val perTopic = "Articles par thème dans « Pour toi »"
        override val perTopicHint = "Ensuite, « Tous les articles sur … » ouvre la liste infinie du thème."
        override val perTopicDescription = "Articles par thème"
        override val newsAgeOverline = "Période"
        override fun newsAge(age: NewsAge) = when (age) {
            NewsAge.DAY -> "Aujourd'hui"
            NewsAge.TWO_DAYS -> "2 jours"
            NewsAge.WEEK -> "Une semaine"
            NewsAge.ALL -> "Tout ce que livrent les sources"
        }
        override val newsAgeHint = "Un thème affiche toujours au moins ses trois articles les plus récents."
        override val showImages = "Afficher les images"
        override val showImagesOn = "Les photos des rédactions dans le fil"
        override val showImagesOff = "Seulement des couvertures sobres aux couleurs du thème – économise des données"
        override val readInApp = "Lire les articles dans l'appli"
        override val readInAppOn = "Texte intégral avec images – sans pub ni bandeau de cookies"
        override val readInAppOff = "Chaque article s'ouvre dans le navigateur"
        override val readableOnly = "Articles complets uniquement"
        override val readableOnlyOn =
            "L'appli vérifie chaque article avant de l'afficher : tu ne vois que des articles lisibles en entier dans l'appli – pas de paywall, pas de simple accroche, pas de mur de cookies. Pour cela, elle charge les articles à l'avance, aussi en données mobiles (consomme un peu de données) ; hors ligne, elle n'affiche que ceux déjà vérifiés."
        override val readableOnlyOff = "Tous les articles – certains ne se lisent en entier que sur le site de l'éditeur"
        override val hideRead = "Masquer les articles lus"
        override val hideReadOn = "Ce que tu as lu ou écouté quitte les listes à la prochaine actualisation – jamais sous ton doigt."
        override val hideReadOff = "Les articles lus restent dans les listes, simplement plus discrets."
        override val historyTitle = "Lus et masqués"
        override fun historySummary(read: Int, hidden: Int) =
            if (read == 0 && hidden == 0) {
                "Vide pour l'instant – tu retrouveras ici ce que tu as lu"
            } else {
                "$read ${if (read == 1) "lu" else "lus"} · $hidden ${if (hidden == 1) "masqué" else "masqués"}"
            }
        override val prefetchWhileReadableOnly =
            "« Articles complets uniquement » est activé : l'appli vérifie et précharge déjà les articles. Ce choix s'applique si tu le désactives."
        override val prefetch = "Précharger les articles"
        override fun prefetchMode(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Non"
            PrefetchMode.WIFI_ONLY -> "Wi-Fi seulement"
            PrefetchMode.ALWAYS -> "Toujours"
        }
        override fun prefetchHint(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Les articles ne se chargent qu'à l'ouverture."
            PrefetchMode.WIFI_ONLY -> "En Wi-Fi, l'appli précharge les articles à l'écran – ils s'ouvrent aussitôt, même hors ligne."
            PrefetchMode.ALWAYS -> "Précharge les articles à l'écran aussi en données mobiles."
        }

        override val weatherCardsOverline = "Afficher sur « Aujourd'hui »"
        override val weatherCardsHint = "« Dehors » avec la température est toujours là."
        override fun weatherCardTitle(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Alertes"
            WeatherCard.CURVE -> "Déroulé de la journée"
            WeatherCard.CLOTHING -> "Quoi porter"
        }
        override fun weatherCardText(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Verglas, tempête, canicule – quand quelque chose arrive"
            WeatherCard.CURVE -> "Température et pluie au fil de la journée"
            WeatherCard.CLOTHING -> "Ce que tu devrais porter aujourd'hui"
        }

        override val modeOverline = "Mode"
        override fun themeMode(mode: ThemeMode) = when (mode) {
            ThemeMode.SYSTEM -> "Système"
            ThemeMode.LIGHT -> "Clair"
            ThemeMode.DARK -> "Sombre"
        }
        override val accentOverline = "Couleur d'accent"
        override fun accentName(accent: AccentId) = when (accent) {
            AccentId.DYNAMIC -> "Material You"
            AccentId.MORGENROT -> "Aurore"
            AccentId.SONNE -> "Soleil"
            AccentId.OZEAN -> "Océan"
            AccentId.WALD -> "Forêt"
            AccentId.LAVENDEL -> "Lavande"
            AccentId.BEERE -> "Baie"
            AccentId.GRAPHIT -> "Graphite"
        }

        override val effectsOverline = "Effets"
        override fun effectsMode(mode: EffectsMode) = when (mode) {
            EffectsMode.AUTO -> "Automatique"
            EffectsMode.FULL -> "Effets complets"
            EffectsMode.LIGHT -> "Économe"
        }
        override fun effectsHint(tier: DeviceTier, reason: TierReason, chosen: Boolean): String {
            val verdict = if (tier == DeviceTier.FULL) "effets complets" else "économe"
            val why = when (reason) {
                TierReason.LOW_RAM_DEVICE, TierReason.LITTLE_MEMORY, TierReason.SLOW_CPU -> "plus fluide sur un matériel simple"
                TierReason.OLD_ANDROID -> "plus fluide sur un Android ancien"
                TierReason.POWER_SAVER -> "l'économiseur de batterie est activé"
                TierReason.CAPABLE -> "assez de puissance pour le verre et un ciel vivant"
            }
            val lead = when {
                !chosen -> "En automatique, ce serait"
                reason == TierReason.POWER_SAVER -> "Pour l'instant"
                else -> "Pour ce téléphone"
            }
            return "$lead : $verdict – $why"
        }

        override fun version(version: String) = "Version $version"
        override val testBuild = "Version de test"
        override val aboutApp = "Fonctionne entièrement sur ton téléphone : météo d'Open-Meteo, infos directement des flux des rédactions et de Google News. Pas de compte, pas de cloud, pas de pub."
        override val linkWeather = "Données météo"
        override val linkWeatherText = "Open-Meteo.com · CC BY 4.0"
        override val linkNews = "Actualités"
        override val linkNewsText = "Flux des rédactions et Google News · tous droits aux éditeurs"
        override val linkCode = "Code source"
        override val openWebsite = "Ouvrir le site"
        override val footer = "Fait pour des matins tranquilles"

        override val updateOverline = "Mise à jour"
        override val updateIdle = "Chercher de nouvelles versions"
        override val updateChecking = "Recherche de mises à jour …"
        override val updateUpToDate = "Tu es à jour"
        override fun updateAvailable(version: String) = "La version $version est là"
        override fun updateDownloading(version: String) = "Téléchargement de la version $version …"
        override val updateVerifying = "Vérification du paquet et de la signature …"
        override val updateNeedsPermission = "Autorise une fois, puis ça continue"
        override fun updateReady(version: String) = "La version $version est prête"
        override val updateFailed = "Ça n'a pas marché"
        override val updateCheck = "Chercher des mises à jour"
        override val updateCheckAgain = "Chercher à nouveau"
        override fun updateIsCurrent(app: String, version: String) = "$app $version est à jour."
        override val updateDownload = "Télécharger & installer"
        override val updatePermissionText = "Android le demande une seule fois : active « Autoriser cette source » et reviens – l'installation reprend aussitôt."
        override val updateConfirmInDialog = "Vérifié – confirme l'installation dans la fenêtre d'Android."
        override val updateVerified = "Téléchargé et vérifié."
        override val updateReopenInstaller = "Rouvrir l'installation"
        override val updateInstallNow = "Installer maintenant"
        override fun releasedOn(date: String) = "du $date"
        override val connecting = "Connexion …"
        override fun downloadedOf(received: String, total: String) = "$received sur $total"

        override fun briefingTimeSet(time: String) = "Ton briefing arrive désormais à $time."
        override val keepOneTopic = "Au moins un thème reste dans ton fil."
        override fun topicCreatedAddFeed(title: String) = "« $title » créé – ajoute maintenant une source."
        override fun topicCreatedSearch(title: String) = "« $title » est maintenant dans ton fil."
        override val topicDeleted = "Thème supprimé."
        override fun feedAdded(name: String) = "« $name » ajouté."
        override val feedRemoved = "Source supprimée."
        override val saveFailed = "Impossible d'enregistrer pour le moment."
        override fun placeAdded(name: String) = "Tu suis maintenant l'actualité de $name."
        override fun placeRemoved(name: String) = "$name n'est plus dans ton fil."
    }

    private object Es : SettingsStrings {
        override val pageOverline = "Tu mañana"
        override val pageTitle = "Ajustes"

        override val languageOverline = "De dónde y cómo"
        override val languageTitle = "Idioma y región"
        override val briefingOverline = "Tu despertador"
        override val briefingTitle = "Resumen matinal"
        override val topicsOverline = "Tu feed"
        override val topicsTitle = "Tus temas"
        override val sourcesOverline = "De dónde vienen las noticias"
        override val sourcesTitle = "Fuentes"
        override val feedOverline = "Cómo se lee tu feed"
        override val feedTitle = "Feed"
        override val lookOverline = "Tu estilo"
        override val lookTitle = "Apariencia"
        override val placesOverline = "Tu tiempo"
        override val placesTitle = "Lugares y franja horaria"
        override val appOverline = "Acerca de"
        override val appTitle = "App"

        override val statusNotifications = "Notificaciones"
        override val statusExactAlarm = "Hora exacta"
        override val statusBattery = "Batería"
        override val stateAllowed = "permitidas"
        override val stateNotAllowed = "no permitidas"
        override val stateChannelOff = "canal desactivado"
        override val stateUnrestricted = "sin restricciones"
        override val stateOptimized = "optimización activa"
        override val heroOffTitle = "Tu resumen matinal está desactivado"
        override fun heroOffSubtitle(time: String) = "Actívalo de nuevo abajo y llegará cada mañana a las $time."
        override fun heroReadyTitle(day: String, time: String) = "Todo listo para $day, $time"
        override val heroReadySubtitle = "El tiempo y las noticias llegan puntuales – no tienes que hacer nada más."
        override val heroNothingArrives = "Por ahora no te llega nada por la mañana"
        override fun heroAlmostReady(day: String, time: String) = "Casi listo para $day, $time"
        override fun heroProblems(count: Int) = "Aún faltan $count detalles – toca los puntos amarillos."
        override val hintNotifications = "Permite las notificaciones – toca el punto amarillo de abajo."
        override val hintExactAlarm = "Con «Hora exacta», tu resumen llega al minuto."
        override val hintBattery = "Saca la app de la optimización de batería para que Android no la frene por la mañana."
        override val sealOverlineReady = "Estado · todo bien"
        override val sealOverlineAttention = "Estado · revisar"
        override val sealOverlineOff = "Estado · en pausa"
        override val sealReady = "Todo listo"
        override val sealAttention = "Necesita tu atención"
        override val sealOff = "Desactivado"
        override val pillFix = "Solucionar"
        override val pillGoToSection = "Ir a la sección"
        override val samsungHint = "En Samsung, además: Información de la app → Batería → «Sin restricciones»."
        override val openAppInfo = "Abrir información de la app"

        override val dayAfterTomorrow = "pasado mañana"
        override fun onDate(date: String) = "el $date"
        override val countdownNow = "en un momento"
        override fun countdownMinutes(minutes: Long) = "en $minutes min"
        override fun countdownHours(hours: Long) = "en $hours h"
        override fun countdownHoursMinutes(hours: Long, minutes: Long) = "en $hours h $minutes min"
        override fun nextBriefingCountdown(day: String, countdown: String) = "Próximo resumen: $day · $countdown"
        override fun nextBriefingAt(day: String, time: String) = "Próximo resumen: $day a las $time"

        override val everyMorningAt = "Cada mañana a las"
        override val briefingSwitch = "Resumen matinal"
        override val changeTime = "Cambiar la hora"
        override fun timeDescription(time: String) = "Hora $time"
        override val clockSuffix = ""
        override val briefingIsOff = "Desactivado – por la mañana no llega ninguna notificación."
        override val testNotification = "Notificación de prueba"
        override val testNeedsPermission = "Primero permite las notificaciones."
        override val allow = "Permitir"
        override val testQueued = "Ya llega – la notificación de prueba está en camino."
        override val testFailed = "No se pudo iniciar la notificación de prueba."
        override val settingOpenFailed = "No se pudo abrir ese ajuste."

        override val timeDialogTitle = "¿A qué hora quieres tu resumen?"
        override val timeDialogDial = "Vista de reloj"
        override val timeDialogKeyboard = "Escribir la hora"
        override val apply = "Aplicar"

        override val inTheFeed = "En el feed"
        override val whileReading = "Al leer"
        override val perTopic = "Noticias por tema en «Para ti»"
        override val perTopicHint = "Después, «Todas las noticias de …» abre la lista infinita del tema."
        override val perTopicDescription = "Noticias por tema"
        override val newsAgeOverline = "Periodo"
        override fun newsAge(age: NewsAge) = when (age) {
            NewsAge.DAY -> "Hoy"
            NewsAge.TWO_DAYS -> "2 días"
            NewsAge.WEEK -> "Una semana"
            NewsAge.ALL -> "Todo lo que den las fuentes"
        }
        override val newsAgeHint = "Un tema siempre muestra al menos sus tres noticias más recientes."
        override val showImages = "Mostrar imágenes"
        override val showImagesOn = "Las fotos de las redacciones en el feed"
        override val showImagesOff = "Solo portadas sobrias en el color del tema – ahorra datos"
        override val readInApp = "Leer artículos en la app"
        override val readInAppOn = "Texto completo con imágenes – sin anuncios ni avisos de cookies"
        override val readInAppOff = "Cada artículo se abre en el navegador"
        override val readableOnly = "Solo artículos completos"
        override val readableOnlyOn =
            "La app comprueba cada artículo antes de mostrarlo: solo ves noticias que se pueden leer completas en la app – sin muro de pago, sin un simple adelanto, sin muro de cookies. Para ello carga los artículos por adelantado, también con datos móviles (gasta algunos datos); sin conexión solo muestra los ya comprobados."
        override val readableOnlyOff = "Todas las noticias – algunas solo se leen completas en la web del medio"
        override val hideRead = "Ocultar artículos leídos"
        override val hideReadOn = "Lo que has leído o escuchado sale de las listas en la próxima actualización – nunca bajo tu dedo."
        override val hideReadOff = "Lo leído se queda en las listas, solo más discreto."
        override val historyTitle = "Leídos y ocultos"
        override fun historySummary(read: Int, hidden: Int) =
            if (read == 0 && hidden == 0) {
                "Vacío por ahora – aquí encontrarás lo que has leído"
            } else {
                "$read ${if (read == 1) "leído" else "leídos"} · $hidden ${if (hidden == 1) "oculto" else "ocultos"}"
            }
        override val prefetchWhileReadableOnly =
            "«Solo artículos completos» está activado: la app ya comprueba y carga los artículos por adelantado. Esta opción vale si lo desactivas."
        override val prefetch = "Precargar artículos"
        override fun prefetchMode(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "No"
            PrefetchMode.WIFI_ONLY -> "Solo con wifi"
            PrefetchMode.ALWAYS -> "Siempre"
        }
        override fun prefetchHint(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Los artículos solo se cargan al abrirlos."
            PrefetchMode.WIFI_ONLY -> "Con wifi, la app precarga los artículos en pantalla – se abren al instante, incluso sin conexión."
            PrefetchMode.ALWAYS -> "Precarga los artículos en pantalla también con datos móviles."
        }

        override val weatherCardsOverline = "Mostrar en «Hoy»"
        override val weatherCardsHint = "«Afuera» con la temperatura siempre está."
        override fun weatherCardTitle(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Avisos"
            WeatherCard.CURVE -> "El día de un vistazo"
            WeatherCard.CLOTHING -> "Qué ponerse"
        }
        override fun weatherCardText(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Hielo, tormenta, calor – cuando algo se avecina"
            WeatherCard.CURVE -> "Temperatura y lluvia a lo largo del día"
            WeatherCard.CLOTHING -> "Lo que deberías ponerte hoy"
        }

        override val modeOverline = "Modo"
        override fun themeMode(mode: ThemeMode) = when (mode) {
            ThemeMode.SYSTEM -> "Sistema"
            ThemeMode.LIGHT -> "Claro"
            ThemeMode.DARK -> "Oscuro"
        }
        override val accentOverline = "Color de acento"
        override fun accentName(accent: AccentId) = when (accent) {
            AccentId.DYNAMIC -> "Material You"
            AccentId.MORGENROT -> "Amanecer"
            AccentId.SONNE -> "Sol"
            AccentId.OZEAN -> "Océano"
            AccentId.WALD -> "Bosque"
            AccentId.LAVENDEL -> "Lavanda"
            AccentId.BEERE -> "Frutos rojos"
            AccentId.GRAPHIT -> "Grafito"
        }

        override val effectsOverline = "Efectos"
        override fun effectsMode(mode: EffectsMode) = when (mode) {
            EffectsMode.AUTO -> "Automático"
            EffectsMode.FULL -> "Efectos completos"
            EffectsMode.LIGHT -> "Ligero"
        }
        override fun effectsHint(tier: DeviceTier, reason: TierReason, chosen: Boolean): String {
            val verdict = if (tier == DeviceTier.FULL) "efectos completos" else "ligero"
            val why = when (reason) {
                TierReason.LOW_RAM_DEVICE, TierReason.LITTLE_MEMORY, TierReason.SLOW_CPU -> "más fluido en hardware sencillo"
                TierReason.OLD_ANDROID -> "más fluido en un Android antiguo"
                TierReason.POWER_SAVER -> "el ahorro de batería está activado"
                TierReason.CAPABLE -> "potencia de sobra para el cristal y un cielo vivo"
            }
            val lead = when {
                !chosen -> "En automático sería"
                reason == TierReason.POWER_SAVER -> "Ahora mismo"
                else -> "Para este móvil"
            }
            return "$lead: $verdict – $why"
        }

        override fun version(version: String) = "Versión $version"
        override val testBuild = "Versión de prueba"
        override val aboutApp = "Funciona por completo en tu móvil: el tiempo de Open-Meteo, las noticias directamente de los feeds de las redacciones y de Google News. Sin cuenta, sin nube, sin anuncios."
        override val linkWeather = "Datos del tiempo"
        override val linkWeatherText = "Open-Meteo.com · CC BY 4.0"
        override val linkNews = "Noticias"
        override val linkNewsText = "Feeds de las redacciones y Google News · todos los derechos de los medios"
        override val linkCode = "Código fuente"
        override val openWebsite = "Abrir la web"
        override val footer = "Hecho para mañanas tranquilas"

        override val updateOverline = "Actualización"
        override val updateIdle = "Buscar versiones nuevas"
        override val updateChecking = "Buscando actualizaciones …"
        override val updateUpToDate = "Estás al día"
        override fun updateAvailable(version: String) = "Ya está aquí la versión $version"
        override fun updateDownloading(version: String) = "Descargando la versión $version …"
        override val updateVerifying = "Comprobando paquete y firma …"
        override val updateNeedsPermission = "Permítelo una vez y sigue solo"
        override fun updateReady(version: String) = "La versión $version está lista"
        override val updateFailed = "No ha funcionado"
        override val updateCheck = "Buscar actualizaciones"
        override val updateCheckAgain = "Buscar de nuevo"
        override fun updateIsCurrent(app: String, version: String) = "$app $version está al día."
        override val updateDownload = "Descargar e instalar"
        override val updatePermissionText = "Android lo pregunta una sola vez: activa «Permitir de esta fuente» y vuelve – la instalación sigue enseguida."
        override val updateConfirmInDialog = "Comprobado – confirma la instalación en el diálogo de Android."
        override val updateVerified = "Descargado y comprobado."
        override val updateReopenInstaller = "Volver a abrir la instalación"
        override val updateInstallNow = "Instalar ahora"
        override fun releasedOn(date: String) = "del $date"
        override val connecting = "Conectando …"
        override fun downloadedOf(received: String, total: String) = "$received de $total"

        override fun briefingTimeSet(time: String) = "Tu resumen llega ahora a las $time."
        override val keepOneTopic = "Al menos un tema se queda en tu feed."
        override fun topicCreatedAddFeed(title: String) = "«$title» creado – ahora añade una fuente."
        override fun topicCreatedSearch(title: String) = "«$title» ya está en tu feed."
        override val topicDeleted = "Tema eliminado."
        override fun feedAdded(name: String) = "«$name» añadida."
        override val feedRemoved = "Fuente eliminada."
        override val saveFailed = "No se ha podido guardar ahora mismo."
        override fun placeAdded(name: String) = "Ahora sigues las noticias de $name."
        override fun placeRemoved(name: String) = "$name ya no está en tu feed."
    }

    private object It : SettingsStrings {
        override val pageOverline = "Il tuo mattino"
        override val pageTitle = "Impostazioni"

        override val languageOverline = "Da dove e come"
        override val languageTitle = "Lingua e regione"
        override val briefingOverline = "La tua sveglia"
        override val briefingTitle = "Briefing del mattino"
        override val topicsOverline = "Il tuo feed"
        override val topicsTitle = "I tuoi temi"
        override val sourcesOverline = "Da dove arrivano le notizie"
        override val sourcesTitle = "Fonti"
        override val feedOverline = "Come si legge il tuo feed"
        override val feedTitle = "Feed"
        override val lookOverline = "Il tuo stile"
        override val lookTitle = "Aspetto"
        override val placesOverline = "Il tuo meteo"
        override val placesTitle = "Luoghi e fascia oraria"
        override val appOverline = "Info"
        override val appTitle = "App"

        override val statusNotifications = "Notifiche"
        override val statusExactAlarm = "Orario esatto"
        override val statusBattery = "Batteria"
        override val stateAllowed = "consentite"
        override val stateNotAllowed = "non consentite"
        override val stateChannelOff = "canale disattivato"
        override val stateUnrestricted = "senza restrizioni"
        override val stateOptimized = "ottimizzazione attiva"
        override val heroOffTitle = "Il tuo briefing del mattino è spento"
        override fun heroOffSubtitle(time: String) = "Riaccendilo qui sotto: arriverà ogni mattina alle $time."
        override fun heroReadyTitle(day: String, time: String) = "Tutto pronto per $day, $time"
        override val heroReadySubtitle = "Meteo e notizie arrivano puntuali – non devi fare altro."
        override val heroNothingArrives = "Per ora al mattino non ti arriva nulla"
        override fun heroAlmostReady(day: String, time: String) = "Quasi pronto per $day, $time"
        override fun heroProblems(count: Int) = "Mancano ancora $count piccole cose – tocca i punti gialli."
        override val hintNotifications = "Consenti le notifiche – tocca il punto giallo qui sotto."
        override val hintExactAlarm = "Con «Orario esatto» il tuo briefing arriva al minuto."
        override val hintBattery = "Togli l'app dall'ottimizzazione della batteria, così Android non la rallenta al mattino."
        override val sealOverlineReady = "Stato · tutto ok"
        override val sealOverlineAttention = "Stato · da controllare"
        override val sealOverlineOff = "Stato · in pausa"
        override val sealReady = "Tutto pronto"
        override val sealAttention = "Richiede la tua attenzione"
        override val sealOff = "Spento"
        override val pillFix = "Risolvi"
        override val pillGoToSection = "Vai alla sezione"
        override val samsungHint = "Su Samsung anche: Info app → Batteria → «Senza restrizioni»."
        override val openAppInfo = "Apri info app"

        override val dayAfterTomorrow = "dopodomani"
        override fun onDate(date: String) = "il $date"
        override val countdownNow = "a momenti"
        override fun countdownMinutes(minutes: Long) = "tra $minutes min"
        override fun countdownHours(hours: Long) = "tra $hours h"
        override fun countdownHoursMinutes(hours: Long, minutes: Long) = "tra $hours h $minutes min"
        override fun nextBriefingCountdown(day: String, countdown: String) = "Prossimo briefing: $day · $countdown"
        override fun nextBriefingAt(day: String, time: String) = "Prossimo briefing: $day alle $time"

        override val everyMorningAt = "Ogni mattina alle"
        override val briefingSwitch = "Briefing del mattino"
        override val changeTime = "Cambia orario"
        override fun timeDescription(time: String) = "Orario $time"
        override val clockSuffix = ""
        override val briefingIsOff = "Spento – al mattino non arriva nessuna notifica."
        override val testNotification = "Notifica di prova"
        override val testNeedsPermission = "Prima consenti le notifiche."
        override val allow = "Consenti"
        override val testQueued = "Arriva subito – la notifica di prova è in viaggio."
        override val testFailed = "Impossibile avviare la notifica di prova."
        override val settingOpenFailed = "Impossibile aprire questa impostazione."

        override val timeDialogTitle = "A che ora vuoi il tuo briefing?"
        override val timeDialogDial = "Quadrante"
        override val timeDialogKeyboard = "Digita l'orario"
        override val apply = "Applica"

        override val inTheFeed = "Nel feed"
        override val whileReading = "Durante la lettura"
        override val perTopic = "Notizie per tema in «Per te»"
        override val perTopicHint = "Poi «Tutte le notizie su …» apre la lista infinita del tema."
        override val perTopicDescription = "Notizie per tema"
        override val newsAgeOverline = "Periodo"
        override fun newsAge(age: NewsAge) = when (age) {
            NewsAge.DAY -> "Oggi"
            NewsAge.TWO_DAYS -> "2 giorni"
            NewsAge.WEEK -> "Una settimana"
            NewsAge.ALL -> "Tutto quello che danno le fonti"
        }
        override val newsAgeHint = "Un tema mostra sempre almeno le sue tre notizie più recenti."
        override val showImages = "Mostra immagini"
        override val showImagesOn = "Le foto delle redazioni nel feed"
        override val showImagesOff = "Solo copertine sobrie nel colore del tema – risparmia dati"
        override val readInApp = "Leggi gli articoli nell'app"
        override val readInAppOn = "Testo completo con immagini – senza pubblicità né banner dei cookie"
        override val readInAppOff = "Ogni articolo si apre nel browser"
        override val readableOnly = "Solo articoli completi"
        override val readableOnlyOn =
            "L'app verifica ogni articolo prima di mostrarlo: vedi solo notizie che si leggono per intero nell'app – niente paywall, niente semplici anteprime, niente muri dei cookie. Per questo carica gli articoli in anticipo, anche con i dati mobili (consuma qualche dato); offline mostra solo quelli già verificati."
        override val readableOnlyOff = "Tutte le notizie – alcune si leggono per intero solo sul sito della testata"
        override val hideRead = "Nascondi articoli letti"
        override val hideReadOn = "Ciò che hai letto o ascoltato lascia le liste al prossimo aggiornamento – mai sotto il dito."
        override val hideReadOff = "Gli articoli letti restano nelle liste, solo più discreti."
        override val historyTitle = "Letti e nascosti"
        override fun historySummary(read: Int, hidden: Int) =
            if (read == 0 && hidden == 0) {
                "Ancora vuoto – qui ritrovi ciò che hai letto"
            } else {
                "$read ${if (read == 1) "letto" else "letti"} · $hidden ${if (hidden == 1) "nascosto" else "nascosti"}"
            }
        override val prefetchWhileReadableOnly =
            "«Solo articoli completi» è attivo: l'app verifica e carica già gli articoli in anticipo. Questa scelta vale se lo disattivi."
        override val prefetch = "Precarica gli articoli"
        override fun prefetchMode(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "No"
            PrefetchMode.WIFI_ONLY -> "Solo Wi-Fi"
            PrefetchMode.ALWAYS -> "Sempre"
        }
        override fun prefetchHint(mode: PrefetchMode) = when (mode) {
            PrefetchMode.OFF -> "Gli articoli si caricano solo quando li apri."
            PrefetchMode.WIFI_ONLY -> "In Wi-Fi l'app precarica gli articoli sullo schermo – si aprono subito, anche offline."
            PrefetchMode.ALWAYS -> "Precarica gli articoli sullo schermo anche con i dati mobili."
        }

        override val weatherCardsOverline = "Mostra su «Oggi»"
        override val weatherCardsHint = "«Fuori» con la temperatura c'è sempre."
        override fun weatherCardTitle(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Allerte"
            WeatherCard.CURVE -> "Andamento della giornata"
            WeatherCard.CLOTHING -> "Cosa indossare"
        }
        override fun weatherCardText(card: WeatherCard) = when (card) {
            WeatherCard.WARNINGS -> "Ghiaccio, tempesta, caldo – quando qualcosa è in arrivo"
            WeatherCard.CURVE -> "Temperatura e pioggia nel corso della giornata"
            WeatherCard.CLOTHING -> "Cosa dovresti indossare oggi"
        }

        override val modeOverline = "Modalità"
        override fun themeMode(mode: ThemeMode) = when (mode) {
            ThemeMode.SYSTEM -> "Sistema"
            ThemeMode.LIGHT -> "Chiaro"
            ThemeMode.DARK -> "Scuro"
        }
        override val accentOverline = "Colore d'accento"
        override fun accentName(accent: AccentId) = when (accent) {
            AccentId.DYNAMIC -> "Material You"
            AccentId.MORGENROT -> "Aurora"
            AccentId.SONNE -> "Sole"
            AccentId.OZEAN -> "Oceano"
            AccentId.WALD -> "Bosco"
            AccentId.LAVENDEL -> "Lavanda"
            AccentId.BEERE -> "Frutti di bosco"
            AccentId.GRAPHIT -> "Grafite"
        }

        override val effectsOverline = "Effetti"
        override fun effectsMode(mode: EffectsMode) = when (mode) {
            EffectsMode.AUTO -> "Automatico"
            EffectsMode.FULL -> "Effetti completi"
            EffectsMode.LIGHT -> "Leggero"
        }
        override fun effectsHint(tier: DeviceTier, reason: TierReason, chosen: Boolean): String {
            val verdict = if (tier == DeviceTier.FULL) "effetti completi" else "leggero"
            val why = when (reason) {
                TierReason.LOW_RAM_DEVICE, TierReason.LITTLE_MEMORY, TierReason.SLOW_CPU -> "più fluido su hardware semplice"
                TierReason.OLD_ANDROID -> "più fluido su un Android datato"
                TierReason.POWER_SAVER -> "il risparmio energetico è attivo"
                TierReason.CAPABLE -> "potenza sufficiente per il vetro e un cielo vivace"
            }
            val lead = when {
                !chosen -> "In automatico sarebbe"
                reason == TierReason.POWER_SAVER -> "Al momento"
                else -> "Per questo telefono"
            }
            return "$lead: $verdict – $why"
        }

        override fun version(version: String) = "Versione $version"
        override val testBuild = "Versione di prova"
        override val aboutApp = "Funziona tutta sul tuo telefono: meteo da Open-Meteo, notizie direttamente dai feed delle redazioni e da Google News. Niente account, niente cloud, niente pubblicità."
        override val linkWeather = "Dati meteo"
        override val linkWeatherText = "Open-Meteo.com · CC BY 4.0"
        override val linkNews = "Notizie"
        override val linkNewsText = "Feed delle redazioni e Google News · tutti i diritti agli editori"
        override val linkCode = "Codice sorgente"
        override val openWebsite = "Apri il sito"
        override val footer = "Fatto per mattine tranquille"

        override val updateOverline = "Aggiornamento"
        override val updateIdle = "Cerca nuove versioni"
        override val updateChecking = "Cerco aggiornamenti …"
        override val updateUpToDate = "Sei aggiornato"
        override fun updateAvailable(version: String) = "È arrivata la versione $version"
        override fun updateDownloading(version: String) = "Scarico la versione $version …"
        override val updateVerifying = "Controllo pacchetto e firma …"
        override val updateNeedsPermission = "Consenti una volta, poi si prosegue"
        override fun updateReady(version: String) = "La versione $version è pronta"
        override val updateFailed = "Non ha funzionato"
        override val updateCheck = "Cerca aggiornamenti"
        override val updateCheckAgain = "Cerca di nuovo"
        override fun updateIsCurrent(app: String, version: String) = "$app $version è aggiornata."
        override val updateDownload = "Scarica e installa"
        override val updatePermissionText = "Android lo chiede una volta sola: attiva «Consenti da questa fonte» e torna qui – l'installazione prosegue subito."
        override val updateConfirmInDialog = "Verificato – conferma l'installazione nella finestra di Android."
        override val updateVerified = "Scaricato e verificato."
        override val updateReopenInstaller = "Riapri l'installazione"
        override val updateInstallNow = "Installa ora"
        override fun releasedOn(date: String) = "del $date"
        override val connecting = "Connessione …"
        override fun downloadedOf(received: String, total: String) = "$received di $total"

        override fun briefingTimeSet(time: String) = "Il tuo briefing ora arriva alle $time."
        override val keepOneTopic = "Almeno un tema resta nel tuo feed."
        override fun topicCreatedAddFeed(title: String) = "«$title» creato – ora aggiungi una fonte."
        override fun topicCreatedSearch(title: String) = "«$title» è ora nel tuo feed."
        override val topicDeleted = "Tema eliminato."
        override fun feedAdded(name: String) = "«$name» aggiunta."
        override val feedRemoved = "Fonte rimossa."
        override val saveFailed = "Non è stato possibile salvare in questo momento."
        override fun placeAdded(name: String) = "Ora segui le notizie da $name."
        override fun placeRemoved(name: String) = "$name non è più nel tuo feed."
    }
}

@Composable
@ReadOnlyComposable
fun settingsStrings(): SettingsStrings = SettingsStrings.of(LocalAppLanguage.current)
