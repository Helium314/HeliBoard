package org.oscar.kb.latin.setup;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prompts")
public class Prompt {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String userInput;
    private String aiOutput;

    public Prompt(String userInput, String aiOutput) {
        this.userInput = userInput;
        this.aiOutput = aiOutput;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserInput() {
        return userInput;
    }

    public String getAiOutput() {
        return aiOutput;
    }
}
