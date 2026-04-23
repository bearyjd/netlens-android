package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.WolTarget

@Dao
interface WolTargetDao {

    @Query("SELECT * FROM wol_targets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WolTarget>>

    @Insert
    suspend fun insert(target: WolTarget)

    @Update
    suspend fun update(target: WolTarget)

    @Delete
    suspend fun delete(target: WolTarget)

    @Query("SELECT * FROM wol_targets WHERE id = :id")
    suspend fun getById(id: Long): WolTarget?
}
