package com.ventouxlabs.netlens.feature.wifi.engine

import com.ventouxlabs.netlens.feature.wifi.model.WifiBand

object ChannelCalculator {

    fun frequencyToChannel(frequencyMhz: Int): Int = when {
        frequencyMhz in 2412..2484 -> {
            if (frequencyMhz == 2484) 14 else (frequencyMhz - 2412) / 5 + 1
        }
        frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> 0
    }

    fun bandForFrequency(frequencyMhz: Int): WifiBand = when {
        frequencyMhz in 2400..2500 -> WifiBand.TWO_GHZ
        frequencyMhz in 5150..5850 -> WifiBand.FIVE_GHZ
        frequencyMhz in 5925..7125 -> WifiBand.SIX_GHZ
        else -> WifiBand.ALL
    }
}
