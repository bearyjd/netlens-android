package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single measured spot in a survey — the aggregate of a short burst of RSSI samples taken
 * while standing still, rather than one instantaneous reading (Wi-Fi RSSI swings several dB
 * second to second, so a lone sample says very little about a room).
 */
@Entity(
    tableName = "wifi_survey_points",
    indices = [Index("sessionId"), Index("capturedAt")],
    foreignKeys = [
        ForeignKey(
            entity = WifiSurveySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WifiSurveyPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val label: String,
    val capturedAt: Long,
    val avgRssi: Int,
    val minRssi: Int,
    val maxRssi: Int,
    val sampleCount: Int,
    // BSSID serving this spot — in a mesh/multi-AP house this is what tells you *which* radio
    // a room is actually being held by, which is usually the real story behind a weak corner.
    val bssid: String?,
    val frequency: Int,
    val channel: Int,
    val linkSpeedMbps: Int,
)
