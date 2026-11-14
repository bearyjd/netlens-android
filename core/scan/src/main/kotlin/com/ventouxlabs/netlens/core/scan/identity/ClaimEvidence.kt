package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.ObservedField

internal const val CLAIM_DETAIL = "unverified identifier claim"

/** An identifier claim as evidence only: weight 0, flagged in the detail text, never an identifier row. */
internal fun claimEvidence(claim: DeviceIdentifier, now: Long) = DeviceObservationEntity(
    deviceId = 0, source = claim.source.name, field = ObservedField.PRODUCT.name,
    value = "${claim.kind.name}:${claim.value}", weight = 0, detail = CLAIM_DETAIL, lastSeen = now,
)
