package com.ventoux.netlens.core.oui

interface OuiLookup {
    suspend fun lookup(mac: String): String?
}
