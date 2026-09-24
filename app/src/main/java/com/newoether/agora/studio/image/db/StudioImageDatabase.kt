package com.newoether.agora.studio.image.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudioImageEntity::class,
        SavedPromptEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class StudioImageDatabase : RoomDatabase() {

    abstract fun studioImageDao(): StudioImageDao
    abstract fun savedPromptDao(): SavedPromptDao

    companion object {
        @Volatile
        private var INSTANCE: StudioImageDatabase? = null

        fun getInstance(context: Context): StudioImageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioImageDatabase::class.java,
                    "studio_image_cache_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
