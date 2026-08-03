package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.LanScanInventoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LanScanInventoryDao {
    @Query("SELECT * FROM lan_scan_inventories ORDER BY createdAt DESC")
    fun getAll(): Flow<List<LanScanInventoryEntry>>

    @Insert
    suspend fun insert(entry: LanScanInventoryEntry)

    @Query("DELETE FROM lan_scan_inventories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
