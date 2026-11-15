package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.data.dao.NetworkKeyAliasDao
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity
import com.ventouxlabs.netlens.core.scan.identity.DeviceIdentityResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a scan's [NetworkKey]s to the one canonical key sightings and NETWORK-scoped identifiers are
 * stored under. Only strong keys (`gw-mac`, `gw-id`) are ever looked up or registered. `l3` is a
 * same-session hint: with no strong key it is used as the storage scope as is (so two networks that
 * share an l3 and expose nothing stronger do share a scope, no worse than the single fallback key
 * before network keys existed), and with no key at all the shared fallback key applies.
 */
@Singleton
class NetworkKeyCanonicalizer @Inject constructor(
    private val aliasDao: NetworkKeyAliasDao,
    private val resolver: DeviceIdentityResolver,
) {
    /** The canonical key, and whether every established strong key already agrees with it. */
    private class Decision(val canonical: String, val consistent: Boolean)

    /**
     * A gateway MAC decides the canonical key (its own root if known, else itself): a `gw-id` is a
     * weaker, spoofable claim and must never pull an unknown MAC into another network. Without a
     * MAC, the known roots decide (the smallest if they disagree) and a fresh network takes its
     * smallest key, so the result never depends on key order. Aliases (and the canonical's own
     * self-row) are registered only when no strong key points at a different root; each canonical
     * keeps at most [MAX_GATEWAY_ID_ALIASES] gateway-id aliases, newest first.
     */
    suspend fun canonicalize(keys: List<NetworkKey>, now: Long): String {
        val strong = keys.filter { it.isStrong }
        if (strong.isEmpty()) return keys.firstOrNull()?.key ?: resolver.canonicalKey("")
        val decision = decide(strong)
        if (decision.consistent) {
            val canonical = decision.canonical
            strong.map { it.key }.plus(canonical).distinct().forEach { aliasDao.insertIfNew(NetworkKeyAliasEntity(it, canonical, now)) }
            aliasDao.pruneGatewayIdAliases(canonical, MAX_GATEWAY_ID_ALIASES)
        }
        return decision.canonical
    }

    /**
     * Read-only. The canonical key the current network is, or null when it cannot be vouched for:
     * no strong key, or strong keys that disagree (a known key pointing at another root than the
     * gateway MAC's). A watch match must rest on this, never on `l3`.
     */
    suspend fun currentIdentity(keys: List<NetworkKey>): String? {
        val strong = keys.filter { it.isStrong }
        if (strong.isEmpty()) return null
        return decide(strong).takeIf { it.consistent }?.canonical
    }

    /** The canonical key of the network a `watched_networks` row (which holds only a gateway MAC) stands for. */
    suspend fun canonicalForGatewayMac(mac: String): String? =
        NetworkKey.gatewayMac(mac)?.let { resolver.canonicalKey(it.key) }

    private suspend fun decide(strong: List<NetworkKey>): Decision {
        val mac = strong.filter { it.kind == NetworkKey.Kind.GATEWAY_MAC }.minByOrNull { it.key }
        val roots = strong.mapNotNull { root(it) }.toSortedSet()
        val canonical = when {
            mac != null -> root(mac) ?: mac.key
            roots.isNotEmpty() -> roots.first()
            else -> strong.minOf { it.key }
        }
        return Decision(canonical, roots.all { it == canonical })
    }

    private suspend fun root(key: NetworkKey): String? =
        aliasDao.canonicalFor(key.key)?.let { resolver.canonicalKey(key.key) }

    private companion object {
        const val MAX_GATEWAY_ID_ALIASES = 8
    }
}
