package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.TransactionRunner

/** Pass-through [TransactionRunner]: runs the block directly. NOT atomic; real rollback is covered by Room tests. */
class FakeTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
