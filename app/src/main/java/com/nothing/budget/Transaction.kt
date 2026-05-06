package com.nothing.budget

data class Transaction(
    val id: Long,
    val amount: Double,
    val currency: String,
    val description: String,
    val timestamp: Long,
    val type: Type,
    val rawSms: String,
    /** The session this transaction belongs to. Required. */
    val sessionId: String
) {
    enum class Type { EXPENSE, INCOME }
}
