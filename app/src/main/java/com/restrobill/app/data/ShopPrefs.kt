package com.restrobill.app.data

import android.content.Context

/**
 * Tiny settings store (SharedPreferences, not Room): the restaurant name
 * printed on bills/KOTs and the GST percent. Kept out of the database
 * because these are app settings, not business records — the same split
 * the PC RestroBill uses for its shop profile.
 */
class ShopPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("restrobill_prefs", Context.MODE_PRIVATE)

    var restaurantName: String
        get() = prefs.getString("restaurant_name", "RestroBill") ?: "RestroBill"
        set(value) = prefs.edit().putString("restaurant_name", value).apply()

    /** GST percent charged on the discounted subtotal; default 5%. */
    var gstPercent: Int
        get() = prefs.getInt("gst_percent", 5)
        set(value) = prefs.edit().putInt("gst_percent", value.coerceIn(0, 28)).apply()

    /** True once the 12-item starter menu has been seeded. */
    var menuSeeded: Boolean
        get() = prefs.getBoolean("menu_seeded", false)
        set(value) = prefs.edit().putBoolean("menu_seeded", value).apply()
}
