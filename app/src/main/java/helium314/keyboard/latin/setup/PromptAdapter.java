package helium314.keyboard.latin.setup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import helium314.keyboard.latin.R;

public class PromptAdapter extends RecyclerView.Adapter<PromptAdapter.PromptViewHolder> {

    private List<Prompt> prompts = new ArrayList<>();

    @NonNull
    @Override
    public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompt_history, parent, false);
        return new PromptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
        Prompt prompt = prompts.get(position);
        holder.promptTextView.setText(prompt.getText());
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
        TextView promptTextView;

        PromptViewHolder(View itemView) {
            super(itemView);
            promptTextView = itemView.findViewById(R.id.tv_prompt_text);
        }
    }
}

