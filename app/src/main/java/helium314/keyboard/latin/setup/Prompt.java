package helium314.keyboard.latin.setup;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prompt_table")
public class Prompt {

    @PrimaryKey(autoGenerate = true)
    private int id;

    public String text;

    public Prompt(String text) {
        this.text = text;
    }

    // Getters and setters
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
}
