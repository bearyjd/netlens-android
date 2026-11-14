package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.NetworkKeyAliasDao
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity

/** In-memory [NetworkKeyAliasDao]; `aliasKey` is the primary key, so a second insert returns -1. */
class FakeNetworkKeyAliasDao : NetworkKeyAliasDao {
    private val rows = mutableListOf<NetworkKeyAliasEntity>()

    val all: List<NetworkKeyAliasEntity> get() = rows.toList()

    override suspend fun canonicalFor(key: String): String? = rows.find { it.aliasKey == key }?.canonicalKey

    override suspend fun insertIfNew(alias: NetworkKeyAliasEntity): Long {
        if (rows.any { it.aliasKey == alias.aliasKey }) return -1L
        rows += alias
        return rows.size.toLong()
    }
}
