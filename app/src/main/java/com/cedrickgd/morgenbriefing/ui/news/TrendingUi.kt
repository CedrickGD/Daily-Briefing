package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.news.TrendSuggestions

/**
 * The search suggestions of the moment (the search bar's examples, the terms of the search
 * screen); none in previews, where there is no MorgenbriefingApp.
 */
@Composable
internal fun rememberTrendSuggestions(): State<TrendSuggestions> {
    val context = LocalContext.current
    val suggestions = remember(context) { (context.applicationContext as? MorgenbriefingApp)?.container?.trending?.suggestions }
    return suggestions?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(TrendSuggestions.NONE) }
}
