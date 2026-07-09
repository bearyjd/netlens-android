package com.ventouxlabs.netlens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.ui.ThemeMode
import com.ventouxlabs.netlens.ui.theme.toThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    userPreferences: UserPreferencesRepository,
) : ViewModel() {

    // Seeded synchronously so the very first frame already renders in the
    // persisted theme — seeding with SYSTEM and waiting for DataStore causes a
    // visible light/dark flash on cold start for users with a manual override.
    // DataStore's first read is a small local file; the block is bounded.
    private val initialThemeMode: ThemeMode =
        runBlocking { userPreferences.themeMode.first().toThemeMode() }

    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
        .map { it.toThemeMode() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialThemeMode,
        )
}
