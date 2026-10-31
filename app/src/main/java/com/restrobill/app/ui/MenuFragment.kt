package com.restrobill.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.restrobill.app.data.MenuItem
import com.restrobill.app.data.RestroDatabase
import com.restrobill.app.data.parseRupeesToPaise
import com.restrobill.app.databinding.DialogMenuItemBinding
import com.restrobill.app.databinding.FragmentMenuBinding
import kotlinx.coroutines.launch

/** Menu management: search, add, edit, and deactivate dishes. Deactivation
 *  (not deletion) keeps old bills and running orders intact — a deleted
 *  dish must never rewrite history. */
class MenuFragment : Fragment() {
    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MenuAdapter
    private var allItems: List<MenuItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = MenuAdapter(emptyList()) { item -> showEditDialog(item) }
        binding.menuList.layoutManager = LinearLayoutManager(requireContext())
        binding.menuList.adapter = adapter

        binding.searchBox.doAfterTextChanged { applyFilter() }
        binding.fabAdd.setOnClickListener { showEditDialog(null) }

        val dao = RestroDatabase.get(requireContext()).dao()
        viewLifecycleOwner.lifecycleScope.launch {
            dao.allMenuItems().collect { items ->
                allItems = items
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val q = binding.searchBox.text.toString().trim().lowercase()
        adapter.update(
            if (q.isEmpty()) allItems
            else allItems.filter {
                it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
            }
        )
    }

    private fun showEditDialog(existing: MenuItem?) {
        val dialogBinding = DialogMenuItemBinding.inflate(layoutInflater)
        if (existing != null) {
            dialogBinding.nameInput.setText(existing.name)
            dialogBinding.categoryInput.setText(existing.category)
            dialogBinding.priceInput.setText("%.2f".format(existing.pricePaise / 100.0))
            dialogBinding.vegCheck.isChecked = existing.isVeg
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Add dish" else "Edit dish")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null) // overridden below to validate
            .setNegativeButton("Cancel", null)
            .apply {
                if (existing != null && existing.active) {
                    setNeutralButton("Deactivate") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            RestroDatabase.get(requireContext()).dao()
                                .updateMenuItem(existing.copy(active = false))
                        }
                        Toast.makeText(requireContext(), "Dish deactivated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.nameInput.text.toString().trim()
            val category = dialogBinding.categoryInput.text.toString().trim()
                .ifEmpty { "Other" }
            val price = parseRupeesToPaise(dialogBinding.priceInput.text.toString())
            if (name.isEmpty() || price == null || price <= 0) {
                Toast.makeText(
                    requireContext(),
                    "Enter a name and a price above zero", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val dao = RestroDatabase.get(requireContext()).dao()
                if (existing == null) {
                    dao.insertMenuItem(
                        MenuItem(
                            name = name, category = category,
                            pricePaise = price, isVeg = dialogBinding.vegCheck.isChecked
                        )
                    )
                } else {
                    dao.updateMenuItem(
                        existing.copy(
                            name = name, category = category,
                            pricePaise = price, isVeg = dialogBinding.vegCheck.isChecked
                        )
                    )
                }
            }
            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
