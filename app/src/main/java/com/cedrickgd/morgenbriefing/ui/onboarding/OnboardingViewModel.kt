package com.cedrickgd.morgenbriefing.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.SettingsRepository
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.ui.settings.EditionChange
import com.cedrickgd.morgenbriefing.ui.settings.LanguageChoice
import com.cedrickgd.morgenbriefing.util.attempt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The first-start sheet's settings: the interface language (applies at once), the news region
 * and the news language – three choices that never change one another. A fresh install
 * already reads the phone's country in the phone's language
 * ([com.cedrickgd.morgenbriefing.data.SettingsMigration]); picking another region here also
 * moves the default topics along ([EditionChange]).
 */
class OnboardingViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Null until the DataStore emitted. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .catch { e -> Log.w(TAG, "Settings unavailable", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** [picked] as the interface language; the phone's own language means "follow the phone". */
    fun setLanguage(picked: AppLanguage?) = edit { it.copy(uiLanguage = LanguageChoice.preference(picked)) }

    fun setNewsRegion(country: String) = edit { EditionChange.region(it, country) }

    fun setNewsLanguage(language: String) = edit { EditionChange.language(it, language) }

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            attempt { settingsRepository.update(transform) }.onFailure { Log.w(TAG, "Onboarding setting not saved", it) }
        }
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                OnboardingViewModel(app.container.settings)
            }
        }
    }
}
