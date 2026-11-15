package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity

@Dao
interface NetworkKeyAliasDao {
    @Query("SELECT canonicalKey FROM network_key_aliases WHERE aliasKey = :key")
    suspend fun canonicalFor(key: String): String?

    /** Keeps the [keep] newest `gw-id:` aliases of [canonical]; the canonical's own row and `gw-mac:` aliases stay. */
    @Query(
        "DELETE FROM network_key_aliases WHERE canonicalKey = :canonical AND aliasKey LIKE 'gw-id:%' " +
            "AND aliasKey != :canonical AND aliasKey NOT IN (SELECT aliasKey FROM network_key_aliases " +
            "WHERE canonicalKey = :canonical AND aliasKey LIKE 'gw-id:%' AND aliasKey != :canonical " +
            "ORDER BY firstSeen DESC, aliasKey LIMIT :keep)",
    )
    suspend fun pruneGatewayIdAliases(canonical: String, keep: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(alias: NetworkKeyAliasEntity): Long
}
