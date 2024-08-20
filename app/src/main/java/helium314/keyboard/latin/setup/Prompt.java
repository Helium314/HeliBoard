package helium314.keyboard.latin.setup;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "prompt_table")
public class Prompt {

    @PrimaryKey(autoGenerate = true)
    private int id;

    public String text;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // Constructor
    public Prompt(String text, long timestamp) {
        this.text = text;
        this.timestamp = timestamp;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

//package helium314.keyboard.latin.setup;
//
//import androidx.room.ColumnInfo;
//import androidx.room.Entity;
//import androidx.room.PrimaryKey;
//
//@Entity(tableName = "prompt_table")
//public class Prompt {
//
//    @PrimaryKey(autoGenerate = true)
//    private int id;
//
//    public String text;
//
//    @ColumnInfo(name = "timestamp")
//    private long timestamp;
//
//    public Prompt(String text, long timestamp) {
//        this.text = text;
//        this.timestamp = timestamp;
//    }
//
//    // Getters and setters
//    public int getId() {
//        return id;
//    }
//
//    public void setId(int id) {
//        this.id = id;
//    }
//
//    public String getText() {
//        return text;
//    }
//
//    public void setText(String text) {
//        this.text = text;
//    }
//
//    public long getTimestamp() {
//        return timestamp;
//    }
//
//    public void setTimestamp(long timestamp) {
//        this.timestamp = timestamp;
//    }
//}
