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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(alias: NetworkKeyAliasEntity): Long
}
