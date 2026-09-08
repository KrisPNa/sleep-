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
        entities = {Series.class, Collection.class, SeriesCollectionCrossRef.class, MediaFile.class},
        version = 14,
        exportSchema = false
)
public abstract class SeriesDatabase extends RoomDatabase {

    public abstract SeriesDao seriesDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS series_collection_cross_ref (" +
                    "seriesId INTEGER NOT NULL, " +
                    "collectionId INTEGER NOT NULL, " +
                    "isWatched INTEGER DEFAULT 0, " +
                    "PRIMARY KEY(seriesId, collectionId))");
            try {
                database.execSQL("INSERT INTO series_collection_cross_ref SELECT * FROM series_collections");
                Log.d("Migration", "Data copied from old table");
            } catch (Exception e) {
                Log.d("Migration", "No data to copy or table doesn't exist");
            }
            try {
                database.execSQL("DROP TABLE IF EXISTS series_collections");
            } catch (Exception e) {
                Log.d("Migration", "Could not drop old table");
            }
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE series ADD COLUMN watchUrl TEXT");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE series ADD COLUMN watchAt TEXT");
        }
    };

    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE series ADD COLUMN cloudId TEXT");
            database.execSQL("ALTER TABLE series ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE series ADD COLUMN syncDirty INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE series ADD COLUMN cloudImagePath TEXT");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_series_cloudId ON series(cloudId)");

            database.execSQL("ALTER TABLE collections ADD COLUMN cloudId TEXT");
            database.execSQL("ALTER TABLE collections ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE collections ADD COLUMN syncDirty INTEGER NOT NULL DEFAULT 1");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_cloudId ON collections(cloudId)");

            database.execSQL("ALTER TABLE media_files ADD COLUMN cloudId TEXT");
            database.execSQL("ALTER TABLE media_files ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE media_files ADD COLUMN syncDirty INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE media_files ADD COLUMN storagePath TEXT");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_media_files_cloudId ON media_files(cloudId)");

            database.execSQL("ALTER TABLE series_collection_cross_ref ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE series_collection_cross_ref ADD COLUMN syncDirty INTEGER NOT NULL DEFAULT 1");

            database.execSQL("UPDATE series SET updatedAt = createdAt, syncDirty = 1 WHERE updatedAt = 0");
            database.execSQL("UPDATE collections SET updatedAt = createdAt, syncDirty = 1 WHERE updatedAt = 0");
            database.execSQL("UPDATE media_files SET updatedAt = createdAt, syncDirty = 1 WHERE updatedAt = 0");
            database.execSQL("UPDATE series_collection_cross_ref SET updatedAt = " +
                    System.currentTimeMillis() + ", syncDirty = 1");
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
                            .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_11_12,
                                    MIGRATION_12_13,
                                    MIGRATION_13_14
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
