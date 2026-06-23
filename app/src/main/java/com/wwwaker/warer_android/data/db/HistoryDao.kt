package com.wwwaker.warer_android.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE type = :type ORDER BY timestamp DESC")
    fun getHistoryByType(type: String): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE input LIKE '%' || :query || '%' " +
        "OR plainText LIKE '%' || :query || '%' ORDER BY timestamp DESC"
    )
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Delete
    suspend fun delete(entry: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): HistoryEntity?
}
