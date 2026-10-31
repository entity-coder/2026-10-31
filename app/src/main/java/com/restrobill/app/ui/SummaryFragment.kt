package com.restrobill.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.restrobill.app.data.RestroDatabase
import com.restrobill.app.data.formatRupees
import com.restrobill.app.data.todayIso
import com.restrobill.app.databinding.FragmentSummaryBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Day summary for today: revenue, bill count, table-wise totals,
 *  item-wise sales, top 5 dishes, and the bill list. Everything is read
 *  from stored bills (never from mutable order lines), so the summary is
 *  a record of what was actually charged. */
class SummaryFragment : Fragment() {
    private var _binding: FragmentSummaryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: BillAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = BillAdapter(emptyList())
        binding.billsList.layoutManager = LinearLayoutManager(requireContext())
        binding.billsList.adapter = adapter
        binding.summaryDate.text = "Today: ${todayIso()}"

        val dao = RestroDatabase.get(requireContext()).dao()
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                dao.billsForDate(todayIso()),
                dao.billLinesForDate(todayIso())
            ) { bills, lines -> bills to lines }.collect { (bills, lines) ->
                adapter.update(bills)
                val revenue = bills.sumOf { it.totalPaise }
                binding.summaryTotals.text =
                    "${bills.size} bills • Revenue ${formatRupees(revenue)}"
                val byTable = bills.groupBy { it.tableLabel }
                    .entries.sortedBy { it.key }
                    .joinToString("\n") { (table, ts) ->
                        "$table: ${ts.size} bills, ${formatRupees(ts.sumOf { it.totalPaise })}"
                    }
                binding.tableWise.text =
                    if (byTable.isEmpty()) "No bills yet today" else byTable
                val byItem = lines.groupBy { it.nameSnapshot }
                    .map { (name, ls) -> name to (ls.sumOf { it.qty } to ls.sumOf { it.amountPaise }) }
                    .sortedByDescending { it.second.first }
                binding.itemWise.text = if (byItem.isEmpty()) "No items sold yet today" else
                    byItem.joinToString("\n") { (name, qtyAmt) ->
                        "$name: x${qtyAmt.first}, ${formatRupees(qtyAmt.second)}"
                    }
                binding.topDishes.text = if (byItem.isEmpty()) "—" else
                    byItem.take(5).joinToString("\n") { (name, qtyAmt) -> "• $name (x${qtyAmt.first})" }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
