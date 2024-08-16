package helium314.keyboard.latin.setup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// AITextDatabase.java
@Database(entities = {Prompt.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Perform schema changes here, e.g., add new columns, tables, etc.
            // Example: Add a new column to an existing table
            database.execSQL("ALTER TABLE prompt ADD COLUMN new_column TEXT");
        }
    };

    static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "prompt_database")
                            .addMigrations(MIGRATION_1_2) // Add migration here
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


