package com.example

import com.example.data.CurrencyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    private val testRates = mapOf(
        "EUR" to 1.0,
        "USD" to 1.08,
        "GBP" to 0.85,
        "JPY" to 160.0,
        "CAD" to 1.48
    )

    @Test
    fun testParse45USD() {
        val res = CurrencyParser.parseAndConvert("45USD", testRates)
        assertNotNull(res)
        assertEquals(45.0, res!!.amount, 0.001)
        assertEquals("USD", res.sourceCurrency)
        // 45 / 1.08 = 41.67
        assertEquals(41.67, res.eurAmount, 0.01)
        assertEquals("€41.67", res.formattedEur)
    }

    @Test
    fun testParseDollar22() {
        val res = CurrencyParser.parseAndConvert("$22", testRates)
        assertNotNull(res)
        assertEquals(22.0, res!!.amount, 0.001)
        assertEquals("USD", res.sourceCurrency)
        // 22 / 1.08 = 20.37
        assertEquals(20.37, res.eurAmount, 0.01)
        assertEquals("€20.37", res.formattedEur)
    }

    @Test
    fun testParse35Yen() {
        val res = CurrencyParser.parseAndConvert("35¥", testRates)
        assertNotNull(res)
        assertEquals(35.0, res!!.amount, 0.001)
        assertEquals("JPY", res.sourceCurrency)
        // 35 / 160.0 = 0.22
        assertEquals(0.22, res.eurAmount, 0.01)
        assertEquals("€0.22", res.formattedEur)
    }

    @Test
    fun testParsePound120() {
        val res = CurrencyParser.parseAndConvert("£120", testRates)
        assertNotNull(res)
        assertEquals(120.0, res!!.amount, 0.001)
        assertEquals("GBP", res.sourceCurrency)
        // 120 / 0.85 = 141.18
        assertEquals(141.18, res.eurAmount, 0.01)
    }

    @Test
    fun testSuffixFormatting() {
        val res = CurrencyParser.parseAndConvert("45USD", testRates, symbolPrefix = false)
        assertNotNull(res)
        assertEquals("41.67 €", res!!.formattedEur)
    }

    @Test
    fun testNumberParsing() {
        assertEquals(1234.56, CurrencyParser.parseNumber("1,234.56")!!, 0.001)
        assertEquals(1234.56, CurrencyParser.parseNumber("1.234,56")!!, 0.001)
        assertEquals(45.5, CurrencyParser.parseNumber("45,50")!!, 0.001)
        assertEquals(45.5, CurrencyParser.parseNumber("45.50")!!, 0.001)
        assertEquals(22.0, CurrencyParser.parseNumber("22")!!, 0.001)
    }

    @Test
    fun testInvalidInput() {
        val res = CurrencyParser.parseAndConvert("Hello world", testRates)
        assertNull(res)
    }
}
