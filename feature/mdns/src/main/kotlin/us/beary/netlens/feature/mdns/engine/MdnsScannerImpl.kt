package us.beary.netlens.feature.mdns.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import us.beary.netlens.feature.mdns.model.MdnsService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MdnsScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MdnsScanner {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    @Volatile
    private var activeListener: NsdManager.DiscoveryListener? = null

    override fun discoverServices(serviceType: String): Flow<MdnsService> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(regType: String) {
                // Discovery started successfully
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(serviceInfo) { resolved ->
                    resolved?.let { trySend(it) }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // Service is no longer available; not emitting removal
            }

            override fun onDiscoveryStopped(serviceType: String) {
                // Discovery stopped
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(
                    IllegalStateException(
                        "mDNS discovery failed for $serviceType (error $errorCode)",
                    ),
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Best-effort stop; nothing to do
            }
        }

        activeListener = discoveryListener

        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )

        awaitClose {
            stopDiscoveryQuietly(discoveryListener)
            activeListener = null
        }
    }

    override fun stopDiscovery() {
        activeListener?.let { listener ->
            stopDiscoveryQuietly(listener)
            activeListener = null
        }
    }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        onResolved: (MdnsService?) -> Unit,
    ) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    onResolved(null)
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val attributes = buildMap {
                        info.attributes.forEach { (key, value) ->
                            put(key, value?.let { String(it, Charsets.UTF_8) } ?: "")
                        }
                    }

                    onResolved(
                        MdnsService(
                            serviceName = info.serviceName,
                            serviceType = info.serviceType,
                            host = info.host?.hostAddress,
                            port = info.port,
                            attributes = attributes,
                        ),
                    )
                }
            },
        )
    }

    private fun stopDiscoveryQuietly(listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Listener was not registered or already stopped
        }
    }
}
