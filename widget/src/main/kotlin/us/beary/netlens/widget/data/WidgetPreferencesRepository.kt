package us.beary.netlens.widget.data

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
import us.beary.netlens.widget.model.WidgetColor
import us.beary.netlens.widget.model.WidgetPage
import us.beary.netlens.widget.model.WidgetPreferences
import us.beary.netlens.widget.model.WidgetSize
import us.beary.netlens.widget.model.WidgetTextSize

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
    private val PAGES = stringPreferencesKey("pages")
    private val AUTO_ADVANCE = intPreferencesKey("auto_advance_seconds")

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
            prefs[PAGES] = updated.pages.joinToString(",") { it.name }
            prefs[AUTO_ADVANCE] = updated.autoAdvanceSeconds
        }
    }

    private fun Preferences.toWidgetPreferences(): WidgetPreferences = WidgetPreferences(
        backgroundAlpha = this[BG_ALPHA] ?: 0.6f,
        backgroundColor = this[BG_COLOR]?.let { runCatching { WidgetColor.valueOf(it) }.getOrNull() } ?: WidgetColor.BLACK,
        accentColor = this[ACCENT_COLOR]?.let { runCatching { WidgetColor.valueOf(it) }.getOrNull() } ?: WidgetColor.GREEN,
        textSize = this[TEXT_SIZE]?.let { runCatching { WidgetTextSize.valueOf(it) }.getOrNull() } ?: WidgetTextSize.MEDIUM,
        cornerRadius = this[CORNER_RADIUS] ?: 16,
        widgetSize = this[WIDGET_SIZE]?.let { runCatching { WidgetSize.valueOf(it) }.getOrNull() } ?: WidgetSize.SMALL,
        pages = this[PAGES]?.split(",")?.mapNotNull { runCatching { WidgetPage.valueOf(it) }.getOrNull() }
            ?.ifEmpty { null } ?: listOf(WidgetPage.CONNECTION, WidgetPage.NETWORK),
        autoAdvanceSeconds = this[AUTO_ADVANCE] ?: 0,
    )
}
