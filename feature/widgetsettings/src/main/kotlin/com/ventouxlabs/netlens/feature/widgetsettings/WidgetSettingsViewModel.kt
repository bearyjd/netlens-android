package com.ventouxlabs.netlens.feature.widgetsettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.widget.refreshAllWidgets
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WidgetSettingsViewModel @Inject constructor(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    val ipInfoConsent: StateFlow<Boolean> = userPreferencesRepository.ipInfoConsentGranted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setIpInfoConsent(granted: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setIpInfoConsent(granted)
        }
    }

    fun refreshWidgets() {
        viewModelScope.launch {
            refreshAllWidgets(context)
        }
    }
}
