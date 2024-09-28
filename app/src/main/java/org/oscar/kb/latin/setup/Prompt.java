package org.oscar.kb.latin.setup;


import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prompts")
public class Prompt {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String userInput;
    private long timestamp; // Add a timestamp field

    //private String aiOutput;
    //private PromptType type; // "User Input" or "AI Output"

//    public enum PromptType {
//        USER_INPUT,
//        AI_OUTPUT
//    }

    public Prompt(String userInput,long timestamp) {
        this.userInput = userInput;
        this.timestamp = timestamp;
        //this.aiOutput = aiOutput;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setUserInput(String text) {
        this.userInput = text;
    }

//    public String getAiOutput() {
//        return aiOutput;
//    }
//    public void setAiOutput(String aiOutput) {
//        this.aiOutput = aiOutput;
//    }
//    public PromptType getType() {
//        return type;
//    }
//
//    public void setType(PromptType type) {
//        this.type = type;
//    }

}
