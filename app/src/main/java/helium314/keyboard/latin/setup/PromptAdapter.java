package helium314.keyboard.latin.setup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.setup.Prompt;

public class PromptAdapter extends RecyclerView.Adapter<PromptAdapter.PromptViewHolder> {
    private List<Prompt> prompts = new ArrayList<>();

    @NonNull
    @Override
    public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_prompt_history, parent, false);
        return new PromptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
        Prompt prompt = prompts.get(position);
        holder.textView.setText(prompt.getText());

        // Convert timestamp to a human-readable date
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
        String formattedDate = sdf.format(new Date(prompt.getTimestamp()));
        holder.timestampView.setText(formattedDate);
    }

    @Override
    public int getItemCount() {
        return prompts.size();
    }

    public void submitList(List<Prompt> newPrompts) {
        prompts.clear();
        prompts.addAll(newPrompts);
        notifyDataSetChanged();
    }

    public static class PromptViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        TextView timestampView;

        public PromptViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv_prompt_text);
            timestampView = itemView.findViewById(R.id.tv_timestamp);
        }
    }
}
