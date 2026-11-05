package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Ballot
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Museum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.ui.components.Emoji

/** The Material Symbols (Rounded) pictogram of every [TopicIcon]. */
@Suppress("DEPRECATION") // the non-AutoMirrored names are fine: none of these is directional text
fun TopicIcon.vector(): ImageVector = when (this) {
    TopicIcon.NEWSPAPER -> Icons.Rounded.Newspaper
    TopicIcon.BOLT_NEWS -> Icons.Rounded.NewReleases
    TopicIcon.HOW_TO_VOTE -> Icons.Rounded.HowToVote
    TopicIcon.GAVEL -> Icons.Rounded.Gavel
    TopicIcon.BALLOT -> Icons.Rounded.Ballot
    TopicIcon.FLAG -> Icons.Rounded.Flag
    TopicIcon.SHIELD_DEFENSE -> Icons.Rounded.Shield
    TopicIcon.EU_STARS -> Icons.Rounded.Stars
    TopicIcon.PUBLIC -> Icons.Rounded.Public
    TopicIcon.EUROPE -> Icons.Rounded.Language
    TopicIcon.AMERICAS -> Icons.Rounded.TravelExplore
    TopicIcon.ASIA -> Icons.Rounded.Explore
    TopicIcon.MIDDLE_EAST -> Icons.Rounded.Map
    TopicIcon.AFRICA -> Icons.Rounded.Public
    TopicIcon.CANDLESTICK -> Icons.Rounded.CandlestickChart
    TopicIcon.SHOW_CHART -> Icons.Rounded.ShowChart
    TopicIcon.TRENDING_UP -> Icons.Rounded.TrendingUp
    TopicIcon.BITCOIN -> Icons.Rounded.CurrencyBitcoin
    TopicIcon.BANK -> Icons.Rounded.AccountBalance
    TopicIcon.FACTORY -> Icons.Rounded.Factory
    TopicIcon.SAVINGS -> Icons.Rounded.Savings
    TopicIcon.HOME_WORK -> Icons.Rounded.HomeWork
    TopicIcon.ROCKET_STARTUP -> Icons.Rounded.RocketLaunch
    TopicIcon.PERCENT -> Icons.Rounded.Percent
    TopicIcon.SMART_TOY -> Icons.Rounded.SmartToy
    TopicIcon.PHONE -> Icons.Rounded.PhoneAndroid
    TopicIcon.CODE -> Icons.Rounded.Code
    TopicIcon.SECURITY -> Icons.Rounded.Security
    TopicIcon.MEMORY -> Icons.Rounded.Memory
    TopicIcon.WIFI -> Icons.Rounded.Wifi
    TopicIcon.SCIENCE -> Icons.Rounded.Science
    TopicIcon.TELESCOPE -> Icons.Rounded.SatelliteAlt
    TopicIcon.ATOM -> Icons.Rounded.Biotech
    TopicIcon.PETS -> Icons.Rounded.Pets
    TopicIcon.MUSEUM -> Icons.Rounded.Museum
    TopicIcon.HEALTH -> Icons.Rounded.HealthAndSafety
    TopicIcon.RESTAURANT -> Icons.Rounded.Restaurant
    TopicIcon.PSYCHOLOGY -> Icons.Rounded.Psychology
    TopicIcon.ECO -> Icons.Rounded.Eco
    TopicIcon.BOLT -> Icons.Rounded.Bolt
    TopicIcon.FOREST -> Icons.Rounded.Forest
    TopicIcon.THERMOSTAT -> Icons.Rounded.Thermostat
    TopicIcon.SOCCER -> Icons.Rounded.SportsSoccer
    TopicIcon.MOTORSPORTS -> Icons.Rounded.SportsMotorsports
    TopicIcon.TENNIS -> Icons.Rounded.SportsTennis
    TopicIcon.BASKETBALL -> Icons.Rounded.SportsBasketball
    TopicIcon.FOOTBALL -> Icons.Rounded.SportsFootball
    TopicIcon.SKI -> Icons.Rounded.DownhillSkiing
    TopicIcon.SPORTS -> Icons.Rounded.Sports
    TopicIcon.GAMEPAD -> Icons.Rounded.SportsEsports
    TopicIcon.MOVIE -> Icons.Rounded.Movie
    TopicIcon.MUSIC -> Icons.Rounded.MusicNote
    TopicIcon.BOOK -> Icons.Rounded.MenuBook
    TopicIcon.STAR -> Icons.Rounded.Star
    TopicIcon.THEATER -> Icons.Rounded.TheaterComedy
    TopicIcon.CAR -> Icons.Rounded.DirectionsCar
    TopicIcon.EV -> Icons.Rounded.ElectricCar
    TopicIcon.TRAIN -> Icons.Rounded.Train
    TopicIcon.LOCATION -> Icons.Rounded.LocationOn
    TopicIcon.SEARCH -> Icons.Rounded.Search
    TopicIcon.TAG -> Icons.Rounded.Tag
    TopicIcon.BOOKMARK -> Icons.Rounded.Bookmark
    TopicIcon.LIGHTBULB -> Icons.Rounded.Lightbulb
    TopicIcon.PALETTE -> Icons.Rounded.Palette
    TopicIcon.TRAVEL -> Icons.Rounded.Flight
    TopicIcon.FOOD -> Icons.Rounded.LunchDining
    TopicIcon.DEVICES -> Icons.Rounded.Devices
}

/** The pictogram of [topic]: its [TopicIcon], or null for old custom topics that only have an emoji. */
fun Topic.iconVector(): ImageVector? = TopicIcon.fromKey(icon)?.vector()

/** A neutral stand-in for topics without icon and emoji. */
@Suppress("DEPRECATION")
val FallbackTopicIcon: ImageVector = Icons.Rounded.Article

/**
 * The topic's pictogram at [size]: the Material icon in [tint], or – for custom topics created
 * before icons existed – their emoji. Use inside a tinted container (see [TopicBadge]).
 */
@Composable
fun TopicGlyph(topic: Topic, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val vector = topic.iconVector()
    when {
        vector != null -> Icon(vector, contentDescription = null, tint = tint, modifier = modifier.size(size))
        topic.emoji.isNotBlank() -> Emoji(topic.emoji, size = (size.value * 0.95f).sp, modifier = modifier)
        else -> Icon(FallbackTopicIcon, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
