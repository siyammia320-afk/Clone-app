package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClonedAppEntity::class, SpaceCategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OmniSpaceDatabase : RoomDatabase() {
    abstract fun omniSpaceDao(): OmniSpaceDao

    companion object {
        @Volatile
        private var INSTANCE: OmniSpaceDatabase? = null

        fun getDatabase(context: Context): OmniSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniSpaceDatabase::class.java,
                    "omnispace_cloner_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
