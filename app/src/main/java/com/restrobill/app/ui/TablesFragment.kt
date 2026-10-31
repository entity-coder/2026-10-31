package com.restrobill.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.restrobill.app.data.RestroDatabase
import com.restrobill.app.data.RestaurantOrder
import com.restrobill.app.data.formatRupees
import com.restrobill.app.data.lineTotalPaise
import com.restrobill.app.data.todayIso
import com.restrobill.app.databinding.FragmentTablesBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The ten-table floor view. Occupancy is derived from OPEN orders — there
 *  is no separate occupied flag to drift out of sync. Tapping a free table
 *  starts an order; tapping an occupied table opens it. */
class TablesFragment : Fragment() {
    private var _binding: FragmentTablesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TableAdapter

    companion object {
        val TABLE_LABELS = (1..10).map { "T$it" }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTablesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TableAdapter(emptyList(), emptyMap()) { label, order ->
            if (order == null) {
                // Start a fresh order, then open it.
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = RestroDatabase.get(requireContext()).dao()
                    val id = dao.insertOrder(
                        RestaurantOrder(
                            tableLabel = label,
                            createdAt = System.currentTimeMillis(),
                            dateIso = todayIso()
                        )
                    )
                    openOrder(id)
                }
            } else {
                openOrder(order.id)
            }
        }
        binding.tablesList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.tablesList.adapter = adapter

        val dao = RestroDatabase.get(requireContext()).dao()
        viewLifecycleOwner.lifecycleScope.launch {
            // Running totals need both open orders and their lines. The
            // lines Flow never completes, so take a one-shot snapshot with
            // first() per order (small data set: at most 10 open orders).
            combine(dao.openOrders(), dao.billsForDate(todayIso())) { orders, bills ->
                orders to bills
            }.collect { (orders, bills) ->
                val byTable = orders.associateBy { it.tableLabel }
                val cells = TABLE_LABELS.map { label -> label to byTable[label] }
                // Header: occupied tables + today's billed revenue.
                val revenue = bills.sumOf { it.totalPaise }
                binding.headerStats.text =
                    "${orders.size} of 10 tables occupied • Today's billed: ${formatRupees(revenue)}"
                val totals = mutableMapOf<Long, Long>()
                for (order in orders) {
                    val lines = dao.linesForOrder(order.id).first()
                    totals[order.id] = lines.sumOf { lineTotalPaise(it.pricePaiseSnapshot, it.qty) }
                }
                adapter.update(cells, totals)
            }
        }
    }

    private fun openOrder(orderId: Long) {
        val intent = Intent(requireContext(), OrderActivity::class.java)
        intent.putExtra(OrderActivity.EXTRA_ORDER_ID, orderId)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
