package org.oscar.kb.latin.setup;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;
public class PromptHistoryRepository {
    private final PromptDao promptDao;

    public PromptHistoryRepository(Context context) {
        AppDatabase database = AppDatabase.getDatabase(context);
        promptDao = database.promptDao();
    }
    public LiveData<List<Prompt>> getAllPrompts() {
        return promptDao.getAllPrompts();
    }

    public void insert(Prompt prompt) {
        new Thread(() -> promptDao.insert(prompt)).start();
    }
}