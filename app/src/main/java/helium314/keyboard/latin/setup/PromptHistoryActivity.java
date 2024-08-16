package helium314.keyboard.latin.setup;


import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;

import helium314.keyboard.latin.R;

public class PromptHistoryActivity extends AppCompatActivity {

    private PromptAdapter adapter;
    private PromptHistoryViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_message);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PromptAdapter();
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(PromptHistoryViewModel.class);
        viewModel.getPromptHistory().observe(this, prompts -> {
            if (prompts != null) {
                adapter.setPrompts(prompts);
                Log.d("PromptHistoryActivity", "Loaded prompts: " + prompts);
            } else {
                Log.d("PromptHistoryActivity", "No prompts found.");
            }
        });
    }
}

