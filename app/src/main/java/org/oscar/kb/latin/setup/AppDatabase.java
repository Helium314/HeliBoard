package org.oscar.kb.latin.setup;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Prompt.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    // Migration from version 1 to 2
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `prompt_table` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`user_input` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL DEFAULT 0, " +
                    "`type` TEXT NOT NULL DEFAULT 'user_input')");
        }
    };

    // Migration from version 2 to 3
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE prompt_table ADD COLUMN `ai_output` TEXT");
        }
    };

    // Migration from version 3 to 4
    // Migration from version 3 to 4
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Rename 'text' to 'user_input' if it exists
            database.execSQL("ALTER TABLE prompt_table RENAME COLUMN text TO user_input");

            // Add 'ai_output' column
            database.execSQL("ALTER TABLE prompt_table ADD COLUMN ai_output TEXT");

            // Add 'type' column
            database.execSQL("ALTER TABLE prompt_table ADD COLUMN type TEXT NOT NULL DEFAULT 'user_input'");
        }
    };

    static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "prompt_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)  // Include the latest migration
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static AppDatabase getDatabase(Context context) {
        return getInstance(context);
    }

    public abstract PromptDao promptDao();
}
