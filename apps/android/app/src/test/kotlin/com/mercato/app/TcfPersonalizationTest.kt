package com.mercato.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure TCF purpose-bit rule behind UMP consent mapping: personalised
 * ads need consent on purposes 1, 3, 4 and consent or legitimate interest
 * on purposes 2, 7, 9, 10 (Google's documented policy). Bit N-1 of the
 * strings carries purpose N.
 */
class TcfPersonalizationTest {

    // Purposes 1..10, all granted by consent.
    private val allConsents = "1111111111"

    @Test
    fun fullConsentAllowsPersonalized() {
        assertTrue(ConsentManager.tcfAllowsPersonalized(allConsents, ""))
    }

    @Test
    fun missingCorePurposeDeniesPersonalized() {
        // Purpose 1 (index 0), 3 (index 2) and 4 (index 3) each are
        // consent-only requirements: dropping any one of them is fatal.
        for (index in listOf(0, 2, 3)) {
            val bits = StringBuilder(allConsents).also { it[index] = '0' }.toString()
            assertFalse("purpose ${index + 1}", ConsentManager.tcfAllowsPersonalized(bits, ""))
        }
    }

    @Test
    fun flexiblePurposesAcceptLegitimateInterest() {
        // Purposes 2, 7, 9, 10 can come from legitimate interest instead.
        val consents = "1011001100" // 1,3,4 granted; 2,7,9,10 missing
        val legitimate = "0100001011" // 2,7,9,10 via legitimate interest
        assertTrue(ConsentManager.tcfAllowsPersonalized(consents, legitimate))
    }

    @Test
    fun legitimateInterestAloneCannotReplaceConsentOnlyPurposes() {
        assertFalse(ConsentManager.tcfAllowsPersonalized("", allConsents))
    }

    @Test
    fun emptyOrShortStringsDeny() {
        assertFalse(ConsentManager.tcfAllowsPersonalized("", ""))
        assertFalse(ConsentManager.tcfAllowsPersonalized("1111", "1111"))
    }
}
