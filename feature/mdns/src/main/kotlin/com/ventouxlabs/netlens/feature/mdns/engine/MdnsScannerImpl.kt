package com.ventouxlabs.netlens.feature.mdns.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.ventouxlabs.netlens.feature.mdns.model.MdnsService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MdnsScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MdnsScanner {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun discoverServices(serviceType: String): Flow<MdnsService> = callbackFlow {
        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        val discoveryListener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(regType: String) {
                // Discovery started successfully
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.trySend(serviceInfo)
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

        // Process resolves serially to avoid FAILURE_ALREADY_ACTIVE on API < 34
        launch {
            for (service in resolveQueue) {
                val resolved = resolveServiceSuspending(service)
                resolved?.let { trySend(it) }
            }
        }

        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )

        awaitClose {
            resolveQueue.close()
            stopDiscoveryQuietly(discoveryListener)
        }
    }

    override fun stopDiscovery() {
        // No-op: cleanup is handled by coroutine cancellation via awaitClose
    }

    private suspend fun resolveServiceSuspending(
        serviceInfo: NsdServiceInfo,
    ): MdnsService? = suspendCancellableCoroutine { cont ->
        try {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val attributes = buildMap {
                            info.attributes.forEach { (key, value) ->
                                put(key, value?.let { String(it, Charsets.UTF_8) } ?: "")
                            }
                        }

                        if (cont.isActive) {
                            cont.resume(
                                MdnsService(
                                    serviceName = info.serviceName,
                                    serviceType = info.serviceType,
                                    host = info.host?.hostAddress,
                                    port = info.port,
                                    attributes = attributes,
                                ),
                            )
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
}
