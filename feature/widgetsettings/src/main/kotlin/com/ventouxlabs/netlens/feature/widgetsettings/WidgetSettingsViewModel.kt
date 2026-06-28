package com.ventouxlabs.netlens.feature.widgetsettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ventouxlabs.netlens.widget.data.WidgetPreferencesRepository
import com.ventouxlabs.netlens.widget.model.WidgetColor
import com.ventouxlabs.netlens.widget.model.WidgetPreferences
import com.ventouxlabs.netlens.widget.model.WidgetSize
import com.ventouxlabs.netlens.widget.model.WidgetTextSize
import com.ventouxlabs.netlens.widget.refreshAllWidgets
import javax.inject.Inject

@HiltViewModel
class WidgetSettingsViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    val prefs = WidgetPreferencesRepository.observe(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetPreferences())

    fun setBackgroundColor(color: WidgetColor) = update { it.copy(backgroundColor = color) }
    fun setBackgroundAlpha(alpha: Float) = update { it.copy(backgroundAlpha = alpha) }
    fun setAccentColor(color: WidgetColor) = update { it.copy(accentColor = color) }
    fun setTextSize(size: WidgetTextSize) = update { it.copy(textSize = size) }
    fun setCornerRadius(radius: Int) = update { it.copy(cornerRadius = radius) }
    fun setWidgetSize(size: WidgetSize) = update { it.copy(widgetSize = size) }

    fun applyToWidget() {
        viewModelScope.launch {
            refreshAllWidgets(context)
        }
    }

    private fun update(transform: (WidgetPreferences) -> WidgetPreferences) {
        viewModelScope.launch {
            WidgetPreferencesRepository.update(context, transform)
        }
    }
}
