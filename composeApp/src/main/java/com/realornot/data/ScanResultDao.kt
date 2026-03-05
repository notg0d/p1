package com.realornot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.realornot.model.ScanResult

@Dao
interface ScanResultDao {
    @Insert
    suspend fun insert(result: ScanResult): Long

    @Query("SELECT * FROM scan_results ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanResult>>

    @Query("SELECT COUNT(*) FROM scan_results")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM scan_results WHERE id = :id")
    suspend fun delete(id: Long)
}
