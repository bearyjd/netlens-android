package com.ventouxlabs.netlens.feature.devices.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.effectiveDeviceType
import com.ventouxlabs.netlens.core.data.model.effectiveModel
import com.ventouxlabs.netlens.core.data.model.effectiveOs
import com.ventouxlabs.netlens.core.network.DisplayText

/** Display-name precedence: customName ?: hostname ?: vendor ?: ip. */
fun KnownDeviceEntity.displayName(): String =
    customName ?: hostname ?: vendor ?: ip

// Type/OS/model as shown and exported: the user's override if set, else the detected value,
// flattened because both come from the network or free-form input (see DisplayText).
fun KnownDeviceEntity.displayType(): String? = DisplayText.flatten(effectiveDeviceType())

fun KnownDeviceEntity.displayOs(): String? = DisplayText.flatten(effectiveOs())

fun KnownDeviceEntity.displayModel(): String? =
    DisplayText.flatten(effectiveModel())?.take(MAX_DISPLAY_MODEL_LENGTH)

// Legacy rows predate the scan-side cap (core:scan MAX_MODEL_LENGTH), so cap again for display.
private const val MAX_DISPLAY_MODEL_LENGTH = 80
