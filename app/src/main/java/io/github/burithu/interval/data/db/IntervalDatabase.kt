package io.github.burithu.interval.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [ReminderEntity::class, ReminderStateEntity::class, EventLogEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IntervalDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun stateDao(): ReminderStateDao
    abstract fun logDao(): EventLogDao

    companion object {
        fun create(context: Context): IntervalDatabase =
            Room.databaseBuilder(context, IntervalDatabase::class.java, "interval.db").build()
    }
}
