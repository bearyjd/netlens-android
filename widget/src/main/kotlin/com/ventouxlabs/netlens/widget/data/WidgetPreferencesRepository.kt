package com.ventouxlabs.netlens.widget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.ventouxlabs.netlens.widget.model.WidgetColor
import com.ventouxlabs.netlens.widget.model.WidgetPreferences
import com.ventouxlabs.netlens.widget.model.WidgetSize
import com.ventouxlabs.netlens.widget.model.WidgetTextSize

private val Context.widgetPrefsStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_preferences",
)

object WidgetPreferencesRepository {

    private val BG_ALPHA = floatPreferencesKey("bg_alpha")
    private val BG_COLOR = stringPreferencesKey("bg_color")
    private val ACCENT_COLOR = stringPreferencesKey("accent_color")
    private val TEXT_SIZE = stringPreferencesKey("text_size")
    private val CORNER_RADIUS = intPreferencesKey("corner_radius")
    private val WIDGET_SIZE = stringPreferencesKey("widget_size")

    fun observe(context: Context): Flow<WidgetPreferences> =
        context.widgetPrefsStore.data.map { it.toWidgetPreferences() }

    suspend fun get(context: Context): WidgetPreferences =
        context.widgetPrefsStore.data.first().toWidgetPreferences()

    suspend fun update(context: Context, transform: (WidgetPreferences) -> WidgetPreferences) {
        context.widgetPrefsStore.edit { prefs ->
            val current = prefs.toWidgetPreferences()
            val updated = transform(current)
            prefs[BG_ALPHA] = updated.backgroundAlpha
            prefs[BG_COLOR] = updated.backgroundColor.name
            prefs[ACCENT_COLOR] = updated.accentColor.name
            prefs[TEXT_SIZE] = updated.textSize.name
            prefs[CORNER_RADIUS] = updated.cornerRadius
            prefs[WIDGET_SIZE] = updated.widgetSize.name
        }
    }

    private val WIDGET_SIZE_MIGRATION = mapOf(
        "SMALL" to WidgetSize.COMPACT,
        "MEDIUM" to WidgetSize.STANDARD,
        "WIDE" to WidgetSize.STANDARD,
        "BANNER" to WidgetSize.DASHBOARD,
    )

    internal fun migrateWidgetSize(stored: String): WidgetSize? =
        runCatching { WidgetSize.valueOf(stored) }.getOrNull()
            ?: WIDGET_SIZE_MIGRATION[stored]

    private fun Preferences.toWidgetPreferences(): WidgetPreferences {
        val defaults = WidgetPreferences()
        return WidgetPreferences(
            backgroundAlpha = this[BG_ALPHA] ?: defaults.backgroundAlpha,
            backgroundColor = this[BG_COLOR]?.let { runCatching { WidgetColor.valueOf(it) }.getOrNull() } ?: defaults.backgroundColor,
            accentColor = this[ACCENT_COLOR]?.let { runCatching { WidgetColor.valueOf(it) }.getOrNull() } ?: defaults.accentColor,
            textSize = this[TEXT_SIZE]?.let { runCatching { WidgetTextSize.valueOf(it) }.getOrNull() } ?: defaults.textSize,
            cornerRadius = this[CORNER_RADIUS] ?: defaults.cornerRadius,
            widgetSize = this[WIDGET_SIZE]?.let { migrateWidgetSize(it) } ?: defaults.widgetSize,
        )
    }
}
