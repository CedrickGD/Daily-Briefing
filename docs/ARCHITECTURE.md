# Daily Briefing – Architektur

Android-App für alle: Wetter („Vor der Tür“, Tagesverlauf, was anziehen) und Nachrichten aus
gefolgten Kategorien, Unterthemen, Suchen und Orten – aus 23 Länder-Ausgaben, in fünf
Oberflächensprachen, mit einem Lesemodus, der den ganzen Artikel ohne Werbung in der App zeigt.
Morgens eine Benachrichtigung mit Wetter und Schlagzeilen. Hell/Dunkel + Akzentfarben,
„Morgenglas“-Design ([DESIGN.md](DESIGN.md)). Alles läuft auf dem Gerät; es gibt keinen Server,
keine Cloud und keine KI. (Paket und applicationId heißen aus Update-Gründen weiter
`com.cedrickgd.morgenbriefing`.)

## Datenfluss

```
Öffnen · Herunterziehen · Morgenalarm (AlarmManager, exakt, Standard 06:30)
  ├─ WeatherRepository ── OpenMeteoClient ─────────► api.open-meteo.com (Wetter, Geocoding)
  │     └─ WeatherStore: store/weather/latest.json (Offline-Start)
  └─ NewsRepository (je gefolgtem Thema)
        ├─ TopicSources.resolve(thema, settings) = Quellen des Themas:
        │     · FeedCatalog: Redaktions-Feeds der Ausgabe (Sprache/Land), dem (Unter-)Thema zugeordnet
        │     · GoogleNews: Rubrik, Top-Meldungen oder Suche (Taxonomy-Suchbegriff je Sprache,
        │       Ort mit Bundesstaat, eigener Suchbegriff) in der Ausgabe des Themas;
        │       mit „nur lesbar“ als -site:-Ausschlüsse (KnownWalls + gelernte Seiten)
        │     · Regions: Sender des Bundeslandes (nur deutsche Ausgaben) · eigene Feeds
        ├─ RssClient (OkHttp + RssParser) holt bis zu 6 parallel
        ├─ zusammenführen, gleiche Meldung (Link oder Überschrift) raus, neueste zuerst,
        │   ausgefallene Quellen behalten ihre letzten Meldungen (3 Tage)
        ├─ loadMore: ältere Google-News-Zeitfenster (after:/before:) anhängen = endlose Liste
        ├─ search(query, page): flüchtige Ergebnisse, Seiten = Zeitfenster; SearchQuery versteht
        │   die Anfrage (Ticker wie SLV exakt, Anführungszeichen, OR, -Wort), Hauptausgabe + US/GB
        │   parallel, SearchRanking sortiert nach Relevanz (ohne -site:, das Google lockert)
        └─ NewsCache: store/news/<thema>.json (Offline-Start)

Artikel öffnen / vorladen
  ArticleRepository ── LinkResolver (GoogleNewsDecoder, Cache store/google-news-links.json)
     → Seite der Redaktion (eigene Cookies je Artikel, Accept-Language der Ausgabe)
     → ArticleExtractor (Readability4J + jsoup, JSON-LD-Rückfall) → ArticleCleaner (Doppeltes raus)
     → Urteil OK / PAYWALL / CONSENT / BLOCKED / NOT_ARTICLE → ReadabilityStore lernt je Seite
     → Cache store/articles/<id>.json (7 Tage)
```

* **Sprachen**: die Oberfläche spricht `AppLanguage` (DE, EN, FR, ES, IT; Standard: wie das
  Handy). Jeder Text steht in einer typisierten Strings-Datei je Bereich (`i18n/*Strings.kt`,
  Muster in `CommonStrings.kt`): ein Interface, ein Objekt je Sprache – fehlt eine Übersetzung,
  baut die App nicht. `LocalAppLanguage` wird in `AppRoot` aus den Einstellungen gesetzt;
  Worker, Benachrichtigungen und reine Logik bekommen die Sprache als Parameter.
* **Ausgabe** (`NewsEdition`): Land + Sprache der Nachrichten, unabhängig von der App-Sprache.
  Ein lokaler Ort im Ausland liest die Ausgabe seines Landes (`NewsEdition.forCountry`).
* **Themen** (`Taxonomy`, Fassade `Topics`): 12 Kategorien mit 47 Unterthemen (`politik/wahlen`,
  `wirtschaft/boerse` …), je mit `TopicIcon` (Material Symbols, `ui/news/TopicIcons.kt`), Farbe,
  Google-News-Rubrik und Suchbegriffen je Sprache. Titel kommen aus `TopicStrings`. Was der Feed
  zeigt, steht geordnet in `AppSettings.followed`: Kategorien, Unterthemen, `local:`-Orte und
  `custom:`-Themen (Feeds oder Suchbegriff). Ältere Installationen werden aus
  `topicOrder`/`hiddenTopics` übernommen (`SettingsMigration`).
* **Nachrichten** liest `RssClient` mit Androids `XmlPullParser` (kXML; in Unit-Tests dieselbe
  Implementierung über `kxml2`). `RssParser` versteht RSS 2.0, RSS 1.0 (RDF) und Atom;
  `GoogleNews` baut die URLs und liest Herausgeber und Domain aus `<source url>`. Artikel-Ids
  sind ein Hash des normalisierten Links.
* **Feed-Logik** (`ui/news/FeedLogic.kt`, `NewsModels.kt`): pro gefolgtem Thema eine Sektion
  („Für dich“), „Neueste“ über alle Themen ohne Doppelte, je Thema eine endlose Liste mit
  `loadMore` am Ende; „nur lesbar“ filtert Meldungen gesperrter Seiten; sichtbare Meldungen
  gehen an den `ArticlePrefetcher` (Status „Volltext“/„Bezahlinhalt“, Bild für Google-News-Karten).
* **Benachrichtigung**: `MorningWorker` holt Wetter und Feeds, je gefolgtem Thema die neueste
  Schlagzeile, `WeatherInsights.notification` baut den Text in der App-Sprache.
* **Updates**: die App prüft GitHub Releases von `CedrickGD/Daily-Briefing` ohne Token (der alte
  Repo-Name leitet weiter), lädt die APK, prüft die Signatur und übergibt sie dem Installer.
* **Standort**: `LocationFinder` holt einen groben Fix über `LocationManager` (ohne Play
  Services) und den Ortsnamen über Androids `Geocoder`.

## Module (`app/src/main/java/com/cedrickgd/morgenbriefing`)

| Paket | Inhalt |
|---|---|
| `i18n` | `AppLanguage`, `LocalAppLanguage` und alle Texte (`CommonStrings`, `TopicStrings`, `FeedStrings`, `DiscoverStrings`, `SearchStrings`, `ReaderStrings`, `SettingsStrings`, `SourcesStrings`, `RegionStrings`, `OnboardingStrings`, `WeatherStrings`, `ClothingStrings`, `FormatStrings`, `NotifyStrings`, `ListenStrings`, `UpdateStrings`, `ComponentStrings`, `ShellStrings`) |
| `data/model` | `Topic`, `TopicIcon`, `FeedSource`, `NewsItem`, `TopicNews`, `NewsEdition`, `Topics` (Fassade), Wetter, `AppSettings` |
| `data/news` | `Taxonomy` (Kategorien + Unterthemen), `TopicSources` (Quellen eines Themas), `GoogleNews` (URLs, Suchbegriffe, Ausschlüsse, Zeitfenster, Kursseiten), `SearchQuery` (Suchanfrage verstehen, ganze Wörter), `SearchRanking` (Relevanz der Treffer), `FeedCatalog` + `Regions`, `RssParser`, `RssDates`, `HtmlText` |
| `data/remote` | `OpenMeteoClient`, `RssClient` (Feeds lesen, Feed hinter einer Website finden), `GoogleNewsDecoder` (Google-News-Link → Artikel-URL) |
| `data/local` | `WeatherStore`, `NewsCache` (JSON-Dateien, atomar geschrieben) |
| `data` | `SettingsRepository` (DataStore) + `SettingsOps` (folgen, sortieren, Orte, Suchen) + `SettingsMigration`, `WeatherRepository`, `NewsRepository` |
| `data/article` | `ArticleExtractor`, `ArticleCleaner`, `PageSignals` (Bezahlschranke/Cookie-Wand), `ArticleRepository`, `ReadabilityStore` (`store/readability.json`, Startliste `KnownWalls`), `ArticlePrefetcher` + `NetworkMeter`, `LinkResolver`, `SessionCookies`. Prüfung gegen echte Seiten: `ArticleLiveEvaluation` (`MB_LIVE=1`) |
| `domain` | `Condition`, `WeatherInsights`, `ClothingAdvisor` (Headline, Kleidung, Warnungen, Notification-Text in jeder Sprache), `Fmt` |
| `location` | `LocationFinder` („Mein Standort“) |
| `notify` | `AlarmScheduler`, `MorningAlarmReceiver`, `SystemEventsReceiver`, `MorningWorker`, `Notifications` |
| `update` | In-App-Updater: `UpdateChecker`, `ApkDownloader`, `ApkVerifier`, `ApkInstaller`, `UpdateWorker` |
| `listen` | „Zuhören“: Text-to-Speech (Stimme in der Sprache der Nachrichten), Mini-Player, Audio-Fokus |
| `ui/shell` | Wischbarer Pager Heute → Einstellungen, Glas-Seitenindikator, Chrome-Steuerung, `ShellNavigator` |
| `ui/glass` | Morgenglas: `Modifier.glass`, `GlassCard`, durchgehender Himmel (`AmbientSky`), Seitentitel/-leiste |
| `ui/today`, `ui/news` | Heute-Seite, News-Feed (Chips, Sektionen mit Themen-Icons, endlose Listen, Themen-Sheet, Vorladen) |
| `ui/discover` | „Themen entdecken“: Kategorie-Kacheln, Kategorie mit Unterthemen und Vorschau, lokale Orte, gefolgte Suchen |
| `ui/search` | Suche: Feld, letzte Suchen, „Gerade wichtig“, endlose Ergebnisse (Relevanz/Neueste, Alle Sprachen/Nur Ausgabe, „EN“-Marke, mit „nur lesbar“ nur geprüfte: `SearchView`), „Diesem Thema folgen“ |
| `ui/settings` | Einstellungen: Status, Sprache & Region, lokale Nachrichten, Wecker, Deine Themen (sortieren, eigene), Quellen, Feed & Lesen, Darstellung, Orte, Wetterblöcke, App/Updates |
| `ui/onboarding` | Erster Start: Sprache, Ausgabe, Benachrichtigungen, Ort |
| `ui/theme` | Theme, Akzent-Schemata, Typografie, `Reading` (Serif-Lesestile) |
| `ui/nav` | `MainRoute` (der Pager), `ArticleRoute`, `DiscoverRoute`, `SearchRoute` (darüber geschoben) |
| `ui/article` | `ArticleScreen` + `ArticleViewModel`: der Lesemodus (Bild, Überschrift, Teaser, Textblöcke, Zuhören, Teilen, Browser als Ausweg, eigene Zustände für Bezahlschranke, Cookie-Wand und Sperre) |

Kein DI-Framework: `AppContainer` (ein Objekt pro Prozess) wird über `context.container`
erreicht, Vorlesen über `context.listen`. ViewModels über `viewModelFactory { initializer { … } }`.

## Plattform-Regeln

* **minSdk 24 (Android 7.0)**, target/compile 36. `java.time` via Core-Library-Desugaring.
  Alles, was neuer ist, mit `Build.VERSION.SDK_INT`-Checks (Dynamic Color 31+, echte
  Glas-Unschärfe 31+ sonst getönte Fläche, POST_NOTIFICATIONS 33+, exakte Alarme 31+,
  `getCurrentLocation` 30+).
* Android 7 kennt die Let's-Encrypt-Wurzel nicht: `network_security_config.xml` bündelt
  ISRG Root X1/X2 zusätzlich zu den System-Zertifikaten. Klartext-`http://` ist aus; Feeds und
  Bilder gehen nur über https.
* **Edge-to-edge** auf allen Versionen, Insets korrekt für **Gesten- und 3-Tasten-Navigation**.
  Keine Leiste unten: zwischen den Seiten wird gewischt, der Glas-Indikator sitzt über der
  Navigationsleiste.
* **Predictive Back**: Zurück auf „Einstellungen“ gleitet zu „Heute“.
* Exakter Alarm: `setExactAndAllowWhileIdle`; ohne Berechtigung Fallback `setAndAllowWhileIdle`.
  Neu planen bei Boot, Update, Zeit-/Zeitzonenwechsel und nach jedem Auslösen.
* Samsung-Akku-Optimierung: die Einstellungen führen zu „Nicht eingeschränkt“.
* Nichts Geheimes auf dem Gerät: Einstellungen und Caches nehmen an Android-Backups teil.

## Design

Siehe [DESIGN.md](DESIGN.md): Morgenglas – ein durchgehender, wetterabhängiger Himmel hinter
allen Seiten, Glasflächen darüber, Serif-Überschriften, redaktioneller Feed (großes Bild, große
Serif-Headline, Lead, Meta-Zeile mit Zuhören/Teilen), ruhige Bewegung, Barrierefreiheit
(Content-Descriptions, Touch-Ziele ≥ 48 dp, Kontrast ≥ 4.5:1, „Animationen entfernen“).
