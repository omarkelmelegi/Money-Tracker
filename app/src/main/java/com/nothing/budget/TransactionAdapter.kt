package com.nothing.budget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private var items: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.VH>() {

    private val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val df = SimpleDateFormat("dd MMM", Locale.US)

    fun submit(list: List<Transaction>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.label.text = t.description
        holder.date.text = df.format(Date(t.timestamp))
        val sign = if (t.type == Transaction.Type.EXPENSE) "− " else "+ "
        holder.amount.text = "$sign${nf.format(t.amount)}"
        val ctx = holder.itemView.context
        val colorRes = if (t.type == Transaction.Type.EXPENSE) R.color.expense else R.color.income
        holder.amount.setTextColor(ContextCompat.getColor(ctx, colorRes))
        holder.itemView.setOnClickListener { onItemClick(t) }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.txn_label)
        val date: TextView = v.findViewById(R.id.txn_date)
        val amount: TextView = v.findViewById(R.id.txn_amount)
    }
}
