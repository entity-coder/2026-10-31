package com.restrobill.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.restrobill.app.R
import com.restrobill.app.data.MenuItem
import com.restrobill.app.data.RestroDatabase
import com.restrobill.app.data.ShopPrefs
import com.restrobill.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Host activity: bottom navigation across Tables / Menu / Summary, plus
 *  the Settings entry in the toolbar menu. Seeds the 12-item starter menu
 *  exactly once, on first launch. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        seedMenuIfNeeded()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_tables -> TablesFragment()
                R.id.nav_menu -> MenuFragment()
                else -> SummaryFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TablesFragment())
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** The starter menu mirrors the PC RestroBill's 12-item seed menu, so
     *  a new install is usable without any setup. Prices are paise. */
    private fun seedMenuIfNeeded() {
        val prefs = ShopPrefs(this)
        if (prefs.menuSeeded) return
        val seed = listOf(
            MenuItem(name = "Paneer Tikka", category = "Starters", pricePaise = 22000, isVeg = true),
            MenuItem(name = "Chicken 65", category = "Starters", pricePaise = 24000, isVeg = false),
            MenuItem(name = "Veg Manchurian", category = "Starters", pricePaise = 16000, isVeg = true),
            MenuItem(name = "Butter Chicken", category = "Main Course", pricePaise = 32000, isVeg = false),
            MenuItem(name = "Paneer Butter Masala", category = "Main Course", pricePaise = 26000, isVeg = true),
            MenuItem(name = "Dal Tadka", category = "Main Course", pricePaise = 18000, isVeg = true),
            MenuItem(name = "Chicken Biryani", category = "Main Course", pricePaise = 28000, isVeg = false),
            MenuItem(name = "Veg Biryani", category = "Main Course", pricePaise = 20000, isVeg = true),
            MenuItem(name = "Butter Naan", category = "Breads", pricePaise = 4000, isVeg = true),
            MenuItem(name = "Tandoori Roti", category = "Breads", pricePaise = 2500, isVeg = true),
            MenuItem(name = "Masala Chai", category = "Beverages", pricePaise = 3000, isVeg = true),
            MenuItem(name = "Gulab Jamun", category = "Desserts", pricePaise = 8000, isVeg = true)
        )
        lifecycleScope.launch {
            val dao = RestroDatabase.get(this@MainActivity).dao()
            for (item in seed) dao.insertMenuItem(item)
            prefs.menuSeeded = true
        }
    }
}
