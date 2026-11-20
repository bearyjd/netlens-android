package com.ventouxlabs.netlens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.ui.ThemeMode
import com.ventouxlabs.netlens.ui.theme.toPreferenceValue
import com.ventouxlabs.netlens.ui.theme.toThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
        .map { it.toThemeMode() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode.toPreferenceValue())
        }
    }

    val dnsResolver: StateFlow<String> = userPreferences.dnsResolverOverride
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    private val _dnsResolverError = MutableStateFlow(false)
    val dnsResolverError: StateFlow<Boolean> = _dnsResolverError.asStateFlow()

    /** Blank input restores the built-in default; non-IP input is rejected and flagged. */
    fun setDnsResolver(server: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                userPreferences.setDnsResolverOverride(server)
                _dnsResolverError.value = false
                onSaved()
            } catch (_: IllegalArgumentException) {
                _dnsResolverError.value = true
            }
        }
    }

    fun clearDnsResolverError() {
        _dnsResolverError.value = false
    }
}
