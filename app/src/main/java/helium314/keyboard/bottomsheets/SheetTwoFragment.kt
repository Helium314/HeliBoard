package helium314.keyboard.bottomsheets

import android.content.Context
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
import helium314.keyboard.latin.R
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

class SheetTwoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view= inflater.inflate(R.layout.fragment_sheet2, container, false)


        val instructionText = "Tap to select Oscar Keyboard as your default      \n                        input method"
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

        val textViewInstruction=view.findViewById<TextView>(R.id.texts)
        textViewInstruction.text = spannableString

        val buttonSetup = view.findViewById<Button>(R.id.setupID)
        buttonSetup.setOnClickListener {
            // Open the input method picker to select the keyboard
//            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        return view
        }
}