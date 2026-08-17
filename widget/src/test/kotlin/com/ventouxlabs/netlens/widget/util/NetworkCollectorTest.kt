package com.ventouxlabs.netlens.widget.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.net.InetAddress

/**
 * Exercises [NetworkCollector.collect]'s real branches against Robolectric-shadowed
 * `ConnectivityManager` state. The framework-independent derivations live in
 * [NetworkCollectorPureTest].
 *
 * **Three things deliberately not asserted here, each confirmed unavailable rather than assumed:**
 * - `localIp` / `hasIpv6` need `LinkProperties.linkAddresses`, and `LinkAddress` has a
 *   package-private constructor with no public `addLinkAddress` — not constructible in a unit test.
 * - Real `rssi` / `linkSpeedMbps` on the WiFi branch need `NetworkCapabilities.transportInfo`, but
 *   Robolectric's sandboxed `WifiInfo` does not implement `TransportInfo`, so `setTransportInfo`
 *   throws `ClassCastException`.
 * - A meaningful `rssiLevel` comes from the deprecated `WifiManager.calculateSignalLevel(rssi, 5)`,
 *   which Robolectric stubs to a constant (`-55` and `-95` both return 4). Asserting a specific
 *   level would be a green test that proves nothing.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkCollectorTest {

    private lateinit var context: Context
    private lateinit var cm: ConnectivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun capabilities(
        transport: Int,
        vararg caps: Int,
        downstreamKbps: Int? = null,
    ): NetworkCapabilities {
        var nc = ShadowNetworkCapabilities.newInstance()
        shadowOf(nc).addTransportType(transport)
        caps.forEach { shadowOf(nc).addCapability(it) }
        downstreamKbps?.let { nc = shadowOf(nc).setLinkDownstreamBandwidthKbps(it) }
        return nc
    }

    /** Registers [network] as an available network with the given capabilities and link props. */
    private fun register(network: Network, caps: NetworkCapabilities, linkProps: LinkProperties? = null) {
        shadowOf(cm).addNetwork(network, null)
        shadowOf(cm).setNetworkCapabilities(network, caps)
        linkProps?.let { shadowOf(cm).setLinkProperties(network, it) }
    }

    @Test
    fun `no active network yields empty data`() = runBlocking {
        shadowOf(cm).clearAllNetworks()

        assertEquals(CollectedNetworkData(), NetworkCollector.collect(context))
    }

    // The second early-return guard: an active network exists but reports no capabilities.
    @Test
    fun `active network without capabilities yields empty data`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        shadowOf(cm).setNetworkCapabilities(network, null)

        assertEquals(CollectedNetworkData(), NetworkCollector.collect(context))
    }

    // The WiFi branch's signal values come from NetworkCapabilities.transportInfo, which cannot be
    // populated here — Robolectric's sandboxed WifiInfo does not implement TransportInfo, so
    // setTransportInfo(wifiInfo) throws ClassCastException (same family of limitation as
    // detectEncryptionType's). What IS assertable: the branch degrades gracefully to the
    // "unknown" sentinels instead of crashing when no WifiInfo is available.
    @Test
    fun `wifi network without transport info degrades to unknown signal values`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_WIFI))

        val data = NetworkCollector.collect(context)

        assertEquals(-1000, data.rssi)
        assertEquals(-1, data.rssiLevel)
        assertEquals(-1, data.linkSpeedMbps)
        assertEquals("", data.cellGeneration)
    }

    // Cellular has no per-link speed, so collect() falls back to the kernel's downstream estimate.
    // The pure cellularLinkSpeedMbps test proves the arithmetic; this proves it's actually wired.
    @Test
    fun `cellular network derives link speed from the downstream bandwidth estimate`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, downstreamKbps = 50_000))

        val data = NetworkCollector.collect(context)

        assertEquals(50, data.linkSpeedMbps)
    }

    @Test
    fun `unmetered capability clears isMetered`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(
            network,
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )

        assertFalse(NetworkCollector.collect(context).isMetered)
    }

    @Test
    fun `absence of the unmetered capability sets isMetered`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR))

        assertTrue(NetworkCollector.collect(context).isMetered)
    }

    // Captive portal is derived, not reported: INTERNET but not VALIDATED. All three meaningful
    // combinations are asserted so the derivation can't degrade to "just checks one flag".
    @Test
    fun `captive portal is internet without validation`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(
            network,
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_INTERNET),
        )

        assertTrue(NetworkCollector.collect(context).isCaptivePortal)
    }

    @Test
    fun `a validated internet network is not a captive portal`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(
            network,
            capabilities(
                NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ),
        )

        assertFalse(NetworkCollector.collect(context).isCaptivePortal)
    }

    @Test
    fun `a network without internet capability is not a captive portal`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_WIFI))

        assertFalse(NetworkCollector.collect(context).isCaptivePortal)
    }

    @Test
    fun `vpn transport sets isVpn and reports the tunnel interface name`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        val linkProps = LinkProperties().apply { interfaceName = "tun0" }
        register(network, capabilities(NetworkCapabilities.TRANSPORT_VPN), linkProps)

        val data = NetworkCollector.collect(context)

        assertTrue(data.isVpn)
        assertEquals("tun0", data.vpnInterfaceName)
    }

    // A VPN whose interface doesn't look like a tunnel reports an empty name rather than leaking
    // an arbitrary interface string into the widget.
    @Test
    fun `vpn with a non-tunnel interface name reports an empty interface`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        val linkProps = LinkProperties().apply { interfaceName = "wlan0" }
        register(network, capabilities(NetworkCapabilities.TRANSPORT_VPN), linkProps)

        val data = NetworkCollector.collect(context)

        assertTrue(data.isVpn)
        assertEquals("", data.vpnInterfaceName)
    }

    // The outer catch deliberately degrades to empty data (a widget has no error surface), but
    // it must (a) actually degrade rather than crash the worker, and (b) no longer be silent —
    // the Log.w is what makes "widget looks empty" diagnosable from a bug report.
    @Test
    fun `an internal failure degrades to empty data and logs the exception`() = runBlocking {
        val throwingContext = object : android.content.ContextWrapper(context) {
            override fun getSystemService(name: String): Any = error("boom: injected failure")
        }

        val data = NetworkCollector.collect(throwingContext)

        assertEquals(CollectedNetworkData(), data)
        val logged = org.robolectric.shadows.ShadowLog.getLogsForTag("NetworkCollector")
        assertTrue(logged.any { it.throwable?.message == "boom: injected failure" })
    }

    // What IS assertable for cellular signal, and what is not: Robolectric 4.16 ships no
    // ShadowSignalStrength (confirmed against shadows-framework-4.16.jar), so a SignalStrength's
    // cellSignalStrengths list cannot be populated without reflecting into hidden framework
    // constructors — which this module's tests deliberately don't do (same ruling as
    // LinkAddress and TransportInfo). So `dbm` extraction is untestable here; what this pins is
    // that the cellular branch *consults* TelephonyManager.signalStrength and maps its `level` —
    // a present-but-empty signal yields level 0, not the -1 "no signal object" sentinel.
    @Test
    fun `a present cellular signal reports its level, not the no-signal sentinel`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR))
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        val strength = org.robolectric.shadow.api.Shadow.newInstanceOf(android.telephony.SignalStrength::class.java)
        shadowOf(tm).setSignalStrength(strength)

        val data = NetworkCollector.collect(context)

        assertEquals(-1000, data.rssi) // empty cellSignalStrengths -> dbm unavailable
        assertEquals(0, data.rssiLevel) // but the signal object's own level IS read
    }

    @Test
    fun `a missing cellular signal reports both no-signal sentinels`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR))
        // No setSignalStrength: TelephonyManager.signalStrength stays null.

        val data = NetworkCollector.collect(context)

        assertEquals(-1000, data.rssi)
        assertEquals(-1, data.rssiLevel)
    }

    // SPIKE: cell generation via the shadow's network-type setter.
    @Test
    fun `cellular network reports its generation from the telephony network type`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        register(network, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR))
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        @Suppress("DEPRECATION")
        shadowOf(tm).setNetworkType(android.telephony.TelephonyManager.NETWORK_TYPE_LTE)

        assertEquals("LTE", NetworkCollector.collect(context).cellGeneration)
    }

    @Test
    fun `dns servers are read from the active link properties`() = runBlocking {
        val network = requireNotNull(cm.activeNetwork)
        val linkProps = LinkProperties().apply {
            setDnsServers(listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("9.9.9.9")))
        }
        register(network, capabilities(NetworkCapabilities.TRANSPORT_WIFI), linkProps)

        assertEquals(listOf("1.1.1.1", "9.9.9.9"), NetworkCollector.collect(context).dnsServers)
    }
}
