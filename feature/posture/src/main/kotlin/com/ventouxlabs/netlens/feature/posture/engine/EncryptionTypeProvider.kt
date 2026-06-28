package com.ventouxlabs.netlens.feature.posture.engine

interface EncryptionTypeProvider {
    fun currentEncryptionType(): String?
}
