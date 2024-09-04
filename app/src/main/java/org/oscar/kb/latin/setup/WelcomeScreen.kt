package org.oscar.kb.latin.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.oscar.kb.R

class WelcomeScreen : AppCompatActivity() {
    lateinit var Getbutton:Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome_screen)

        Getbutton=findViewById(R.id.getButton)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Getbutton.setOnClickListener(){
            val intent= Intent(this@WelcomeScreen,MainActivity::class.java)
            startActivity(intent)
        }
    }
}