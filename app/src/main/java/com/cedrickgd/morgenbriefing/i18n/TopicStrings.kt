package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Names and one-line descriptions of the built-in topics
 * ([com.cedrickgd.morgenbriefing.data.news.Taxonomy]) plus the texts of place and search
 * topics. Built-in titles are never stored, so they follow the interface language; custom topics
 * keep the title the user gave them.
 *
 * The tables are keyed by topic id; a unit test checks that every built-in id has a title and a
 * subtitle in every language.
 */
interface TopicStrings {
    /** The title of a built-in topic (also `region` and `search`); null for ids it does not know. */
    fun title(id: String): String?

    /** One line on what the topic covers; null for ids it does not know. */
    fun subtitle(id: String): String?

    /** Subtitle of a followed place ("Lokale Nachrichten aus Washington"). */
    fun localSubtitle(place: String): String

    /** Subtitle of a topic that follows a search ("Suche „Midterms“"). */
    fun searchSubtitle(query: String): String

    /** Subtitle of an own topic that reads the user's feeds. */
    val customSubtitle: String

    companion object {
        fun of(lang: AppLanguage): TopicStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    /** One language's table: id → title and subtitle. */
    private abstract class Table(private val rows: Map<String, Pair<String, String>>) : TopicStrings {
        override fun title(id: String): String? = rows[id]?.first
        override fun subtitle(id: String): String? = rows[id]?.second
    }

    private object De : Table(
        mapOf(
            "top" to ("Schlagzeilen" to "Das Wichtigste des Tages auf einen Blick"),
            "politik" to ("Politik" to "Regierung, Parlament, Parteien und Wahlen"),
            "politik/inland" to ("Inland" to "Bundestag, Regierung und Länder"),
            "politik/wahlen" to ("Wahlen" to "Landtagswahlen, Midterms, Umfragen und Ergebnisse"),
            "politik/eu" to ("Europäische Union" to "Kommission, Parlament und Gipfel in Brüssel"),
            "politik/usa" to ("US-Politik" to "Weißes Haus, Kongress und Supreme Court"),
            "politik/sicherheit" to ("Sicherheit & Verteidigung" to "Bundeswehr, NATO und Konflikte"),
            "welt" to ("Welt" to "Was in anderen Ländern passiert"),
            "welt/europa" to ("Europa" to "Von Frankreich bis zur Ukraine"),
            "welt/amerika" to ("Amerika" to "USA, Kanada und Lateinamerika"),
            "welt/asien" to ("Asien" to "China, Japan, Indien und Korea"),
            "welt/nahost" to ("Nahost" to "Israel, Iran, Gaza und die Golfstaaten"),
            "welt/afrika" to ("Afrika" to "Politik und Gesellschaft auf dem Kontinent"),
            "wirtschaft" to ("Wirtschaft & Finanzen" to "Märkte, Unternehmen und dein Geld"),
            "wirtschaft/boerse" to ("Börse & Märkte" to "DAX, Wall Street und Aktien von heute"),
            "wirtschaft/trading" to ("Trading & Anlegen" to "ETFs, Analysen, Kursziele und Dividenden"),
            "wirtschaft/krypto" to ("Krypto" to "Bitcoin, Ethereum und Blockchain"),
            "wirtschaft/konjunktur" to ("Zinsen & Konjunktur" to "EZB, Fed, Inflation und Wachstum"),
            "wirtschaft/unternehmen" to ("Unternehmen" to "Quartalszahlen, Übernahmen und Jobs"),
            "wirtschaft/geld" to ("Verbraucher & Finanzen" to "Rente, Steuern, Sparen und Versicherungen"),
            "wirtschaft/immobilien" to ("Immobilien & Wohnen" to "Mieten, Kaufpreise und Bauzinsen"),
            "wirtschaft/startups" to ("Start-ups" to "Gründer, Finanzierungsrunden und Investoren"),
            "ki" to ("Tech & KI" to "Neues aus der digitalen Welt"),
            "ki/ki" to ("Künstliche Intelligenz" to "ChatGPT, Sprachmodelle und Forschung"),
            "ki/smartphones" to ("Smartphones & Gadgets" to "iPhone, Android und neue Geräte"),
            "ki/software" to ("Software & Apps" to "Betriebssysteme, Updates und Open Source"),
            "ki/security" to ("IT-Sicherheit" to "Sicherheitslücken, Hacker und Datenlecks"),
            "ki/hardware" to ("Hardware & Chips" to "Prozessoren, Grafikkarten und Laptops"),
            "ki/netz" to ("Internet & Netzpolitik" to "Soziale Medien, Datenschutz und Breitband"),
            "wissenschaft" to ("Wissenschaft" to "Forschung, Entdeckungen und Studien"),
            "wissenschaft/raumfahrt" to ("Raumfahrt & Astronomie" to "NASA, ESA, SpaceX und der Blick ins All"),
            "wissenschaft/physik" to ("Physik & Chemie" to "Von Quanten bis zum CERN"),
            "wissenschaft/natur" to ("Natur & Tiere" to "Artenvielfalt, Biologie und Tierwelt"),
            "wissenschaft/geschichte" to ("Geschichte & Archäologie" to "Ausgrabungen und neue Blicke auf die Vergangenheit"),
            "gesundheit" to ("Gesundheit" to "Medizin, Vorsorge und Pflege"),
            "gesundheit/medizin" to ("Medizin & Forschung" to "Therapien, Studien und Impfungen"),
            "gesundheit/ernaehrung" to ("Ernährung & Fitness" to "Gesund essen, Bewegung und Abnehmen"),
            "gesundheit/psyche" to ("Psyche" to "Psychische Gesundheit, Stress und Psychologie"),
            "klima" to ("Klima & Energie" to "Klimawandel, Energiewende und Umwelt"),
            "klima/klimawandel" to ("Klimawandel" to "Erderwärmung, Klimapolitik und Extremwetter"),
            "klima/energie" to ("Energie" to "Strom, Gas, Solar, Wind und Wasserstoff"),
            "klima/umwelt" to ("Umwelt & Naturschutz" to "Artenschutz, Plastik und Wälder"),
            "sport" to ("Sport" to "Ergebnisse, Hintergründe und Transfers"),
            "sport/fussball" to ("Fußball" to "Bundesliga, Champions League und Nationalteam"),
            "sport/motorsport" to ("Motorsport" to "Formel 1, MotoGP und Rallye"),
            "sport/tennis" to ("Tennis" to "ATP, WTA und die Grand Slams"),
            "sport/basketball" to ("Basketball" to "NBA, BBL und EuroLeague"),
            "sport/football" to ("American Football" to "NFL, College Football und Super Bowl"),
            "sport/wintersport" to ("Wintersport" to "Ski alpin, Biathlon und Skispringen"),
            "entertainment" to ("Kultur & Unterhaltung" to "Film, Musik, Games und Bücher"),
            "entertainment/games" to ("Games" to "Konsolen, PC-Spiele und Neuerscheinungen"),
            "entertainment/film" to ("Film & Serien" to "Kino, Streaming und Hollywood"),
            "entertainment/musik" to ("Musik" to "Alben, Konzerte und Charts"),
            "entertainment/buecher" to ("Bücher" to "Romane, Autorinnen und Literaturpreise"),
            "entertainment/leute" to ("Promis & Leute" to "Stars, Royals und roter Teppich"),
            "auto" to ("Auto & Mobilität" to "Autos, Hersteller und wie wir unterwegs sind"),
            "auto/elektro" to ("Elektroautos" to "E-Autos, Laden und Batterien"),
            "auto/verkehr" to ("Verkehr & Bahn" to "Bahn, Nahverkehr und Flughäfen"),
            "region" to ("Region" to "Nachrichten aus deinem Bundesland"),
            "search" to ("Suche" to "Treffer zu deiner Suche"),
        ),
    ) {
        override fun localSubtitle(place: String) = "Lokale Nachrichten aus $place"
        override fun searchSubtitle(query: String) = "Suche „$query“"
        override val customSubtitle = "Dein eigenes Thema"
    }

    private object En : Table(
        mapOf(
            "top" to ("Top stories" to "The day's most important news at a glance"),
            "politik" to ("Politics" to "Government, parliament, parties and elections"),
            "politik/inland" to ("National politics" to "Government and parliament at home"),
            "politik/wahlen" to ("Elections" to "Midterms, state votes, polls and results"),
            "politik/eu" to ("European Union" to "Commission, Parliament and summits in Brussels"),
            "politik/usa" to ("US politics" to "White House, Congress and the Supreme Court"),
            "politik/sicherheit" to ("Security & defence" to "Armed forces, NATO and conflicts"),
            "welt" to ("World" to "What is happening in other countries"),
            "welt/europa" to ("Europe" to "From France to Ukraine"),
            "welt/amerika" to ("Americas" to "Canada, Mexico and Latin America"),
            "welt/asien" to ("Asia" to "China, Japan, India and Korea"),
            "welt/nahost" to ("Middle East" to "Israel, Iran, Gaza and the Gulf states"),
            "welt/afrika" to ("Africa" to "Politics and society across the continent"),
            "wirtschaft" to ("Business & finance" to "Markets, companies and your money"),
            "wirtschaft/boerse" to ("Stock market" to "Wall Street, indices and today's movers"),
            "wirtschaft/trading" to ("Trading & investing" to "ETFs, analyst ratings, price targets and dividends"),
            "wirtschaft/krypto" to ("Crypto" to "Bitcoin, Ethereum and blockchain"),
            "wirtschaft/konjunktur" to ("Economy & rates" to "Fed, ECB, inflation and growth"),
            "wirtschaft/unternehmen" to ("Companies" to "Earnings, mergers and jobs"),
            "wirtschaft/geld" to ("Personal finance" to "Retirement, taxes, savings and insurance"),
            "wirtschaft/immobilien" to ("Real estate & housing" to "Rents, home prices and mortgages"),
            "wirtschaft/startups" to ("Startups" to "Founders, funding rounds and investors"),
            "ki" to ("Tech & AI" to "News from the digital world"),
            "ki/ki" to ("Artificial intelligence" to "ChatGPT, language models and research"),
            "ki/smartphones" to ("Phones & gadgets" to "iPhone, Android and new devices"),
            "ki/software" to ("Software & apps" to "Operating systems, updates and open source"),
            "ki/security" to ("Cybersecurity" to "Vulnerabilities, hackers and data breaches"),
            "ki/hardware" to ("Hardware & chips" to "Processors, graphics cards and laptops"),
            "ki/netz" to ("Internet & digital policy" to "Social media, privacy and broadband"),
            "wissenschaft" to ("Science" to "Research, discoveries and studies"),
            "wissenschaft/raumfahrt" to ("Space & astronomy" to "NASA, ESA, SpaceX and the night sky"),
            "wissenschaft/physik" to ("Physics & chemistry" to "From quantum physics to CERN"),
            "wissenschaft/natur" to ("Nature & animals" to "Wildlife, biology and biodiversity"),
            "wissenschaft/geschichte" to ("History & archaeology" to "Excavations and new looks at the past"),
            "gesundheit" to ("Health" to "Medicine, prevention and care"),
            "gesundheit/medizin" to ("Medicine & research" to "Treatments, trials and vaccines"),
            "gesundheit/ernaehrung" to ("Nutrition & fitness" to "Healthy eating, exercise and weight loss"),
            "gesundheit/psyche" to ("Mental health" to "Wellbeing, stress and psychology"),
            "klima" to ("Climate & energy" to "Climate change, the energy transition and the environment"),
            "klima/klimawandel" to ("Climate change" to "Global warming, climate policy and extreme weather"),
            "klima/energie" to ("Energy" to "Power, gas, solar, wind and hydrogen"),
            "klima/umwelt" to ("Environment & conservation" to "Wildlife protection, plastic and forests"),
            "sport" to ("Sports" to "Results, stories and transfers"),
            "sport/fussball" to ("Soccer" to "Premier League, Champions League and the World Cup"),
            "sport/motorsport" to ("Motorsport" to "Formula 1, MotoGP and NASCAR"),
            "sport/tennis" to ("Tennis" to "ATP, WTA and the Grand Slams"),
            "sport/basketball" to ("Basketball" to "NBA, WNBA and EuroLeague"),
            "sport/football" to ("American football" to "NFL, college football and the Super Bowl"),
            "sport/wintersport" to ("Winter sports" to "Alpine skiing, biathlon and ski jumping"),
            "entertainment" to ("Culture & entertainment" to "Film, music, games and books"),
            "entertainment/games" to ("Games" to "Consoles, PC games and new releases"),
            "entertainment/film" to ("Film & TV" to "Movies, streaming and Hollywood"),
            "entertainment/musik" to ("Music" to "Albums, concerts and charts"),
            "entertainment/buecher" to ("Books" to "Novels, authors and literary prizes"),
            "entertainment/leute" to ("Celebrities" to "Stars, royals and the red carpet"),
            "auto" to ("Cars & mobility" to "Cars, carmakers and how we get around"),
            "auto/elektro" to ("Electric cars" to "EVs, charging and batteries"),
            "auto/verkehr" to ("Transport & rail" to "Trains, public transport and airports"),
            "region" to ("Local" to "News from where you live"),
            "search" to ("Search" to "Results for your search"),
        ),
    ) {
        override fun localSubtitle(place: String) = "Local news from $place"
        override fun searchSubtitle(query: String) = "Search “$query”"
        override val customSubtitle = "Your own topic"
    }

    private object Fr : Table(
        mapOf(
            "top" to ("À la une" to "L'essentiel de l'actualité du jour"),
            "politik" to ("Politique" to "Gouvernement, parlement, partis et élections"),
            "politik/inland" to ("Politique intérieure" to "Gouvernement et parlement du pays"),
            "politik/wahlen" to ("Élections" to "Scrutins, sondages et résultats"),
            "politik/eu" to ("Union européenne" to "Commission, Parlement et sommets à Bruxelles"),
            "politik/usa" to ("Politique américaine" to "Maison Blanche, Congrès et Cour suprême"),
            "politik/sicherheit" to ("Sécurité & défense" to "Armées, OTAN et conflits"),
            "welt" to ("Monde" to "Ce qui se passe dans les autres pays"),
            "welt/europa" to ("Europe" to "De l'Allemagne à l'Ukraine"),
            "welt/amerika" to ("Amériques" to "États-Unis, Canada et Amérique latine"),
            "welt/asien" to ("Asie" to "Chine, Japon, Inde et Corée"),
            "welt/nahost" to ("Proche-Orient" to "Israël, Iran, Gaza et les pays du Golfe"),
            "welt/afrika" to ("Afrique" to "Politique et société sur le continent"),
            "wirtschaft" to ("Économie & finances" to "Marchés, entreprises et ton argent"),
            "wirtschaft/boerse" to ("Bourse & marchés" to "CAC 40, Wall Street et les actions du jour"),
            "wirtschaft/trading" to ("Trading & placements" to "ETF, analyses, objectifs de cours et dividendes"),
            "wirtschaft/krypto" to ("Crypto" to "Bitcoin, Ethereum et blockchain"),
            "wirtschaft/konjunktur" to ("Taux & conjoncture" to "BCE, Fed, inflation et croissance"),
            "wirtschaft/unternehmen" to ("Entreprises" to "Résultats, rachats et emploi"),
            "wirtschaft/geld" to ("Budget & finances perso" to "Retraite, impôts, épargne et assurances"),
            "wirtschaft/immobilien" to ("Immobilier & logement" to "Loyers, prix et crédits immobiliers"),
            "wirtschaft/startups" to ("Start-up" to "Fondateurs, levées de fonds et investisseurs"),
            "ki" to ("Tech & IA" to "L'actualité du monde numérique"),
            "ki/ki" to ("Intelligence artificielle" to "ChatGPT, modèles de langage et recherche"),
            "ki/smartphones" to ("Smartphones & gadgets" to "iPhone, Android et nouveaux appareils"),
            "ki/software" to ("Logiciels & applis" to "Systèmes d'exploitation, mises à jour et open source"),
            "ki/security" to ("Cybersécurité" to "Failles, pirates et fuites de données"),
            "ki/hardware" to ("Matériel & puces" to "Processeurs, cartes graphiques et ordinateurs portables"),
            "ki/netz" to ("Internet & numérique" to "Réseaux sociaux, vie privée et fibre"),
            "wissenschaft" to ("Sciences" to "Recherche, découvertes et études"),
            "wissenschaft/raumfahrt" to ("Espace & astronomie" to "NASA, ESA, SpaceX et le ciel étoilé"),
            "wissenschaft/physik" to ("Physique & chimie" to "Du quantique au CERN"),
            "wissenschaft/natur" to ("Nature & animaux" to "Faune, biologie et biodiversité"),
            "wissenschaft/geschichte" to ("Histoire & archéologie" to "Fouilles et nouveaux regards sur le passé"),
            "gesundheit" to ("Santé" to "Médecine, prévention et soins"),
            "gesundheit/medizin" to ("Médecine & recherche" to "Traitements, essais et vaccins"),
            "gesundheit/ernaehrung" to ("Nutrition & forme" to "Bien manger, bouger et perdre du poids"),
            "gesundheit/psyche" to ("Santé mentale" to "Bien-être, stress et psychologie"),
            "klima" to ("Climat & énergie" to "Changement climatique, transition énergétique et environnement"),
            "klima/klimawandel" to ("Changement climatique" to "Réchauffement, politique climatique et météo extrême"),
            "klima/energie" to ("Énergie" to "Électricité, gaz, solaire, éolien et hydrogène"),
            "klima/umwelt" to ("Environnement" to "Protection de la nature, plastique et forêts"),
            "sport" to ("Sport" to "Résultats, coulisses et transferts"),
            "sport/fussball" to ("Football" to "Ligue 1, Ligue des champions et les Bleus"),
            "sport/motorsport" to ("Sports mécaniques" to "Formule 1, MotoGP et rallye"),
            "sport/tennis" to ("Tennis" to "ATP, WTA et les tournois du Grand Chelem"),
            "sport/basketball" to ("Basket" to "NBA, Betclic Élite et Euroligue"),
            "sport/football" to ("Football américain" to "NFL, football universitaire et Super Bowl"),
            "sport/wintersport" to ("Sports d'hiver" to "Ski alpin, biathlon et saut à ski"),
            "entertainment" to ("Culture & divertissement" to "Cinéma, musique, jeux et livres"),
            "entertainment/games" to ("Jeux vidéo" to "Consoles, jeux PC et nouveautés"),
            "entertainment/film" to ("Cinéma & séries" to "Films, streaming et Hollywood"),
            "entertainment/musik" to ("Musique" to "Albums, concerts et classements"),
            "entertainment/buecher" to ("Livres" to "Romans, auteurs et prix littéraires"),
            "entertainment/leute" to ("People" to "Stars, familles royales et tapis rouge"),
            "auto" to ("Auto & mobilité" to "Voitures, constructeurs et façons de se déplacer"),
            "auto/elektro" to ("Voitures électriques" to "Véhicules électriques, recharge et batteries"),
            "auto/verkehr" to ("Transports & train" to "Train, transports en commun et aéroports"),
            "region" to ("Local" to "L'actualité près de chez toi"),
            "search" to ("Recherche" to "Résultats de ta recherche"),
        ),
    ) {
        override fun localSubtitle(place: String) = "Actualité locale : $place"
        override fun searchSubtitle(query: String) = "Recherche « $query »"
        override val customSubtitle = "Ton thème à toi"
    }

    private object Es : Table(
        mapOf(
            "top" to ("Titulares" to "Lo más importante del día de un vistazo"),
            "politik" to ("Política" to "Gobierno, parlamento, partidos y elecciones"),
            "politik/inland" to ("Política nacional" to "Gobierno y parlamento del país"),
            "politik/wahlen" to ("Elecciones" to "Comicios, encuestas y resultados"),
            "politik/eu" to ("Unión Europea" to "Comisión, Parlamento y cumbres en Bruselas"),
            "politik/usa" to ("Política de EE. UU." to "Casa Blanca, Congreso y Tribunal Supremo"),
            "politik/sicherheit" to ("Seguridad y defensa" to "Fuerzas armadas, OTAN y conflictos"),
            "welt" to ("Mundo" to "Lo que pasa en otros países"),
            "welt/europa" to ("Europa" to "De Francia a Ucrania"),
            "welt/amerika" to ("América" to "Estados Unidos, Canadá y Latinoamérica"),
            "welt/asien" to ("Asia" to "China, Japón, India y Corea"),
            "welt/nahost" to ("Oriente Medio" to "Israel, Irán, Gaza y los países del Golfo"),
            "welt/afrika" to ("África" to "Política y sociedad en el continente"),
            "wirtschaft" to ("Economía y finanzas" to "Mercados, empresas y tu dinero"),
            "wirtschaft/boerse" to ("Bolsa y mercados" to "Ibex, Wall Street y las acciones de hoy"),
            "wirtschaft/trading" to ("Trading e inversión" to "ETF, análisis, precios objetivo y dividendos"),
            "wirtschaft/krypto" to ("Cripto" to "Bitcoin, Ethereum y blockchain"),
            "wirtschaft/konjunktur" to ("Tipos y coyuntura" to "BCE, Fed, inflación y crecimiento"),
            "wirtschaft/unternehmen" to ("Empresas" to "Resultados, fusiones y empleo"),
            "wirtschaft/geld" to ("Finanzas personales" to "Pensiones, impuestos, ahorro y seguros"),
            "wirtschaft/immobilien" to ("Vivienda e inmobiliario" to "Alquileres, precios e hipotecas"),
            "wirtschaft/startups" to ("Startups" to "Fundadores, rondas de financiación e inversores"),
            "ki" to ("Tecnología e IA" to "Novedades del mundo digital"),
            "ki/ki" to ("Inteligencia artificial" to "ChatGPT, modelos de lenguaje e investigación"),
            "ki/smartphones" to ("Móviles y gadgets" to "iPhone, Android y nuevos dispositivos"),
            "ki/software" to ("Software y apps" to "Sistemas operativos, actualizaciones y código abierto"),
            "ki/security" to ("Ciberseguridad" to "Vulnerabilidades, hackers y filtraciones"),
            "ki/hardware" to ("Hardware y chips" to "Procesadores, tarjetas gráficas y portátiles"),
            "ki/netz" to ("Internet y política digital" to "Redes sociales, privacidad y banda ancha"),
            "wissenschaft" to ("Ciencia" to "Investigación, descubrimientos y estudios"),
            "wissenschaft/raumfahrt" to ("Espacio y astronomía" to "NASA, ESA, SpaceX y el cielo nocturno"),
            "wissenschaft/physik" to ("Física y química" to "De la cuántica al CERN"),
            "wissenschaft/natur" to ("Naturaleza y animales" to "Fauna, biología y biodiversidad"),
            "wissenschaft/geschichte" to ("Historia y arqueología" to "Excavaciones y nuevas miradas al pasado"),
            "gesundheit" to ("Salud" to "Medicina, prevención y cuidados"),
            "gesundheit/medizin" to ("Medicina e investigación" to "Tratamientos, ensayos y vacunas"),
            "gesundheit/ernaehrung" to ("Nutrición y forma física" to "Comer sano, ejercicio y perder peso"),
            "gesundheit/psyche" to ("Salud mental" to "Bienestar, estrés y psicología"),
            "klima" to ("Clima y energía" to "Cambio climático, transición energética y medio ambiente"),
            "klima/klimawandel" to ("Cambio climático" to "Calentamiento, política climática y fenómenos extremos"),
            "klima/energie" to ("Energía" to "Luz, gas, solar, eólica e hidrógeno"),
            "klima/umwelt" to ("Medio ambiente" to "Conservación, plásticos y bosques"),
            "sport" to ("Deportes" to "Resultados, historias y fichajes"),
            "sport/fussball" to ("Fútbol" to "LaLiga, Champions League y la selección"),
            "sport/motorsport" to ("Motor" to "Fórmula 1, MotoGP y rally"),
            "sport/tennis" to ("Tenis" to "ATP, WTA y los Grand Slams"),
            "sport/basketball" to ("Baloncesto" to "NBA, Liga Endesa y Euroliga"),
            "sport/football" to ("Fútbol americano" to "NFL, fútbol universitario y Super Bowl"),
            "sport/wintersport" to ("Deportes de invierno" to "Esquí alpino, biatlón y saltos"),
            "entertainment" to ("Cultura y ocio" to "Cine, música, videojuegos y libros"),
            "entertainment/games" to ("Videojuegos" to "Consolas, juegos de PC y novedades"),
            "entertainment/film" to ("Cine y series" to "Películas, streaming y Hollywood"),
            "entertainment/musik" to ("Música" to "Álbumes, conciertos y listas"),
            "entertainment/buecher" to ("Libros" to "Novelas, autores y premios literarios"),
            "entertainment/leute" to ("Famosos" to "Estrellas, realeza y alfombra roja"),
            "auto" to ("Motor y movilidad" to "Coches, fabricantes y cómo nos movemos"),
            "auto/elektro" to ("Coches eléctricos" to "Vehículos eléctricos, recarga y baterías"),
            "auto/verkehr" to ("Transporte y tren" to "Trenes, transporte público y aeropuertos"),
            "region" to ("Local" to "Noticias de donde vives"),
            "search" to ("Búsqueda" to "Resultados de tu búsqueda"),
        ),
    ) {
        override fun localSubtitle(place: String) = "Noticias locales de $place"
        override fun searchSubtitle(query: String) = "Búsqueda «$query»"
        override val customSubtitle = "Tu propio tema"
    }

    private object It : Table(
        mapOf(
            "top" to ("Prima pagina" to "Le notizie più importanti del giorno"),
            "politik" to ("Politica" to "Governo, parlamento, partiti ed elezioni"),
            "politik/inland" to ("Politica interna" to "Governo e parlamento del paese"),
            "politik/wahlen" to ("Elezioni" to "Voto, sondaggi e risultati"),
            "politik/eu" to ("Unione europea" to "Commissione, Parlamento e vertici a Bruxelles"),
            "politik/usa" to ("Politica Usa" to "Casa Bianca, Congresso e Corte suprema"),
            "politik/sicherheit" to ("Sicurezza e difesa" to "Forze armate, Nato e conflitti"),
            "welt" to ("Mondo" to "Cosa succede negli altri paesi"),
            "welt/europa" to ("Europa" to "Dalla Francia all'Ucraina"),
            "welt/amerika" to ("Americhe" to "Stati Uniti, Canada e America latina"),
            "welt/asien" to ("Asia" to "Cina, Giappone, India e Corea"),
            "welt/nahost" to ("Medio Oriente" to "Israele, Iran, Gaza e i paesi del Golfo"),
            "welt/afrika" to ("Africa" to "Politica e società nel continente"),
            "wirtschaft" to ("Economia e finanza" to "Mercati, aziende e i tuoi soldi"),
            "wirtschaft/boerse" to ("Borsa e mercati" to "Piazza Affari, Wall Street e i titoli del giorno"),
            "wirtschaft/trading" to ("Trading e investimenti" to "ETF, analisi, prezzi obiettivo e dividendi"),
            "wirtschaft/krypto" to ("Cripto" to "Bitcoin, Ethereum e blockchain"),
            "wirtschaft/konjunktur" to ("Tassi e congiuntura" to "Bce, Fed, inflazione e crescita"),
            "wirtschaft/unternehmen" to ("Aziende" to "Conti trimestrali, acquisizioni e lavoro"),
            "wirtschaft/geld" to ("Risparmio e famiglie" to "Pensioni, tasse, risparmio e assicurazioni"),
            "wirtschaft/immobilien" to ("Casa e immobiliare" to "Affitti, prezzi e mutui"),
            "wirtschaft/startups" to ("Startup" to "Fondatori, round di finanziamento e investitori"),
            "ki" to ("Tech e IA" to "Novità dal mondo digitale"),
            "ki/ki" to ("Intelligenza artificiale" to "ChatGPT, modelli linguistici e ricerca"),
            "ki/smartphones" to ("Smartphone e gadget" to "iPhone, Android e nuovi dispositivi"),
            "ki/software" to ("Software e app" to "Sistemi operativi, aggiornamenti e open source"),
            "ki/security" to ("Sicurezza informatica" to "Vulnerabilità, hacker e furti di dati"),
            "ki/hardware" to ("Hardware e chip" to "Processori, schede video e portatili"),
            "ki/netz" to ("Internet e digitale" to "Social network, privacy e banda larga"),
            "wissenschaft" to ("Scienza" to "Ricerca, scoperte e studi"),
            "wissenschaft/raumfahrt" to ("Spazio e astronomia" to "NASA, ESA, SpaceX e il cielo stellato"),
            "wissenschaft/physik" to ("Fisica e chimica" to "Dalla meccanica quantistica al CERN"),
            "wissenschaft/natur" to ("Natura e animali" to "Fauna, biologia e biodiversità"),
            "wissenschaft/geschichte" to ("Storia e archeologia" to "Scavi e nuovi sguardi sul passato"),
            "gesundheit" to ("Salute" to "Medicina, prevenzione e cura"),
            "gesundheit/medizin" to ("Medicina e ricerca" to "Terapie, studi clinici e vaccini"),
            "gesundheit/ernaehrung" to ("Alimentazione e fitness" to "Mangiare sano, movimento e dieta"),
            "gesundheit/psyche" to ("Salute mentale" to "Benessere, stress e psicologia"),
            "klima" to ("Clima ed energia" to "Cambiamento climatico, transizione energetica e ambiente"),
            "klima/klimawandel" to ("Cambiamento climatico" to "Riscaldamento globale, politica climatica ed eventi estremi"),
            "klima/energie" to ("Energia" to "Luce, gas, solare, eolico e idrogeno"),
            "klima/umwelt" to ("Ambiente" to "Tutela della natura, plastica e foreste"),
            "sport" to ("Sport" to "Risultati, retroscena e mercato"),
            "sport/fussball" to ("Calcio" to "Serie A, Champions League e Nazionale"),
            "sport/motorsport" to ("Motori" to "Formula 1, MotoGP e rally"),
            "sport/tennis" to ("Tennis" to "ATP, WTA e i tornei del Grande Slam"),
            "sport/basketball" to ("Basket" to "NBA, Serie A ed Eurolega"),
            "sport/football" to ("Football americano" to "NFL, football universitario e Super Bowl"),
            "sport/wintersport" to ("Sport invernali" to "Sci alpino, biathlon e salto con gli sci"),
            "entertainment" to ("Cultura e spettacolo" to "Cinema, musica, videogiochi e libri"),
            "entertainment/games" to ("Videogiochi" to "Console, giochi per PC e novità"),
            "entertainment/film" to ("Cinema e serie" to "Film, streaming e Hollywood"),
            "entertainment/musik" to ("Musica" to "Album, concerti e classifiche"),
            "entertainment/buecher" to ("Libri" to "Romanzi, autori e premi letterari"),
            "entertainment/leute" to ("Vip e gossip" to "Star, reali e red carpet"),
            "auto" to ("Auto e mobilità" to "Auto, costruttori e come ci spostiamo"),
            "auto/elektro" to ("Auto elettriche" to "Veicoli elettrici, ricarica e batterie"),
            "auto/verkehr" to ("Trasporti e treni" to "Treni, trasporto pubblico e aeroporti"),
            "region" to ("Locale" to "Notizie da dove vivi"),
            "search" to ("Ricerca" to "Risultati della tua ricerca"),
        ),
    ) {
        override fun localSubtitle(place: String) = "Notizie locali da $place"
        override fun searchSubtitle(query: String) = "Ricerca «$query»"
        override val customSubtitle = "Il tuo tema personale"
    }
}

@Composable
@ReadOnlyComposable
fun topicStrings(): TopicStrings = TopicStrings.of(LocalAppLanguage.current)
