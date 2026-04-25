package com.ventoux.netlens.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.state.GlanceStateDefinition
import java.io.File

object IpWidgetStateDefinition : GlanceStateDefinition<Preferences> {

    val IP_KEY = stringPreferencesKey("widget_ip")
    val ISP_KEY = stringPreferencesKey("widget_isp")
    val IS_VPN_KEY = booleanPreferencesKey("widget_is_vpn")
    val COUNTRY_CODE_KEY = stringPreferencesKey("widget_country_code")
    val IS_CONNECTED_KEY = booleanPreferencesKey("widget_is_connected")
    val SSID_KEY = stringPreferencesKey("widget_ssid")
    val LOCAL_IP_KEY = stringPreferencesKey("widget_local_ip")
    val GATEWAY_KEY = stringPreferencesKey("widget_gateway")
    val DNS_SERVERS_KEY = stringPreferencesKey("widget_dns_servers")
    val CAROUSEL_PAGE_KEY = intPreferencesKey("carousel_page_index")

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
    publicIp = this[IpWidgetStateDefinition.IP_KEY].orEmpty(),
    isp = this[IpWidgetStateDefinition.ISP_KEY].orEmpty(),
    countryCode = this[IpWidgetStateDefinition.COUNTRY_CODE_KEY].orEmpty(),
    isVpn = this[IpWidgetStateDefinition.IS_VPN_KEY] ?: false,
    isConnected = this[IpWidgetStateDefinition.IS_CONNECTED_KEY] ?: false,
    ssid = this[IpWidgetStateDefinition.SSID_KEY],
    localIp = this[IpWidgetStateDefinition.LOCAL_IP_KEY],
    gateway = this[IpWidgetStateDefinition.GATEWAY_KEY],
    dnsServers = this[IpWidgetStateDefinition.DNS_SERVERS_KEY]
        ?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
)
