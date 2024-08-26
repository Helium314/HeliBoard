package org.samyarth.oskey.bottomsheets


import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.provider.Settings
import org.samyarth.oskey.R

class SheetOneFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view = inflater.inflate(R.layout.fragment_sheet1, container, false)

        val instructionText =
            "Tap the toggle to enable Oscar Keyboard in \n                your keyboard list"
        val spannableString = SpannableString(instructionText)

        val startIndex = instructionText.indexOf("Oscar Keyboard")
        val endIndex = startIndex + "Oscar Keyboard".length

        if (startIndex >= 0) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.BLACK), // Set color to black
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

        val textViewInstruction = view.findViewById<TextView>(R.id.text_view_instruction)
        textViewInstruction.text = spannableString

        val buttonEnable = view.findViewById<Button>(R.id.enableId)
        buttonEnable.setOnClickListener {
            // Open the system settings to enable the keyboard
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        return view
    }

}