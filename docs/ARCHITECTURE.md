# Daily Briefing – Architektur

Android-App für alle: Wetter („Vor der Tür“, Tagesverlauf, was anziehen) und Nachrichten aus
gefolgten Kategorien, Unterthemen, Suchen und Orten – über 22 Länder in 11 Sprachen (Region und
Sprache frei kombinierbar), in fünf Oberflächensprachen, mit einem Lesemodus, der den ganzen Artikel ohne Werbung in der App zeigt.
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
        │       mit „nur lesbar“ als -site:-Ausschlüsse (KnownWalls + gelernte Seiten; nur die
        │       erste Seite – ältere Zeitfenster ohne, sonst ignoriert Google das Fenster)
        │     · Region × Sprache ohne eigene Google-News-Ausgabe (USA auf Deutsch): Ausgabe der
        │       Sprache; Heimat-Themen und Orte als Suche nach dem Namen (CountryQuery)
        │     · Regions: Sender des Bundeslandes (nur Deutschland auf Deutsch) · eigene Feeds
        ├─ RssClient (OkHttp + RssParser) holt bis zu 6 parallel
        ├─ zusammenführen, gleiche Meldung (Link oder Überschrift) raus, neueste zuerst,
        │   ausgefallene Quellen behalten ihre letzten Meldungen (3 Tage)
        ├─ loadOlder: ältere Google-News-Zeitfenster (after:/before:) anhängen = endlose Liste
        │   (auch „Schlagzeilen“: Suche der Hauptressorts); Ergebnis GREW / EMPTY (ruhige Tage,
        │   nächster Aufruf sucht weiter zurück) / FAILED (offline) / END
        ├─ search(query, page): flüchtige Ergebnisse, Seiten = Zeitfenster; SearchQuery versteht
        │   die Anfrage (Ticker wie SLV exakt, Anführungszeichen, OR, -Wort), Hauptausgabe + US/GB
        │   parallel, SearchRanking sortiert nach Relevanz (ohne -site:, das Google lockert)
        └─ NewsCache: store/news/<thema>.json (Offline-Start)

Artikel öffnen / vorladen
  ArticleRepository ── LinkResolver (GoogleNewsDecoder, Cache store/google-news-links.json)
     → Seite der Redaktion (eigene Cookies je Artikel, Accept-Language der Ausgabe)
     → ArticleExtractor (Readability4J + jsoup, JSON-LD-Rückfall) → ArticleCleaner (Doppeltes raus)
     → Urteil OK / PAYWALL / CONSENT / BLOCKED / NOT_ARTICLE → ReadabilityStore lernt je Seite
     → ArticleCompleteness: vollständig oder nur Anriss (Abo-Hinweise, erklärte Länge, nur der
       Vorspann, abgeschnitten, Video-/Start-/Kursseite) → Status READABLE = „geprüft“
     → Cache store/articles/<id>.json (7 Tage), Urteil je Artikel store/verdicts.json (7 Tage)

Titelbilder (Meldungen ohne Bild im Feed: alle von Google News)
  CoverResolver ── vor der Liste: was im Bild ist und die nächste Seite, 3 parallel (sparsam 2),
     auf jeder Verbindung (auch mobil), unabhängig von „Artikel vorladen“; nicht mit „Bilder aus“
     → LinkResolver (aufgelöste Links werden nie erneut gefragt; bei 429/Fehlern Pause 2–30 min)
     → CoverLookup: nur der Seitenanfang bis </head> (≤ 384 kB, meist 8 kB) → og:image /
       twitter:image (CoverMeta), nur https
     → CoverStore store/covers.json (Bild 30 Tage, „kein Bild“ 12 Std.) → Karten zeigen es sofort
     → was Leser und Prefetcher finden (leadImage), zählt mit
  CoverPreloader (Coil): lädt die Bilder des nächsten Bildschirms in Kartengröße vor

„Nur vollständige Artikel“ (AppSettings.readableOnly)
  Listen (Für dich, Neueste, Themen, Entdecken) zeigen nur READABLE-Meldungen (VerifiedLists):
  geprüfte hinten angehängt, erste ungeprüfte hält die Liste an, Gezeigtes bleibt bis zum
  nächsten Aufbau; die nächsten ~20 Kandidaten (Für dich: was die Abschnitte füllt) prüft der
  ArticlePrefetcher vorab – 3 parallel (mit sparsamen Effekten 2, geparst auf zwei Threads
  niedriger Priorität), auch mobil, offline nichts (Hinweis, später weiter).
  Die Morgen-Benachrichtigung prüft je Thema die neuesten Kandidaten (höchstens 20 s).
```

* **Sprachen**: die Oberfläche spricht `AppLanguage` (DE, EN, FR, ES, IT; Standard: wie das
  Handy). Jeder Text steht in einer typisierten Strings-Datei je Bereich (`i18n/*Strings.kt`,
  Muster in `CommonStrings.kt`): ein Interface, ein Objekt je Sprache – fehlt eine Übersetzung,
  baut die App nicht. `LocalAppLanguage` wird in `AppRoot` aus den Einstellungen gesetzt;
  Worker, Benachrichtigungen und reine Logik bekommen die Sprache als Parameter.
* **Region und Nachrichten-Sprache** (`NewsEdition` = Land + Sprache, gespeichert als
  `news_edition` „US:de“): zwei Einstellungen, unabhängig voneinander und von der App-Sprache;
  jedes der 22 Länder (`COUNTRIES`) geht mit jeder der 11 Sprachen (`LANGUAGES`). Google News
  hat nur für 26 Paare eine eigene Ausgabe (`NewsEdition.ALL`, `isNative`). Für alle anderen
  leitet es stumm auf die Heimat-Ausgabe der Sprache um und lässt das Land fallen
  (`hl=de&gl=US&ceid=US:de` → Top-Meldungen und Rubriken aus Deutschland, „Inland“ und
  Ortsseiten leer; nur Suchen liefern – geprüft 15.11.2026). Darum fragt die App immer eine
  Ausgabe, die es gibt (`NewsEdition.google`), und holt das Land über den Namen:
  * **Heimat-Themen** (`Taxonomy.DOMESTIC`: Schlagzeilen, Politik, Inland, Wahlen, Wirtschaft,
    Konjunktur, Geld, Immobilien) werden zur Suche „Land + Themenwörter“
    (`TopicDef.queryFor`, `CountryQuery`: `USA OR "Vereinigte Staaten" Politik OR Regierung …`;
    Schlagzeilen = das Land in den letzten 7 Tagen) – ohne Redaktions-Feeds, die über die
    Heimatländer der Sprache berichten. Das endlose Nachladen sucht mit denselben Wörtern.
  * **Alle anderen Themen** (Welt, Tech, Wissenschaft, Börse, Sport …) lesen Rubrik, Suche und
    die länderunabhängigen Redaktions-Feeds der Sprache; Feeds, die an ein anderes Land
    gebunden sind, und die Bundesland-Sender (nur Deutschland auf Deutsch) bleiben draußen.
  * **Orte** (`TopicSources.placeSource`) liest die App in der Nachrichten-Sprache mit dem Land
    des Ortes als Region: gibt es die Ausgabe (Wien auf Deutsch, London auf Englisch), über
    die Ortsseite von Google News; sonst (Washington, D.C. auf Deutsch) als Suche nach dem
    Namen in den letzten 14 Tagen. Bringt das weniger als 12 Meldungen (`FeedSource.ENOUGH`),
    liest `RssClient.fetch` zusätzlich die Ortsseite in der Hauptsprache des Landes
    (`FeedSource.fallback`) – deren Meldungen tragen die Quellen-Id ihrer Ausgabe, damit
    Vorlesen und Silbentrennung ihre Sprache kennen (`ItemEdition`).
  Ein Sprachwechsel lässt die gefolgten Themen, wie sie sind; ein Regionswechsel tauscht nur
  einen unveränderten Standard-Feed (`EditionChange`). Eine Neuinstallation beginnt mit Land
  und Sprache des Handys (`NewsEdition.default`).
* **Themen** (`Taxonomy`, Fassade `Topics`): 12 Kategorien mit 47 Unterthemen (`politik/wahlen`,
  `wirtschaft/boerse` …), je mit `TopicIcon` (Material Symbols, `ui/news/TopicIcons.kt`), Farbe,
  Google-News-Rubrik und Suchbegriffen je Sprache. Titel kommen aus `TopicStrings`. Was der Feed
  zeigt, steht geordnet in `AppSettings.followed`: Kategorien, Unterthemen, `local:`-Orte und
  `custom:`-Themen (Feeds oder Suchbegriff). Ältere Installationen werden aus
  `topicOrder`/`hiddenTopics` übernommen (`SettingsMigration`).
* **Nachrichten** liest `RssClient` mit Androids `XmlPullParser` (kXML; in Unit-Tests dieselbe
  Implementierung über `kxml2`). `RssParser` versteht RSS 2.0, RSS 1.0 (RDF) und Atom;
  `GoogleNews` baut die URLs und liest Herausgeber und Domain aus `<source url>`; aus dem Titel
  fällt der angehängte Herausgeber, auch wenn die Seite ihn schon selbst im Titel trägt
  („… - Politik - SZ.de - SZ.de“, „… - boerse.de - boerse.de“ → nur die Überschrift). Artikel-Ids
  sind ein Hash des normalisierten Links. Was eine erste Seite nur mitschleppt, bleibt draußen
  (`NewsRepository.current`, Verlags-Feeds wie Google News): alles, was älter als 60 Tage ist
  (gemessen 15.11.2026: bei ZDFheute 25 von 205 Einträgen, 70 bis 2579 Tage alt; die Suche
  „KI“ bringt 7 Produkt- und Hilfeseiten von vor Jahren unter 100 Treffern) – außer die Quelle
  hat nichts Neueres; Älteres kommt über die endlose Liste. **Nie in einer Liste**
  (`isNoNews`): Rubrik- und Themenseiten (`/thema/…`), Nachschlagewerke (wikipedia.org) und
  alles, was älter ist, als das Blättern je reicht (395 Tage = 365 Tage Historie + letztes
  Fenster). Selbst hinzugefügte Feeds behalten alles.
  **Blättern ohne `-site:`**: die Zeitfenster der endlosen Liste werden ohne Ausschlüsse
  angefragt – mit ihnen antwortet Google News locker und füllt jede Seite mit Seiten weit
  außerhalb des Fensters auf (Wikipedia-Artikel von 2011, Uni-, Job- und Shopseiten: 1 bis 42
  je Fenster, ohne `-site:` keine einzige; 15.11.2026). Gesperrte Seiten fallen danach nach
  Domain weg, und `pageOf` lässt nur durch, was im Fenster liegt (± 2 Tage). Seiten, die
  frühere Versionen so geholt haben, wirft `upgraded` beim Lesen des Caches einmal weg
  (`TopicNews.format`).
* **Feed-Logik** (`ui/news/FeedLogic.kt`, `NewsModels.kt`): pro gefolgtem Thema eine Sektion
  („Für dich“), „Neueste“ über alle Themen ohne Doppelte, je Thema eine endlose Liste mit
  `loadMore` am Ende. Auch „Neueste“ und das Ende von „Für dich“ sind endlos (`FeedTimeline.kt`):
  alles, was die Themen haben, neueste zuerst (die Zeitraum-Einstellung begrenzt nur die Karten
  von „Für dich“), aber – jenseits der letzten 24 Stunden – nur so weit zurück, wie jedes noch
  blätterbare Thema gelesen ist; am Ende blättern die Themen mit der kürzesten Historie weiter
  (`loadOlder` im ViewModel, höchstens 4, sparsam 2 zugleich). Die Liste eines Themas hält es
  genauso (`readSoFar`): solange das Thema blättern kann, zeigt sie nur, was bis zur gelesenen
  Tiefe reicht (immer die neuesten 20 und die letzten 24 Stunden) – der alte Schwanz eines
  Verlags-Feeds wartet, bis das Blättern bei ihm ankommt; scheitert das Nachladen, zeigt sie
  alles, was da ist.
  **Ältere Seiten hängen nur unten an**: jede endlose Liste merkt sich ihre Reihenfolge
  (`FeedOrder`, im ViewModel von Aufbau zu Aufbau gereicht) und setzt sie fort (`appendOnly`) –
  was eine Seite bringt, steht darunter, auch wenn es dem Datum nach zwischen Gezeigtes gehörte
  (das Fenster einer Seite beginnt am Tag, an dem die Liste endet). Erst eine Aktualisierung
  (neues `fetchedAt`) sortiert neu. Listen-Einträge sind nach Artikel verschlüsselt, nicht nach
  Position. **Jede Geschichte einmal**: derselbe Artikel kommt über zwei Feeds eines Verlags
  („taz Politik“ und Google News' „taz.de“, „heise online“ und „heise IT“) mit zwei Links –
  `StoryTwins` erkennt ihn an Verlagsfamilie (Domain), fast gleicher Überschrift und fast
  gleicher Zeit; `NewsRepository.dedupe` faltet ihn je Thema, `storiesOnce` über die Themen
  hinweg. Der Artikel des Verlags gewinnt und übernimmt Bild und Teaser, die nur der Zwilling
  hat. **Früh nachladen** (`nearEnd`): aufgedeckt wird 12 Einträge vor dem Ende, Älteres
  angefragt 40 davor; mit „nur vollständige Artikel“ liegen 30 geprüfte Meldungen unter dem
  Bildschirm bereit, und nachgeladen wird, sobald weniger als 30 Kandidaten zu prüfen bleiben.
  Bringen Seiten nichts Zeigbares (120 Kandidaten geprüft, keiner vollständig – `BarrenLoads`),
  hört die Liste auf, von selbst zu fragen, und bietet „Weiter zurück suchen“ an, statt endlos
  „wird geprüft“ zu zeigen. „Nur vollständige Artikel“ filtert Meldungen gesperrter Seiten und zeigt
  nur vom `ArticlePrefetcher` geprüfte (`VerifiedLists.kt`, `ui/today/TodayWindows.kt`: was als
  Nächstes geprüft wird); ohne die Einstellung gehen sichtbare Meldungen zum Vorladen an den
  Prefetcher (Marke „Bezahlinhalt“/„Nur im Browser“ – vollständige Artikel tragen keine –, Bild für
  Google-News-Karten).
* **Lange Artikel ausblenden** (`AppSettings.hideLong`, Standard an; `data/article/LongReads`):
  der Prefetcher misst jeden gelesenen Artikel (extrahierte Wörter, sonst die erklärte Länge der
  Redaktion) und legt die Länge vor dem Urteil in `lengths` und mit ihm in `store/verdicts.json` ab
  – nie wird dafür neu geladen; ältere Urteile ohne Länge misst er aus dem Artikel-Cache. Mehr als
  900 Wörter (≈ 4–5 Minuten) gilt als lang. Mit „nur vollständige Artikel“ wird ein langer Artikel
  gar nicht erst als geprüft gezeigt (`Verification.long`); ohne die Einstellung fallen gemessene
  lange Artikel beim nächsten Aufbau nach einer Aktualisierung weg (`ReadabilityHides.longIds`),
  ungemessene zeigen sich sofort. Liveblogs und Ticker erkennt `isLiveBlog` an Überschrift oder
  Link und lässt sie immer weg.
* **Gelesen & ausgeblendet** (`data/local/ReadHistoryStore` → `store/read-history.json`): eine
  Meldung gilt als gelesen, wenn sie im Leser öffnet (`ArticleViewModel`), im Browser
  (`ShellNavigator.openArticle`, ⋮ „Im Browser öffnen“) oder angehört wird (`ListenController`
  meldet jedes neue Vorlesen); ⋮ „Ausblenden“ nimmt sie von Hand heraus. Je Geschichte ein
  Eintrag (Titel, Link, Quelle, Bild, Zeit, Thema, `readAt`, `hiddenAt`), erkannt wie beim
  Zusammenführen (`StoryIndex`: gleiche Id, gleicher – auch aufgelöster – Link, gleiche gefaltete
  Überschrift oder `StoryTwins`), höchstens 1000 Einträge und 60 Tage, beim Schreiben gekürzt;
  `state` (StateFlow `ReadHistory`) treibt Karten, Feed und Archiv. Der Feed (`buildFeed`,
  `ReadFilter`) lässt ausgeblendete sofort weg, gelesene mit „Gelesene Artikel ausblenden“ nur nach
  dem Stand des letzten Neuaufbaus (`TodayViewModel.readAtBuild`: Start, Aktualisieren,
  Tab-Wechsel) – gelesene Karten bleiben bis dahin, nur ruhiger. Die Reihenfolge der Listen wird
  vor dem Filtern gemerkt (wer wieder eingeblendet wird, steht an seinem Platz), und
  Weggelassenes zählt fürs Blättern mit (`heldBack`), damit eine Liste voller Gelesenem weiter
  nachlädt.
* **Benachrichtigung**: `MorningWorker` holt Wetter und Feeds, je gefolgtem Thema die neueste
  Schlagzeile (mit „nur vollständige Artikel“ die neueste geprüfte), `WeatherInsights.notification`
  baut den Text in der App-Sprache.
* **Updates**: die App prüft GitHub Releases von `CedrickGD/Daily-Briefing` ohne Token (der alte
  Repo-Name leitet weiter), lädt die APK, prüft die Signatur und übergibt sie dem Installer.
* **Standort**: `LocationFinder` holt einen groben Fix über `LocationManager` (ohne Play
  Services) und den Ortsnamen über Androids `Geocoder`.
* **Lesemodus – nichts doppelt**: `ArticleCleaner.clean` räumt auf, was die Seite selbst doppelt
  sagt (im Cache, `ArticleContent.FORMAT` = 5), `ArticleCleaner.arrange` passt den Text unter den
  Kopf des Lesers: Dort wird **einmal** entschieden, welches Bild oben steht (Feed-Bild, sonst
  `og:image`, sonst ein Bild der ersten drei Blöcke → `ArticleUiState.hero`), und jede Kopie davon
  fliegt aus dem Text (`ImageMatch`: gleiche Datei ohne Größenangaben, gleiche Id, ARD/ZDF-Varianten
  nach der Tilde). `showsAlt` (ui/article) druckt den Alt-Text nur ohne echte Bildunterschrift.
  Gemessen wird mit `ArticleDuplicateSurvey` (`MB_SURVEY_DIR`, aus dem normalen Testlauf heraus):
  echte Seiten speichern, offline durch die Pipeline schicken, alles Gezeigte auf Wiederholungen
  prüfen (Bilder auch per Pixel-Hash) und zwei Stände vergleichen (was fiel weg, steht es noch da?).

## Module (`app/src/main/java/com/cedrickgd/morgenbriefing`)

| Paket | Inhalt |
|---|---|
| `i18n` | `AppLanguage`, `LocalAppLanguage` und alle Texte (`CommonStrings`, `TopicStrings`, `FeedStrings`, `HistoryStrings`, `DiscoverStrings`, `SearchStrings`, `ReaderStrings`, `SettingsStrings`, `SourcesStrings`, `RegionStrings`, `OnboardingStrings`, `WeatherStrings`, `ClothingStrings`, `FormatStrings`, `NotifyStrings`, `ListenStrings`, `UpdateStrings`, `ComponentStrings`, `ShellStrings`) |
| `data/model` | `Topic`, `TopicIcon`, `FeedSource`, `NewsItem`, `TopicNews`, `NewsEdition`, `Topics` (Fassade), Wetter, `AppSettings` |
| `data/news` | `Taxonomy` (Kategorien + Unterthemen), `TopicSources` (Quellen eines Themas), `GoogleNews` (URLs, Suchbegriffe, Ausschlüsse, Zeitfenster, Kursseiten), `CountryQuery` (wie Nachrichten ein Land in einer Sprache nennen), `SearchQuery` (Suchanfrage verstehen, ganze Wörter), `SearchRanking` (Relevanz der Treffer), `FeedCatalog` + `Regions`, `RssParser`, `RssDates`, `HtmlText` |
| `data/remote` | `OpenMeteoClient`, `RssClient` (Feeds lesen, Feed hinter einer Website finden), `GoogleNewsDecoder` (Google-News-Link → Artikel-URL) |
| `data/local` | `WeatherStore`, `NewsCache` (JSON-Dateien, atomar geschrieben), `ReadHistoryStore` + `ReadHistory`/`StoryIndex`/`HistoryEntry` (gelesen & ausgeblendet) |
| `data` | `SettingsRepository` (DataStore) + `SettingsOps` (folgen, sortieren, Orte, Suchen) + `SettingsMigration`, `WeatherRepository`, `NewsRepository` |
| `data/article` | `ArticleExtractor`, `ArticleCleaner`, `PageSignals` (Bezahlschranke/Cookie-Wand), `ArticleCompleteness` (vollständig oder Anriss), `ArticleRepository`, `ReadabilityStore` (`store/readability.json`, Startliste `KnownWalls`), `ArticlePrefetcher` + `NetworkMeter` + `VerdictStore` (`store/verdicts.json`), `CoverResolver` + `CoverLookup` + `CoverStore` (`store/covers.json`: Titelbilder für Meldungen ohne Bild), `LinkResolver`, `SessionCookies`. Prüfung gegen echte Seiten: `ArticleLiveEvaluation` (`MB_LIVE=1`) |
| `domain` | `Condition`, `WeatherInsights`, `ClothingAdvisor` (Headline, Kleidung, Warnungen, Notification-Text in jeder Sprache), `Fmt` |
| `location` | `LocationFinder` („Mein Standort“) |
| `notify` | `AlarmScheduler`, `MorningAlarmReceiver`, `SystemEventsReceiver`, `MorningWorker`, `Notifications` |
| `update` | In-App-Updater: `UpdateChecker`, `ApkDownloader`, `ApkVerifier`, `ApkInstaller`, `UpdateWorker` |
| `perf` | Geräteklasse und Effekte: `DeviceTiers` (LOW/FULL, rein, getestet), `DeviceProbe` (Speicher, Low-RAM, schnellster Kern aus sysfs, Energiesparmodus), `Effects` (FULL/LIGHT aus Einstellung × Klasse), `Performance` (prozessweit im `AppContainer`), `BackgroundWork` (2 Parse-Threads niedriger Priorität) |
| `listen` | „Zuhören“: Text-to-Speech (Stimme in der Sprache der Nachrichten), Mini-Player mit Tempo-Chip, Audio-Fokus. „Vorlesen“ (`AppSettings.listen`: Engine, feste Sprache, Stimme je Sprache, Vorliebe weiblich/männlich, Tempo, Tonhöhe): `VoiceCatalog` (rein, getestet) macht aus `getVoices()` Listeneinträge („Stimme 1 · weiblich · offline“, lokal/online einmal, nicht installierte gezählt), `VoiceGenders` nennt ein Geschlecht nur, wenn die Stimme es verrät (Wort „female“/„male“, Samsung `SMTf…`/`SMTm…`, Tabelle der Google-Codes), `VoiceChoice` wählt: gewählt → Vorliebe → beste Offline-Stimme (wie früher) → Engine-Standard. Der `ListenController` setzt Stimme, Tempo und Tonhöhe vor jedem Vorlesen; Änderungen mitten im Artikel setzen am Satzanfang neu an. Tempo/Tonhöhe sind Faktoren auf die System-Einstellung der Sprachausgabe (1× = wie das Handy) |
| `ui/shell` | Wischbarer Pager Heute → Einstellungen, Glas-Seitenindikator, Chrome-Steuerung, `ShellNavigator` |
| `ui/glass` | Morgenglas: `Modifier.glass`, `GlassCard`, durchgehender Himmel (`AmbientSky`), Seitentitel/-leiste |
| `ui/today`, `ui/news` | Heute-Seite: kompakte Wetterkarte (`TodayWeather`), Feed-Leiste mit Suche und Tabs (`FeedChips`, pinnt oben), Glaskarten je Thema aus Listen-Stücken (`FeedCards` = reine Logik, `StoryCards`, `FeedSections`), endlose Listen, „Deine Themen“-Sheet, Vorladen |
| `ui/discover` | „Themen entdecken“: Kategorie-Kacheln, Kategorie mit Unterthemen und Vorschau, lokale Orte, gefolgte Suchen |
| `ui/search` | Suche: Feld, letzte Suchen, „Gerade wichtig“, endlose Ergebnisse (Relevanz/Neueste, Alle Sprachen/Nur Ausgabe, „EN“-Marke, mit „nur lesbar“ nur geprüfte: `SearchView`), „Diesem Thema folgen“ |
| `ui/settings` | Einstellungen: Status, Sprache & Region (App-Sprache, Nachrichten-Region, Nachrichten-Sprache: `EditionLogic`, `LanguageRegion`), lokale Nachrichten, Wecker, Deine Themen (sortieren, eigene), Quellen, Feed & Lesen, Vorlesen (`ListenSection`), Darstellung, Orte, Wetterblöcke, App/Updates |
| `ui/onboarding` | Erster Start: App-Sprache, Nachrichten (Region und Sprache getrennt), Benachrichtigungen, Ort |
| `ui/theme` | Theme, Akzent-Schemata, Typografie, `Reading` (Serif-Lesestile) |
| `ui/history` | „Gelesen & ausgeblendet“: `HistoryScreen` + `HistoryViewModel`, `HistoryLogic` (Suche, Filter, Tage – rein, getestet) |
| `ui/nav` | `MainRoute` (der Pager), `ArticleRoute`, `DiscoverRoute`, `SearchRoute`, `HistoryRoute` (darüber geschoben) |
| `ui/article` | `ArticleScreen` + `ArticleViewModel`: der Lesemodus (Bild, Überschrift, Teaser, Textblöcke, Zuhören, Teilen, Browser als Ausweg, eigene Zustände für Bezahlschranke, Cookie-Wand und Sperre) |

Kein DI-Framework: `AppContainer` (ein Objekt pro Prozess) wird über `context.container`
erreicht, Vorlesen über `context.listen`. ViewModels über `viewModelFactory { initializer { … } }`.

## Plattform-Regeln

* **minSdk 24 (Android 7.0)**, target/compile 36. `java.time` via Core-Library-Desugaring.
  Alles, was neuer ist, mit `Build.VERSION.SDK_INT`-Checks (Dynamic Color 31+, echte
  Glas-Unschärfe 31+ und nur mit vollen Effekten, sonst getönte Fläche, POST_NOTIFICATIONS 33+,
  exakte Alarme 31+, `getCurrentLocation` 30+).
* **Budget-Handys** (z. B. Galaxy A13: Exynos 850, 8 × Cortex-A55 @ 2,0 GHz, Mali-G52 MP1, 4 GB)
  laufen flüssig, ein Flaggschiff behält den vollen Look. `DeviceTiers` (`perf/DeviceTier.kt`)
  stuft ein Handy als **LOW** ein bei: Androids Low-RAM-Flag, API < 26, weniger als **5 GiB**
  gemeldetem Speicher (`totalMem`; ein „4 GB“-Handy meldet ≈ 3,6–3,8 GiB, ein „6 GB“-Handy
  ≈ 5,3–5,7 GiB), schnellstem Kern unter **2,2 GHz** (`cpuinfo_max_freq`, nur wenn lesbar;
  Emulatoren melden meist nichts – dann zählt der Speicher) oder Energiesparmodus (live über
  `ACTION_POWER_SAVE_MODE_CHANGED`). A13 → LOW, 6 GB / 2,4 GHz → FULL, S26 Ultra → FULL.
  Die Einstellung **Effekte** (`EffectsMode`: Automatisch · Volle Effekte · Sparsam, Standard
  Automatisch) ergibt mit der Klasse `Effects` FULL oder LIGHT; `AppRoot` stellt es als
  `LocalEffects` bereit, der Hintergrund fragt `Performance.light`. **LIGHT**: kein Haze (keine
  Blur-Quelle, keine Aufnahme-Layer, getöntes Glas wie vor Android 12, keine Fenster-Unschärfe
  hinter Dialogen), Himmel mit 10 fps und stehenden Partikeln (beim Scrollen still wie immer),
  keine dekorativen Schleifen (Siegel-Puls, wippendes Emoji; Equalizer mit 10 fps), Nachbarseiten
  im Pager ohne Offscreen-Tönung, Artikelprüfung 2 statt 3 parallel auf `BackgroundWork.parsing`,
  Bild-Speichercache 15 statt 20 %. **FULL**: alles wie bisher. „Animationen entfernen“ gilt in
  beiden.
* **Nichts Schweres auf dem Main-Thread**: Feed-Aufbau, Wetter-JSON, Artikel-Parsing, das
  Einstellungs-Mapping (`flowOn(Default)`), JSON-Caches (`NewsCache`, `VerdictStore`,
  `ReadabilityStore`, `GoogleNewsDecoder` – beim `warm()` auf IO geladen) und das Bild-Dekodieren
  (Coil, in Anzeigegröße) laufen im Hintergrund. Debug-Builds schalten StrictMode ein
  (Disk, Netzwerk, `noteSlowCall` → Log), Release-Builds nicht.
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
allen Seiten, Glasflächen darüber, Serif-Überschriften, News als Glaskarten (erste Meldung groß,
dann kompakte Zeilen, alles Weitere im ⋮-Menü), ruhige Bewegung, Barrierefreiheit
(Content-Descriptions, Touch-Ziele ≥ 48 dp, Kontrast ≥ 4.5:1, „Animationen entfernen“).
