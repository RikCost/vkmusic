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
import com.vkmusic.data.db.ExportedTrackDao
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
        // «Моя музыка» — кэш, он перезагружается из VK. Реестр сохранённых файлов теряется нельзя:
        // без него треки, лежащие файлом в папке, снова качаются из сети.
        Room.databaseBuilder(context, AppDatabase::class.java, "vkmusic.db")
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideExportedTrackDao(db: AppDatabase): ExportedTrackDao = db.exportedTrackDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS exported_tracks " +
                    "(id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, location TEXT NOT NULL, " +
                    "mimeType TEXT NOT NULL, sizeBytes INTEGER NOT NULL, savedAtMs INTEGER NOT NULL)",
            )
        }
    }
}
