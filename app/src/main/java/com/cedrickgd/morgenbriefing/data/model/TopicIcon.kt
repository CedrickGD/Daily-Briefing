package com.cedrickgd.morgenbriefing.data.model

/**
 * The pictograms of topics, stored by name in [Topic.icon]. The data layer only knows the keys;
 * `ui/news/TopicIcons.kt` maps each to a Material Symbols (Rounded) vector. Never rename an
 * entry – custom topics store the name. New entries may be added.
 */
enum class TopicIcon {
    NEWSPAPER, BOLT_NEWS, HOW_TO_VOTE, GAVEL, BALLOT, FLAG, SHIELD_DEFENSE, EU_STARS,
    PUBLIC, EUROPE, AMERICAS, ASIA, MIDDLE_EAST, AFRICA,
    CANDLESTICK, SHOW_CHART, TRENDING_UP, BITCOIN, BANK, FACTORY, SAVINGS, HOME_WORK, ROCKET_STARTUP, PERCENT,
    SMART_TOY, PHONE, CODE, SECURITY, MEMORY, WIFI,
    SCIENCE, TELESCOPE, ATOM, PETS, MUSEUM,
    HEALTH, RESTAURANT, PSYCHOLOGY,
    ECO, BOLT, FOREST, THERMOSTAT,
    SOCCER, MOTORSPORTS, TENNIS, BASKETBALL, FOOTBALL, SKI, SPORTS,
    GAMEPAD, MOVIE, MUSIC, BOOK, STAR, THEATER,
    CAR, EV, TRAIN,
    LOCATION, SEARCH, TAG, BOOKMARK, LIGHTBULB, PALETTE, TRAVEL, FOOD,
    /** Screen and phone: technology as a whole (the "Tech & KI" category). */
    DEVICES,
    ;

    companion object {
        fun fromKey(key: String?): TopicIcon? = entries.firstOrNull { it.name == key }
    }
}
