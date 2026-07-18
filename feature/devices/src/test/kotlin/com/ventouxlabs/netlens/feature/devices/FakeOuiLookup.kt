package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.oui.OuiLookup

class FakeOuiLookup : OuiLookup {
    var vendors: Map<String, String> = emptyMap()
    override suspend fun lookup(mac: String): String? = vendors[mac]
}
