package com.ventoux.netlens.feature.celltower.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.ventoux.netlens.feature.celltower.model.CellTowerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject

data class CellTowerState(
    val connected: CellTowerInfo?,
    val neighbors: List<CellTowerInfo>,
)

interface CellTowerReader {
    fun observe(): Flow<CellTowerState>
    fun readOnce(): CellTowerState?
}

class CellTowerReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CellTowerReader {

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun observe(): Flow<CellTowerState> = flow {
        while (currentCoroutineContext().isActive) {
            readOnce()?.let { emit(it) }
            delay(REFRESH_INTERVAL_MS)
        }
    }

    override fun readOnce(): CellTowerState? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        @Suppress("MissingPermission")
        val allCells = telephonyManager.allCellInfo ?: return null
        val operatorName = telephonyManager.networkOperatorName ?: ""

        val parsed = allCells.mapNotNull { parseCellInfo(it, operatorName) }
        val connected = parsed.firstOrNull { it.isRegistered }
        val neighbors = parsed.filter { !it.isRegistered }

        return CellTowerState(connected = connected, neighbors = neighbors)
    }

    private fun parseCellInfo(info: CellInfo, operatorName: String): CellTowerInfo? = when (info) {
        is CellInfoLte -> parseLte(info, operatorName)
        is CellInfoNr -> parseNr(info, operatorName)
        is CellInfoGsm -> parseGsm(info, operatorName)
        is CellInfoWcdma -> parseWcdma(info, operatorName)
        else -> null
    }

    private fun parseLte(info: CellInfoLte, operatorName: String): CellTowerInfo {
        val identity = info.cellIdentity
        val signal = info.cellSignalStrength
        return CellTowerInfo(
            networkType = "LTE",
            operatorName = operatorName,
            cellId = formatId(identity.ci),
            tac = formatId(identity.tac),
            band = if (identity.earfcn != CellInfo.UNAVAILABLE) "EARFCN ${identity.earfcn}" else "",
            rsrp = signal.rsrp.takeUnless { it == CellInfo.UNAVAILABLE },
            rsrq = signal.rsrq.takeUnless { it == CellInfo.UNAVAILABLE },
            sinr = signal.rssnr.takeUnless { it == CellInfo.UNAVAILABLE },
            rssi = null,
            isRegistered = info.isRegistered,
        )
    }

    private fun parseNr(info: CellInfoNr, operatorName: String): CellTowerInfo {
        val identity = info.cellIdentity as android.telephony.CellIdentityNr
        val signal = info.cellSignalStrength as CellSignalStrengthNr
        return CellTowerInfo(
            networkType = "5G NR",
            operatorName = operatorName,
            cellId = formatLong(identity.nci),
            tac = formatId(identity.tac),
            band = if (identity.nrarfcn != CellInfo.UNAVAILABLE) "NRARFCN ${identity.nrarfcn}" else "",
            rsrp = signal.ssRsrp.takeUnless { it == CellInfo.UNAVAILABLE },
            rsrq = signal.ssRsrq.takeUnless { it == CellInfo.UNAVAILABLE },
            sinr = signal.ssSinr.takeUnless { it == CellInfo.UNAVAILABLE },
            rssi = null,
            isRegistered = info.isRegistered,
        )
    }

    private fun parseGsm(info: CellInfoGsm, operatorName: String): CellTowerInfo {
        val identity = info.cellIdentity
        val signal = info.cellSignalStrength
        return CellTowerInfo(
            networkType = "GSM",
            operatorName = operatorName,
            cellId = formatId(identity.cid),
            tac = formatId(identity.lac),
            band = if (identity.arfcn != CellInfo.UNAVAILABLE) "ARFCN ${identity.arfcn}" else "",
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = signal.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
            isRegistered = info.isRegistered,
        )
    }

    private fun parseWcdma(info: CellInfoWcdma, operatorName: String): CellTowerInfo {
        val identity = info.cellIdentity
        val signal = info.cellSignalStrength
        return CellTowerInfo(
            networkType = "WCDMA",
            operatorName = operatorName,
            cellId = formatId(identity.cid),
            tac = formatId(identity.lac),
            band = if (identity.uarfcn != CellInfo.UNAVAILABLE) "UARFCN ${identity.uarfcn}" else "",
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = signal.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
            isRegistered = info.isRegistered,
        )
    }

    private fun formatId(value: Int): String =
        if (value == CellInfo.UNAVAILABLE) "" else value.toString()

    private fun formatLong(value: Long): String =
        if (value == CellInfo.UNAVAILABLE_LONG) "" else value.toString()

    private companion object {
        const val REFRESH_INTERVAL_MS = 5_000L
    }
}
