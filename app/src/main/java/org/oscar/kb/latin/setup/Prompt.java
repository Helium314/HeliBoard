package org.oscar.kb.latin.setup;


import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prompts")
public class Prompt {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String userInput;
    private String aiOutput;
    //private PromptType type; // "User Input" or "AI Output"

//    public enum PromptType {
//        USER_INPUT,
//        AI_OUTPUT
//    }

    public Prompt(String userInput, String aiOutput) {
        this.userInput = userInput;
        this.aiOutput = aiOutput;
    }
    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String text) {
        this.userInput = userInput;
    }

    public String getAiOutput() {
        return aiOutput;
    }
    public void setAiOutput(String aiOutput) {
        this.aiOutput = aiOutput;
    }
//    public PromptType getType() {
//        return type;
//    }
//
//    public void setType(PromptType type) {
//        this.type = type;
//    }

}
