package com.ventoux.netlens.core.oui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OuiLookup @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var table: Map<String, String>? = null

    suspend fun lookup(mac: String): String? {
        val prefix = mac.take(8).uppercase().replace('-', ':')
        val loaded = table ?: loadTable()
        return loaded[prefix]
    }

    private suspend fun loadTable(): Map<String, String> = mutex.withLock {
        table?.let { return it }

        val map = withContext(Dispatchers.IO) {
            val result = HashMap<String, String>(30_000)
            context.assets.open("oui.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEach { line ->
                        if (line.contains("(hex)")) {
                            val parts = line.split("(hex)")
                            if (parts.size == 2) {
                                val prefix = parts[0].trim().uppercase().replace('-', ':')
                                val vendor = parts[1].trim()
                                result[prefix] = vendor
                            }
                        }
                    }
                }
            }
            result
        }
        table = map
        map
    }
}
