package com.socialvest.app.domain.price

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PriceMathTest {

    @Test
    fun `rupiah amount becomes sen`() {
        assertEquals(100_050L, PriceMath.toSen(BigDecimal("1000.50")))
        assertEquals(1_223_760_131_44L, PriceMath.toSen(BigDecimal("1223760131.44")))
    }

    @Test
    fun `zero and negative prices are rejected`() {
        assertNull(PriceMath.toSen(BigDecimal.ZERO))
        assertNull(PriceMath.toSen(BigDecimal("-5")))
    }

    @Test
    fun `usd is multiplied by the idr rate`() {
        assertEquals(
            0,
            BigDecimal("36000").compareTo(
                PriceMath.usdToIdr(BigDecimal("2"), BigDecimal("18000"))
            )
        )
    }

    @Test
    fun `one troy ounce at one rupiah per usd is one rupiah per gram`() {
        val perGram = PriceMath.goldOunceToGramIdr(
            PriceMath.OUNCES_PER_GRAM,
            BigDecimal.ONE
        )
        assertEquals(0, BigDecimal.ONE.compareTo(perGram))
    }

    @Test
    fun `world gold quote is converted to a plausible rupiah per gram`() {
        // GC=F ~ USD 3,886.30/oz with USD/IDR ~ 18,190.
        val sen = PriceMath.toSen(
            PriceMath.goldOunceToGramIdr(
                BigDecimal("3886.30"),
                BigDecimal("18190")
            )
        )

        val rupiahPerGram = requireNotNull(sen) / 100
        assertTrue(
            "Harga emas per gram di luar rentang wajar: $rupiahPerGram",
            rupiahPerGram in 2_200_000L..2_350_000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero exchange rate is rejected`() {
        PriceMath.usdToIdr(BigDecimal.ONE, BigDecimal.ZERO)
    }
}
