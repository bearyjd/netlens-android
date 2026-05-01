package com.ventoux.netlens.feature.wifi

import com.ventoux.netlens.feature.wifi.engine.ChannelCalculator
import com.ventoux.netlens.feature.wifi.model.WifiBand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChannelCalculatorTest {

    @Test
    fun `2412 MHz maps to channel 1`() {
        assertEquals(1, ChannelCalculator.frequencyToChannel(2412))
    }

    @Test
    fun `2437 MHz maps to channel 6`() {
        assertEquals(6, ChannelCalculator.frequencyToChannel(2437))
    }

    @Test
    fun `2462 MHz maps to channel 11`() {
        assertEquals(11, ChannelCalculator.frequencyToChannel(2462))
    }

    @Test
    fun `2484 MHz maps to channel 14`() {
        assertEquals(14, ChannelCalculator.frequencyToChannel(2484))
    }

    @Test
    fun `5180 MHz maps to channel 36`() {
        assertEquals(36, ChannelCalculator.frequencyToChannel(5180))
    }

    @Test
    fun `5240 MHz maps to channel 48`() {
        assertEquals(48, ChannelCalculator.frequencyToChannel(5240))
    }

    @Test
    fun `5745 MHz maps to channel 149`() {
        assertEquals(149, ChannelCalculator.frequencyToChannel(5745))
    }

    @Test
    fun `5825 MHz maps to channel 165`() {
        assertEquals(165, ChannelCalculator.frequencyToChannel(5825))
    }

    @Test
    fun `5955 MHz maps to 6 GHz channel 1`() {
        assertEquals(1, ChannelCalculator.frequencyToChannel(5955))
    }

    @Test
    fun `6115 MHz maps to 6 GHz channel 33`() {
        assertEquals(33, ChannelCalculator.frequencyToChannel(6115))
    }

    @Test
    fun `unknown frequency returns 0`() {
        assertEquals(0, ChannelCalculator.frequencyToChannel(1000))
    }

    @Test
    fun `bandForFrequency 2437 returns TWO_GHZ`() {
        assertEquals(WifiBand.TWO_GHZ, ChannelCalculator.bandForFrequency(2437))
    }

    @Test
    fun `bandForFrequency 5180 returns FIVE_GHZ`() {
        assertEquals(WifiBand.FIVE_GHZ, ChannelCalculator.bandForFrequency(5180))
    }

    @Test
    fun `bandForFrequency 5955 returns SIX_GHZ`() {
        assertEquals(WifiBand.SIX_GHZ, ChannelCalculator.bandForFrequency(5955))
    }

    @Test
    fun `bandForFrequency unknown returns ALL`() {
        assertEquals(WifiBand.ALL, ChannelCalculator.bandForFrequency(1000))
    }
}
