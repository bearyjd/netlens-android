package com.ventoux.netlens.core.network

sealed class VpnState {
    abstract fun serialize(): String

    object None : VpnState() {
        override fun serialize(): String = "NONE"
    }

    object FullTunnel : VpnState() {
        override fun serialize(): String = "FULL"
    }

    object SplitTunnel : VpnState() {
        override fun serialize(): String = "SPLIT"
    }

    companion object {
        fun deserialize(value: String?): VpnState = when (value) {
            "FULL" -> FullTunnel
            "SPLIT" -> SplitTunnel
            else -> None
        }
    }
}
