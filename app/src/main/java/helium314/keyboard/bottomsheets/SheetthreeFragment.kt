package helium314.keyboard.bottomsheets

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
import androidx.appcompat.widget.AppCompatButton
import helium314.keyboard.latin.R
import helium314.keyboard.latin.setup.KeyboardselectionActivity

class SheetThreeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view= inflater.inflate(R.layout.fragment_sheet3, container, false)
        val instructionText = "    Installation Successful !!  \n  Tap to try using the keyboard"
        val spannableString = SpannableString(instructionText)

        val startIndex = instructionText.indexOf("Installation Successful !!")
        val endIndex = startIndex + "Installation Successful !!".length

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

        val textViewInstruction=view.findViewById<TextView>(R.id.txtes)
        textViewInstruction.text = spannableString
        var textButto=view.findViewById<AppCompatButton>(R.id.openKeyboardId)
        textButto.setOnClickListener(View.OnClickListener {
            //open keyboard
            var intent= Intent(requireContext(), KeyboardselectionActivity::class.java)
            startActivity(intent)
        })
        return view}


}