package com.restrobill.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RestroDao {
    // ---- Menu ----
    @Query("SELECT * FROM menu_items ORDER BY category, name")
    fun allMenuItems(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE active = 1 ORDER BY category, name")
    fun activeMenuItems(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun menuItemById(id: Long): MenuItem?

    @Insert
    suspend fun insertMenuItem(item: MenuItem): Long

    @Update
    suspend fun updateMenuItem(item: MenuItem)

    // ---- Orders ----
    @Query("SELECT * FROM orders WHERE status = 'OPEN' ORDER BY createdAt")
    fun openOrders(): Flow<List<RestaurantOrder>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun orderById(id: Long): Flow<RestaurantOrder?>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun orderByIdOnce(id: Long): RestaurantOrder?

    @Insert
    suspend fun insertOrder(order: RestaurantOrder): Long

    @Update
    suspend fun updateOrder(order: RestaurantOrder)

    // ---- Order lines ----
    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY id")
    fun linesForOrder(orderId: Long): Flow<List<OrderLine>>

    @Insert
    suspend fun insertLine(line: OrderLine): Long

    @Update
    suspend fun updateLine(line: OrderLine)

    @Query("DELETE FROM order_lines WHERE id = :id")
    suspend fun deleteLine(id: Long)

    @Query("UPDATE order_lines SET orderId = :toOrderId WHERE id IN (:lineIds)")
    suspend fun moveLines(lineIds: List<Long>, toOrderId: Long)

    @Query("UPDATE order_lines SET kotPrinted = 1 WHERE orderId = :orderId")
    suspend fun markAllKotPrinted(orderId: Long)

    // ---- Bills ----
    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Insert
    suspend fun insertBillLines(lines: List<BillLine>)

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun billCount(): Int

    @Query("SELECT * FROM bills WHERE dateIso = :dateIso ORDER BY id DESC")
    fun billsForDate(dateIso: String): Flow<List<Bill>>

    @Query("SELECT * FROM bill_lines WHERE billId IN (SELECT id FROM bills WHERE dateIso = :dateIso)")
    fun billLinesForDate(dateIso: String): Flow<List<BillLine>>
}
