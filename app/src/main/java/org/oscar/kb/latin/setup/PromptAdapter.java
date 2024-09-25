package org.oscar.kb.latin.setup;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.oscar.kb.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PromptAdapter extends RecyclerView.Adapter<PromptAdapter.PromptViewHolder> {
    private List<Prompt> promptList = new ArrayList<>();

    public void submitList(List<Prompt> prompts) {
        this.promptList.clear(); // Clear the old list
        this.promptList.addAll(prompts); // Add the new list of prompts
        notifyDataSetChanged(); // Notify the adapter
    }

    @NonNull
    @Override
    public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompt_history, parent, false);
        return new PromptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
        Prompt currentPrompt = promptList.get(position);
        holder.bind(currentPrompt);
    }

    @Override
    public int getItemCount() {
        return promptList.size();
    }

    public static class PromptViewHolder extends RecyclerView.ViewHolder {
        private final TextView promptText;

        public PromptViewHolder(@NonNull View itemView) {
            super(itemView);
            promptText = itemView.findViewById(R.id.tv);
        }

        public void bind(Prompt prompt) {
            promptText.setText(prompt.getText());

            // Highlight based on type
            if (prompt.getType().equals("User Input")) {
                promptText.setTextColor(Color.BLACK);  // Highlight user input
            } else if (prompt.getType().equals("AI Output")) {
                promptText.setTextColor(Color.GRAY);   // Gray out AI output
            }
        }
    }
}



