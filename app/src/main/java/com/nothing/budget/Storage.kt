package com.nothing.budget

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence layer.
 *
 * Two collections live in SharedPreferences as JSON:
 *   - sessions: list of [Session]. At most one active (endedAt == null).
 *   - transactions: list of [Transaction], each tagged with sessionId.
 *
 * Pre-session transactions from older app versions are silently dropped on
 * load (they have no sessionId), so an update from the previous version
 * effectively wipes the historical inbox scan and starts fresh.
 */
class Storage(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class ThemeMode(val key: String) {
        AUTO("auto"), LIGHT("light"), DARK("dark");

        fun toAppCompatMode(): Int = when (this) {
            AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }

        companion object {
            fun fromKey(k: String?): ThemeMode = values().firstOrNull { it.key == k } ?: AUTO
        }
    }

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString(KEY_THEME, ThemeMode.AUTO.key))
        set(v) { prefs.edit().putString(KEY_THEME, v.key).apply() }

    var defaultCurrency: String
        get() = prefs.getString(KEY_CURRENCY, "AED") ?: "AED"
        set(v) { prefs.edit().putString(KEY_CURRENCY, v).apply() }

    // ===== Sessions =====

    fun loadSessions(): List<Session> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val list = ArrayList<Session>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list += Session(
                id = o.optString("id"),
                name = o.optString("name", "Month"),
                budget = o.optDouble("budget", 0.0),
                currency = o.optString("currency", "AED"),
                startedAt = o.optLong("startedAt"),
                endedAt = if (o.has("endedAt") && !o.isNull("endedAt")) o.optLong("endedAt") else null
            )
        }
        return list.sortedByDescending { it.startedAt }
    }

    fun saveSessions(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("budget", s.budget)
                put("currency", s.currency)
                put("startedAt", s.startedAt)
                if (s.endedAt != null) put("endedAt", s.endedAt) else put("endedAt", JSONObject.NULL)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun activeSession(): Session? = loadSessions().firstOrNull { it.isActive }

    fun sessionById(id: String): Session? = loadSessions().firstOrNull { it.id == id }

    /**
     * Ends the currently-active session (if any) and creates a new active
     * session with the given budget. Returns the new session.
     */
    fun startNewSession(budget: Double, currency: String? = null, name: String? = null): Session {
        val now = System.currentTimeMillis()
        val sessions = loadSessions().toMutableList()

        // End the active one (most recent un-ended session).
        val activeIdx = sessions.indexOfFirst { it.isActive }
        if (activeIdx >= 0) {
            sessions[activeIdx] = sessions[activeIdx].copy(endedAt = now)
        }

        // Choose a name. If the user didn't supply one, build "MMM yyyy" and
        // disambiguate against any sessions that already have that name.
        val baseName = (name?.takeIf { it.isNotBlank() }) ?: Session.defaultNameForNow()
        val finalName = if (sessions.none { it.name == baseName }) baseName else {
            var n = 2
            while (sessions.any { it.name == "$baseName ($n)" }) n++
            "$baseName ($n)"
        }

        val new = Session(
            name = finalName,
            budget = budget,
            currency = currency ?: defaultCurrency,
            startedAt = now,
            endedAt = null
        )
        sessions.add(new)
        saveSessions(sessions)
        return new
    }

    fun updateSession(updated: Session) {
        val list = loadSessions().toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            list[idx] = updated
            saveSessions(list)
        }
    }

    /**
     * End the currently-active session without starting a new one. Leaves the
     * app in a "no active session" state — the user has to explicitly start
     * a new session to begin recording again.
     */
    fun endActiveSession() {
        val list = loadSessions().toMutableList()
        val idx = list.indexOfFirst { it.isActive }
        if (idx >= 0) {
            list[idx] = list[idx].copy(endedAt = System.currentTimeMillis())
            saveSessions(list)
        }
    }

    // ===== Transactions =====

    fun loadTransactions(): List<Transaction> {
        val raw = prefs.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val list = ArrayList<Transaction>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            // Drop pre-session entries (older app versions stored these without
            // a sessionId — they're stale and get cleaned up on the next save).
            val sessionId = o.optString("sessionId", "")
            if (sessionId.isBlank()) continue
            list += Transaction(
                id = o.optLong("id"),
                amount = o.optDouble("amount", 0.0),
                currency = o.optString("currency", "AED"),
                description = o.optString("description", "—"),
                timestamp = o.optLong("timestamp"),
                type = if (o.optString("type") == "INCOME") Transaction.Type.INCOME else Transaction.Type.EXPENSE,
                rawSms = o.optString("rawSms", ""),
                sessionId = sessionId
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveTransactions(list: List<Transaction>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("amount", t.amount)
                put("currency", t.currency)
                put("description", t.description)
                put("timestamp", t.timestamp)
                put("type", t.type.name)
                put("rawSms", t.rawSms)
                put("sessionId", t.sessionId)
            })
        }
        prefs.edit().putString(KEY_TRANSACTIONS, arr.toString()).apply()
    }

    fun transactionsFor(sessionId: String): List<Transaction> =
        loadTransactions().filter { it.sessionId == sessionId }

    fun addTransaction(t: Transaction): Boolean {
        val existing = loadTransactions()
        if (existing.any { it.id == t.id }) return false
        saveTransactions(existing + t)
        return true
    }

    fun updateTransaction(updated: Transaction) {
        val list = loadTransactions().toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            list[idx] = updated
            saveTransactions(list)
        }
    }

    fun deleteTransaction(id: Long) {
        val list = loadTransactions().filter { it.id != id }
        saveTransactions(list)
    }

    /** Removes every transaction belonging to [sessionId]. The session itself stays. */
    fun clearTransactions(sessionId: String) {
        val list = loadTransactions().filter { it.sessionId != sessionId }
        saveTransactions(list)
    }

    // ===== SmsParser settings =====

    /**
     * Builds a [Settings] for the parser. Budget here is purely informational
     * (the parser doesn't use it); currency falls back to the active session's
     * currency, then the global default.
     */
    fun loadSettings(): Settings = Settings(
        budget = activeSession()?.budget ?: 0.0,
        defaultCurrency = activeSession()?.currency ?: defaultCurrency,
        expenseKeywords = Settings.DEFAULT.expenseKeywords,
        incomeKeywords = Settings.DEFAULT.incomeKeywords
    )

    companion object {
        private const val PREFS = "nothing_budget_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_CURRENCY = "default_currency"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_TRANSACTIONS = "transactions"
    }
}
