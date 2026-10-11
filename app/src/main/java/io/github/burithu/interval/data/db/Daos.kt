package io.github.burithu.interval.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderStateDao {
    @Query("SELECT * FROM reminder_state")
    fun observeAll(): Flow<List<ReminderStateEntity>>

    @Query("SELECT * FROM reminder_state WHERE reminderId = :id")
    suspend fun get(id: Long): ReminderStateEntity?

    @Upsert
    suspend fun upsert(state: ReminderStateEntity)
}

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY id DESC LIMIT $MAX_ROWS")
    fun observeRecent(): Flow<List<EventLogEntity>>

    @Insert
    suspend fun insertRow(event: EventLogEntity)

    @Query("DELETE FROM event_log WHERE id NOT IN (SELECT id FROM event_log ORDER BY id DESC LIMIT $MAX_ROWS)")
    suspend fun trim()

    @Transaction
    suspend fun insert(event: EventLogEntity) {
        insertRow(event)
        trim()
    }

    companion object {
        const val MAX_ROWS = 200
    }
}
