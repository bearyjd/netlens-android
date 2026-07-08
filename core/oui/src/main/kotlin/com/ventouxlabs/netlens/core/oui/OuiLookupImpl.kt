package com.ventouxlabs.netlens.core.oui

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
class OuiLookupImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OuiLookup {
    private val mutex = Mutex()
    @Volatile private var table: Map<String, String>? = null

    override suspend fun lookup(mac: String): String? {
        val prefix = normalizePrefix(mac)
        val loaded = table ?: loadTable()
        return loaded[prefix]
    }

    private suspend fun loadTable(): Map<String, String> = mutex.withLock {
        table?.let { return it }

        val map = withContext(Dispatchers.IO) {
            context.assets.open("oui.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines -> parseOuiTable(lines) }
            }
        }
        table = map
        map
    }

    companion object {
        /** Normalizes a MAC address (any case, `-` or `:` separated) to an uppercase, colon-separated OUI prefix. */
        internal fun normalizePrefix(mac: String): String =
            mac.take(8).uppercase().replace('-', ':')

        /** Parses the IEEE `oui.txt` "(hex)" lines into a prefix -> vendor lookup map. */
        internal fun parseOuiTable(lines: Sequence<String>): Map<String, String> {
            val result = HashMap<String, String>(30_000)
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
            return result
        }
    }
}
