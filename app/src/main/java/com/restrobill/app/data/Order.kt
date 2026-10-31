package com.restrobill.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Order lifecycle. Occupancy is derived, never stored separately: a table
 *  is occupied exactly when it has an OPEN order. MERGED means this
 *  order's lines were moved into another table's order (merge tables);
 *  BILLED means it was closed by a bill. */
enum class OrderStatus { OPEN, BILLED, MERGED }

/** One running order for one table. [createdAt] is epoch millis;
 *  [dateIso] is the yyyy-MM-dd billing date used by the day summary. */
@Entity(tableName = "orders")
data class RestaurantOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableLabel: String,
    val status: OrderStatus = OrderStatus.OPEN,
    val createdAt: Long,
    val dateIso: String
)

/**
 * One dish on an order. [nameSnapshot] / [pricePaiseSnapshot] are copied
 * from the menu at add time, so later menu edits never rewrite a running
 * order. [kotPrinted] tracks which lines have already gone to the kitchen,
 * so re-printing a KOT only sends the new lines (plus a "REPRINT" header
 * when the waiter explicitly asks for a full reprint).
 */
@Entity(
    tableName = "order_lines",
    foreignKeys = [ForeignKey(
        entity = RestaurantOrder::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("orderId")]
)
data class OrderLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val menuItemId: Long,
    val nameSnapshot: String,
    val pricePaiseSnapshot: Long,
    val qty: Int,
    val note: String = "",
    val kotPrinted: Boolean = false
)

/** A closed bill. [tableLabel] is snapshotted so table-wise day totals
 *  survive even if the order row is later merged away. */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billNo: String,
    val tableLabel: String,
    val dateIso: String,
    val createdAt: Long,
    val subtotalPaise: Long,
    val discountPaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val paymentMode: String
)

/** Per-dish snapshot of a bill, so the day summary's item-wise sales and
 *  top dishes are computed from bills, never from mutable order lines. */
@Entity(
    tableName = "bill_lines",
    foreignKeys = [ForeignKey(
        entity = Bill::class,
        parentColumns = ["id"],
        childColumns = ["billId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("billId")]
)
data class BillLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val nameSnapshot: String,
    val qty: Int,
    val amountPaise: Long
)
