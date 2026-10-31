package com.restrobill.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.restrobill.app.data.Bill
import com.restrobill.app.data.BillLine
import com.restrobill.app.data.DiscountType
import com.restrobill.app.data.OrderLine
import com.restrobill.app.data.OrderStatus
import com.restrobill.app.data.RestroDatabase
import com.restrobill.app.data.RestaurantOrder
import com.restrobill.app.data.ShopPrefs
import com.restrobill.app.data.billNo
import com.restrobill.app.data.computeBill
import com.restrobill.app.data.formatRupees
import com.restrobill.app.data.lineTotalPaise
import com.restrobill.app.data.todayIso
import com.restrobill.app.databinding.ActivityOrderBinding
import com.restrobill.app.databinding.DialogBillBinding
import com.restrobill.app.databinding.DialogLineEditBinding
import com.restrobill.app.databinding.DialogPickDishBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One running order: add dishes, adjust quantities, add notes, send KOTs,
 *  merge tables, split lines to another table, and bill the table. */
class OrderActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }

    private lateinit var binding: ActivityOrderBinding
    private lateinit var adapter: OrderLineAdapter
    private var order: RestaurantOrder? = null
    private var lines: List<OrderLine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1)
        if (orderId < 0) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        adapter = OrderLineAdapter(
            emptyList(),
            onClick = { showLineEditDialog(it) },
            onPlus = { changeQty(it, it.qty + 1) },
            onMinus = { if (it.qty > 1) changeQty(it, it.qty - 1) }
        )
        binding.linesList.layoutManager = LinearLayoutManager(this)
        binding.linesList.adapter = adapter

        val dao = RestroDatabase.get(this).dao()
        lifecycleScope.launch {
            dao.orderById(orderId).collect { o ->
                order = o
                if (o == null) {
                    finish()
                    return@collect
                }
                binding.orderTitle.text = "Table ${o.tableLabel} — running order"
            }
        }
        lifecycleScope.launch {
            dao.linesForOrder(orderId).collect { ls ->
                lines = ls
                adapter.update(ls)
                val total = ls.sumOf { lineTotalPaise(it.pricePaiseSnapshot, it.qty) }
                binding.orderTotal.text = "Running total: ${formatRupees(total)}"
            }
        }

        binding.addItemButton.setOnClickListener { showPickDishDialog(orderId) }
        binding.kotButton.setOnClickListener { sendKot(reprint = false) }
        binding.billButton.setOnClickListener { showBillDialog(orderId) }
        binding.mergeButton.setOnClickListener { showMergeDialog(orderId) }
        binding.splitButton.setOnClickListener { showSplitDialog(orderId) }
    }

    private fun changeQty(line: OrderLine, newQty: Int) {
        lifecycleScope.launch {
            RestroDatabase.get(this@OrderActivity).dao().updateLine(line.copy(qty = newQty))
        }
    }

    /** Dish picker: tapping + adds one portion. An existing line for the
     *  same dish grows by one instead of duplicating the row. */
    private fun showPickDishDialog(orderId: Long) {
        val dialogBinding = DialogPickDishBinding.inflate(LayoutInflater.from(this))
        val pickAdapter = PickDishAdapter(emptyList()) { dish ->
            lifecycleScope.launch {
                val dao = RestroDatabase.get(this@OrderActivity).dao()
                val existing = dao.linesForOrder(orderId).first()
                    .find { it.menuItemId == dish.id }
                if (existing != null) {
                    dao.updateLine(existing.copy(qty = existing.qty + 1))
                } else {
                    dao.insertLine(
                        OrderLine(
                            orderId = orderId, menuItemId = dish.id,
                            nameSnapshot = dish.name, pricePaiseSnapshot = dish.pricePaise,
                            qty = 1
                        )
                    )
                }
            }
            Toast.makeText(this, "${dish.name} added", Toast.LENGTH_SHORT).show()
        }
        dialogBinding.pickList.layoutManager = LinearLayoutManager(this)
        dialogBinding.pickList.adapter = pickAdapter
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add dish (tap + for one portion)")
            .setView(dialogBinding.root)
            .setNegativeButton("Done", null)
            .create()
        lifecycleScope.launch {
            RestroDatabase.get(this@OrderActivity).dao().activeMenuItems()
                .collect { pickAdapter.update(it) }
        }
        dialog.show()
    }

    /** Edit quantity / kitchen note, or remove the line entirely. */
    private fun showLineEditDialog(line: OrderLine) {
        val dialogBinding = DialogLineEditBinding.inflate(LayoutInflater.from(this))
        dialogBinding.qtyInput.setText(line.qty.toString())
        dialogBinding.noteInput.setText(line.note)
        AlertDialog.Builder(this)
            .setTitle(line.nameSnapshot)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val qty = dialogBinding.qtyInput.text.toString().toIntOrNull()
                if (qty == null || qty <= 0) {
                    Toast.makeText(this, "Quantity must be at least 1", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    RestroDatabase.get(this@OrderActivity).dao().updateLine(
                        line.copy(
                            qty = qty,
                            note = dialogBinding.noteInput.text.toString().trim()
                        )
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Remove line") { _, _ ->
                lifecycleScope.launch {
                    RestroDatabase.get(this@OrderActivity).dao().deleteLine(line.id)
                }
            }
            .show()
    }

    /**
     * KOT: normally only lines not yet sent to the kitchen are printed, so
     * the kitchen never cooks a dish twice. When nothing is new, offer a
     * full reprint instead of silently printing an empty ticket.
     */
    private fun sendKot(reprint: Boolean) {
        val current = order ?: return
        val toPrint = if (reprint) lines else lines.filter { !it.kotPrinted }
        if (toPrint.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nothing new for the kitchen")
                .setMessage("All lines have already been sent. Reprint the full ticket?")
                .setPositiveButton("Reprint all") { _, _ -> sendKot(reprint = true) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val prefs = ShopPrefs(this)
        val file = buildKotPdf(this, prefs, current.tableLabel, toPrint, reprint)
        lifecycleScope.launch {
            RestroDatabase.get(this@OrderActivity).dao().markAllKotPrinted(current.id)
        }
        showPrintShareDialog(file, "KOT — Table ${current.tableLabel}")
    }

    /** Billing: discount (percent or flat, capped) then GST on the
     *  discounted subtotal. The bill + snapshot lines are stored, the
     *  order is marked BILLED, and the table frees up. */
    private fun showBillDialog(orderId: Long) {
        if (lines.isEmpty()) {
            Toast.makeText(this, "Add at least one dish before billing", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogBillBinding.inflate(LayoutInflater.from(this))
        val modes = arrayOf("Cash", "UPI", "Card", "Bank Transfer")
        dialogBinding.paymentSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        val prefs = ShopPrefs(this)
        val subtotal = lines.sumOf { lineTotalPaise(it.pricePaiseSnapshot, it.qty) }
        dialogBinding.billPreview.text =
            "Subtotal: ${formatRupees(subtotal)} • GST ${prefs.gstPercent}% on discounted total"
        AlertDialog.Builder(this)
            .setTitle("Bill table")
            .setView(dialogBinding.root)
            .setPositiveButton("Bill & close table") { _, _ ->
                val type = if (dialogBinding.discountPercentRadio.isChecked) {
                    DiscountType.PERCENT
                } else {
                    DiscountType.FLAT
                }
                val rawValue = dialogBinding.discountValueInput.text.toString()
                val discountValue = when (type) {
                    DiscountType.PERCENT -> rawValue.toLongOrNull() ?: 0L
                    DiscountType.FLAT -> {
                        // Flat input is rupees; convert to paise.
                        val rupees = rawValue.toDoubleOrNull() ?: 0.0
                        (rupees * 100).toLong()
                    }
                }
                val totals = computeBill(lines, type, discountValue, prefs.gstPercent)
                val mode = dialogBinding.paymentSpinner.selectedItem as String
                lifecycleScope.launch {
                    val dao = RestroDatabase.get(this@OrderActivity).dao()
                    val no = billNo(dao.billCount())
                    val billId = dao.insertBill(
                        Bill(
                            billNo = no,
                            tableLabel = order?.tableLabel ?: "?",
                            dateIso = todayIso(),
                            createdAt = System.currentTimeMillis(),
                            subtotalPaise = totals.subtotalPaise,
                            discountPaise = totals.discountPaise,
                            gstPaise = totals.cgstPaise + totals.sgstPaise,
                            totalPaise = totals.totalPaise,
                            paymentMode = mode
                        )
                    )
                    dao.insertBillLines(
                        lines.map {
                            BillLine(
                                billId = billId, nameSnapshot = it.nameSnapshot,
                                qty = it.qty,
                                amountPaise = lineTotalPaise(it.pricePaiseSnapshot, it.qty)
                            )
                        }
                    )
                    order?.let { dao.updateOrder(it.copy(status = OrderStatus.BILLED)) }
                    val file = buildBillPdf(
                        this@OrderActivity, prefs,
                        Bill(
                            id = billId, billNo = no,
                            tableLabel = order?.tableLabel ?: "?",
                            dateIso = todayIso(), createdAt = System.currentTimeMillis(),
                            subtotalPaise = totals.subtotalPaise,
                            discountPaise = totals.discountPaise,
                            gstPaise = totals.cgstPaise + totals.sgstPaise,
                            totalPaise = totals.totalPaise, paymentMode = mode
                        ),
                        lines.map {
                            BillLine(
                                billId = billId, nameSnapshot = it.nameSnapshot,
                                qty = it.qty,
                                amountPaise = lineTotalPaise(it.pricePaiseSnapshot, it.qty)
                            )
                        },
                        totals, prefs.gstPercent
                    )
                    showPrintShareDialog(file, "Bill $no")
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Merge: move every line of this order into another open table's
     *  order, then mark this order MERGED (it disappears from the floor). */
    private fun showMergeDialog(orderId: Long) {
        lifecycleScope.launch {
            val dao = RestroDatabase.get(this@OrderActivity).dao()
            val others = dao.openOrders().first()
                .filter { it.id != orderId }
            if (others.isEmpty()) {
                Toast.makeText(
                    this@OrderActivity, "No other open table to merge into",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val labels = others.map { it.tableLabel }.toTypedArray()
            AlertDialog.Builder(this@OrderActivity)
                .setTitle("Merge into which table?")
                .setItems(labels) { _, which ->
                    val target = others[which]
                    lifecycleScope.launch {
                        dao.moveLines(lines.map { it.id }, target.id)
                        order?.let { dao.updateOrder(it.copy(status = OrderStatus.MERGED)) }
                        Toast.makeText(
                            this@OrderActivity,
                            "Merged into ${target.tableLabel}", Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Split: pick lines, pick a free table, move them to a new order
     *  there. The source order keeps its remaining lines. */
    private fun showSplitDialog(orderId: Long) {
        if (lines.isEmpty()) {
            Toast.makeText(this, "Nothing to split", Toast.LENGTH_SHORT).show()
            return
        }
        val names = lines.map { "${it.qty} x ${it.nameSnapshot}" }.toTypedArray()
        val checked = BooleanArray(lines.size)
        AlertDialog.Builder(this)
            .setTitle("Split which lines?")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Next") { _, _ ->
                val chosen = lines.filterIndexed { i, _ -> checked[i] }
                if (chosen.isEmpty() || chosen.size == lines.size) {
                    Toast.makeText(
                        this,
                        "Pick some — but not all — lines to split",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                pickSplitTarget(orderId, chosen)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickSplitTarget(orderId: Long, chosen: List<OrderLine>) {
        lifecycleScope.launch {
            val dao = RestroDatabase.get(this@OrderActivity).dao()
            val occupied = dao.openOrders().first().map { it.tableLabel }.toSet()
            val free = TablesFragment.TABLE_LABELS.filter { it !in occupied }
            if (free.isEmpty()) {
                Toast.makeText(
                    this@OrderActivity, "No free table to split into",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            AlertDialog.Builder(this@OrderActivity)
                .setTitle("Split to which table?")
                .setItems(free.toTypedArray()) { _, which ->
                    lifecycleScope.launch {
                        val newId = dao.insertOrder(
                            RestaurantOrder(
                                tableLabel = free[which],
                                createdAt = System.currentTimeMillis(),
                                dateIso = todayIso()
                            )
                        )
                        dao.moveLines(chosen.map { it.id }, newId)
                        Toast.makeText(
                            this@OrderActivity,
                            "Split to ${free[which]}", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showPrintShareDialog(file: java.io.File, jobName: String) {
        AlertDialog.Builder(this)
            .setTitle(jobName)
            .setMessage("PDF ready. Print it or share it (e.g. WhatsApp).")
            .setPositiveButton("Print") { _, _ -> PdfHelper.printPdf(this, file, jobName) }
            .setNeutralButton("Share") { _, _ -> PdfHelper.sharePdf(this, file, jobName) }
            .setNegativeButton("Done", null)
            .show()
    }
}
