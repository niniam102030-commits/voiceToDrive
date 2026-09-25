package com.personal.audioapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTaskDao {

    @Query("SELECT * FROM file_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FileTask>>

    @Query("SELECT * FROM file_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FileTask?

    @Query("SELECT * FROM file_tasks WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: TaskStatus): List<FileTask>

    @Insert
    suspend fun insert(task: FileTask): Long

    @Update
    suspend fun update(task: FileTask)

    @Delete
    suspend fun delete(task: FileTask)

    @Query("DELETE FROM file_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM file_tasks WHERE status = 'COMPLETED'")
    suspend fun deleteAllCompleted()

    @Query(
        "UPDATE file_tasks SET status = :status, error = :error, updatedAt = :timestamp " +
            "WHERE id = :id"
    )
    suspend fun updateStatus(
        id: Long,
        status: TaskStatus,
        error: String?,
        timestamp: Long
    )
}
