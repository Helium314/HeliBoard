package helium314.keyboard.latin.setup

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import helium314.keyboard.latin.R

class KeyboardselectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_keyboardselection)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val instructionText = "    Welcome to \n" +"Oscar Keyboard"
        val spannableString = SpannableString(instructionText)

        val startIndex = instructionText.indexOf("Oscar Keyboard")
        val endIndex = startIndex + "Oscar Keyboard".length

        if (startIndex >= 0) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#85BDB9")),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannableString.setSpan(
                StyleSpan(Typeface.BOLD), // Set style to bold
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val textViewInstruction=findViewById<TextView>(R.id.textview_Id)
        textViewInstruction.text = spannableString


        val instruction = "Tap on Oscar Keyboard to try the keyboard"
        val spannablestr = SpannableString(instruction)

        val startIdx = instruction.indexOf("Oscar Keyboard")
        val endIdx = startIdx + "Oscar Keyboard".length

        if (startIdx >= 0) {
            spannablestr.setSpan(
                ForegroundColorSpan(Color.BLACK),
                startIdx,
                endIdx,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannablestr.setSpan(
                StyleSpan(Typeface.BOLD), // Set style to bold
                startIdx,
                endIdx,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val textView=findViewById<TextView>(R.id.new_text_view)
        textView.text = spannablestr

    }
}