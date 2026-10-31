package com.restrobill.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.TypeConverter

/** Room needs converters for the OrderStatus enum (stored as its name). */
class OrderStatusConverter {
    @TypeConverter
    fun fromStatus(status: OrderStatus): String = status.name

    @TypeConverter
    fun toStatus(name: String): OrderStatus = OrderStatus.valueOf(name)
}

@Database(
    entities = [MenuItem::class, RestaurantOrder::class, OrderLine::class, Bill::class, BillLine::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(OrderStatusConverter::class)
abstract class RestroDatabase : RoomDatabase() {
    abstract fun dao(): RestroDao

    companion object {
        @Volatile
        private var instance: RestroDatabase? = null

        fun get(context: Context): RestroDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RestroDatabase::class.java,
                    "restrobill.db"
                ).build().also { instance = it }
            }
        }
    }
}
