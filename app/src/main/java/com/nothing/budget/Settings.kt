package com.nothing.budget

data class Settings(
    val budget: Double,
    val defaultCurrency: String,
    val expenseKeywords: List<String>,
    val incomeKeywords: List<String>
) {
    companion object {
        // Order matters: more-specific phrases come first so the parser tries them
        // before falling back to single words. The parser also sorts by length to
        // guarantee the longest match wins (e.g. "transferred to your account"
        // beats a partial "transferred to" match).
        private val DEFAULT_EXPENSE_KEYWORDS = listOf(
            "debited from your account",
            "deducted from your account",
            "transferred from your account",
            "withdrawn from your",
            "transferred from your",
            "you have paid",
            "you paid",
            "you spent",
            "you have spent",
            "purchase of",
            "payment of",
            "payment to",
            "spent at",
            "paid at",
            "sent to",
            "transferred from",
            "deducted",
            "debited",
            "withdrawn",
            "charged",
            "purchase",
            "spent",
            "paid"
        )

        private val DEFAULT_INCOME_KEYWORDS = listOf(
            "credited to your account",
            "transferred to your account",
            "added to your account",
            "deposited to your",
            "credited to your",
            "transferred to your",
            "you have received",
            "you received",
            "received from",
            "refund of",
            "transferred to",
            "credited to",
            "credited",
            "deposited",
            "received"
        )

        val DEFAULT = Settings(
            budget = 3000.0,
            defaultCurrency = "AED",
            expenseKeywords = DEFAULT_EXPENSE_KEYWORDS,
            incomeKeywords = DEFAULT_INCOME_KEYWORDS
        )
    }
}
