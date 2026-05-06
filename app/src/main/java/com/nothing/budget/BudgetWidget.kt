package com.nothing.budget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

class BudgetWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> renderWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val isRelevant = intent.action == ACTION_DATA_CHANGED ||
            intent.action == Intent.ACTION_CONFIGURATION_CHANGED
        if (isRelevant) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, BudgetWidget::class.java))
            ids.forEach { renderWidget(context, mgr, it) }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }

    private fun renderWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val storage = Storage(context)
        val session = storage.activeSession()
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }

        if (session == null) {
            // No active session: invite the user to set one up.
            views.setTextViewText(R.id.widget_remaining_amount, "—")
            views.setTextViewText(R.id.widget_session_label, "OPEN APP TO START")
            views.setTextViewText(R.id.widget_spent, "− 0")
            views.setTextViewText(R.id.widget_received, "+ 0")
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
        } else {
            val txns = storage.transactionsFor(session.id)
            val spent = txns.filter { it.type == Transaction.Type.EXPENSE }.sumOf { it.amount }
            val received = txns.filter { it.type == Transaction.Type.INCOME }.sumOf { it.amount }
            val remaining = session.budget + received - spent
            val pct = if (session.budget > 0) min(1.0, spent / session.budget) else 0.0

            views.setTextViewText(R.id.widget_remaining_amount,
                "${session.currency} ${nf.format(remaining)}")
            views.setTextViewText(R.id.widget_session_label, session.name.uppercase())
            views.setTextViewText(R.id.widget_spent, "− ${nf.format(spent)}")
            views.setTextViewText(R.id.widget_received, "+ ${nf.format(received)}")
            views.setProgressBar(R.id.widget_progress, 100, (pct * 100).toInt(), false)
        }

        // Each widget instance needs its own RemoteAdapter binding — the data
        // URI is built from the intent's encoded form (which includes the
        // appWidgetId extra) so it's unique per instance. Without this the
        // launcher silently re-uses one adapter across all widgets and the
        // list never populates on the home screen.
        val serviceIntent = Intent(context, TransactionListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_list_empty)

        // Tapping anywhere on the widget opens the app.
        val openApp = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_DATA_CHANGED = "com.nothing.budget.ACTION_DATA_CHANGED"

        fun broadcastUpdate(context: Context) {
            val intent = Intent(context, BudgetWidget::class.java).apply {
                action = ACTION_DATA_CHANGED
            }
            context.sendBroadcast(intent)
        }
    }
}
