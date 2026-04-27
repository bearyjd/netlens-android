package com.ventoux.netlens.feature.lanscan.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LanMdnsScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanMdnsScanner {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun discover(timeoutMs: Long): Flow<LanDevice> = callbackFlow {
        val seen = mutableMapOf<String, List<String>>()
        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        launch {
            for (service in resolveQueue) {
                val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    resolveServiceSuspending(service)
                }
                if (resolved != null) {
                    val (ip, serviceType, hostname) = resolved
                    val existing = seen.getOrDefault(ip, emptyList())
                    val updatedServices = if (serviceType in existing) existing else existing + serviceType
                    seen[ip] = updatedServices
                    trySend(
                        LanDevice(
                            ip = ip,
                            hostname = hostname,
                            isReachable = true,
                            discoveryMethod = DiscoveryMethod.MDNS,
                            services = updatedServices,
                        ),
                    )
                }
            }
        }

        for (type in SERVICE_TYPES) {
            val listener = createDiscoveryListener(resolveQueue)
            listeners.add(listener)
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: Exception) {
                // Some service types may fail on certain devices
            }
        }

        launch {
            delay(timeoutMs)
            close()
        }

        awaitClose {
            resolveQueue.close()
            listeners.forEach { stopDiscoveryQuietly(it) }
        }
    }

    private fun createDiscoveryListener(resolveQueue: Channel<NsdServiceInfo>): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    private suspend fun resolveServiceSuspending(
        serviceInfo: NsdServiceInfo,
    ): Triple<String, String, String?>? = suspendCancellableCoroutine { cont ->
        try {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val ip = info.host?.hostAddress
                        if (ip != null && cont.isActive) {
                            val serviceType = info.serviceType
                                ?.removePrefix(".")
                                ?.removeSuffix(".")
                                ?: "unknown"
                            val hostname = info.serviceName
                            cont.resume(Triple(ip, serviceType, hostname))
                        } else if (cont.isActive) {
                            cont.resume(null)
                        }
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun stopDiscoveryQuietly(listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Listener was not registered or already stopped
        }
    }

    companion object {
        private const val RESOLVE_TIMEOUT_MS = 3000L

        private val SERVICE_TYPES = listOf(
            "_http._tcp",
            "_https._tcp",
            "_ssh._tcp",
            "_smb._tcp",
            "_airplay._tcp",
            "_ipp._tcp",
            "_googlecast._tcp",
            "_raop._tcp",
            "_printer._tcp",
            "_homekit._tcp",
            "_companion-link._tcp",
            "_spotify-connect._tcp",
        )
    }
}
