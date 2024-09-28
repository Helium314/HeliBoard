package org.oscar.kb.latin.setup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.oscar.kb.R;

import java.util.ArrayList;
import java.util.List;

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
    public int getItemCount() {
        return promptList.size();
    }

    @Override
    public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
        Prompt currentPrompt = promptList.get(position);
        holder.bind(currentPrompt);
    }

    public static class PromptViewHolder extends RecyclerView.ViewHolder {
        private final TextView originalTranscription;
        private final TextView AITranscription;

        public PromptViewHolder(@NonNull View itemView) {
            super(itemView);
            originalTranscription = itemView.findViewById(R.id.tvUserInput);
            AITranscription = itemView.findViewById(R.id.tvAIOutput);
        }

        public void bind(Prompt prompt) {
//            if (prompt.getType() == Prompt.PromptType.USER_INPUT ) {
//                promptText.setText(prompt.getText()); // Set USER_INPUT text
//
//                //AIpromptText.setText(prompt.getText());
//            }
//
//            if(prompt.getType() == Prompt.PromptType.AI_OUTPUT) {
//                //promptText.setText(""); // Avoid displaying empty text
//
//                AIpromptText.setText(prompt.getText());
//            }
                originalTranscription.setText(prompt.getUserInput());
                AITranscription.setText(prompt.getAiOutput());

        }
    }
}



