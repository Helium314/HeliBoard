package helium314.keyboard.latin.setup;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class PromptHistoryViewModel extends AndroidViewModel {

    private final PromptHistoryRepository repository;
    private final LiveData<List<Prompt>> promptHistory;

    public PromptHistoryViewModel(@NonNull Application application) {
        super(application);
        repository = new PromptHistoryRepository(application);
        promptHistory = repository.getAllPrompts();
        // Add dummy data
        addDummyData();
    }

    public LiveData<List<Prompt>> getPromptHistory() {
        return promptHistory;
    }

    private void addDummyData() {
        new Thread(() -> {
            repository.insert(new Prompt("Hello"));
            repository.insert(new Prompt("How are you?"));
            repository.insert(new Prompt("What is the weather today?"));
        }).start();
    }
}
