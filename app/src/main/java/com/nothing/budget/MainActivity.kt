package com.nothing.budget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var storage: Storage
    private lateinit var adapter: TransactionAdapter

    /**
     * The session currently being viewed. Null means "follow the active one".
     * When the user picks a closed session from the dropdown, this points to
     * that session. Set back to null (or to the active session's id) to view
     * the live data again.
     */
    private var viewedSessionId: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.READ_SMS] == true &&
                result[Manifest.permission.RECEIVE_SMS] == true
            if (!granted) {
                Toast.makeText(
                    this,
                    "SMS permission is required to track transactions",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                findViewById<View>(R.id.permission_banner).visibility = View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = Storage(this)

        viewedSessionId = storage.activeSession()?.id

        setupThemeToggle()

        findViewById<EditText>(R.id.input_budget).inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        findViewById<Button>(R.id.btn_save_budget).setOnClickListener { saveBudget() }
        findViewById<Button>(R.id.btn_edit_budget).setOnClickListener { switchBudgetToEditMode() }
        findViewById<Button>(R.id.btn_new_month).setOnClickListener { showNewMonthDialog() }
        findViewById<View>(R.id.btn_session_picker).setOnClickListener { showSessionPicker() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { confirmClearTransactions() }

        findViewById<Button>(R.id.btn_grant).setOnClickListener {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            ))
        }

        val list: RecyclerView = findViewById(R.id.recycler_transactions)
        list.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(emptyList()) { t -> showEditDialog(t) }
        list.adapter = adapter

        if (!hasSmsPermission()) {
            findViewById<View>(R.id.permission_banner).visibility = View.VISIBLE
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (hasSmsPermission()) {
            findViewById<View>(R.id.permission_banner).visibility = View.GONE
        }
        refresh()
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    // ===== Theme =====

    private fun setupThemeToggle() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle)
        val checkedId = when (storage.themeMode) {
            Storage.ThemeMode.AUTO -> R.id.theme_auto
            Storage.ThemeMode.LIGHT -> R.id.theme_light
            Storage.ThemeMode.DARK -> R.id.theme_dark
        }
        group.check(checkedId)
        group.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (buttonId) {
                R.id.theme_light -> Storage.ThemeMode.LIGHT
                R.id.theme_dark -> Storage.ThemeMode.DARK
                else -> Storage.ThemeMode.AUTO
            }
            if (mode != storage.themeMode) {
                storage.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode.toAppCompatMode())
            }
        }
    }

    // ===== Sessions =====

    /**
     * Returns the session the UI is currently showing. If the explicitly-selected
     * session is gone (e.g. deleted between sessions), falls back to the active
     * one. Returns null only if there are no sessions at all (first run).
     */
    private fun viewedSession(): Session? {
        val id = viewedSessionId
        if (id != null) {
            val s = storage.sessionById(id)
            if (s != null) return s
        }
        val active = storage.activeSession()
        viewedSessionId = active?.id
        return active
    }

    private fun showSessionPicker() {
        val sessions = storage.loadSessions()
        if (sessions.isEmpty()) return
        val items = sessions.map {
            val tag = if (it.isActive) " ●" else ""
            "${it.name}$tag\n${it.rangeLabel()}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("View month")
            .setItems(items) { _, which ->
                viewedSessionId = sessions[which].id
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewMonthDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_new_month, null)
        val budgetInput = view.findViewById<EditText>(R.id.input_new_budget)
        val active = storage.activeSession()
        if (active != null) budgetInput.setText(active.budget.toInt().toString())

        val message = if (active != null)
            "This will end '${active.name}' and start a fresh month."
        else
            "Enter your starting budget."

        val dialog = AlertDialog.Builder(this)
            .setTitle("Start new month")
            .setMessage(message)
            .setView(view)
            .setPositiveButton("Start", null) // overridden so we can validate
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = budgetInput.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(this, "Enter a valid budget", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val s = storage.startNewSession(v)
                viewedSessionId = s.id
                BudgetWidget.broadcastUpdate(this)
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ===== Budget edit toggle =====

    private fun switchBudgetToDisplayMode(budget: Double, currency: String) {
        findViewById<View>(R.id.budget_edit_mode).visibility = View.GONE
        findViewById<View>(R.id.budget_display_mode).visibility = View.VISIBLE
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        findViewById<TextView>(R.id.budget_display_value).text =
            "$currency ${nf.format(budget)}"
    }

    private fun switchBudgetToEditMode() {
        findViewById<View>(R.id.budget_display_mode).visibility = View.GONE
        findViewById<View>(R.id.budget_edit_mode).visibility = View.VISIBLE
        val budgetInput = findViewById<EditText>(R.id.input_budget)
        val session = viewedSession()
        if (session != null) {
            budgetInput.setText(session.budget.toInt().toString())
        } else {
            budgetInput.setText("")
        }
        budgetInput.requestFocus()
        budgetInput.setSelection(budgetInput.text?.length ?: 0)
    }

    /**
     * Saves the budget. If no active session exists, this creates the very
     * first session — that's how the initial setup works. Otherwise it just
     * updates the budget on the currently-viewed session.
     */
    private fun saveBudget() {
        val v = findViewById<EditText>(R.id.input_budget).text.toString().toDoubleOrNull()
        if (v == null || v <= 0) {
            Toast.makeText(this, "Enter a valid budget", Toast.LENGTH_SHORT).show()
            return
        }
        val viewed = viewedSession()
        if (viewed == null) {
            val created = storage.startNewSession(v)
            viewedSessionId = created.id
        } else {
            storage.updateSession(viewed.copy(budget = v))
        }
        BudgetWidget.broadcastUpdate(this)
        refresh()
    }

    // ===== Transactions =====

    private fun showEditDialog(transaction: Transaction) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_transaction, null)
        val typeGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.edit_type_group)
        val amountEdit = view.findViewById<EditText>(R.id.edit_amount)
        val descEdit = view.findViewById<EditText>(R.id.edit_description)

        typeGroup.check(
            if (transaction.type == Transaction.Type.EXPENSE) R.id.edit_type_expense
            else R.id.edit_type_income
        )
        amountEdit.setText(
            if (transaction.amount % 1.0 == 0.0) transaction.amount.toLong().toString()
            else transaction.amount.toString()
        )
        descEdit.setText(transaction.description)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit transaction")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNeutralButton("Delete", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newAmount = amountEdit.text.toString().toDoubleOrNull()
                if (newAmount == null || newAmount <= 0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val newType = if (typeGroup.checkedButtonId == R.id.edit_type_income)
                    Transaction.Type.INCOME else Transaction.Type.EXPENSE
                val newDesc = descEdit.text.toString().trim().ifEmpty { "—" }

                storage.updateTransaction(
                    transaction.copy(
                        amount = newAmount,
                        type = newType,
                        description = newDesc
                    )
                )
                BudgetWidget.broadcastUpdate(this)
                refresh()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete this transaction?")
                    .setMessage(transaction.description)
                    .setPositiveButton("Delete") { _, _ ->
                        storage.deleteTransaction(transaction.id)
                        BudgetWidget.broadcastUpdate(this)
                        refresh()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun confirmClearTransactions() {
        val session = viewedSession() ?: return
        AlertDialog.Builder(this)
            .setTitle("Clear all transactions?")
            .setMessage("This removes every transaction in '${session.name}'. The budget stays.")
            .setPositiveButton("Clear") { _, _ ->
                storage.clearTransactions(session.id)
                BudgetWidget.broadcastUpdate(this)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== UI refresh =====

    private fun refresh() {
        val session = viewedSession()
        val sessions = storage.loadSessions()

        // Session picker label
        val pickerLabel = findViewById<TextView>(R.id.session_picker_label)
        pickerLabel.text = when {
            session == null -> "No active month"
            !session.isActive -> "${session.name} (ended) ▾"
            else -> "${session.name} ▾"
        }
        // Hide the picker if there's only one session — nothing to switch to.
        findViewById<View>(R.id.btn_session_picker).visibility = when {
            session == null -> View.GONE
            sessions.size > 1 -> View.VISIBLE
            else -> View.VISIBLE
        }

        // "+ New month" button shows once a session exists; before any session
        // has been created, the user creates the first one via the budget Save
        // button — so suppress the "+ New" button for first-run clarity.
        findViewById<View>(R.id.btn_new_month).visibility =
            if (session != null) View.VISIBLE else View.GONE

        if (session == null) {
            // First run — start in budget edit mode. Saving creates the first session.
            switchBudgetToEditMode()
            findViewById<TextView>(R.id.summary_remaining).text = "Set a budget"
            findViewById<TextView>(R.id.summary_spent).text = "−"
            findViewById<TextView>(R.id.summary_received).text = "+"
            adapter.submit(emptyList())
            return
        }

        switchBudgetToDisplayMode(session.budget, session.currency)

        val txns = storage.transactionsFor(session.id)
        val spent = txns.filter { it.type == Transaction.Type.EXPENSE }.sumOf { it.amount }
        val received = txns.filter { it.type == Transaction.Type.INCOME }.sumOf { it.amount }
        val remaining = session.budget + received - spent

        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        findViewById<TextView>(R.id.summary_remaining).text =
            "${session.currency} ${nf.format(remaining)}"
        findViewById<TextView>(R.id.summary_spent).text = "− ${nf.format(spent)}"
        findViewById<TextView>(R.id.summary_received).text = "+ ${nf.format(received)}"

        adapter.submit(txns)
    }
}
