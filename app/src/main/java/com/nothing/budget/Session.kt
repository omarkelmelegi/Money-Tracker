package com.nothing.budget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * A budgeting period. The user explicitly opens a session by entering a
 * starting budget; that session collects transactions until the user starts
 * a new one, which automatically ends the current.
 *
 * Exactly zero or one session may be active (i.e. [endedAt] is null) at any
 * time. Past sessions are kept indefinitely so the user can switch back to
 * view them.
 */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val budget: Double,
    val currency: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
) {
    val isActive: Boolean get() = endedAt == null

    fun rangeLabel(): String {
        val df = SimpleDateFormat("MMM d", Locale.US)
        val start = df.format(Date(startedAt))
        val end = endedAt?.let { df.format(Date(it)) } ?: "now"
        return "$start – $end"
    }

    companion object {
        /** Default name for a freshly-started session: e.g. "May 2026". */
        fun defaultNameForNow(): String =
            SimpleDateFormat("MMM yyyy", Locale.US).format(Date())
    }
}
