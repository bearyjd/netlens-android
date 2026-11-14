package com.ventouxlabs.netlens.core.data

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs [block] in one database transaction spanning every DAO (a Room `@Transaction` default method
 * can only reach its own DAO). Callers that need an all-or-nothing write across tables use this; the
 * JVM fake is a pass-through and is not atomic.
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner @Inject constructor(
    private val database: NetLensDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
