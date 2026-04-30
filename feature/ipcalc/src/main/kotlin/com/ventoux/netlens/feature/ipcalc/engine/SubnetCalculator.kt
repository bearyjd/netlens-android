package com.ventoux.netlens.feature.ipcalc.engine

import com.ventoux.netlens.feature.ipcalc.model.SubnetInfo

interface SubnetCalculator {
    fun calculate(input: String): SubnetInfo
}
