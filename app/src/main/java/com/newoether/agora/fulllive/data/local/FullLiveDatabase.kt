package com.newoether.agora.fulllive.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.newoether.agora.fulllive.data.local.dao.InsightDao
import com.newoether.agora.fulllive.data.local.dao.MessageDao
import com.newoether.agora.fulllive.data.local.dao.PersonaDao
import com.newoether.agora.fulllive.data.local.dao.SessionDao
import com.newoether.agora.fulllive.data.local.dao.StatDao
import com.newoether.agora.fulllive.data.local.entity.ConversationSessionEntity
import com.newoether.agora.fulllive.data.local.entity.MessageEntity
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.data.local.entity.StatEntryEntity
import com.newoether.agora.fulllive.data.local.entity.UserInsightEntity

@Database(
    entities = [
        PersonaEntity::class,
        ConversationSessionEntity::class,
        MessageEntity::class,
        UserInsightEntity::class,
        StatEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FullLiveDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun insightDao(): InsightDao
    abstract fun statDao(): StatDao

    companion object {
        @Volatile
        private var INSTANCE: FullLiveDatabase? = null

        fun getDatabase(context: Context): FullLiveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FullLiveDatabase::class.java,
                    "fullive_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
