package com.nothing.budget

/**
 * Parses an SMS body into a [Transaction], or returns null if it doesn't look
 * like a debit/credit message.
 *
 * Matching strategy:
 *   1. Combine expense + income keywords, sort by length descending. The longest
 *      matching phrase wins, so "transferred to your account" (income) beats a
 *      shorter "transferred from" (expense) even when both appear in the body.
 *   2. Require an amount with an adjacent currency token. This filters out
 *      unrelated SMS that happen to contain matching words.
 *   3. Description extraction tries multiple anchor patterns ("at", "to NAME",
 *      "from NAME", "for X"), then falls back to a cleaned snippet of the body.
 */
object SmsParser {

    private val CURRENCY_TOKENS = listOf(
        "AED", "USD", "EUR", "GBP", "SAR", "INR", "QAR", "OMR", "KWD",
        "BHD", "PKR", "EGP", "Rs"
    )
    private val CURRENCY_SYMBOLS = listOf("\\$", "£", "€", "₹", "د\\.إ")
    private val CURRENCY_ALT = (CURRENCY_TOKENS + CURRENCY_SYMBOLS).joinToString("|")

    private val AMOUNT_BEFORE = Regex(
        """(?:$CURRENCY_ALT)\.?\s*([\d,]+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_AFTER = Regex(
        """([\d,]+(?:\.\d+)?)\s*(?:$CURRENCY_ALT)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        body: String,
        smsId: Long,
        timestamp: Long,
        settings: Settings,
        sessionId: String
    ): Transaction? {
        val type = matchKeyword(body, settings) ?: return null
        val amount = extractAmount(body) ?: return null
        if (amount <= 0) return null
        val currency = extractCurrency(body) ?: settings.defaultCurrency
        val description = extractDescription(body, type)

        return Transaction(
            id = smsId,
            amount = amount,
            currency = currency,
            description = description,
            timestamp = timestamp,
            type = type,
            rawSms = body,
            sessionId = sessionId
        )
    }

    private fun matchKeyword(body: String, settings: Settings): Transaction.Type? {
        val lower = body.lowercase()
        val all = settings.expenseKeywords.map { it to Transaction.Type.EXPENSE } +
            settings.incomeKeywords.map { it to Transaction.Type.INCOME }
        return all
            .filter { (kw, _) -> kw.isNotBlank() && lower.contains(kw.lowercase()) }
            .maxByOrNull { (kw, _) -> kw.length }
            ?.second
    }

    private fun extractAmount(body: String): Double? {
        val raw = AMOUNT_BEFORE.find(body)?.groupValues?.getOrNull(1)
            ?: AMOUNT_AFTER.find(body)?.groupValues?.getOrNull(1)
            ?: return null
        return raw.replace(",", "").toDoubleOrNull()
    }

    private fun extractCurrency(body: String): String? {
        val regex = Regex("($CURRENCY_ALT)", RegexOption.IGNORE_CASE)
        val match = regex.find(body)?.value?.uppercase() ?: return null
        return when (match) {
            "$" -> "USD"
            "£" -> "GBP"
            "€" -> "EUR"
            "₹" -> "INR"
            "RS" -> "INR"
            "د.إ", "د\\.إ" -> "AED"
            else -> match
        }
    }

    private fun extractDescription(body: String, type: Transaction.Type): String {
        val stop = """(?:on|using|via|ref(?:erence)?|bal(?:ance)?|avl|valid|otp|date|time|info|dt)\b"""

        val orderedAnchors: List<Regex> = when (type) {
            Transaction.Type.EXPENSE -> listOf(
                Regex("""\bat\s+([^,.\n;:]+?)(?=\s+$stop|\s+\d{1,2}[-/:]|[.,;]|$)""", RegexOption.IGNORE_CASE),
                Regex("""\bto\s+(?!your\b)([^,.\n;:]+?)(?=\s+$stop|\s+from\b|\s+\d{1,2}[-/:]|[.,;]|$)""", RegexOption.IGNORE_CASE),
                Regex("""\bfor\s+([^,.\n;:]+?)(?=\s+$stop|\s+\d{1,2}[-/:]|[.,;]|$)""", RegexOption.IGNORE_CASE)
            )
            Transaction.Type.INCOME -> listOf(
                Regex("""\bfrom\s+(?!your\b)([^,.\n;:]+?)(?=\s+$stop|\s+to\b|\s+\d{1,2}[-/:]|[.,;]|$)""", RegexOption.IGNORE_CASE),
                Regex("""\bby\s+([^,.\n;:]+?)(?=\s+$stop|\s+\d{1,2}[-/:]|[.,;]|$)""", RegexOption.IGNORE_CASE),
                Regex("""\bat\s+([^,.\n;:]+?)(?=\s+$stop|\s+\d{1,2}[-/:]|[.,;]|$)""", RegexOption.IGNORE_CASE)
            )
        }

        for (re in orderedAnchors) {
            val cand = re.find(body)?.groupValues?.getOrNull(1)?.cleanCandidate() ?: continue
            if (isUsefulCandidate(cand)) return cand
        }

        val cleaned = body
            .replace(Regex("""(?:$CURRENCY_ALT)\.?\s*[\d,]+(?:\.\d+)?""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""[\d,]+(?:\.\d+)?\s*(?:$CURRENCY_ALT)""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\d{1,2}[-/]\w{2,9}[-/]\d{2,4}"""), " ")
            .replace(Regex("""\d{1,2}[-/:]\d{1,2}[-/:]\d{2,4}"""), " ")
            .replace(Regex("""\d{1,2}:\d{2}(?::\d{2})?"""), " ")
            .replace(Regex("""\bavl\s*bal[.\s:]*[\d,.]+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bavailable\s*balance[.\s:]*[\d,.]+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bbalance[.\s:]*[\d,.]+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bbal[.\s:]*[\d,.]+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bref(?:erence)?(?:\s*(?:no|number|#))?[.\s:#]*\w+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\botp[.\s:]*\w+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\b[Aa]/[Cc][.\s:#]*[\dxX*]+"""), " ")
            .replace(Regex("""\b[xX*]{2,}\d*\b"""), " ")
            .replace(Regex("""\b\d{6,}\b"""), " ")
            .replace(Regex(
                """\b(?:debited|credited|deducted|transferred|withdrawn|charged|deposited|received|sent|paid|spent|purchase|payment|refund|info|dear|customer|valid|thanks|thank|you|your|account|acc)\b""",
                RegexOption.IGNORE_CASE
            ), " ")
            .replace(Regex("""\b(?:from|to|at|on|by|of|for|the|in|is|has|been|with|via|using|and)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""[^\w\s.&'\-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.length >= 3) return cleaned.take(50).trim()

        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.take(50)?.trim()
        return if (!firstLine.isNullOrBlank()) firstLine else "Transaction"
    }

    private fun String.cleanCandidate(): String =
        this.trim()
            .trimEnd('.', ',', ';', ':', '*', '-')
            .replace(Regex("""\s+"""), " ")

    private fun isUsefulCandidate(s: String): Boolean {
        if (s.length !in 2..50) return false
        val lower = s.lowercase()
        if (lower.matches(Regex("""\d+"""))) return false
        if (lower.contains("your account")) return false
        if (lower.contains("your acc")) return false
        if (lower.contains("your a/c")) return false
        if (lower in setOf("you", "the", "this", "that")) return false
        return true
    }
}
