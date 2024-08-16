package helium314.keyboard.latin.setup;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class PromptHistoryViewModel extends AndroidViewModel {

    private final PromptHistoryRepository mRepository;
    private final LiveData<List<Prompt>> mAllPrompts;

    public PromptHistoryViewModel(Application application) {
        super(application);
        mRepository = new PromptHistoryRepository(application);
        mAllPrompts = mRepository.getAllPrompts();
    }

    public LiveData<List<Prompt>> getAllPrompts() {
        return mAllPrompts;
    }

    public void insert(Prompt prompt) {
        mRepository.insert(prompt);
    }
}
