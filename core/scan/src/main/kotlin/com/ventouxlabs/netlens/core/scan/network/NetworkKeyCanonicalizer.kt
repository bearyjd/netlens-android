package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.data.dao.NetworkKeyAliasDao
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity
import com.ventouxlabs.netlens.core.scan.identity.DeviceIdentityResolver
import javax.inject.Inject
import javax.inject.Singleton

/** Maps a scan's [NetworkKey]s to the one canonical key sightings and NETWORK-scoped identifiers are stored under. */
@Singleton
class NetworkKeyCanonicalizer @Inject constructor(
    private val aliasDao: NetworkKeyAliasDao,
    private val resolver: DeviceIdentityResolver,
) {
    /**
     * Looks only the strong keys up. If one is known its canonical key wins and the other strong keys
     * become its aliases (and it maps to itself); otherwise the strongest key is canonical. `l3` is never looked up or
     * registered: with no strong key it is used as is, and with no key at all the shared fallback
     * key applies, as before network keys existed.
     */
    suspend fun canonicalize(keys: List<NetworkKey>, now: Long): String {
        val strong = keys.filter { it.isStrong }
        if (strong.isEmpty()) return keys.firstOrNull()?.key ?: resolver.canonicalKey("")
        val canonical = strong.firstNotNullOfOrNull { key ->
            aliasDao.canonicalFor(key.key)?.let { resolver.canonicalKey(key.key) }
        } ?: strong.first().key
        // The canonical key maps to itself so a later scan that sees only that key still finds it known.
        strong.map { it.key }.plus(canonical).distinct().forEach { aliasDao.insertIfNew(NetworkKeyAliasEntity(it, canonical, now)) }
        return canonical
    }

    /** Read-only: every key [keys]' strong members are or resolve to. Empty when there is no strong key. */
    suspend fun strongIdentities(keys: List<NetworkKey>): Set<String> =
        keys.filter { it.isStrong }.flatMap { listOf(it.key, resolver.canonicalKey(it.key)) }.toSet()

    /** The canonical key of the network a `watched_networks` row (which holds only a gateway MAC) stands for. */
    suspend fun canonicalForGatewayMac(mac: String): String? =
        NetworkKey.gatewayMac(mac)?.let { resolver.canonicalKey(it.key) }
}
