package com.ventouxlabs.netlens.feature.devices.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

/** Display-name precedence: customName ?: hostname ?: vendor ?: ip. */
fun KnownDeviceEntity.displayName(): String =
    customName ?: hostname ?: vendor ?: ip
