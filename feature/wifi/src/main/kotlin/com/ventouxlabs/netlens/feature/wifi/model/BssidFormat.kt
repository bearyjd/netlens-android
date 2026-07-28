package com.ventouxlabs.netlens.feature.wifi.model

/**
 * Last two octets of a BSSID.
 *
 * In a mesh the APs differ only in the tail, so the tail is the part that carries information —
 * and the full MAC crowds out the label it sits next to. It also keeps the whole address off
 * screen and out of exports: a BSSID is an AP's MAC, which public wardriving databases resolve
 * to street-level coordinates, so a shared survey should not carry one.
 */
internal fun apShortName(bssid: String): String =
    bssid.split(':').takeLast(2).joinToString(":")
