package us.beary.netlens.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.state.GlanceStateDefinition
import java.io.File

object IpWidgetStateDefinition : GlanceStateDefinition<Preferences> {

    val IP_KEY = stringPreferencesKey("widget_ip")
    val ISP_KEY = stringPreferencesKey("widget_isp")
    val IS_VPN_KEY = booleanPreferencesKey("widget_is_vpn")

    private const val DATA_STORE_NAME = "ip_widget_prefs"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = DATA_STORE_NAME,
    )

    override suspend fun getDataStore(
        context: Context,
        fileKey: String,
    ): DataStore<Preferences> = context.dataStore

    override fun getLocation(
        context: Context,
        fileKey: String,
    ): File = context.filesDir.resolve("datastore").resolve("$DATA_STORE_NAME.preferences_pb")
}

fun Preferences.toIpWidgetState(): IpWidgetState = IpWidgetState(
    ip = this[IpWidgetStateDefinition.IP_KEY].orEmpty(),
    isp = this[IpWidgetStateDefinition.ISP_KEY].orEmpty(),
    isVpn = this[IpWidgetStateDefinition.IS_VPN_KEY] ?: false,
)
