package com.example.seriestracker.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.seriestracker.data.dao.SeriesDao;
import com.example.seriestracker.data.entities.Collection;
import com.example.seriestracker.data.entities.MediaFile;
import com.example.seriestracker.data.entities.Series;
import com.example.seriestracker.data.entities.SeriesCollectionCrossRef;

@Database(
        entities = {Series.class, Collection.class, SeriesCollectionCrossRef.class,  MediaFile.class},
        version = 8,
        exportSchema = false
)
public abstract class SeriesDatabase extends RoomDatabase {

    public abstract SeriesDao seriesDao();

    // Миграция с версии 1 на 2
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Создаем новую таблицу связей с правильным именем
            database.execSQL("CREATE TABLE IF NOT EXISTS series_collection_cross_ref (" +
                    "seriesId INTEGER NOT NULL, " +
                    "collectionId INTEGER NOT NULL, " +
                    "isWatched INTEGER DEFAULT 0, " +
                    "PRIMARY KEY(seriesId, collectionId))");

            // Копируем данные из старой таблицы (если она существует)
            try {
                database.execSQL("INSERT INTO series_collection_cross_ref SELECT * FROM series_collections");
                Log.d("Migration", "Data copied from old table");
            } catch (Exception e) {
                Log.d("Migration", "No data to copy or table doesn't exist");
            }

            // Удаляем старую таблицу
            try {
                database.execSQL("DROP TABLE IF EXISTS series_collections");
                Log.d("Migration", "Old table dropped");
            } catch (Exception e) {
                Log.d("Migration", "Could not drop old table");
            }
        }
    };

    private static volatile SeriesDatabase INSTANCE;

    public static SeriesDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (SeriesDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    SeriesDatabase.class,
                                    "series_database"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()  // Удалит БД при ошибках
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}