package com.ventouxlabs.netlens.feature.ipcalc.engine

import com.ventouxlabs.netlens.feature.ipcalc.model.SubnetInfo

interface SubnetCalculator {
    fun calculate(input: String): SubnetInfo
}
