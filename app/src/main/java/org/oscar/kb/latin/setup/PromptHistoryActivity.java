package org.oscar.kb.latin.setup;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.oscar.kb.R;
import java.util.List;
public class PromptHistoryActivity extends AppCompatActivity {

    private PromptHistoryViewModel promptViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_message);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        final PromptAdapter adapter = new PromptAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        promptViewModel = new ViewModelProvider(this).get(PromptHistoryViewModel.class);
        promptViewModel.getAllPrompts().observe(this, new Observer<List<Prompt>>() {
            @Override
            public void onChanged(List<Prompt> prompts) {
                // Update the cached copy of the prompts in the adapter.
                adapter.submitList(prompts);
            }
        });
    }
}
