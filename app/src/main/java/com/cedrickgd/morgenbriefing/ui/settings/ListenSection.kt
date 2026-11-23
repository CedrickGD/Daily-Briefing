package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.VoicePreference
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.listenStrings
import com.cedrickgd.morgenbriefing.listen
import com.cedrickgd.morgenbriefing.container
import com.cedrickgd.morgenbriefing.listen.GoogleVoiceHint
import com.cedrickgd.morgenbriefing.listen.GoogleVoices
import com.cedrickgd.morgenbriefing.listen.ListenBars
import com.cedrickgd.morgenbriefing.listen.ListenLanguages
import com.cedrickgd.morgenbriefing.listen.ListenVoices
import com.cedrickgd.morgenbriefing.listen.NeuralRuntime
import com.cedrickgd.morgenbriefing.listen.NeuralVoice
import com.cedrickgd.morgenbriefing.listen.NeuralVoiceCatalog
import com.cedrickgd.morgenbriefing.listen.NeuralVoiceIds
import com.cedrickgd.morgenbriefing.listen.NeuralVoicesState
import com.cedrickgd.morgenbriefing.listen.VoiceCatalog
import com.cedrickgd.morgenbriefing.listen.VoiceEntry
import com.cedrickgd.morgenbriefing.listen.VoiceGender
import com.cedrickgd.morgenbriefing.listen.VoiceInfo
import com.cedrickgd.morgenbriefing.listen.VoicePanel
import com.cedrickgd.morgenbriefing.listen.VoiceSource
import com.cedrickgd.morgenbriefing.listen.gender
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.theme.rememberReduceMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Callbacks of the "Vorlesen" card; every change is stored and applied by the listen controller. */
internal class ListenCardActions(
    val onEngine: (String?) -> Unit,
    val onLanguage: (String?) -> Unit,
    val onPreference: (VoicePreference, language: String) -> Unit,
    val onVoice: (language: String, name: String?) -> Unit,
    val onRate: (Float) -> Unit,
    val onPitch: (Float) -> Unit,
)

/**
 * Settings section "Vorlesen": the engine (when the phone has several), the reading language
 * (automatic or fixed), the voice per language with "Probehören" – "Automatisch" first, then the
 * installed voices ("Stimme 1 · weiblich · offline"), a gender preference only where the voices
 * reveal one –, "Natürliche Stimmen" to download (only when the speech library loads), a hint to
 * Google's engine, speed and pitch with a live sample, and "Weitere Stimmen installieren". Honest
 * notes when the phone has no (offline) voice for a language or a chosen voice is gone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListenCard(settings: AppSettings, actions: ListenCardActions, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val controller = remember(context) { context.listen }
    val voices by controller.voices.collectAsStateWithLifecycle()
    val reading by controller.state.collectAsStateWithLifecycle()
    // Every time the card is on screen (and after installing voices): fresh engines and voices.
    LifecycleResumeEffect(controller) {
        controller.refreshVoices()
        onPauseOrDispose { }
    }

    val strings = listenStrings()
    val listen = settings.listen
    val languages = remember(settings) { ListenLanguages.voiceLanguages(settings) }
    var picked by rememberSaveable(listen.language) { mutableStateOf(listen.language ?: settings.edition.language) }
    val language = picked.takeIf { it in languages } ?: languages.first()
    val country = ListenLanguages.preferredCountry(language, settings.edition)
    val panel = remember(voices.voices, language, listen, country) { VoiceCatalog.panel(voices.voices, language, listen, country) }
    val preview: (String?, Float, Float) -> Unit = { voice, rate, pitch -> controller.preview(language, voice, rate, pitch) }
    // Natural voices: only when the speech library loads (probed once, off the main thread).
    val neuralReady by produceState(initialValue = false) { value = NeuralRuntime.probe() }
    val neural = if (neuralReady) remember(context) { context.container.neuralVoices } else null
    val neuralState = neural?.state?.collectAsStateWithLifecycle()?.value ?: NeuralVoicesState()
    // The chosen natural voice reads only while it is on the phone; otherwise "Automatisch" does.
    val neuralSelected = panel.neuralChosen?.takeIf { neuralReady && it in neuralState.installed }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.RecordVoiceOver)
            Spacer(Modifier.width(14.dp))
            Text(
                text = strings.cardHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        if (voices.engines.size > 1) {
            Spacer(Modifier.height(18.dp))
            Overline(strings.engineOverline)
            Spacer(Modifier.height(8.dp))
            val active = listen.engine?.takeIf { wanted -> voices.engines.any { it.name == wanted } } ?: voices.defaultEngine
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                voices.engines.forEach { engine ->
                    ChoicePill(
                        label = engine.shortLabel,
                        selected = engine.name == active,
                        // The phone's default engine is stored as "default", so it follows a change there.
                        onClick = { actions.onEngine(engine.name.takeIf { it != voices.defaultEngine }) },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        LanguageChoice(settings = settings, strings = strings, onLanguage = actions.onLanguage)

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        if (languages.size > 1) {
            Overline(strings.voiceForOverline)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                languages.forEach { code ->
                    ChoicePill(label = languageLabel(strings, code), selected = code == language, onClick = { picked = code })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Overline(strings.voicesOverline(language), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        VoiceSection(
            voices = voices,
            panel = panel,
            listen = listen,
            neuralSelected = neuralSelected != null,
            strings = strings,
            onRetry = controller::refreshVoices,
            onInstall = { controller.openVoiceInstaller(context, language) },
            onPreference = { actions.onPreference(it, language) },
            onVoice = { actions.onVoice(language, it) },
            onPreview = { voice -> preview(voice, listen.rate, listen.pitch) },
        )

        val offered = remember(language) { NeuralVoiceCatalog.forLanguage(language) }
        if (neuralReady && offered.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            NeuralSection(
                offered = offered,
                state = neuralState,
                selected = neuralSelected,
                previewing = voices.previewing,
                metered = remember { !context.container.network.isUnmetered() },
                strings = strings,
                onSelect = { voice -> actions.onVoice(language, NeuralVoiceIds.choice(voice.id)) },
                onPreview = { voice -> preview(NeuralVoiceIds.choice(voice.id), listen.rate, listen.pitch) },
                onDownload = controller::downloadNeural,
                onCancel = controller::cancelNeural,
                onDelete = controller::deleteNeural,
            )
        }

        GoogleVoices.hint(voices, language)?.let { hint ->
            Spacer(Modifier.height(14.dp))
            GoogleHintCard(
                hint = hint,
                strings = strings,
                onAction = {
                    when (hint) {
                        GoogleVoiceHint.INSTALL -> openGoogleTtsListing(context)
                        // The phone's default engine is stored as "default", so it follows a change there.
                        GoogleVoiceHint.USE -> actions.onEngine(GoogleVoices.PACKAGE.takeIf { it != voices.defaultEngine })
                    }
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        // The sample after a change uses the voice this language reads with.
        val sampleVoice = neuralSelected?.let(NeuralVoiceIds::choice) ?: panel.chosen?.voice?.name
        SpeedAndPitch(
            listen = listen,
            strings = strings,
            onRate = { rate ->
                actions.onRate(rate)
                // While an article plays, the article itself is the preview.
                if (!reading.speaking) preview(sampleVoice, rate, listen.pitch)
            },
            onPitch = { pitch ->
                actions.onPitch(pitch)
                if (!reading.speaking) preview(sampleVoice, listen.rate, pitch)
            },
        )

        Spacer(Modifier.height(18.dp))
        if (panel.notInstalled > 0 && voices.loaded) {
            Text(
                text = strings.notInstalledVoices(panel.notInstalled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        // Without any voice for the language, the note above already offers the installer.
        if (!panel.noVoice) {
            GlassPillButton(
                text = strings.installMoreVoices,
                onClick = { controller.openVoiceInstaller(context, language) },
                icon = Icons.Rounded.Download,
                style = PillStyle.Quiet,
            )
        }
    }
}

/** "Automatisch" (each article in its language) or always one language. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChoice(settings: AppSettings, strings: ListenStrings, onLanguage: (String?) -> Unit) {
    val fixed = settings.listen.language
    Overline(strings.languageOverline)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.selectableGroup(),
    ) {
        ChoicePill(label = strings.languageAuto, selected = fixed == null, onClick = { onLanguage(null) }, icon = Icons.Rounded.AutoMode)
        ListenLanguages.fixedChoices(settings.edition, fixed).forEach { code ->
            ChoicePill(label = languageLabel(strings, code), selected = fixed == code, onClick = { onLanguage(code) })
        }
    }
    AnimatedContent(targetState = fixed, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "listenLanguageHint") { language ->
        Text(
            text = if (language == null) strings.languageAutoHint else strings.languageFixedHint(language),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "Englisch" for a pill (French, Spanish and Italian names start lowercase in running text). */
@Composable
private fun languageLabel(strings: ListenStrings, code: String): String {
    val locale = LocalAppLanguage.current.locale
    return strings.languageName(code).replaceFirstChar { it.titlecase(locale) }
}

/** The voices of one language: loading, failure, no voice, or the preference pills and the list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(
    voices: ListenVoices,
    panel: VoicePanel,
    listen: ListenSettings,
    neuralSelected: Boolean,
    strings: ListenStrings,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onPreference: (VoicePreference) -> Unit,
    onVoice: (String?) -> Unit,
    onPreview: (String?) -> Unit,
) {
    when {
        !voices.loaded && !voices.failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(strings.loadingVoices, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        !voices.loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.voicesFailed,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(commonStrings().retry) }
        }
        voices.voices.isEmpty() -> Note(strings.engineListsNoVoices)
        panel.noVoice -> {
            Note(strings.noVoiceFor(panel.language), icon = Icons.Rounded.WarningAmber, tint = StatusPalette.attention())
            Spacer(Modifier.height(10.dp))
            GlassPillButton(text = strings.installMoreVoices, onClick = onInstall, icon = Icons.Rounded.Download)
        }
        else -> {
            // Only the genders at least one listed voice reveals.
            val shown = panel.genders
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Overline(strings.preferenceOverline)
                Spacer(Modifier.height(8.dp))
                val effective = listen.voicePreference.takeIf { it == VoicePreference.ANY || it.gender in shown } ?: VoicePreference.ANY
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.selectableGroup(),
                ) {
                    ChoicePill(strings.preferenceAny, selected = effective == VoicePreference.ANY, onClick = { onPreference(VoicePreference.ANY) })
                    if (VoiceGender.FEMALE in shown) {
                        ChoicePill(strings.preferenceFemale, selected = effective == VoicePreference.FEMALE, onClick = { onPreference(VoicePreference.FEMALE) })
                    }
                    if (VoiceGender.MALE in shown) {
                        ChoicePill(strings.preferenceMale, selected = effective == VoicePreference.MALE, onClick = { onPreference(VoicePreference.MALE) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            VoiceList(
                panel = panel,
                listen = listen,
                neuralSelected = neuralSelected,
                previewing = voices.previewing,
                strings = strings,
                onVoice = onVoice,
                onPreview = onPreview,
            )
            if (panel.chosenMissing) {
                Spacer(Modifier.height(8.dp))
                Note(strings.chosenVoiceMissing, icon = Icons.Rounded.WarningAmber, tint = StatusPalette.attention())
            }
            if (panel.onlineOnly) {
                Spacer(Modifier.height(8.dp))
                Note(strings.onlineOnlyFor(panel.language), icon = Icons.Rounded.WarningAmber, tint = StatusPalette.attention())
            }
        }
    }
}

/** "Automatisch", the voices of the preferred country, and the others behind a toggle. */
@Composable
private fun VoiceList(
    panel: VoicePanel,
    listen: ListenSettings,
    neuralSelected: Boolean,
    previewing: String?,
    strings: ListenStrings,
    onVoice: (String?) -> Unit,
    onPreview: (String?) -> Unit,
) {
    val manyCountries = panel.entries.map { it.voice.country }.distinct().size > 1
    Column(Modifier.selectableGroup()) {
        val autoGender = if (panel.auto.source == VoiceSource.PREFERENCE) listen.voicePreference.gender else null
        VoiceRow(
            title = strings.voiceAuto,
            subtitle = strings.voiceAutoText(panel.auto.source, autoGender, panel.autoEntry?.number),
            selected = panel.chosen == null && !neuralSelected,
            previewing = previewing == ListenVoices.AUTO_VOICE,
            previewLabel = strings.previewVoice(strings.voiceAuto),
            strings = strings,
            onSelect = { onVoice(null) },
            onPreview = { onPreview(null) },
        )
        panel.nearby.forEach { entry ->
            EntryRow(entry, panel, manyCountries, previewing, strings, onVoice, onPreview)
        }
        if (panel.elsewhere.isNotEmpty()) {
            var showAll by rememberSaveable(panel.language) { mutableStateOf(panel.chosen in panel.elsewhere) }
            AnimatedVisibility(visible = showAll, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    panel.elsewhere.forEach { entry ->
                        EntryRow(entry, panel, manyCountries, previewing, strings, onVoice, onPreview)
                    }
                }
            }
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) strings.fewerRegions else strings.otherRegions(panel.elsewhere.size))
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: VoiceEntry,
    panel: VoicePanel,
    manyCountries: Boolean,
    previewing: String?,
    strings: ListenStrings,
    onVoice: (String?) -> Unit,
    onPreview: (String?) -> Unit,
) {
    val voice = entry.voice
    val label = strings.voiceLabel(entry.number, entry.gender, voice.offline)
    VoiceRow(
        title = label,
        subtitle = strings.voiceDetails(
            country = voice.country.takeIf { manyCountries && it.isNotEmpty() },
            quality = voice.quality,
            slow = voice.latency >= VoiceInfo.LATENCY_HIGH,
        ),
        selected = panel.chosen == entry,
        previewing = previewing == voice.name,
        previewLabel = strings.previewVoice(label),
        strings = strings,
        onSelect = { onVoice(voice.name) },
        onPreview = { onPreview(voice.name) },
    )
}

/** A selectable voice row (radio, two lines) with its own "Probehören" button. */
@Composable
private fun VoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    previewing: Boolean,
    previewLabel: String,
    strings: ListenStrings,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = rememberToggleHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) haptic(true)
                    onSelect()
                },
            )
            .heightIn(min = 56.dp)
            .padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        PreviewButton(previewing = previewing, label = previewLabel, playingLabel = strings.previewPlaying, onClick = onPreview)
        trailing?.invoke()
    }
}

@Composable
private fun PreviewButton(previewing: Boolean, label: String, playingLabel: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = if (previewing) playingLabel else label },
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (previewing) {
                ListenBars(color = primary, animate = !reduceMotion, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * "Natürliche Stimmen" for one language: installed voices are choices of the same kind as the
 * engine's (radio, sample, delete), the others offer their download with its size; a running
 * download shows its progress and can be stopped. The recordings and their licence stand under
 * each voice, the source of all of them below the list.
 */
@Composable
private fun NeuralSection(
    offered: List<NeuralVoice>,
    state: NeuralVoicesState,
    selected: String?,
    previewing: String?,
    metered: Boolean,
    strings: ListenStrings,
    onSelect: (NeuralVoice) -> Unit,
    onPreview: (NeuralVoice) -> Unit,
    onDownload: (NeuralVoice) -> Unit,
    onCancel: (NeuralVoice) -> Unit,
    onDelete: (NeuralVoice) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Overline(strings.neuralOverline, color = colors.primary)
    Spacer(Modifier.height(6.dp))
    Text(strings.neuralHint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    val missing = offered.any { it.id !in state.installed }
    if (metered && missing) {
        Spacer(Modifier.height(10.dp))
        Note(strings.neuralMetered, icon = Icons.Rounded.WarningAmber, tint = StatusPalette.attention())
    }
    Spacer(Modifier.height(6.dp))
    Column(Modifier.selectableGroup()) {
        offered.forEach { voice ->
            val title = strings.neuralVoiceTitle(voice.name, voice.gender)
            val details = strings.neuralVoiceDetails(voice.dataset, voice.licence)
            val download = state.downloads[voice.id]
            when {
                voice.id in state.installed -> VoiceRow(
                    title = title,
                    subtitle = details,
                    selected = selected == voice.id,
                    previewing = previewing == NeuralVoiceIds.choice(voice.id),
                    previewLabel = strings.previewVoice(title),
                    strings = strings,
                    onSelect = { onSelect(voice) },
                    onPreview = { onPreview(voice) },
                    trailing = {
                        IconButton(onClick = { onDelete(voice) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = strings.neuralDelete(voice.name), tint = colors.onSurfaceVariant)
                        }
                    },
                )
                download != null -> NeuralDownloadRow(title, download.unpacking, download.fraction, download.done, download.total, strings) {
                    onCancel(voice)
                }
                else -> NeuralOfferRow(
                    title = title,
                    details = details,
                    failed = voice.id in state.failed,
                    action = strings.neuralDownload(megabytes(voice.bytes)),
                    strings = strings,
                    onDownload = { onDownload(voice) },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(strings.neuralSource, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
}

/** A voice that is not on the phone: who it is, whose recordings, and "Laden · 21 MB". */
@Composable
private fun NeuralOfferRow(title: String, details: String, failed: Boolean, action: String, strings: ListenStrings, onDownload: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 38.dp, top = 8.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (failed) {
            Spacer(Modifier.height(4.dp))
            Text(strings.neuralFailed, style = MaterialTheme.typography.bodySmall, color = StatusPalette.attention())
        }
        Spacer(Modifier.height(8.dp))
        GlassPillButton(text = action, onClick = onDownload, icon = Icons.Rounded.Download, style = PillStyle.Tonal)
    }
}

/** A voice on its way: "8 von 21 MB" with a bar, or "Wird entpackt …", and a button to stop it. */
@Composable
private fun NeuralDownloadRow(
    title: String,
    unpacking: Boolean,
    fraction: Float,
    done: Long,
    total: Long,
    strings: ListenStrings,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val status = if (unpacking) strings.neuralUnpacking else strings.neuralProgress(megabytes(done), megabytes(total))
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 38.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { stateDescription = status },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (unpacking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.primary.copy(alpha = 0.16f))
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.16f),
                )
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        IconButton(onClick = onCancel, enabled = !unpacking) {
            Icon(Icons.Rounded.Close, contentDescription = strings.neuralCancel(title), tint = colors.onSurfaceVariant)
        }
    }
}

/** Points to "Google Sprachausgabe": get it from the Play Store, or read with it when it is already there. */
@Composable
private fun GoogleHintCard(hint: GoogleVoiceHint, strings: ListenStrings, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lamina(RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { }) {
            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (hint == GoogleVoiceHint.INSTALL) strings.googleInstallHint else strings.googleUseHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        GlassPillButton(
            text = if (hint == GoogleVoiceHint.INSTALL) strings.googleInstallAction else strings.googleUseAction,
            onClick = onAction,
            icon = if (hint == GoogleVoiceHint.INSTALL) Icons.Rounded.Download else null,
            style = PillStyle.Tonal,
            maxLines = 2,
        )
    }
}

/** Opens Google's text-to-speech in the Play Store app, else on the web. */
private fun openGoogleTtsListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${GoogleVoices.PACKAGE}"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${GoogleVoices.PACKAGE}"))
    listOf(market, web).firstOrNull { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

/** Bytes as whole megabytes (10^6, as file managers show them), at least 1. */
private fun megabytes(bytes: Long): Int = ((bytes + 500_000) / 1_000_000).toInt().coerceAtLeast(if (bytes > 0) 1 else 0)

/** Speed as a big factor with a calm slider, pitch in words; both play a sample when let go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedAndPitch(listen: ListenSettings, strings: ListenStrings, onRate: (Float) -> Unit, onPitch: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val rates = ListenSettings.RATES
    val pitches = ListenSettings.PITCHES
    // Local while dragging; follows the stored value whenever it changes (also from the mini player).
    var rateIndex by remember(listen.rate) { mutableIntStateOf(closest(rates, listen.rate)) }
    var pitchIndex by remember(listen.pitch) { mutableIntStateOf(closest(pitches, listen.pitch)) }

    Overline(strings.speedOverline)
    AnimatedContent(targetState = rateIndex, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "listenRate") { index ->
        Text(text = strings.rateLabel(rates[index]), style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
    }
    Slider(
        value = rateIndex.toFloat(),
        onValueChange = {
            val next = it.roundToInt().coerceIn(0, rates.lastIndex)
            if (next != rateIndex) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            rateIndex = next
        },
        onValueChangeFinished = { onRate(rates[rateIndex]) },
        valueRange = 0f..rates.lastIndex.toFloat(),
        steps = rates.size - 2,
        thumb = { CalmSliderThumb() },
        track = { state -> CalmSliderTrack(state) },
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics {
                contentDescription = strings.speedDescription
                stateDescription = strings.rateLabel(rates[rateIndex])
            },
    )
    Text(text = strings.speedHint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

    Spacer(Modifier.height(18.dp))
    Overline(strings.pitchOverline)
    Spacer(Modifier.height(4.dp))
    AnimatedContent(targetState = pitchIndex, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "listenPitch") { index ->
        Text(text = strings.pitchLabel(pitches[index]), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
    }
    Slider(
        value = pitchIndex.toFloat(),
        onValueChange = {
            val next = it.roundToInt().coerceIn(0, pitches.lastIndex)
            if (next != pitchIndex) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            pitchIndex = next
        },
        onValueChangeFinished = { onPitch(pitches[pitchIndex]) },
        valueRange = 0f..pitches.lastIndex.toFloat(),
        steps = pitches.size - 2,
        thumb = { CalmSliderThumb() },
        track = { state -> CalmSliderTrack(state) },
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics {
                contentDescription = strings.pitchDescription
                stateDescription = strings.pitchLabel(pitches[pitchIndex])
            },
    )
}

private fun closest(options: List<Float>, value: Float): Int =
    options.indices.minBy { abs(options[it] - value) }

/** A quiet hint on a lamina, with a small icon. */
@Composable
private fun Note(text: String, icon: ImageVector = Icons.Rounded.Info, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lamina(RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
