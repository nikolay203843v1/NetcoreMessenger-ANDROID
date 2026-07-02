package com.netcoremessenger.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.netcoremessenger.core.data.local.NetcoreDatabase
import com.netcoremessenger.core.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN album_id TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetcoreDatabase {
        return Room.databaseBuilder(
            context,
            NetcoreDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .addMigrations(MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideUserDao(db: NetcoreDatabase) = db.userDao()

    @Provides
    fun provideChatDao(db: NetcoreDatabase) = db.chatDao()

    @Provides
    fun provideMessageDao(db: NetcoreDatabase) = db.messageDao()

    @Provides
    fun provideMediaDao(db: NetcoreDatabase) = db.mediaDao()

    @Provides
    fun provideSyncStateDao(db: NetcoreDatabase) = db.syncStateDao()
}
