package com.restrobill.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One dish on the menu. Prices are integer paise (never Double) so bill
 * maths is exact. [active] is a soft delete: deactivated dishes stop
 * appearing in the ordering picker, but old bills keep their snapshot
 * names and prices, so history never changes.
 */
@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val pricePaise: Long,
    val isVeg: Boolean,
    val active: Boolean = true
)
