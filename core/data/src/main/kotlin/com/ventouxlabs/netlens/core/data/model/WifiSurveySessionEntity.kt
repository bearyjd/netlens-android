package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One walk-through of a building: a named container for the signal readings captured while
 * moving between rooms. [endedAt] stays null while the survey is still running.
 */
@Entity(
    tableName = "wifi_survey_sessions",
    indices = [Index("startedAt")],
)
data class WifiSurveySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // SSID the survey was recorded against; null when the phone wasn't associated at start.
    val ssid: String?,
    val startedAt: Long,
    val endedAt: Long? = null,
)
