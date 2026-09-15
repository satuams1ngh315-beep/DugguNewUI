package com.duggustore.app

import com.duggustore.app.data.model.WalletTransaction
import com.duggustore.app.data.repository.walletBalance
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wallet balance is always derived from the ledger — never stored. */
class WalletMathTest {

    private fun tx(amount: Int, type: String) =
        WalletTransaction(userId = "u", title = "t", amount = amount, type = type)

    @Test
    fun emptyLedgerIsZero() {
        assertEquals(0, emptyList<WalletTransaction>().walletBalance())
    }

    @Test
    fun creditsAddUp() {
        assertEquals(
            100,
            listOf(tx(50, "CREDIT"), tx(50, "CREDIT")).walletBalance()
        )
    }

    @Test
    fun debitsSubtract() {
        assertEquals(
            30,
            listOf(tx(50, "CREDIT"), tx(20, "DEBIT")).walletBalance()
        )
    }

    @Test
    fun anythingThatIsNotCreditCountsAsDebit() {
        // Mirrors the implementation: only "CREDIT" adds, everything else subtracts.
        assertEquals(-10, listOf(tx(10, "REFUND")).walletBalance())
    }
}
