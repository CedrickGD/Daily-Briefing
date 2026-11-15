package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.TopicIcon

/** A section of a Google News edition (`/rss/headlines/section/topic/<name>`). */
enum class NewsSection { WORLD, NATION, BUSINESS, TECHNOLOGY, ENTERTAINMENT, SPORTS, SCIENCE, HEALTH }

/**
 * One built-in topic of [Taxonomy]: a category (`wirtschaft`) or a sub-topic
 * (`wirtschaft/boerse`). Titles are not stored here; they follow the interface language
 * ([com.cedrickgd.morgenbriefing.i18n.TopicStrings]).
 */
data class TopicDef(
    /** Stable id, stored in the settings: never rename one. */
    val id: String,
    /** The category of a sub-topic; null for categories. */
    val parentId: String?,
    val icon: TopicIcon,
    /** The topic's own color (ARGB) for covers, chips and section accents. */
    val seed: Long,
    /** Shown by the old interface and in plain-text places (notifications) instead of [icon]. */
    val emoji: String,
    /** The Google News section that carries this topic in every edition, if one fits. */
    val section: NewsSection? = null,
    /** The edition's top stories (`/rss?hl=…`) instead of a section or a search. */
    val topStories: Boolean = false,
    /**
     * Google News search queries by language (`de`, `en`, `fr`, `es`, `it`), optionally refined
     * per country (`en_GB`, `de_AT` …) where the same words mean something else (domestic
     * politics), or for a country Google News has no edition of in that language
     * (`de_abroad`, see [queryFor]). Without date operators; paging adds `after:`/`before:`.
     */
    val queries: Map<String, String> = emptyMap(),
) {
    val isCategory: Boolean get() = parentId == null

    /**
     * Whether the topic's news are a country's own (top stories, home politics, the economy,
     * housing) rather than a subject that reads the same everywhere (science, tech, the world).
     */
    val domestic: Boolean get() = id in Taxonomy.DOMESTIC

    /**
     * The search query for [edition]: the country-specific one, else the language's, else the
     * English one (Google News finds English coverage in every edition); null when the topic
     * has no queries at all.
     *
     * For a pair Google News has no edition of ([NewsEdition.isNative] = false: the USA in
     * German) the query runs in the edition of the language, which knows nothing of the
     * country. A [domestic] topic is therefore narrowed to the country by name
     * ([CountryQuery]): `USA OR "Vereinigte Staaten" Wirtschaft OR Konjunktur …` – with the
     * `<language>_abroad` words where the language's own query names its home institutions
     * ("Bundestag"), and the country alone for the top stories and for a language without
     * queries. Other topics keep the language's query: tech news in German are the same news.
     */
    fun queryFor(edition: NewsEdition): String? {
        val language = edition.language
        if (edition.isNative || !domestic) return queries["${language}_${edition.country}"] ?: queries[language] ?: queries["en"]
        val country = CountryQuery.terms(edition.country, language)
        val words = queries["${language}_${Taxonomy.ABROAD}"] ?: queries[language]
        return if (words == null) country else "$country $words"
    }

    /** Whether [edition]'s language has its own query (not the English fallback). */
    fun speaks(edition: NewsEdition): Boolean = queries.containsKey(edition.language)
}

/**
 * The built-in topics: 12 categories with their sub-topics, in display order (each category
 * followed by its sub-topics). Pure data, unit-tested (unique ids, parents, queries, titles).
 *
 * Every query was tried against Google News (DE and US editions, 05.11.2026): each returns
 * fresh, on-topic stories. Queries use `OR` (binds tighter than the implicit AND) and quoted
 * phrases; a trailing word without `OR` narrows the whole list ("… OR Conservatives Canada").
 */
object Taxonomy {
    const val TOP = "top"
    const val REGION = "region"

    /** Key suffix of the query a topic uses for a country abroad (`de_abroad`, see [TopicDef.queryFor]). */
    const val ABROAD = "abroad"

    /**
     * The topics whose news belong to the edition's country ([TopicDef.domestic]): where Google
     * News has no edition of a country in the user's language, they search for the country by
     * name instead of reading the language's home edition (German top stories are Germany's).
     */
    val DOMESTIC: Set<String> = setOf(
        TOP, "politik", "politik/inland", "politik/wahlen",
        "wirtschaft", "wirtschaft/konjunktur", "wirtschaft/geld", "wirtschaft/immobilien",
    )

    /**
     * Politics of a country abroad, in words every country has – the languages' own queries
     * name their home institutions ("Bundestag", "Congress"). With the country in front
     * ([TopicDef.queryFor]) `USA OR "Vereinigte Staaten" Politik OR Regierung OR Parlament OR
     * Wahl` finds 68 stories (28 of the last two days, US politics); the German home query
     * finds 38, many of them off the subject (checked 15.11.2026).
     */
    private val POLITICS_ABROAD = arrayOf(
        "de_$ABROAD" to "Politik OR Regierung OR Parlament OR Wahl OR Präsident OR Opposition",
        "en_$ABROAD" to "politics OR government OR parliament OR election OR president OR \"prime minister\"",
        "fr_$ABROAD" to "politique OR gouvernement OR parlement OR élection OR président OR opposition",
        "es_$ABROAD" to "política OR gobierno OR parlamento OR elecciones OR presidente OR oposición",
        "it_$ABROAD" to "politica OR governo OR parlamento OR elezioni OR presidente OR opposizione",
    )

    private fun cat(
        id: String,
        icon: TopicIcon,
        seed: Long,
        emoji: String,
        section: NewsSection? = null,
        queries: Map<String, String> = emptyMap(),
        topStories: Boolean = false,
    ) = TopicDef(id, null, icon, seed, emoji, section, topStories, queries)

    private fun sub(parent: String, sub: String, icon: TopicIcon, seed: Long, emoji: String, queries: Map<String, String>) =
        TopicDef("$parent/$sub", parent, icon, seed, emoji, queries = queries)

    private fun q(de: String, en: String, fr: String, es: String, it: String, vararg countries: Pair<String, String>) =
        mapOf("de" to de, "en" to en, "fr" to fr, "es" to es, "it" to it) + countries

    val ALL: List<TopicDef> = listOf(
        // Schlagzeilen
        // The first page is the edition's top stories; the query only pages into the past (the
        // endless list): the headline desks of a date window come closest to "top stories then"
        // (tried in DE, US and FR, 15.11.2026: 100 results per three recent days).
        cat(
            TOP, TopicIcon.NEWSPAPER, 0xFFB23A48, "📰", topStories = true,
            queries = q(
                de = "Politik OR Wirtschaft OR Ausland OR Bundesregierung OR Bundestag",
                en = "politics OR economy OR world OR government OR \"breaking news\"",
                fr = "politique OR économie OR international OR gouvernement OR société",
                es = "política OR economía OR internacional OR gobierno OR sociedad",
                it = "politica OR economia OR esteri OR governo OR cronaca",
            ),
        ),

        // Politik
        cat(
            "politik", TopicIcon.HOW_TO_VOTE, 0xFF2F6FDE, "🏛️", NewsSection.NATION,
            q(
                de = "Politik OR Bundestag OR Bundesregierung OR Koalition OR Parteien",
                en = "politics OR Congress OR \"White House\" OR Senate OR lawmakers",
                fr = "politique OR gouvernement OR \"Assemblée nationale\" OR Sénat OR partis",
                es = "política OR Gobierno OR Congreso OR Senado OR partidos",
                it = "politica OR governo OR Parlamento OR Senato OR partiti",
                *POLITICS_ABROAD,
            ),
        ),
        sub(
            "politik", "inland", TopicIcon.GAVEL, 0xFF1E5BB8, "🏛️",
            q(
                de = "Bundestag OR Bundesregierung OR Bundeskanzler OR Koalition OR Bundesrat OR Ministerpräsident",
                en = "Congress OR \"White House\" OR Senate OR \"House of Representatives\" OR governor",
                fr = "\"Assemblée nationale\" OR Sénat OR gouvernement OR Élysée OR Matignon",
                es = "Congreso OR Gobierno OR Moncloa OR PSOE OR PP",
                it = "Parlamento OR Governo OR \"Palazzo Chigi\" OR Camera OR Senato",
                "de_AT" to "Nationalrat OR Bundesregierung OR Bundeskanzler OR Koalition OR Landeshauptmann OR ÖVP OR SPÖ OR FPÖ",
                "de_CH" to "Bundesrat OR Nationalrat OR Ständerat OR Abstimmung OR Bundeshaus",
                "en_GB" to "Parliament OR \"Downing Street\" OR Westminster OR \"prime minister\" OR Labour OR Conservatives",
                "en_CA" to "Parliament OR Ottawa OR \"prime minister\" OR Liberals OR Conservatives Canada",
                "en_AU" to "Parliament OR Canberra OR \"prime minister\" OR Labor OR Coalition Australia",
                "en_IE" to "Dáil OR Taoiseach OR \"Fianna Fáil\" OR \"Fine Gael\" OR \"Sinn Féin\"",
                "en_IN" to "\"Lok Sabha\" OR \"Rajya Sabha\" OR \"prime minister\" OR BJP OR Congress India",
                "fr_BE" to "\"gouvernement fédéral\" OR \"Chambre des représentants\" OR \"Premier ministre\" OR N-VA OR \"Les Engagés\" OR MR",
                "fr_CH" to "\"Conseil fédéral\" OR \"Conseil national\" OR \"Conseil des États\" OR votation",
                "fr_CA" to "\"Chambre des communes\" OR Ottawa OR \"premier ministre\" OR libéraux OR conservateurs Canada",
                "es_MX" to "\"Cámara de Diputados\" OR Senado OR \"Palacio Nacional\" OR Morena OR \"presidenta de México\"",
                "es_AR" to "\"Casa Rosada\" OR Congreso OR Senado OR \"Cámara de Diputados\" Argentina",
                "es_US" to "\"Casa Blanca\" OR Congreso OR Senado OR \"Cámara de Representantes\" OR gobernador",
                *POLITICS_ABROAD,
            ),
        ),
        sub(
            "politik", "wahlen", TopicIcon.BALLOT, 0xFF3D5AFE, "🗳️",
            q(
                de = "Landtagswahl OR Bundestagswahl OR Kommunalwahl OR Wahlkampf OR Wahlergebnis OR Umfrage OR Midterms OR Präsidentschaftswahl",
                en = "election OR elections OR midterms OR primaries OR polling OR ballot OR \"campaign trail\"",
                fr = "élection OR élections OR scrutin OR sondage OR \"campagne électorale\" OR présidentielle OR municipales",
                es = "elecciones OR comicios OR encuesta OR \"campaña electoral\" OR votación OR candidato",
                it = "elezioni OR voto OR sondaggi OR \"campagna elettorale\" OR regionali OR ballottaggio",
            ),
        ),
        sub(
            "politik", "eu", TopicIcon.EU_STARS, 0xFF1A4F9C, "🇪🇺",
            q(
                de = "\"Europäische Union\" OR EU-Kommission OR Europaparlament OR Brüssel OR EU-Gipfel",
                en = "\"European Union\" OR \"European Commission\" OR \"European Parliament\" OR Brussels OR \"EU leaders\"",
                fr = "\"Union européenne\" OR \"Commission européenne\" OR \"Parlement européen\" OR Bruxelles",
                es = "\"Unión Europea\" OR \"Comisión Europea\" OR \"Parlamento Europeo\" OR Bruselas",
                it = "\"Unione europea\" OR \"Commissione europea\" OR \"Parlamento europeo\" OR Bruxelles",
            ),
        ),
        sub(
            "politik", "usa", TopicIcon.FLAG, 0xFF283593, "🇺🇸",
            q(
                de = "US-Präsident OR \"Weißes Haus\" OR US-Kongress OR US-Regierung OR Republikaner OR Demokraten",
                en = "\"White House\" OR Congress OR Senate OR \"Supreme Court\" OR Republicans OR Democrats",
                fr = "\"Maison Blanche\" OR \"Congrès américain\" OR \"président américain\" OR républicains OR démocrates",
                es = "\"Casa Blanca\" OR \"Congreso de Estados Unidos\" OR \"presidente estadounidense\" OR republicanos OR demócratas",
                it = "\"Casa Bianca\" OR \"Congresso Usa\" OR \"presidente americano\" OR repubblicani OR democratici",
            ),
        ),
        sub(
            "politik", "sicherheit", TopicIcon.SHIELD_DEFENSE, 0xFF37474F, "🛡️",
            q(
                de = "Bundeswehr OR NATO OR Verteidigung OR Militär OR Sicherheitspolitik OR Rüstung",
                en = "NATO OR Pentagon OR military OR defense OR troops OR \"national security\"",
                fr = "OTAN OR armée OR défense OR militaire OR \"sécurité nationale\"",
                es = "OTAN OR ejército OR defensa OR militar OR \"seguridad nacional\"",
                it = "Nato OR esercito OR difesa OR militare OR \"sicurezza nazionale\"",
            ),
        ),

        // Welt
        cat(
            "welt", TopicIcon.PUBLIC, 0xFF0F9D9A, "🌍", NewsSection.WORLD,
            q(
                de = "Ausland OR international OR Krieg OR Diplomatie OR Gipfel OR UN",
                en = "world OR international OR war OR diplomacy OR summit OR \"United Nations\"",
                fr = "international OR monde OR guerre OR diplomatie OR sommet OR ONU",
                es = "internacional OR mundo OR guerra OR diplomacia OR cumbre OR ONU",
                it = "esteri OR mondo OR guerra OR diplomazia OR vertice OR Onu",
            ),
        ),
        sub(
            "welt", "europa", TopicIcon.EUROPE, 0xFF00796B, "🏰",
            q(
                de = "Europa OR Frankreich OR Italien OR Spanien OR Polen OR Großbritannien OR Ukraine",
                en = "Europe OR France OR Germany OR Italy OR Spain OR Poland OR Ukraine",
                fr = "Europe OR Allemagne OR Italie OR Espagne OR Pologne OR Ukraine OR Royaume-Uni",
                es = "Europa OR Francia OR Alemania OR Italia OR Polonia OR Ucrania OR \"Reino Unido\"",
                it = "Europa OR Francia OR Germania OR Spagna OR Polonia OR Ucraina OR \"Regno Unito\"",
            ),
        ),
        sub(
            "welt", "amerika", TopicIcon.AMERICAS, 0xFF00838F, "🌎",
            q(
                de = "USA OR Kanada OR Mexiko OR Brasilien OR Argentinien OR Lateinamerika",
                en = "Canada OR Mexico OR Brazil OR Argentina OR \"Latin America\" OR Venezuela OR Colombia",
                fr = "\"États-Unis\" OR Canada OR Mexique OR Brésil OR Argentine OR \"Amérique latine\"",
                es = "\"Estados Unidos\" OR Canadá OR México OR Brasil OR Argentina OR Latinoamérica",
                it = "\"Stati Uniti\" OR Canada OR Messico OR Brasile OR Argentina OR \"America latina\"",
            ),
        ),
        sub(
            "welt", "asien", TopicIcon.ASIA, 0xFF00897B, "🌏",
            q(
                de = "China OR Japan OR Indien OR Südkorea OR Taiwan OR Asien",
                en = "China OR Japan OR India OR \"South Korea\" OR Taiwan OR Asia",
                fr = "Chine OR Japon OR Inde OR \"Corée du Sud\" OR Taïwan OR Asie",
                es = "China OR Japón OR India OR \"Corea del Sur\" OR Taiwán OR Asia",
                it = "Cina OR Giappone OR India OR \"Corea del Sud\" OR Taiwan OR Asia",
            ),
        ),
        sub(
            "welt", "nahost", TopicIcon.MIDDLE_EAST, 0xFF6D7A2B, "🕌",
            q(
                de = "Nahost OR Israel OR Iran OR Gaza OR Libanon OR Syrien OR Saudi-Arabien",
                en = "\"Middle East\" OR Israel OR Iran OR Gaza OR Lebanon OR Syria OR \"Saudi Arabia\"",
                fr = "Proche-Orient OR Moyen-Orient OR Israël OR Iran OR Gaza OR Liban OR Syrie",
                es = "\"Oriente Medio\" OR \"Oriente Próximo\" OR Israel OR Irán OR Gaza OR Líbano OR Siria",
                it = "\"Medio Oriente\" OR Israele OR Iran OR Gaza OR Libano OR Siria",
            ),
        ),
        sub(
            "welt", "afrika", TopicIcon.AFRICA, 0xFF8D6E2B, "🌍",
            q(
                de = "Afrika OR Nigeria OR Kenia OR Südafrika OR Äthiopien OR Sudan OR Kongo",
                en = "Africa OR Nigeria OR Kenya OR \"South Africa\" OR Ethiopia OR Sudan OR Congo",
                fr = "Afrique OR Sénégal OR \"Côte d'Ivoire\" OR Mali OR Algérie OR Maroc OR Congo",
                es = "África OR Nigeria OR Kenia OR Sudáfrica OR Etiopía OR Sudán OR Marruecos",
                it = "Africa OR Nigeria OR Kenya OR Sudafrica OR Etiopia OR Sudan OR Libia",
            ),
        ),

        // Wirtschaft & Finanzen
        cat(
            "wirtschaft", TopicIcon.TRENDING_UP, 0xFF2E8B57, "📈", NewsSection.BUSINESS,
            q(
                de = "Wirtschaft OR Konjunktur OR Unternehmen OR Börse OR Inflation OR Arbeitsmarkt",
                en = "economy OR business OR markets OR inflation OR earnings OR jobs",
                fr = "économie OR entreprises OR Bourse OR inflation OR croissance OR emploi",
                es = "economía OR empresas OR Bolsa OR inflación OR empleo OR mercados",
                it = "economia OR imprese OR Borsa OR inflazione OR lavoro OR mercati",
            ),
        ),
        sub(
            "wirtschaft", "boerse", TopicIcon.CANDLESTICK, 0xFF1B7A4A, "💹",
            q(
                de = "DAX OR Börse OR Aktien OR \"Wall Street\" OR Aktienmarkt OR Dow",
                en = "\"stock market\" OR stocks OR \"Wall Street\" OR \"S&P 500\" OR Nasdaq OR \"Dow Jones\"",
                fr = "Bourse OR \"CAC 40\" OR actions OR \"Wall Street\" OR marchés",
                es = "Bolsa OR Ibex OR acciones OR \"Wall Street\" OR mercados",
                it = "Borsa OR \"Piazza Affari\" OR \"Ftse Mib\" OR azioni OR \"Wall Street\"",
            ),
        ),
        sub(
            "wirtschaft", "trading", TopicIcon.SHOW_CHART, 0xFF2E7D32, "📊",
            q(
                de = "ETF OR Kursziel OR Anleger OR Aktienanalyse OR Dividende OR Analysten",
                en = "ETF OR \"price target\" OR investors OR dividend OR \"analyst rating\" OR \"buy rating\"",
                fr = "ETF OR \"objectif de cours\" OR investisseurs OR dividende OR analystes",
                es = "ETF OR \"precio objetivo\" OR inversores OR dividendo OR analistas",
                it = "ETF OR \"prezzo obiettivo\" OR investitori OR dividendo OR analisti",
            ),
        ),
        sub(
            "wirtschaft", "krypto", TopicIcon.BITCOIN, 0xFFB26A00, "🪙",
            q(
                de = "Bitcoin OR Krypto OR Kryptowährung OR Ethereum OR Blockchain",
                en = "Bitcoin OR crypto OR cryptocurrency OR Ethereum OR blockchain",
                fr = "Bitcoin OR crypto OR cryptomonnaie OR Ethereum OR blockchain",
                es = "Bitcoin OR cripto OR criptomoneda OR Ethereum OR blockchain",
                it = "Bitcoin OR cripto OR criptovaluta OR Ethereum OR blockchain",
            ),
        ),
        sub(
            "wirtschaft", "konjunktur", TopicIcon.BANK, 0xFF33691E, "🏦",
            q(
                de = "EZB OR Leitzins OR Inflation OR Konjunktur OR Notenbank OR Zinsen",
                en = "\"Federal Reserve\" OR \"interest rates\" OR inflation OR recession OR \"central bank\" OR ECB",
                fr = "BCE OR \"taux d'intérêt\" OR inflation OR croissance OR récession OR \"banque centrale\"",
                es = "BCE OR \"tipos de interés\" OR inflación OR PIB OR recesión OR \"banco central\"",
                it = "Bce OR \"tassi di interesse\" OR inflazione OR Pil OR recessione OR \"banca centrale\"",
            ),
        ),
        sub(
            "wirtschaft", "unternehmen", TopicIcon.FACTORY, 0xFF455A64, "🏭",
            q(
                de = "Konzern OR Quartalszahlen OR Übernahme OR Stellenabbau OR Umsatz OR Gewinnwarnung",
                en = "earnings OR \"quarterly results\" OR merger OR acquisition OR layoffs OR CEO",
                fr = "entreprise OR résultats OR rachat OR fusion OR licenciements OR \"chiffre d'affaires\"",
                es = "empresa OR resultados OR fusión OR adquisición OR despidos OR facturación",
                it = "aziende OR trimestrale OR fusione OR acquisizione OR licenziamenti OR fatturato",
            ),
        ),
        sub(
            "wirtschaft", "geld", TopicIcon.SAVINGS, 0xFF00796B, "💶",
            q(
                de = "Rente OR Steuern OR Sparen OR Verbraucher OR Kredit OR Versicherung",
                en = "\"personal finance\" OR taxes OR \"retirement savings\" OR pension OR \"credit card\" OR \"Social Security\"",
                fr = "retraite OR impôts OR épargne OR \"pouvoir d'achat\" OR crédit OR assurance",
                es = "pensiones OR impuestos OR ahorro OR hipoteca OR consumidores OR Hacienda",
                it = "pensioni OR tasse OR risparmio OR mutuo OR consumatori OR bollette",
            ),
        ),
        sub(
            "wirtschaft", "immobilien", TopicIcon.HOME_WORK, 0xFF6D4C41, "🏠",
            q(
                de = "Immobilien OR Mieten OR Wohnungsmarkt OR Bauzinsen OR Hauspreise OR Wohnungsbau",
                en = "\"housing market\" OR \"real estate\" OR \"home prices\" OR mortgage OR rents",
                fr = "immobilier OR loyers OR \"prix de l'immobilier\" OR \"crédit immobilier\" OR logement",
                es = "vivienda OR inmobiliario OR alquiler OR \"precio de la vivienda\" OR hipotecas",
                it = "immobiliare OR affitti OR \"prezzi delle case\" OR mutui OR \"mercato immobiliare\"",
            ),
        ),
        sub(
            "wirtschaft", "startups", TopicIcon.ROCKET_STARTUP, 0xFF5E35B1, "🚀",
            q(
                de = "Startup OR Start-up OR Gründer OR Finanzierungsrunde OR Risikokapital OR Investoren",
                en = "startup OR \"venture capital\" OR \"funding round\" OR \"Series A\" OR unicorn",
                fr = "startup OR \"levée de fonds\" OR \"capital-risque\" OR licorne OR \"jeune pousse\"",
                es = "startup OR \"ronda de financiación\" OR \"capital riesgo\" OR emprendedores OR unicornio",
                it = "startup OR \"round di finanziamento\" OR \"venture capital\" OR unicorno OR imprenditori",
            ),
        ),

        // Tech & KI
        cat(
            "ki", TopicIcon.DEVICES, 0xFF7B61FF, "🤖", NewsSection.TECHNOLOGY,
            q(
                de = "Technik OR KI OR Software OR Smartphone OR Internet OR Digitalisierung",
                en = "technology OR AI OR software OR smartphone OR internet OR \"big tech\"",
                fr = "technologie OR IA OR logiciel OR smartphone OR internet OR numérique",
                es = "tecnología OR IA OR software OR smartphone OR internet OR digital",
                it = "tecnologia OR IA OR software OR smartphone OR internet OR digitale",
            ),
        ),
        sub(
            "ki", "ki", TopicIcon.SMART_TOY, 0xFF6A4BE0, "🤖",
            q(
                de = "KI OR \"künstliche Intelligenz\" OR ChatGPT OR OpenAI OR Sprachmodell OR \"Machine Learning\"",
                en = "AI OR \"artificial intelligence\" OR ChatGPT OR OpenAI OR LLM OR \"machine learning\"",
                fr = "IA OR \"intelligence artificielle\" OR ChatGPT OR OpenAI OR \"modèle de langage\"",
                es = "IA OR \"inteligencia artificial\" OR ChatGPT OR OpenAI OR \"modelo de lenguaje\"",
                it = "IA OR \"intelligenza artificiale\" OR ChatGPT OR OpenAI OR \"modello linguistico\"",
            ),
        ),
        sub(
            "ki", "smartphones", TopicIcon.PHONE, 0xFF5C6BC0, "📱",
            q(
                de = "Smartphone OR iPhone OR Android OR \"Samsung Galaxy\" OR \"Google Pixel\" OR Handy",
                en = "smartphone OR iPhone OR Android OR \"Samsung Galaxy\" OR \"Google Pixel\"",
                fr = "smartphone OR iPhone OR Android OR \"Samsung Galaxy\" OR \"Google Pixel\"",
                es = "smartphone OR móvil OR iPhone OR Android OR \"Samsung Galaxy\"",
                it = "smartphone OR iPhone OR Android OR \"Samsung Galaxy\" OR \"Google Pixel\"",
            ),
        ),
        sub(
            "ki", "software", TopicIcon.CODE, 0xFF3949AB, "💻",
            q(
                de = "Software OR Windows OR macOS OR Linux OR \"Open Source\" OR Browser OR App-Update",
                en = "software OR Windows OR macOS OR Linux OR \"open source\" OR \"app update\" OR browser",
                fr = "logiciel OR Windows OR macOS OR Linux OR \"open source\" OR \"mise à jour\"",
                es = "software OR Windows OR macOS OR Linux OR \"código abierto\" OR actualización",
                it = "software OR Windows OR macOS OR Linux OR \"open source\" OR aggiornamento",
            ),
        ),
        sub(
            "ki", "security", TopicIcon.SECURITY, 0xFF8E2430, "🔐",
            q(
                de = "Sicherheitslücke OR Hackerangriff OR Cyberangriff OR Datenleck OR Ransomware OR Schadsoftware",
                en = "cybersecurity OR hackers OR \"data breach\" OR ransomware OR vulnerability OR malware",
                fr = "cybersécurité OR piratage OR \"fuite de données\" OR rançongiciel OR cyberattaque",
                es = "ciberseguridad OR hackers OR ciberataque OR \"filtración de datos\" OR ransomware",
                it = "\"sicurezza informatica\" OR hacker OR \"attacco informatico\" OR ransomware OR \"violazione dei dati\"",
            ),
        ),
        sub(
            "ki", "hardware", TopicIcon.MEMORY, 0xFF4E5B6E, "🖥️",
            q(
                de = "Prozessor OR Grafikkarte OR Nvidia OR Intel OR AMD OR Chip OR Laptop",
                en = "CPU OR GPU OR Nvidia OR Intel OR AMD OR chip OR laptop OR semiconductor",
                fr = "processeur OR \"carte graphique\" OR Nvidia OR Intel OR AMD OR puce OR semi-conducteurs",
                es = "procesador OR \"tarjeta gráfica\" OR Nvidia OR Intel OR AMD OR chip OR semiconductores",
                it = "processore OR \"scheda video\" OR Nvidia OR Intel OR AMD OR chip OR semiconduttori",
            ),
        ),
        sub(
            "ki", "netz", TopicIcon.WIFI, 0xFF1565C0, "🌐",
            q(
                de = "Netzpolitik OR \"soziale Medien\" OR Glasfaser OR Digitalisierung OR Datenschutz OR Plattformen",
                en = "internet OR \"social media\" OR broadband OR \"net neutrality\" OR privacy OR \"digital policy\"",
                fr = "internet OR \"réseaux sociaux\" OR fibre OR numérique OR \"données personnelles\"",
                es = "internet OR \"redes sociales\" OR \"fibra óptica\" OR digitalización OR privacidad",
                it = "internet OR \"social network\" OR \"fibra ottica\" OR digitale OR privacy",
            ),
        ),

        // Wissenschaft
        cat(
            "wissenschaft", TopicIcon.SCIENCE, 0xFF3949AB, "🔭", NewsSection.SCIENCE,
            q(
                de = "Wissenschaft OR Forschung OR Studie OR Forscher OR Raumfahrt",
                en = "science OR research OR study OR scientists OR space",
                fr = "science OR recherche OR étude OR chercheurs OR espace",
                es = "ciencia OR investigación OR estudio OR científicos OR espacio",
                it = "scienza OR ricerca OR studio OR scienziati OR spazio",
            ),
        ),
        sub(
            "wissenschaft", "raumfahrt", TopicIcon.TELESCOPE, 0xFF283593, "🚀",
            q(
                de = "Raumfahrt OR NASA OR ESA OR SpaceX OR Weltraum OR Rakete OR Astronomie",
                en = "space OR NASA OR SpaceX OR ESA OR astronomy OR telescope OR rocket",
                fr = "espace OR NASA OR ESA OR SpaceX OR fusée OR astronomie OR spatial",
                es = "espacio OR NASA OR ESA OR SpaceX OR cohete OR astronomía",
                it = "spazio OR NASA OR ESA OR SpaceX OR razzo OR astronomia",
            ),
        ),
        sub(
            "wissenschaft", "physik", TopicIcon.ATOM, 0xFF4527A0, "⚛️",
            q(
                de = "Physik OR Quanten OR Teilchen OR Chemie OR CERN OR Physiker",
                en = "physics OR quantum OR particle OR chemistry OR CERN OR physicists",
                fr = "physique OR quantique OR particules OR chimie OR CERN",
                es = "física OR cuántica OR partículas OR química OR CERN",
                it = "fisica OR quantistica OR particelle OR chimica OR CERN",
            ),
        ),
        sub(
            "wissenschaft", "natur", TopicIcon.PETS, 0xFF558B2F, "🦊",
            q(
                de = "Tiere OR Tierart OR Artenschutz OR Biologie OR Natur OR Zoo",
                en = "wildlife OR animals OR species OR biology OR biodiversity OR zoo",
                fr = "animaux OR espèce OR biodiversité OR biologie OR faune",
                es = "animales OR especie OR biodiversidad OR biología OR fauna",
                it = "animali OR specie OR biodiversità OR biologia OR fauna",
            ),
        ),
        sub(
            "wissenschaft", "geschichte", TopicIcon.MUSEUM, 0xFF795548, "🏺",
            q(
                de = "Archäologie OR Archäologen OR Ausgrabung OR Historiker OR Geschichte OR Fund",
                en = "archaeology OR archaeologists OR excavation OR historians OR ancient OR \"history of\"",
                fr = "archéologie OR archéologues OR fouilles OR historiens OR antique",
                es = "arqueología OR arqueólogos OR excavación OR historiadores OR antiguo",
                it = "archeologia OR archeologi OR scavi OR storici OR antico",
            ),
        ),

        // Gesundheit
        cat(
            "gesundheit", TopicIcon.HEALTH, 0xFFD81B60, "🩺", NewsSection.HEALTH,
            q(
                de = "Gesundheit OR Medizin OR Krankheit OR Ärzte OR Krankenkasse OR Pflege",
                en = "health OR medicine OR disease OR doctors OR hospital OR \"public health\"",
                fr = "santé OR médecine OR maladie OR médecins OR hôpital",
                es = "salud OR medicina OR enfermedad OR médicos OR hospital",
                it = "salute OR medicina OR malattia OR medici OR ospedale",
            ),
        ),
        sub(
            "gesundheit", "medizin", TopicIcon.SCIENCE, 0xFFAD1457, "💊",
            q(
                de = "Medizin OR Studie OR Krebs OR Therapie OR Klinik OR Impfung",
                en = "medicine OR \"clinical trial\" OR cancer OR treatment OR hospital OR vaccine",
                fr = "médecine OR cancer OR traitement OR hôpital OR vaccin OR \"essai clinique\"",
                es = "medicina OR cáncer OR tratamiento OR hospital OR vacuna OR \"ensayo clínico\"",
                it = "medicina OR tumore OR terapia OR ospedale OR vaccino OR \"studio clinico\"",
            ),
        ),
        sub(
            "gesundheit", "ernaehrung", TopicIcon.RESTAURANT, 0xFFC2185B, "🥗",
            q(
                de = "Ernährung OR Diät OR Abnehmen OR Fitness OR \"gesund essen\" OR Lebensmittel",
                en = "nutrition OR diet OR \"weight loss\" OR fitness OR \"healthy eating\" OR workout",
                fr = "nutrition OR alimentation OR régime OR \"perte de poids\" OR fitness",
                es = "nutrición OR dieta OR \"perder peso\" OR alimentación OR ejercicio",
                it = "alimentazione OR dieta OR \"perdere peso\" OR nutrizione OR fitness",
            ),
        ),
        sub(
            "gesundheit", "psyche", TopicIcon.PSYCHOLOGY, 0xFF8E24AA, "🧠",
            q(
                de = "\"psychische Gesundheit\" OR Depression OR Psyche OR Stress OR Angststörung OR Psychologie",
                en = "\"mental health\" OR depression OR anxiety OR stress OR psychology OR therapy",
                fr = "\"santé mentale\" OR dépression OR anxiété OR stress OR psychologie",
                es = "\"salud mental\" OR depresión OR ansiedad OR estrés OR psicología",
                it = "\"salute mentale\" OR depressione OR ansia OR stress OR psicologia",
            ),
        ),

        // Klima & Energie
        cat(
            "klima", TopicIcon.ECO, 0xFF43A047, "🌱",
            queries = q(
                de = "Klima OR Klimawandel OR Energiewende OR Umwelt OR Klimaschutz",
                en = "climate OR \"climate change\" OR environment OR \"renewable energy\" OR emissions",
                fr = "climat OR \"réchauffement climatique\" OR environnement OR \"énergies renouvelables\"",
                es = "clima OR \"cambio climático\" OR \"medio ambiente\" OR \"energías renovables\"",
                it = "clima OR \"cambiamento climatico\" OR ambiente OR \"energie rinnovabili\"",
            ),
        ),
        sub(
            "klima", "klimawandel", TopicIcon.THERMOSTAT, 0xFF2E7D32, "🌡️",
            q(
                de = "Klimawandel OR Erderwärmung OR Klimaschutz OR Klimakrise OR Hitzewelle OR CO2",
                en = "\"climate change\" OR \"global warming\" OR \"climate crisis\" OR heatwave OR emissions",
                fr = "\"changement climatique\" OR \"réchauffement climatique\" OR canicule OR émissions",
                es = "\"cambio climático\" OR \"calentamiento global\" OR \"ola de calor\" OR emisiones",
                it = "\"cambiamento climatico\" OR \"riscaldamento globale\" OR \"ondata di calore\" OR emissioni",
            ),
        ),
        sub(
            "klima", "energie", TopicIcon.BOLT, 0xFFB26A00, "⚡",
            q(
                de = "Energiewende OR Solar OR Windkraft OR Strompreis OR Gaspreis OR Wasserstoff",
                en = "\"renewable energy\" OR solar OR \"wind power\" OR \"energy prices\" OR \"natural gas\" OR oil",
                fr = "énergie OR solaire OR éolien OR \"prix de l'électricité\" OR nucléaire OR hydrogène",
                es = "energía OR solar OR eólica OR \"precio de la luz\" OR renovables OR hidrógeno",
                it = "energia OR solare OR eolico OR bollette OR rinnovabili OR idrogeno",
            ),
        ),
        sub(
            "klima", "umwelt", TopicIcon.FOREST, 0xFF33691E, "🌳",
            q(
                de = "Umweltschutz OR Artensterben OR Plastik OR Naturschutz OR Umwelt OR Waldsterben",
                en = "environment OR pollution OR plastic OR deforestation OR conservation OR wildlife",
                fr = "environnement OR pollution OR plastique OR déforestation OR biodiversité",
                es = "\"medio ambiente\" OR contaminación OR plástico OR deforestación OR biodiversidad",
                it = "ambiente OR inquinamento OR plastica OR deforestazione OR biodiversità",
            ),
        ),

        // Sport
        cat(
            "sport", TopicIcon.SPORTS, 0xFFE0A100, "⚽", NewsSection.SPORTS,
            q(
                de = "Sport OR Bundesliga OR \"Formel 1\" OR Tennis OR Handball OR Olympia",
                en = "sports OR NFL OR NBA OR soccer OR baseball OR tennis",
                fr = "sport OR football OR rugby OR tennis OR cyclisme OR basket",
                es = "deportes OR fútbol OR baloncesto OR tenis OR \"Fórmula 1\"",
                it = "sport OR calcio OR tennis OR \"Formula 1\" OR basket OR ciclismo",
            ),
        ),
        sub(
            "sport", "fussball", TopicIcon.SOCCER, 0xFF2E7D32, "⚽",
            q(
                de = "Bundesliga OR Fußball OR \"Champions League\" OR DFB OR Nationalmannschaft OR Transfer",
                en = "\"Premier League\" OR \"Champions League\" OR soccer OR MLS OR \"World Cup\" OR FIFA",
                fr = "football OR \"Ligue 1\" OR \"Ligue des champions\" OR PSG OR \"équipe de France\"",
                es = "fútbol OR LaLiga OR \"Champions League\" OR \"Real Madrid\" OR Barcelona",
                it = "calcio OR \"Serie A\" OR \"Champions League\" OR Juventus OR Inter OR Milan",
            ),
        ),
        sub(
            "sport", "motorsport", TopicIcon.MOTORSPORTS, 0xFFC62828, "🏎️",
            q(
                de = "\"Formel 1\" OR MotoGP OR Motorsport OR \"Grand Prix\" OR DTM OR Rallye",
                en = "\"Formula 1\" OR F1 OR MotoGP OR NASCAR OR IndyCar OR \"Grand Prix\"",
                fr = "\"Formule 1\" OR F1 OR MotoGP OR \"Grand Prix\" OR rallye",
                es = "\"Fórmula 1\" OR F1 OR MotoGP OR \"Gran Premio\" OR rally",
                it = "\"Formula 1\" OR F1 OR MotoGP OR Ferrari OR \"Gran Premio\"",
            ),
        ),
        sub(
            "sport", "tennis", TopicIcon.TENNIS, 0xFF9E9D24, "🎾",
            q(
                de = "Tennis OR ATP OR WTA OR Wimbledon OR \"Grand Slam\" OR \"Davis Cup\"",
                en = "tennis OR ATP OR WTA OR Wimbledon OR \"US Open\" OR \"Grand Slam\"",
                fr = "tennis OR ATP OR WTA OR \"Roland-Garros\" OR Wimbledon",
                es = "tenis OR ATP OR WTA OR Wimbledon OR \"Roland Garros\"",
                it = "tennis OR ATP OR WTA OR Wimbledon OR \"Coppa Davis\"",
            ),
        ),
        sub(
            "sport", "basketball", TopicIcon.BASKETBALL, 0xFFD84315, "🏀",
            q(
                de = "Basketball OR NBA OR BBL OR Euroleague OR Basketballer",
                en = "NBA OR basketball OR WNBA OR EuroLeague",
                fr = "basket OR NBA OR \"Betclic Élite\" OR Euroligue",
                es = "baloncesto OR NBA OR \"Liga Endesa\" OR Euroliga",
                it = "basket OR NBA OR Eurolega OR \"Serie A di basket\"",
            ),
        ),
        sub(
            "sport", "football", TopicIcon.FOOTBALL, 0xFF6D4C41, "🏈",
            q(
                de = "NFL OR \"American Football\" OR \"Super Bowl\" OR Quarterback",
                en = "NFL OR \"college football\" OR \"Super Bowl\" OR quarterback OR touchdown",
                fr = "NFL OR \"football américain\" OR \"Super Bowl\"",
                es = "NFL OR \"fútbol americano\" OR \"Super Bowl\"",
                it = "NFL OR \"football americano\" OR \"Super Bowl\"",
            ),
        ),
        sub(
            "sport", "wintersport", TopicIcon.SKI, 0xFF0277BD, "⛷️",
            q(
                de = "Wintersport OR \"Ski alpin\" OR Biathlon OR Skispringen OR Langlauf OR Rodeln",
                en = "skiing OR \"alpine skiing\" OR biathlon OR snowboard OR \"ski jumping\" OR \"Winter Olympics\"",
                fr = "ski OR biathlon OR \"saut à ski\" OR snowboard OR \"ski alpin\"",
                es = "esquí OR biatlón OR snowboard OR \"deportes de invierno\"",
                it = "sci OR biathlon OR \"salto con gli sci\" OR snowboard OR \"Coppa del mondo di sci\"",
            ),
        ),

        // Kultur & Unterhaltung
        cat(
            "entertainment", TopicIcon.THEATER, 0xFF8E24AA, "🎭", NewsSection.ENTERTAINMENT,
            q(
                de = "Kultur OR Kino OR Musik OR Serie OR Games OR Promis",
                en = "entertainment OR movies OR music OR TV OR celebrities OR \"video games\"",
                fr = "culture OR cinéma OR musique OR série OR people",
                es = "cultura OR cine OR música OR series OR famosos",
                it = "cultura OR cinema OR musica OR serie OR spettacolo",
            ),
        ),
        sub(
            "entertainment", "games", TopicIcon.GAMEPAD, 0xFF6A1B9A, "🎮",
            q(
                de = "Games OR Videospiel OR PlayStation OR Xbox OR Nintendo OR Gaming",
                en = "\"video games\" OR gaming OR PlayStation OR Xbox OR Nintendo OR Steam",
                fr = "\"jeux vidéo\" OR PlayStation OR Xbox OR Nintendo OR gaming",
                es = "videojuegos OR PlayStation OR Xbox OR Nintendo OR gaming",
                it = "videogiochi OR PlayStation OR Xbox OR Nintendo OR gaming",
            ),
        ),
        sub(
            "entertainment", "film", TopicIcon.MOVIE, 0xFF4A148C, "🎬",
            q(
                de = "Kino OR Film OR Serie OR Netflix OR Streaming OR Hollywood",
                en = "movie OR film OR \"box office\" OR Netflix OR \"TV series\" OR Hollywood",
                fr = "cinéma OR film OR série OR Netflix OR \"box-office\"",
                es = "cine OR película OR serie OR Netflix OR taquilla",
                it = "cinema OR film OR serie OR Netflix OR \"box office\"",
            ),
        ),
        sub(
            "entertainment", "musik", TopicIcon.MUSIC, 0xFFAD1457, "🎵",
            q(
                de = "Musik OR Album OR Konzert OR Tournee OR Charts OR Band",
                en = "music OR album OR concert OR tour OR Grammy OR \"new single\"",
                fr = "musique OR album OR concert OR tournée OR chanteur",
                es = "música OR álbum OR concierto OR gira OR cantante",
                it = "musica OR album OR concerto OR tour OR cantante",
            ),
        ),
        sub(
            "entertainment", "buecher", TopicIcon.BOOK, 0xFF5D4037, "📚",
            q(
                de = "Buch OR Roman OR Autorin OR Schriftsteller OR Literatur OR Buchpreis",
                en = "book OR novel OR author OR literature OR bestseller OR \"book prize\"",
                fr = "livre OR roman OR écrivain OR littérature OR \"prix Goncourt\"",
                es = "libro OR novela OR escritor OR literatura OR \"premio literario\"",
                it = "libro OR romanzo OR scrittore OR letteratura OR \"premio Strega\"",
            ),
        ),
        sub(
            "entertainment", "leute", TopicIcon.STAR, 0xFFC2185B, "⭐",
            q(
                de = "Promis OR Prominente OR Royals OR Königshaus OR \"roter Teppich\" OR Schauspielerin",
                en = "celebrity OR celebrities OR royals OR \"royal family\" OR \"red carpet\" OR paparazzi",
                fr = "people OR célébrités OR \"famille royale\" OR actrice OR chanteuse",
                es = "famosos OR celebridades OR \"casa real\" OR actriz OR cantante",
                it = "vip OR celebrità OR \"famiglia reale\" OR attrice OR cantante",
            ),
        ),

        // Auto & Mobilität
        cat(
            "auto", TopicIcon.CAR, 0xFF455A64, "🚗",
            queries = q(
                de = "Auto OR Autoindustrie OR Autobauer OR Elektroauto OR Verkehr OR ADAC",
                en = "cars OR automakers OR \"auto industry\" OR \"electric vehicles\" OR Tesla OR EV",
                fr = "automobile OR voiture OR constructeur OR \"voiture électrique\"",
                es = "automóvil OR coches OR \"coche eléctrico\" OR \"industria automotriz\"",
                it = "auto OR automobili OR \"auto elettrica\" OR \"settore automotive\"",
            ),
        ),
        sub(
            "auto", "elektro", TopicIcon.EV, 0xFF00796B, "🔌",
            q(
                de = "Elektroauto OR E-Auto OR Ladesäule OR Elektromobilität OR Tesla OR Batterie",
                en = "\"electric vehicle\" OR EV OR Tesla OR \"charging station\" OR \"electric car\" OR \"EV battery\"",
                fr = "\"voiture électrique\" OR Tesla OR recharge OR batterie OR \"véhicule électrique\"",
                es = "\"coche eléctrico\" OR Tesla OR \"punto de carga\" OR batería OR \"vehículo eléctrico\"",
                it = "\"auto elettrica\" OR Tesla OR ricarica OR batteria OR \"veicoli elettrici\"",
            ),
        ),
        sub(
            "auto", "verkehr", TopicIcon.TRAIN, 0xFF37474F, "🚆",
            q(
                de = "\"Deutsche Bahn\" OR Bahn OR Verkehr OR Flughafen OR Deutschlandticket OR ÖPNV",
                en = "\"public transport\" OR transit OR rail OR airline OR airport OR traffic",
                fr = "SNCF OR train OR trafic OR aéroport OR \"transports en commun\"",
                es = "Renfe OR tren OR tráfico OR aeropuerto OR \"transporte público\"",
                it = "Trenitalia OR treni OR traffico OR aeroporto OR \"trasporto pubblico\"",
            ),
        ),

        // Region (Bundesland-Presets in Germany, the home place elsewhere)
        cat(REGION, TopicIcon.LOCATION, 0xFFC6613F, "📍"),
    )

    private val byId: Map<String, TopicDef> = ALL.associateBy { it.id }

    /** The categories in display order. */
    val CATEGORIES: List<TopicDef> = ALL.filter { it.isCategory }

    /** A built-in topic by id, or null. */
    fun byId(id: String): TopicDef? = byId[id]

    /** True for every built-in id (categories and sub-topics). */
    fun isBuiltIn(id: String): Boolean = id in byId

    /** The sub-topics of [categoryId], in display order (empty for a sub-topic or an unknown id). */
    fun children(categoryId: String): List<TopicDef> = ALL.filter { it.parentId == categoryId }

    /** The topics a fresh install follows: the region only where its presets exist (Germany). */
    fun defaultFollowed(edition: NewsEdition): List<String> =
        listOf(TOP, "politik", "welt", "wirtschaft", "wirtschaft/boerse", "ki", "wissenschaft", "sport") +
            if (Regions.servesEdition(edition)) listOf(REGION) else emptyList()
}
