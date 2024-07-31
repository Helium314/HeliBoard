package helium314.keyboard.latin.setup

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import helium314.keyboard.latin.R
import helium314.keyboard.latin.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up click listeners
        binding.enableId.setOnClickListener {
            binding.textViewInstruction.visibility = View.VISIBLE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.GONE
        }
        binding.setupID.setOnClickListener {
            binding.textViewInstruction.visibility = View.GONE
            binding.setupOscar.visibility = View.VISIBLE
            binding.tryOscar.visibility = View.GONE
        }
        binding.openKeyboardId.setOnClickListener {
            binding.textViewInstruction.visibility = View.GONE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.VISIBLE
        }
    }
}
