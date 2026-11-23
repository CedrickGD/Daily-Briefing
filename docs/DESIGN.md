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
  im Inhalt dichter (Tint + 30 %), schwebend deckend (100 %), damit kein Text durchscheint – bei
  97 % blieben Überschriften hinter der angehefteten Feed-Leiste, der Kopfleiste und dem Indikator
  noch lesbar.
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
* Artikel öffnen im Reader der App (§7: ein Bild unter der Statusleiste, Serif-Überschrift, Textblöcke, Glas-Controls); „Im Browser öffnen“ bleibt als Ausweg (im ⋮ des Lesers); Sheets („Deine Themen“, Ort, „Darstellung“)
  liegen als Glas über der Seite.
* Innere horizontale Scroller (Tabs der Feed-Leiste, Leser-Pager) haben Vorrang vor dem
  Seitenwischen: was die Tab-Reihe am Anfang/Ende nicht mehr nutzt, verschluckt sie (kein
  Seitenwechsel).
* Querverweise in die Einstellungen landen am Ziel („Ort wählen“ → Orte mit offenem Picker,
  „Quellen & eigene Themen“ → Quellen).

## 4. Seitenkopf

* Kein `LargeTopAppBar` mehr. Jede Seite beginnt direkt unter der Statusleiste (+12 dp) mit
  * einer kleinen Overline (Label, Versalien, Sperrung) – z. B. „DEIN ARCHIV“, „DEIN MORGEN“,
  * dem Seitentitel in Serif, **displaySmall (36 sp)**, linksbündig.
* Beim Scrollen erscheint oben eine schmale **Glas-Kopfleiste** (Statusleiste + 52 dp) mit dem
  Titel klein (titleLarge) – Inhalt gleitet darunter weich weg. Antippen der Leiste scrollt nach
  oben; Berührungen auf ihr erreichen nie den Inhalt darunter.
* „Heute“ hat keine Titelzeile in der Leiste: oben liegt beim Scrollen nur ein Glasstreifen hinter
  der Statusleiste, der die Feed-Leiste (Suche + Tabs) aufnimmt, sobald sie oben ankommt (§7).

## 5. Typografie & Rhythmus

* Serif (SemiBold) für Seitentitel, Schlagzeilen, große Zahlen (Temperatur, Uhrzeit).
* Sans für alles andere; Fließtext 16 sp / 1.55, Lead im Feed 18 sp / 1.5.
* Raster 4 dp. Abstände, überall gleich (Heute, Entdecken, Suche, Leser, Einstellungen):
  * **Bildschirmrand → Inhalt 10 dp** (Karten, Wetter, Hinweise, Suchfeld, Tab-Reihe, Kacheln) –
    Inhalt soll groß wirken, nicht eingerückt. Dazu kommen nur die Sicherheitsabstände (Kamera-
    Ausschnitt, 3-Tasten-Leiste im Querformat); auf breiten Fenstern bleibt die Spalte bei
    höchstens 680 dp.
  * **In Karten 16 dp** bis zum Text, **zwischen Karten 12 dp**.
  * Text, der frei auf dem Himmel steht, beginnt auf der **Textkante der Karten**
    (10 + 16 = 26 dp, `SkyTextInset`) – eine durchgehende linke Linie für alles Lesbare, auf
    jeder Seite gleich: Gruß und Datum auf „Heute“, Seitentitel und Abschnittsüberschriften
    (Overline + Titel) in Einstellungen, Entdecken und Suche, Overlines über einer Liste, der
    kleine Titel in der Glas-Kopfleiste, und bei Einträgen ohne Kartenrahmen (Suchtreffer)
    Überschrift, Vorspann und Meta-Zeile unter dem Bild. Bilder, Kacheln, Pills und Knöpfe
    bleiben auf der Kartenkante (10 dp); Knöpfe neben Text enden an der Kartenkante.
    (Die Karten der Einstellungen haben noch 20 dp Innenabstand: ihr Text steht 4 dp weiter
    innen als die Überschriften darüber.)
  * Listen enden über dem schwebenden Indikator: unten bleibt Platz für Systemleiste + 12 dp +
    Indikator + 12 dp (+ Mini-Player), darüber noch 24–32 dp Luft; über die Seite geschobene
    Seiten (Entdecken, Suche, Leser) haben keinen Indikator und enden über der Systemleiste.
  * Touch-Ziele bleiben ≥ 48 dp, mit Abstand zueinander; nichts wird am Rand beschnitten.
* Jede Sektion hat eine Overline + Titel; nie zwei gleich aussehende Blöcke direkt
  untereinander ohne Hierarchie.

## 6. Bewegung

* Federn statt linearer Tweens (`spring(dampingRatio 0.8, stiffness MediumLow)`).
* Inhalte erscheinen gestaffelt (40–60 ms Versatz), Zahlen zählen hoch, Karten heben sich beim
  Drücken minimal (Scale 0.98).
* Seiten im Pager: Nachbarseiten leicht verkleinert (0.94) und in die Himmelsfarbe getönt (nicht
  transparent – nichts scheint durch ihre Schrift), während man wischt.

## 7. Seiten im Detail

**Heute** – eine Seite, aufgeräumt von oben nach unten (Struktur „B“ mit dem Glas-Look „A“ aus
dem Entwurf, den der Besitzer gewählt hat):

* **Kopf** direkt auf dem Himmel: Datums-Overline und der Gruß in Serif (headlineSmall). Keine
  Knöpfe mehr im Kopf – Suche und Themen sitzen in der Feed-Leiste.
* **Wetter als eine kompakte Glaskarte** (26 dp Radius): zugeklappt Wetter-Emoji, große
  Serif-Temperatur, „Ort · Zustand“ und **eine** Zeile „min – max · Regen 60 % / trocken ·
  Kleidungstipp“, rechts ein Chevron. Antippen klappt sie weich auf (`animateContentSize`, Chevron
  dreht sich): die Serif-Headline des Tages, „Vor der Tür“ (gefühlt, Chips, Hinweis fürs
  Losgehen), die Tageskurve mit Zusammenfassung und die Anzieh-Kacheln – jeweils nur, wenn der
  Wetterblock in den Einstellungen an ist. Auf/zu bleibt über App-Starts erhalten.
  **Warnungen** stehen immer sichtbar unter der Karte (getönte Glas-Banner), auch zugeklappt.
* **Feed-Leiste** unter dem Wetter: ein Suchfeld (sieht aus wie ein Feld, ist ein Knopf und
  öffnet die Suche: „Suchen – z. B. SLV-ETF, Midterms“), rechts daneben ein runder Glasknopf mit
  Verlaufs-Uhr (**„Gelesen & ausgeblendet“**, gleiche Höhe und Rundung wie das Feld), und darunter die Tabs **„Für dich“ ·
  „Neueste“ · je ein Tab pro gefolgtem Thema** (mit Icon; aktiver Tab gefüllt in Themen- bzw.
  Akzentfarbe) und am Ende ein runder **„＋“**. Die Tabs scrollen waagerecht und laufen rechts weich
  aus. Erreicht die Leiste den oberen Rand, **pinnt** sie dort: Die Glasfläche oben – beim
  Scrollen zuerst nur ein Glasstreifen hinter der Statusleiste – nimmt Suchfeld und Tabs auf:
  **eine** Glasfläche mit einer Kante, kein zweiter Balken. Ein Tab-Wechsel bei gepinnter Leiste
  beginnt die neue Ansicht direkt darunter.
* **„＋“ → „Deine Themen“** (Glas-Sheet): die gefolgten Themen mit Ziehgriff zum Sortieren und ✕
  zum Entfernen (das letzte Thema bleibt; Rückgängig per Snackbar), darunter „Themen entdecken“
  (gefüllt) und „Ort hinzufügen“ (öffnet „Themen entdecken“ mit der Ortssuche). Das ist der eine
  Ort auf „Heute“ für Themen; Einstellungen → Deine Themen bleibt wie es ist.
* **Für dich**: pro gefolgtem Thema **eine Glaskarte** – Kopfzeile mit Icon-Kachel (Themenfarbe),
  Themenname in Versalien und **„Alle →“** (wählt sichtbar den Tab des Themas und springt zurück
  an die Leiste – kein versteckter Modus); dann die erste Meldung **groß** (Bild 168 dp, 18 dp
  Radius, Serif-Headline 19 sp, Meta „Quelle · vor 3 Std.“, ⋮) und die nächsten als **kompakte
  Zeilen** (Serif 16 sp, bis 3 Zeilen, Meta-Zeile mit ⋮, rechts ein 80-dp-Bild). Ein vollständig
  lesbarer Artikel trägt **kein Zeichen** – das verspricht einmal die Einstellung „Nur vollständige
  Artikel“ (ein ✓ auf jeder Karte sagte nichts); nur ohne sie zeigen 🔒 (Bezahlinhalt) und 🌐 (nur
  im Browser), was nicht in den Leser geht. **Jede Meldung hat ein Bild**: Bringt der Feed keines mit
  (Google News), sucht die App es vorab auf der Seite der Redaktion – für das, was gleich ins
  Bild kommt, und die nächste Seite, auch mobil – und lädt die Bilder des nächsten Bildschirms
  vor. Bis es da ist, und wenn es keines gibt oder es nicht lädt, zeigt der Bildplatz das
  **generierte Cover** in Themenfarbe (nie eine leere Fläche oder eine bloße Farbkachel neben
  Fotos); der Platz hat immer dieselbe Größe, nichts springt. Nur mit „Bilder anzeigen“ aus
  steht dort eine kleine Kachel mit dem Themen-Icon, der Aufmacher dann wie eine Zeile mit
  größerer Headline. Wie viele Meldungen je Thema, bestimmt die Einstellung „Meldungen pro
  Thema“. Feine Linien trennen die Meldungen einer Karte. Unter den Karten und „Mehr entdecken“
  geht die Seite **endlos weiter**: Overline „NEUESTE“, dann eine Glaskarte mit allen übrigen
  Meldungen, neueste zuerst.
* **Neueste**: **eine** Glaskarte mit kompakten Zeilen, über jeder Headline ein kleines farbiges
  Themen-Label; jede Meldung einmal; endlos – alles, was die Themen haben, neueste zuerst (der
  Zeitraum aus den Einstellungen begrenzt nur die Karten von „Für dich“), und am Ende laden die
  Themen von selbst ältere nach. **Ein Thema**: oben eine schmale Zeile
  „37 MELDUNGEN · STAND 07:12“ (bei Kategorien mit „Unterthemen von …“), darunter eine Glaskarte –
  erste Meldung groß, dann Zeilen –, endlos mit Nachladen älterer Meldungen (auch
  „Schlagzeilen“). Nachgeladen wird ohne Knopf und früh – mehrere Bildschirme vor dem Ende, damit
  ein Wischen nicht an der Lade-Zeile hält; ruhige Tage überspringt die App von selbst.
  **Älteres hängt immer unten an**: keine Karte rutscht, während man liest; neu sortiert wird
  erst beim Aktualisieren. **Aktualisieren wie bei Google News**: was neu ist, steht oben, neueste
  zuerst; mit „nur vollständige Artikel“ werden die neuen Meldungen vor allem anderen geprüft.
  Danach (höchstens 8 s) springt die Seite nach oben, und eine Pill in Akzentfarbe unter der
  Statusleiste sagt „↑ 12 neue Meldungen“ (antippen: zu den Meldungen) – nach dem Herunterziehen
  immer, auch „Keine neuen Meldungen“, beim Start oder der Rückkehr nach einer Weile nur, wenn
  etwas Neues da ist. Derselbe Artikel über zwei Feeds eines Verlags steht einmal da.
  „Weiter zurück suchen“ erscheint nur, wenn das Laden scheitert (offline) oder Seite um Seite
  nichts Vollständiges brachte, „Das war's“ erst, wenn es wirklich nichts Älteres mehr gibt.
  Lange Karten sind aus einzelnen Listen-Elementen gebaut (`glassCardPart`): nur das erste hat die
  oberen, nur das letzte die unteren Rundungen, die Kante läuft ohne Naht durch.
* **Keine Knopfreihe unter den Meldungen.** Das ⋮-Menü (auch per langem Druck auf die Meldung)
  enthält: Zuhören (beim Vorlesen „Vorlesen beenden“, pausiert „Weiter zuhören“), Teilen, Link
  kopieren, Im Browser öffnen, „… folgen“ (Unterthema einer Kategorie-Meldung), „… nicht mehr
  folgen“ (nicht beim letzten Thema), „„Quelle“ ausblenden“ (nicht bei Google News). Davor:
  „Als gelesen markieren“ / „Als ungelesen markieren“ (je nach Zustand) und **„Ausblenden“** – die
  Meldung verschwindet sofort aus allen Listen, Snackbar „Ausgeblendet“ mit „Rückgängig“. Antippen
  öffnet den Leser. Wird eine Meldung vorgelesen, zeigt ihre Meta-Zeile ein kleines Kopfhörer-Symbol.
* **Gelesen**: eine Meldung gilt als gelesen, sobald sie im Leser oder Browser geöffnet oder
  angehört wurde – auch, wenn dieselbe Geschichte über einen anderen Feed kommt. Ihre Karte tritt
  zurück: Überschrift in der leiseren Textfarbe, Bild halb durchscheinend (50 %), am Anfang der
  Meta-Zeile ein gedämpftes Auge mit „Gelesen“ (kein Haken – der hieß früher „Volltext“). Mit
  **„Gelesene Artikel ausblenden“** verschwinden gelesene Meldungen aus allen Listen – aber nur,
  wenn eine Liste neu aufgebaut wird (Start, Aktualisieren, anderer Tab), nie unter dem Finger:
  wer aus dem Leser zurückkommt, sieht die Karte ruhig an ihrem Platz.
* Hinweise (offline, Quellen antworten nicht, nichts vollständig lesbar …) als **schmales
  getöntes Band** unter der Leiste – keine Kartenstapel vor der ersten Meldung. „Weitere Artikel
  werden geprüft …“ schließt die Karte eines Themas als ruhige Zeile ab.
* **Zuhören**: Android TextToSpeech (in der Sprache der Nachrichten) liest Schlagzeile + Teaser vor; ein schwebender
  Glas-Mini-Player (über dem Seitenindikator) zeigt Titel, Tempo-Chip („1,25×“, Antippen wechselt
  1× → 1,25× → 1,5× → 2× → 0,75×, sofort hörbar), Pause/Weiter, Stopp; Antippen öffnet den Artikel.
  Stimme, Sprache, Tempo und Tonhöhe stehen in Einstellungen → Vorlesen.
* Unten „Mehr entdecken“ (Kategorien, denen du noch nicht folgst) und ein ruhiger Ausklang
  (Stand, Anzahl Meldungen, kleines „Bis später ☀️“). Mit sparsamen Effekten (§9) ist alles Glas
  ein milchiger Tint statt Unschärfe.

**Themen entdecken** – über die Seite geschoben (wie der Reader): 12 große Kategorie-Kacheln im
Raster, jede ein Verlauf in Themenfarbe mit weichem Glanz, großem Icon als Wasserzeichen, Titel,
Untertitel und Zähler (gefolgt/Unterthemen). Antippen → Kategorie: farbiger Kopf, „Ganze
Kategorie folgen“, die drei neuesten Schlagzeilen, darunter die Unterthemen als Zeilen mit
Icon-Kachel und Folgen-Knopf. Darunter „Lokal“ (Orte) und „Deine Suchen“.

**Leser** – über die Seite geschoben, ruhige deckende Fläche, kurz und aufgeräumt wie Google News
oder Perplexity: **ein** Bild **randlos** unter der Statusleiste, darunter die Zeit, die
Serif-Überschrift, der Teaser und der Text – keine Quelle, kein zweites Bild. Der Text nutzt die
Breite: Kopf, Absätze und Ausklang stehen **16 dp** vom Rand, die „nicht lesbar“-Karte **10 dp**,
die schwebenden Glas-Knöpfe (je 48 dp) **10 dp**: links Zurück, rechts **„A“** (Darstellung),
**Zuhören** und **⋮** (Teilen, Link kopieren, Im Browser öffnen – im Stil des ⋮-Menüs der Karten).
Die Knöpfe sind eine Stufe dunkler (hell) bzw. heller (dunkel) als die Seite getönt
(`surfaceContainerHighest`), damit sie auf der weißen Seite, auf der Leiste und über jedem Foto
sichtbar sind. Über dem Bild schweben sie allein; sobald der Text sie erreicht, liegt hinter ihnen
eine **Glas-Kopfleiste** (Statusleiste + 64 dp, Haarlinie und weicher Schatten an der unteren
Kante, **ohne Titel** – die Quelle ist nicht die Geschichte) – Text läuft nie unter der Uhr oder
den Knöpfen durch.

**„A“ → „Darstellung“** (Glas-Sheet; der Scrim bleibt leicht, jede Änderung wirkt sofort auf den
offenen Artikel darüber): **Textgröße** als ruhiger Slider zwischen einem kleinen und einem großen
„A“ (85–140 % in 5-%-Schritten, der Wert in Prozent darüber; der Artikel folgt beim Ziehen,
gespeichert wird beim Loslassen), **Schrift** (Mit Serifen · Ohne Serifen), **Zeilenabstand**
(Eng · Normal · Weit) und **Bilder anzeigen** – dieselbe Einstellung wie in Feed & Lesen, das
Sheet sagt, dass sie auch den Feed betrifft. „Zurücksetzen“ erscheint, sobald etwas vom Standard
abweicht. Fließtext, Teaser, Listen und Zitate folgen Größe, Schrift und Zeilenabstand (ohne
Serifen eine Stufe kleiner, 17 statt 18 sp); Überschrift und Zwischenüberschriften wachsen
gedämpft mit (60 % des Zuwachses: bei 140 % Text eine Überschrift von 124 %) und bleiben in Serif.
Lange Wörter werden in der Sprache des Artikels getrennt.

**Ein Bild pro Artikel:** das Bild der Karte (Feed-Bild, sonst ihr Titelbild), sonst das
Aufmacherbild der Seite, sonst das erste Bild im Text. Weitere Bilder, Galerien, Videostandbilder,
Bildunterschriften und Alt-Texte gibt es im Text nicht. **Quellen fallen immer weg** (Deutsch,
Englisch, Französisch): Quellenlisten („Quellen“, „Sources“), „Quelle: dpa“, „Mit Material von
dpa“, „jdl/dpa“, „Avec AFP“, „Mitarbeit: …“, „Reporting by …; Editing by …“, „… contributed to
this report“, Fotonachweise („Foto: …“, „Bild: …“, „©“, „AP Photo/…“), die Autorenzeile als eigene
Zeile, Kästen mit anderen Artikeln („Mehr zum Thema“, „Related“, „À lire aussi“) samt ihren Links,
„Dieser Artikel erschien zuerst …“, Newsletter- und Social-Hinweise, eine Zeile nur mit dem Namen
der Redaktion – und „Quelle: …“ am Ende, die Quelle im Kopf und in der Kopfleiste. Ein Satz, der
eine Quelle nennt („…, sagte er der dpa.“), bleibt. **Nichts steht doppelt:** Überschrift und
Teaser stehen nur im Kopf (wiederholt der Text den Teaser, bleibt der Text und der Teaser schrumpft
oder entfällt). Am Ende steht nur bei vermutlich gekürzten Artikeln ein Hinweis mit „Im Browser
öffnen“.

**Gelesen & ausgeblendet** – über die Seite geschoben, Glas über dem Himmel wie „Themen
entdecken“: Overline „DEIN VERLAUF“ und Serif-Titel, darunter eine Glaskarte mit zwei Zählern
(Auge in Akzentfarbe: gelesen · durchgestrichenes Auge in Tertiärfarbe: ausgeblendet, große
Serif-Zahlen), ein Glas-Suchfeld (Titel oder Quelle) und Filter-Pills Alle · Gelesen ·
Ausgeblendet. Die Geschichten stehen nach Tagen („HEUTE“, „GESTERN“, „DIENSTAG · 6. OKTOBER“ als
Overline auf der Textkante) in Glaskarten: 64-dp-Bild (sonst das generierte Cover), Serif-Titel,
eine kleine getönte Marke „Gelesen“/„Ausgeblendet“, Quelle · Uhrzeit, ⋮ (Teilen, Link kopieren, Im
Browser öffnen, „Wieder einblenden“, „Als ungelesen markieren“ – beides mit „Rückgängig“). Antippen
öffnet den Leser, auch wenn der Feed die Meldung längst nicht mehr hat. Oben rechts „Verlauf
löschen“ mit Rückfrage. Leer: ein Verlaufs-Symbol in weichem Akzentschein und eine Zeile, was hier
landet. Erreichbar über den Knopf neben dem Suchfeld auf „Heute“ und Einstellungen → Feed & Lesen.

**Suche** – Glas-Suchfeld oben, letzte Suchen als Chips, „Gerade wichtig“, Ergebnisse als
große redaktionelle Einträge ohne Kartenrahmen (Bild in voller Breite, Serif-Headline, Lead mit
„… Weiterlesen“, Meta-Zeile mit „Zuhören“, Teilen und ⋮), endlos; „Diesem Thema folgen“ als
gefüllte Pill.

**Einstellungen** – Kopf „DEIN MORGEN / Einstellungen“, dann:
1. **Status-Karte**: großes ✓ „Alles bereit für morgen, 06:30“ oder freundliche Hinweise;
   Status-Pills (Benachrichtigungen, Exakte Uhrzeit, Akku) grün/amber – Antippen behebt.
2. **Morgenbriefing**: riesige Uhrzeit in Serif („06:30“, antippen → Zeitwahl), Schalter,
   „Nächstes Briefing: morgen · in 9 Std.“, Test-Benachrichtigung als Glas-Pill.
3. **Sprache & Region**: drei getrennte Regler, keiner ändert einen anderen – App-Sprache als
   Pills (wie das Handy + 5 Sprachen, wirkt sofort), **Nachrichten-Region** (Flaggen-Kachel,
   Land in Serif → durchsuchbares Glas-Sheet mit Vorschlägen) und **Nachrichten-Sprache**
   (Kachel mit Sprachkürzel, Sprache in sich selbst → Glas-Sheet: zuerst die Sprachen mit
   eigener Google-News-Ausgabe für das Land, dann alle weiteren). Darunter eine Zeile, die bei
   einer Kombination ohne eigene Ausgabe sagt, dass die App Meldungen über das Land sucht.
   Bundesland nur für Deutschland auf Deutsch; darunter „Lokale Nachrichten“ mit Orten aus
   aller Welt. Der erste Start zeigt dieselben zwei Zeilen im Schritt „Deine Nachrichten“.
4. **Deine Themen**: Banner „Themen entdecken“ (Verlauf), gefolgte Themen als Zeilen mit
   Icon-Kachel – ziehen zum Sortieren, ✕ zum Entfernen; „Eigenes Thema“ (Name, Icon aus dem
   Raster, Farbe aus zwölf Perlen, Feeds oder Suchbegriff).
5. **Quellen**: eine Glaskarte mit „Deine Region“ (Bundesland-Preset) und je Thema einer
   aufklappbaren Zeile: alle Feeds mit Schalter, „Eigene Quelle“ (Adresse oder Website, die App
   findet den Feed und zeigt Titel + Anzahl vor dem Speichern), bei eigenen Themen Bearbeiten/Löschen.
6. **Feed & Lesen**: Meldungen pro Thema als große Zahl mit ruhigem Slider, Zeitraum als
   Auswahl-Pills, „Bilder anzeigen“, „Gelesene Artikel ausblenden“ (Standard aus, mit einer
   Zeile, was passiert) und direkt darunter die Zeile „Gelesen & ausgeblendet“ (Zähler, Chevron);
   „In der App lesen“, „Nur lesbare Artikel“, „Lange Artikel ausblenden“ (Standard an: normal lange
   Nachrichten, keine Romane), „Vorladen“.
7. **Vorlesen**: Sprachausgabe als Pills (nur bei mehreren Engines, z. B. Samsung · Google),
   Sprache (Automatisch · feste Sprache), „Stimme für …“ (Sprach-Chips, wenn mehrere zählen),
   „Am liebsten“ Egal · Weiblich · Männlich (nur Geschlechter, die eine Stimme verrät), Stimmen als
   Radio-Zeilen („Stimme 1 · weiblich · offline“, Region · Qualität) mit rundem Probehören-Knopf
   (Equalizer, während die Probe läuft), „Automatisch“ zuerst; Tempo als große Zahl („1,25×“) und
   Tonhöhe in Worten, je mit ruhigem Slider und Hörprobe beim Loslassen; ehrliche Hinweise (keine
   Stimme, nur online, gewählte Stimme fehlt) als Lamina-Zeile; „Weitere Stimmen installieren“.
   Darunter „Natürliche Stimmen“ (nur, wenn die Sprachbibliothek lädt): Overline in Akzentfarbe,
   ein Satz dazu, bei mobilen Daten ein Lamina-Hinweis; geladene Stimmen sind Radio-Zeilen wie die
   der Engine („Thorsten · männlich“, „Aufnahmen: Thorsten-Voice · CC0“) mit Probehören und
   Löschen, die anderen zeigen dieselben zwei Zeilen und eine Pill „Laden · 21 MB“, ein laufender
   Download Balken, „8 von 21 MB“ und Abbrechen; zuletzt die Herkunft in einer ruhigen Zeile. Wo
   es hilft, eine Lamina-Karte zu „Google Sprachausgabe“ mit einer Pill (laden bzw. verwenden).
8. **Darstellung**: drei Vorschau-Kacheln (Mini-Handy mit Himmel + Glaskarte) für
   System/Hell/Dunkel; Akzentfarben als große Farbperlen mit gleitendem Auswahlring + Name;
   **Effekte** als drei Pills mit Symbol (Automatisch · Volle Effekte · Sparsam) und darunter
   eine Zeile, was „Automatisch“ für dieses Handy wählt und warum („Für dieses Handy: sparsam –
   flüssiger auf einfacher Hardware“).
9. **Orte & Zeitfenster**: zwei Ortskarten mit Live-Mini-Wetter; Zeitfenster-Slider mit
   Sonnensymbolen. Der Ort-Picker ist ein Glas-Sheet: Suchfeld mit Live-Ergebnissen (Flagge, Name,
   Region), „Mein Standort“ als Akzent-Pill, Schnellauswahl großer Städte, der aktuelle Ort als
   Chip. Darunter die Wetterblöcke von „Heute“ als Schalter.
10. **App**: Version, Update-Karte, Links (Open-Meteo, Quellen, Quellcode).

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
