package com.vkmusic.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vkmusic.data.db.AppDatabase
import com.vkmusic.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        // В базе только кэш «моей музыки»: он перезагружается из VK, поэтому потеря не страшна.
        Room.databaseBuilder(context, AppDatabase::class.java, "vkmusic.db")
            .addMigrations(MIGRATION_3_5, MIGRATION_4_5)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    // Реестр сохранённых файлов (exported_tracks) убран: схема v5 снова равна схеме v3.
    private val MIGRATION_3_5 = object : Migration(3, 5) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS exported_tracks")
        }
    }
}
