package com.ventoux.netlens.feature.lanscan.engine

import com.ventoux.netlens.core.oui.OuiLookup

internal class FakeOuiLookup : OuiLookup {
    val table = mutableMapOf<String, String>()

    override suspend fun lookup(mac: String): String? {
        val prefix = mac.take(8).uppercase().replace('-', ':')
        return table[prefix]
    }
}
