package com.restrobill.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.restrobill.app.R
import com.restrobill.app.data.Bill
import com.restrobill.app.data.MenuItem
import com.restrobill.app.data.OrderLine
import com.restrobill.app.data.RestaurantOrder
import com.restrobill.app.data.formatRupees
import com.restrobill.app.data.lineTotalPaise

/** One grid cell per table (T1–T10). Occupied tables show their running total. */
class TableAdapter(
    private var tables: List<Pair<String, RestaurantOrder?>>,
    private var totals: Map<Long, Long>,
    private val onClick: (String, RestaurantOrder?) -> Unit
) : RecyclerView.Adapter<TableAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.table_label)
        val status: TextView = v.findViewById(R.id.table_status)
        val total: TextView = v.findViewById(R.id.table_total)
    }

    fun update(tables: List<Pair<String, RestaurantOrder?>>, totals: Map<Long, Long>) {
        this.tables = tables
        this.totals = totals
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_table, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = tables.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val (label, order) = tables[pos]
        h.label.text = label
        if (order == null) {
            h.status.text = "Free"
            h.total.text = ""
        } else {
            h.status.text = "Occupied"
            h.total.text = formatRupees(totals[order.id] ?: 0)
        }
        h.itemView.setOnClickListener { onClick(label, order) }
    }
}

/** Menu list row; tap to edit, deactivated dishes are greyed by the caller. */
class MenuAdapter(
    private var items: List<MenuItem>,
    private val onClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.menu_name)
        val meta: TextView = v.findViewById(R.id.menu_meta)
    }

    fun update(items: List<MenuItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val item = items[pos]
        h.name.text = item.name + if (item.active) "" else " (inactive)"
        h.meta.text = "${item.category} • ${if (item.isVeg) "Veg" else "Non-veg"} • ${formatRupees(item.pricePaise)}"
        h.itemView.alpha = if (item.active) 1f else 0.5f
        h.itemView.setOnClickListener { onClick(item) }
    }
}

/** Running-order lines with +/- quantity buttons and a KOT badge. */
class OrderLineAdapter(
    private var lines: List<OrderLine>,
    private val onClick: (OrderLine) -> Unit,
    private val onPlus: (OrderLine) -> Unit,
    private val onMinus: (OrderLine) -> Unit
) : RecyclerView.Adapter<OrderLineAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.line_name)
        val note: TextView = v.findViewById(R.id.line_note)
        val qty: TextView = v.findViewById(R.id.line_qty)
        val amount: TextView = v.findViewById(R.id.line_amount)
        val kot: TextView = v.findViewById(R.id.line_kot)
        val plus: View = v.findViewById(R.id.line_plus)
        val minus: View = v.findViewById(R.id.line_minus)
    }

    fun update(lines: List<OrderLine>) {
        this.lines = lines
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_line, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = lines.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val line = lines[pos]
        h.name.text = line.nameSnapshot
        h.note.text = line.note
        h.note.visibility = if (line.note.isBlank()) View.GONE else View.VISIBLE
        h.qty.text = "x${line.qty}"
        h.amount.text = formatRupees(lineTotalPaise(line.pricePaiseSnapshot, line.qty))
        h.kot.visibility = if (line.kotPrinted) View.VISIBLE else View.GONE
        h.itemView.setOnClickListener { onClick(line) }
        h.plus.setOnClickListener { onPlus(line) }
        h.minus.setOnClickListener { onMinus(line) }
    }
}

/** Dish picker used when adding to an order: tap + to add one portion. */
class PickDishAdapter(
    private var items: List<MenuItem>,
    private val onAdd: (MenuItem) -> Unit
) : RecyclerView.Adapter<PickDishAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.pick_name)
        val price: TextView = v.findViewById(R.id.pick_price)
        val add: View = v.findViewById(R.id.pick_add)
    }

    fun update(items: List<MenuItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pick_dish, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val item = items[pos]
        h.name.text = "${item.name} (${item.category})"
        h.price.text = formatRupees(item.pricePaise)
        h.add.setOnClickListener { onAdd(item) }
    }
}

/** Day-summary bill list. */
class BillAdapter(
    private var bills: List<Bill>
) : RecyclerView.Adapter<BillAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val no: TextView = v.findViewById(R.id.bill_no)
        val table: TextView = v.findViewById(R.id.bill_table)
        val total: TextView = v.findViewById(R.id.bill_total)
        val mode: TextView = v.findViewById(R.id.bill_mode)
    }

    fun update(bills: List<Bill>) {
        this.bills = bills
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bill, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = bills.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val bill = bills[pos]
        h.no.text = bill.billNo
        h.table.text = bill.tableLabel
        h.total.text = formatRupees(bill.totalPaise)
        h.mode.text = bill.paymentMode
    }
}
