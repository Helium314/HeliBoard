package helium314.keyboard.latin.setup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Prompt.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    // Migration from version 1 to 2
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Create the table in version 2
            database.execSQL("CREATE TABLE IF NOT EXISTS `prompt_table` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT)");
        }
    };

    // Migration from version 2 to 3
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add new column to the table in version 3
            database.execSQL("ALTER TABLE prompt_table ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0");
        }
    };

    static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "prompt_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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


