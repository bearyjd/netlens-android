package com.ventouxlabs.netlens.core.oui

interface OuiLookup {
    suspend fun lookup(mac: String): String?
}
