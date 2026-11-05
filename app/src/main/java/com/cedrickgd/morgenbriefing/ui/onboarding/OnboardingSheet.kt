package com.cedrickgd.morgenbriefing.ui.onboarding

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.onboardingStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassSheet
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.settings.ChoicePill
import com.cedrickgd.morgenbriefing.ui.settings.EditionLabels
import com.cedrickgd.morgenbriefing.ui.settings.EditionPickerSheet
import com.cedrickgd.morgenbriefing.ui.settings.EditionRow
import com.cedrickgd.morgenbriefing.ui.settings.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.settings.Overline
import com.cedrickgd.morgenbriefing.ui.settings.PillStyle
import com.cedrickgd.morgenbriefing.ui.settings.lamina
import com.cedrickgd.morgenbriefing.ui.settings.rememberToggleHaptics
import com.cedrickgd.morgenbriefing.util.SystemStatus
import com.cedrickgd.morgenbriefing.util.notificationsNeedSettings
import com.cedrickgd.morgenbriefing.util.rememberNotificationPermissionRequester
import kotlinx.coroutines.launch

/**
 * First-start sheet on Today: four quick steps – the app's language (the phone's is preselected
 * and switches the sheet at once), where the news come from (the phone's country is
 * preselected), notifications and your place – then "Los geht's". Swiping it away also counts
 * as done: it should welcome, not nag.
 *
 * @param onDone persist `onboardingDone = true`.
 * @param onChoosePlace close the sheet and open the place picker in the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(
    onDone: () -> Unit,
    onChoosePlace: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val strings = onboardingStrings()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editionsOpen by rememberSaveable { mutableStateOf(false) }

    var status by remember { mutableStateOf(SystemStatus.read(context)) }
    // Denied for good (or switched off): the button then opens the system settings – say so.
    var needsSettings by remember { mutableStateOf(notificationsNeedSettings(context, activity, status)) }
    fun recheck() {
        status = SystemStatus.read(context)
        needsSettings = notificationsNeedSettings(context, activity, status)
    }
    LifecycleResumeEffect(context) {
        recheck()
        onPauseOrDispose { }
    }
    val requestNotifications = rememberNotificationPermissionRequester { recheck() }

    // Animate the sheet out first, then leave (otherwise it would vanish abruptly).
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    // Glass like every sheet (docs/DESIGN.md §2); it also keeps the navigation buttons visible
    // on Android 7, which cannot draw them dark on the light sheet.
    GlassSheet(onDismissRequest = onDone, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WelcomeHeader()
            Text(
                text = strings.intro,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            val language = LocalAppLanguage.current
            OnboardingStep(number = 1, title = strings.languageTitle, text = strings.languageText, done = false) {
                OnboardingLanguages(selected = language, onSelect = viewModel::setLanguage)
            }

            val edition = settings?.edition
            OnboardingStep(number = 2, title = strings.editionTitle, text = strings.editionText, done = false) {
                if (edition != null) {
                    val option = remember(edition, language) { EditionLabels.option(edition, language) }
                    EditionRow(option = option, onClick = { editionsOpen = true })
                }
            }

            OnboardingStep(
                number = 3,
                title = if (needsSettings) strings.notificationsBlockedTitle else strings.notificationsTitle,
                text = if (needsSettings) strings.notificationsBlockedText else strings.notificationsText,
                done = status.canNotify,
            ) {
                StepAction(
                    done = status.canNotify,
                    doneLabel = strings.allowed,
                    actionLabel = if (needsSettings) strings.openSettings else strings.allow,
                    icon = Icons.Rounded.NotificationsActive,
                    onAction = requestNotifications,
                )
            }

            OnboardingStep(number = 4, title = strings.placeTitle, text = strings.placeText, done = false) {
                StepAction(
                    done = false,
                    doneLabel = "",
                    actionLabel = strings.choosePlace,
                    icon = Icons.Rounded.Place,
                    onAction = { closeThen(onChoosePlace) },
                )
            }

            GlassPillButton(
                text = strings.start,
                onClick = { closeThen(onDone) },
                style = PillStyle.Filled,
                onGlass = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )
        }
    }

    val current = settings?.edition
    if (editionsOpen && current != null) {
        EditionPickerSheet(
            current = current,
            onPick = { picked: NewsEdition ->
                editionsOpen = false
                viewModel.setEdition(picked)
            },
            onDismiss = { editionsOpen = false },
        )
    }
}

/** A warm sun disc, the overline and the serif welcome. */
@Composable
private fun WelcomeHeader() {
    val strings = onboardingStrings()
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFFD27A), Color(0xFFF08A3C), colors.primary)))
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Overline(strings.overline)
            Text(
                text = strings.title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/** The five languages by their own names; the phone's language carries a phone mark. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingLanguages(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val strings = onboardingStrings()
    val phone = remember { AppLanguage.system() }
    val haptic = rememberToggleHaptics()
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            ChoicePill(
                label = language.nativeName,
                selected = language == selected,
                icon = if (language == phone) Icons.Rounded.Smartphone else null,
                description = if (language == phone) "${language.nativeName}, ${strings.phoneLanguage}" else null,
                onClick = {
                    if (language != selected) haptic(true)
                    onSelect(language)
                },
            )
        }
    }
}

/** One numbered step on a light pane: title, sentence and its control. */
@Composable
private fun OnboardingStep(
    number: Int,
    title: String,
    text: String,
    done: Boolean,
    content: @Composable () -> Unit,
) {
    val strings = onboardingStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lamina(RoundedCornerShape(24.dp), strong = true)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepBadge(number = number, done = done, description = strings.stepOf(number, STEPS))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** "✓ Erlaubt" once done, else the step's button. */
@Composable
private fun StepAction(done: Boolean, doneLabel: String, actionLabel: String, icon: ImageVector, onAction: () -> Unit) {
    AnimatedContent(targetState = done, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stepAction") { isDone ->
        if (isDone) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(doneLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            GlassPillButton(text = actionLabel, onClick = onAction, icon = icon, style = PillStyle.Tonal)
        }
    }
}

@Composable
private fun StepBadge(number: Int, done: Boolean, description: String) {
    val colors = MaterialTheme.colorScheme
    val dark = isGlassDark()
    val spoken = if (done) "$description · ${onboardingStrings().stepDone}" else description
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (done) colors.primary else colors.primaryContainer.copy(alpha = if (dark) 0.6f else 1f))
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        } else {
            Text(text = number.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
        }
    }
}

private const val STEPS = 4
