package com.duggustore.app

import com.duggustore.app.data.repository.SponsoredSlotRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ₹5/day ad quote must mirror the DB's generated fee_amount column. */
class SponsoredSlotTest {

    private val repo = SponsoredSlotRepository()

    @Test
    fun quoteIsFiveRupeesPerDay() {
        assertEquals(5, repo.quoteFee(1))
        assertEquals(35, repo.quoteFee(7))
        assertEquals(150, repo.quoteFee(30))
    }

    @Test
    fun quoteOfZeroDaysIsZero() {
        assertEquals(0, repo.quoteFee(0))
    }
}
