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
    private List<Prompt> prompts = new ArrayList<>();

    @NonNull
    @Override
    public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompt_history, parent, false);
        return new PromptViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
        Prompt currentPrompt = prompts.get(position);
        holder.userInputTextView.setText("User Input: " + currentPrompt.getUserInput());
        holder.aiOutputTextView.setText("AI Output: " + currentPrompt.getAiOutput());
    }

    @Override
    public int getItemCount() {
        return prompts.size();
    }

    public void setPrompts(List<Prompt> prompts) {
        this.prompts = prompts;
        notifyDataSetChanged();
    }

    class PromptViewHolder extends RecyclerView.ViewHolder {
        private TextView userInputTextView;
        private TextView aiOutputTextView;

        public PromptViewHolder(@NonNull View itemView) {
            super(itemView);
            userInputTextView = itemView.findViewById(R.id.tv);
            aiOutputTextView = itemView.findViewById(R.id.ai);
        }
    }
}


