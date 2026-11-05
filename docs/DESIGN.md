# Daily Briefing – Designsprache „Morgenglas“

Ziel: Die App soll sich anfühlen wie ein ruhiger, lebendiger Morgenhimmel, über dem Inhalte als
**Glas** schweben – von oben bis ganz unten, auf jeder Seite. Kein flacher Hintergrund, keine
„tote“ Liste. Vorbild ist die Glas-Ästhetik von iOS, aber eigenständig, warm und materialgerecht
für Android.

## 1. Bühne: der Himmel ist immer da

* **AmbientSky** liegt einmal hinter der ganzen App (hinter allen drei Seiten), nicht pro Screen.
  Beim Wischen zwischen den Seiten bleibt der Himmel stehen und verschiebt sich nur leicht
  (Parallaxe ~12 % der Seitenbreite) – die Seiten gleiten darüber.
* Aufbau, von hinten nach vorn:
  1. Grundfarbe = `colorScheme.background`.
  2. Vertikaler Verlauf über die **volle Höhe**: oben die Himmelsfarben des aktuellen Wetters und
     der Tageszeit (Dämmerung vor Sonnenaufgang, Tag, Abend, Nacht), nach unten in einen
     tieferen, leicht akzentgetönten Ton – unten ist es nie einfarbig flach; ein weiches, warmes
     Horizont-Glühen steigt vom unteren Rand auf.
  3. 3–4 große, weiche Farbflächen („Aurora“, radiale Verläufe, 60–90 % der Breite), die sich
     in 60–120 s langsam bewegen; Farben aus Himmel + `primaryContainer`/`tertiaryContainer`.
  4. Wetterpartikel (Nebelbänder, Regen, Schnee, Sterne, Sonnenschimmer) in der oberen Hälfte.
* Dunkelmodus: tiefes Nachtblau/Indigo, Aurora gedämpft, Sterne. Hellmodus: luftig, pastellig.
* „Animationen entfernen“ → alles statisch.
* Sparsame Effekte (§9) → die Aurora driftet mit 10 fps, die Partikel stehen.

## 2. Glas

* Umsetzung mit **Haze** (`dev.chrisbanes.haze`): Der Himmel ist `hazeSource` (zIndex 0), der
  scrollende Inhalt jeder Seite ebenfalls (`glassContent()`, zIndex 1). Glas **im** Inhalt (Karten,
  Chips) zeigt nur den unscharfen Himmel; **schwebendes** Glas darüber (Kopfleiste, Indikator,
  Mini-Player, Leser-Steuerung) zeigt Himmel **und** Inhalt – Bilder gleiten farbig verschwommen
  darunter durch. Ab Android 12 mit vollen Effekten echte Unschärfe; darunter oder mit
  sparsamen Effekten (§9) kein Haze, sondern ein milchiger Tint:
  im Inhalt dichter (Tint + 30 %), schwebend praktisch deckend (97 %), damit kein Text durchscheint.
* Tint: hell = `surface`; dunkel = ein **gehobener Nachtton** (`surfaceContainerHighest` + Hauch
  `primaryContainer`, etwas Weiß) – dunkles Glas ist heller als der Himmel dahinter, nie ein Loch.
* Stufen (Licht / Dunkel):

  | Stufe | Blur | Tint | Rauschen | Einsatz |
  |---|---|---|---|---|
  | `Thin` | 20 dp | 34 % / 40 % | 0.05 | Chips, Pills |
  | `Regular` | 28 dp | 50 % / 36 % | 0.06 | Karten, Indikator, Mini-Player |
  | `Thick` | 36 dp | 62 % / 58 % | 0.07 | Dialoge, Sheets, Kopfleisten |

  Schwebendes Glas hat einen Mindest-Tint von 62 % / 66 %, damit Symbole und Text auf ihm auch über
  sehr hellen oder dunklen Fotos lesbar bleiben (Symbole in voller `onSurface`-Farbe).

* **Kante**: 1 dp Verlaufsrand (oben links Weiß 55 %/30 % → unten rechts Weiß 8 %/5 %),
  dazu ein zarter Innen-Glanz oben (2–3 dp, Weiß 16 %/10 %). Kopfleisten zeigen keine Kante am
  oberen Bildschirmrand; Inhalt läuft unter ihrer Unterkante weich aus (≈ 18 dp Verlauf).
* Schwebendes Glas nimmt **jede Berührung** in seiner Fläche an – nichts darunter wird aus
  Versehen ausgelöst. Kein harter Schatten; höchstens
  ein sehr weicher Umgebungsschatten (8–16 dp, ≤ 10 % Schwarz) für schwebende Elemente.
* Radien: Karten **28 dp**, Kacheln/Bilder 22 dp, Chips 16 dp, Pills voll rund.
* Text auf Glas muss ≥ 4.5:1 Kontrast haben – lieber stärkerer Tint als blasser Text.

## 3. Navigation ohne Leiste

* Zwei Seiten nebeneinander in einem **HorizontalPager**: **Heute → Einstellungen**.
  Start ist „Heute“. Zurück-Geste auf Einstellungen → sanft zu „Heute“.
* Keine Bottom-Bar. Stattdessen ein **schwebender Glas-Indikator** (Pill, 44 dp hoch, mittig,
  12 dp über der Systemleiste): zwei kleine Symbole (Sonne · Zahnrad); eine farbige
  „Flüssigkeits“-Kapsel gleitet mit dem Wischen von Symbol zu Symbol (folgt
  `currentPageOffsetFraction`). Antippen springt zur Seite. Er blendet beim Runterscrollen aus
  und beim Hochscrollen, an der Spitze und ~0,65 s nach Stillstand (auch am Listenende) wieder
  ein – er soll sich nie wie eine Leiste anfühlen. Während eine Liste scrollt, hält die Aurora
  still. Mit 3-Tasten-Navigation hebt ein weicher Verlauf die Systemtasten vom Inhalt ab.
* Haptik: leichter Tick beim Seitenwechsel.
* Artikel öffnen im Reader der App (§7: Bild unter der Statusleiste, Serif-Überschrift, Textblöcke, Glas-Controls); „Im Browser öffnen“ bleibt als Ausweg; Sheets (Themen, Ort)
  liegen als Glas über der Seite.
* Innere horizontale Scroller (Chip-Reihe, Leser-Pager) haben Vorrang vor dem Seitenwischen:
  was die Chip-Reihe am Anfang/Ende nicht mehr nutzt, verschluckt sie (kein Seitenwechsel).
* Querverweise in die Einstellungen landen am Ziel („Ort wählen“ → Orte mit offenem Picker,
  „Quellen & eigene Themen“ → Quellen).

## 4. Seitenkopf

* Kein `LargeTopAppBar` mehr. Jede Seite beginnt direkt unter der Statusleiste (+12 dp) mit
  * einer kleinen Overline (Label, Versalien, Sperrung) – z. B. „DEIN ARCHIV“, „DEIN MORGEN“,
  * dem Seitentitel in Serif, **displaySmall (36 sp)**, linksbündig.
* Beim Scrollen erscheint oben eine schmale **Glas-Kopfleiste** (Statusleiste + 52 dp) mit dem
  Titel klein (titleLarge) – Inhalt gleitet darunter weich weg. Antippen der Leiste scrollt nach
  oben; Berührungen auf ihr erreichen nie den Inhalt darunter.

## 5. Typografie & Rhythmus

* Serif (SemiBold) für Seitentitel, Schlagzeilen, große Zahlen (Temperatur, Uhrzeit).
* Sans für alles andere; Fließtext 16 sp / 1.55, Lead im Feed 18 sp / 1.5.
* Raster 4 dp; Außenabstand 16 dp, zwischen Karten 14–16 dp, in Karten 20 dp.
* Jede Sektion hat eine Overline + Titel; nie zwei gleich aussehende Blöcke direkt
  untereinander ohne Hierarchie.

## 6. Bewegung

* Federn statt linearer Tweens (`spring(dampingRatio 0.8, stiffness MediumLow)`).
* Inhalte erscheinen gestaffelt (40–60 ms Versatz), Zahlen zählen hoch, Karten heben sich beim
  Drücken minimal (Scale 0.98).
* Seiten im Pager: Nachbarseiten leicht verkleinert (0.94) und in die Himmelsfarbe getönt (nicht
  transparent – nichts scheint durch ihre Schrift), während man wischt.

## 7. Seiten im Detail

**Heute** – Kopf (Datum, Gruß, Serif-Headline) direkt auf dem Himmel; darunter Glas-Karten:
„Vor der Tür“ (große Temperatur), Warnungen (getönte Glas-Banner), Tageskurve, Anziehen
(Kacheln). Danach der **News-Feed** im Stil der Nutzer-Referenz
(`.claude/design-ref/news-feed-reference.jpg`, Google-Discover-artig, editorial):

* **Themen-Chips** in einer horizontalen Reihe: „Für dich“ (alle Themen in Nutzer-Reihenfolge,
  je Thema eine Sektion mit den ersten Meldungen und „Alle … Meldungen zu …“), „Neueste“ (alles
  chronologisch), dann je ein Chip pro sichtbarem Thema (alle Meldungen des Themas, endlos:
  die Liste enthüllt sich seitenweise beim Scrollen und endet mit „Das war's · n Meldungen“).
  Glas-Pills; aktiver Chip gefüllt in Themen-/Akzentfarbe. Erreicht die Chip-Reihe die
  Glas-Kopfleiste, nimmt die Leiste sie auf: **eine** Glasfläche (Titel + Chips) mit einer Kante.
* Hinweise (offline, Quellen antworten nicht, alles ausgeblendet) als schmales getöntes Band über
  dem Feed – keine Kartenstapel vor dem ersten Artikel.
* Themen beginnen mit einer feinen Linie, der Icon-Kachel des Themas (Material Symbol auf getönter Fläche in Themenfarbe) und „TECH & KI … 37 Meldungen“ (keine Pill). Themen haben **Icons, keine Emojis** (nur alte eigene Themen behalten ihr Emoji).
* Ohne Bild im Feed (oder mit „Bilder anzeigen“ aus) ein **typografisches Cover**: Mesh-Verlauf
  in Themenfarbe, große Serif-Initiale als Wasserzeichen, kleines Glas-Badge mit Thema-Icon. Rechts neben den Chips ein runder Glas-Button „Themen“ (Herz-mit-Plus-Symbol) →
  Bottom-Sheet zum Ein-/Ausblenden und Sortieren, mit Sprung zu „Quellen & eigene Themen“.
* **Feed-Eintrag ohne Kartenrahmen**, direkt auf dem Hintergrund: Bild in voller Breite,
  22–24 dp Radius, ~16:10 (oder generiertes Cover in Themenfarbe); darunter die **Schlagzeile
  groß in Serif** (headlineSmall, ~26 sp, 3 Zeilen max.); darunter der Lead in **Serif**
  (bodyLarge ~18 sp, onSurfaceVariant, 3 Zeilen) mit angehängtem „… **Mehr anzeigen**“;
  dann die Meta-Zeile: „vor 3 Std. · Quelle“ links, rechts **„🎧 Zuhören“**, Teilen, ⋮-Menü
  (Thema ausblenden, Quellen anzeigen, Link kopieren). 24–32 dp Abstand zwischen Einträgen,
  dezente Trennlinie optional. Antippen (Bild/Schlagzeile/Text) → Artikel-Leser.
* **Zuhören**: Android TextToSpeech (in der Sprache der Nachrichten) liest Schlagzeile + Teaser vor; ein schwebender
  Glas-Mini-Player (über dem Seitenindikator) zeigt Titel, Pause/Weiter, Stopp; Antippen öffnet
  den Artikel.
* Unten ein ruhiger Ausklang (Stand, Anzahl Meldungen, kleines „Bis später ☀️“).

**Themen entdecken** – über die Seite geschoben (wie der Reader): 12 große Kategorie-Kacheln im
Raster, jede ein Verlauf in Themenfarbe mit weichem Glanz, großem Icon als Wasserzeichen, Titel,
Untertitel und Zähler (gefolgt/Unterthemen). Antippen → Kategorie: farbiger Kopf, „Ganze
Kategorie folgen“, die drei neuesten Schlagzeilen, darunter die Unterthemen als Zeilen mit
Icon-Kachel und Folgen-Knopf. Darunter „Lokal“ (Orte) und „Deine Suchen“.

**Suche** – Glas-Suchfeld oben, letzte Suchen als Chips, „Gerade wichtig“, Ergebnisse als
dieselben Feed-Einträge, endlos; „Diesem Thema folgen“ als gefüllte Pill.

**Einstellungen** – Kopf „DEIN MORGEN / Einstellungen“, dann:
1. **Status-Karte**: großes ✓ „Alles bereit für morgen, 06:30“ oder freundliche Hinweise;
   Status-Pills (Benachrichtigungen, Exakte Uhrzeit, Akku) grün/amber – Antippen behebt.
2. **Morgenbriefing**: riesige Uhrzeit in Serif („06:30“, antippen → Zeitwahl), Schalter,
   „Nächstes Briefing: morgen · in 9 Std.“, Test-Benachrichtigung als Glas-Pill.
3. **Sprache & Region**: App-Sprache als Pills (wie das Handy + 5 Sprachen, wirkt sofort),
   Nachrichten-Ausgabe (Flagge, Land, Sprache → durchsuchbares Glas-Sheet), Bundesland nur für
   deutsche Ausgaben; darunter „Lokale Nachrichten“ mit Orten aus aller Welt.
4. **Deine Themen**: Banner „Themen entdecken“ (Verlauf), gefolgte Themen als Zeilen mit
   Icon-Kachel – ziehen zum Sortieren, ✕ zum Entfernen; „Eigenes Thema“ (Name, Icon aus dem
   Raster, Farbe aus zwölf Perlen, Feeds oder Suchbegriff).
5. **Quellen**: eine Glaskarte mit „Deine Region“ (Bundesland-Preset) und je Thema einer
   aufklappbaren Zeile: alle Feeds mit Schalter, „Eigene Quelle“ (Adresse oder Website, die App
   findet den Feed und zeigt Titel + Anzahl vor dem Speichern), bei eigenen Themen Bearbeiten/Löschen.
6. **Feed & Lesen**: Meldungen pro Thema als große Zahl mit ruhigem Slider, Zeitraum als
   Auswahl-Pills, „Bilder anzeigen“; „In der App lesen“, „Nur lesbare Artikel“, „Vorladen“.
7. **Darstellung**: drei Vorschau-Kacheln (Mini-Handy mit Himmel + Glaskarte) für
   System/Hell/Dunkel; Akzentfarben als große Farbperlen mit gleitendem Auswahlring + Name;
   **Effekte** als drei Pills mit Symbol (Automatisch · Volle Effekte · Sparsam) und darunter
   eine Zeile, was „Automatisch“ für dieses Handy wählt und warum („Für dieses Handy: sparsam –
   flüssiger auf einfacher Hardware“).
8. **Orte & Zeitfenster**: zwei Ortskarten mit Live-Mini-Wetter; Zeitfenster-Slider mit
   Sonnensymbolen. Der Ort-Picker ist ein Glas-Sheet: Suchfeld mit Live-Ergebnissen (Flagge, Name,
   Region), „Mein Standort“ als Akzent-Pill, Schnellauswahl großer Städte, der aktuelle Ort als
   Chip. Darunter die Wetterblöcke von „Heute“ als Schalter.
9. **App**: Version, Update-Karte, Links (Open-Meteo, Quellen, Quellcode).

## 8. Qualitätsregeln

* Getestet auf 1440×3120 (S26 Ultra), einem Budget-Handy wie dem Galaxy A13 (1080×2408, sparsame
  Effekte) und Android 7; hell/dunkel; Gesten- und 3-Tasten-Navigation.
* Nichts liegt unter Statusleiste oder Gestenbalken, außer bewusst (Himmel).
* Touch-Ziele ≥ 48 dp, alles mit Content-Description, Kontrast geprüft.
* 60 fps beim Scrollen: Hintergründe zeichnen nur im Draw-Pass, keine Recomposition pro Frame.

## 9. Effekte: voll oder sparsam

Der Look ist auf jedem Handy derselbe – Himmel, Glas, Serifen, Farben. Was ein Budget-Handy
(Galaxy A13: 8 × Cortex-A55, Mali-G52 MP1) nicht flüssig schafft, lässt „Sparsam“ weg:

| | Volle Effekte | Sparsam |
|---|---|---|
| Glas | echte Unschärfe (Haze, ab Android 12) | milchiger Tint wie vor Android 12, keine Blur-Quelle |
| Dialoge | System-Unschärfe hinter dem Fenster | deckende Fläche |
| Himmel | Aurora mit 60 fps, Partikel bewegt | Aurora mit 10 fps, Partikel stehen |
| Schleifen | Siegel-Puls, wippendes Wetter-Emoji | still; Equalizer beim Zuhören mit 10 fps |
| Pager | Nachbarseiten verkleinert und in den Himmel getönt | nur verkleinert |

Beim Scrollen hält die Aurora in beiden Stufen still; „Animationen entfernen“ macht in beiden
alles statisch. **Automatisch** (Standard) wählt nach Geräteklasse (Architektur:
Plattform-Regeln) und Energiesparmodus; Volle Effekte und Sparsam gelten unabhängig davon.
