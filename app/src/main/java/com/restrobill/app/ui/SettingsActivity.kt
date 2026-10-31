package com.restrobill.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.restrobill.app.data.ShopPrefs
import com.restrobill.app.databinding.ActivitySettingsBinding

/** Restaurant name (printed on bills/KOTs) and GST percent. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val prefs = ShopPrefs(this)
        binding.restaurantNameInput.setText(prefs.restaurantName)
        binding.gstInput.setText(prefs.gstPercent.toString())
        binding.saveButton.setOnClickListener {
            val name = binding.restaurantNameInput.text.toString().trim()
            val gst = binding.gstInput.text.toString().toIntOrNull()
            if (name.isEmpty() || gst == null || gst < 0 || gst > 28) {
                Toast.makeText(this, "Enter a name and GST 0–28%", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.restaurantName = name
            prefs.gstPercent = gst
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
