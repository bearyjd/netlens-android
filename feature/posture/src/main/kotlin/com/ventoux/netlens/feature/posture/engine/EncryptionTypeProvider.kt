package com.ventoux.netlens.feature.posture.engine

interface EncryptionTypeProvider {
    fun currentEncryptionType(): String?
}
