package com.nothing.budget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale

class TransactionListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TransactionListFactory(applicationContext)
}

private class TransactionListFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<Transaction> = emptyList()
    private val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreate() {}
    override fun onDestroy() { items = emptyList() }

    override fun onDataSetChanged() {
        val storage = Storage(context)
        val session = storage.activeSession()
        items = if (session == null) emptyList() else storage.transactionsFor(session.id)
    }

    override fun getCount(): Int = items.size
    override fun hasStableIds(): Boolean = true
    override fun getItemId(position: Int): Long = items[position].id
    override fun getViewTypeCount(): Int = 1
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val t = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_transaction_item)
        rv.setTextViewText(R.id.txn_label, t.description)
        val sign = if (t.type == Transaction.Type.EXPENSE) "− " else "+ "
        rv.setTextViewText(R.id.txn_amount, "$sign${nf.format(t.amount)}")

        // Widget tracks the SYSTEM UI mode, not the in-app theme override.
        val colorRes = if (t.type == Transaction.Type.EXPENSE) R.color.expense else R.color.income
        rv.setTextColor(R.id.txn_amount, resolveSystemColor(context, colorRes))
        return rv
    }

    private fun resolveSystemColor(ctx: Context, colorRes: Int): Int {
        val systemNight = (Resources.getSystem().configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cfg = Configuration(ctx.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (systemNight) Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = ctx.createConfigurationContext(cfg)
        return ContextCompat.getColor(themed, colorRes)
    }
}
