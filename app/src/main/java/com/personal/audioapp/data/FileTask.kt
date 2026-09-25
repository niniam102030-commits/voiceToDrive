package com.personal.audioapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Lifecycle of a single audio file:
 *
 *   PENDING -> COMPRESSING -> UPLOADING -> COMPLETED
 *                                |
 *                                +--------> FAILED
 */
enum class TaskStatus {
    PENDING,
    COMPRESSING,
    UPLOADING,
    COMPLETED,
    FAILED
}

@Entity(tableName = "file_tasks")
data class FileTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Friendly name shown in the list. */
    val displayName: String,

    /** Absolute path of the *current* local file (raw or compressed). */
    val localPath: String,

    /** Google Drive file id once the upload succeeded. */
    val remoteId: String? = null,

    val mimeType: String = "audio/mp4",

    val sizeBytes: Long = 0L,

    val status: TaskStatus = TaskStatus.PENDING,

    /** Human readable failure reason, if any. */
    val error: String? = null,

    /** Snapshot of the "auto delete after upload" toggle at creation time. */
    val deleteLocalAfterUpload: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isFinished: Boolean
        get() = status == TaskStatus.COMPLETED || status == TaskStatus.FAILED
}

class Converters {

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus =
        runCatching { TaskStatus.valueOf(value) }.getOrDefault(TaskStatus.PENDING)
}
