package org.oscar.kb.latin.setup;


import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prompts")
public class Prompt {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String text;
    private String type; // "User Input" or "AI Output"
    private long timestamp;

    public Prompt(String text, String type) {
        this.text = text;
        this.type = type;
        this.timestamp = System.currentTimeMillis(); // Automatically set the current timestamp
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
