package com.example.data

import java.util.Locale
import java.util.regex.Pattern

data class ParsedCurrencyResult(
    val amount: Double,
    val sourceCurrency: String,
    val eurAmount: Double,
    val formattedEur: String
)

object CurrencyParser {

    // Common currency symbol mappings to ISO 4217 currency codes
    private val SYMBOL_MAP = listOf(
        // Multi-char symbols first
        "US$" to "USD",
        "CA$" to "CAD",
        "C$" to "CAD",
        "AU$" to "AUD",
        "A$" to "AUD",
        "NZ$" to "NZD",
        "HK$" to "HKD",
        "S$" to "SGD",
        "R$" to "BRL",
        "MEX$" to "MXN",
        "KČ" to "CZK",
        "KC" to "CZK",
        "ZŁ" to "PLN",
        "ZL" to "PLN",
        "FT" to "HUF",
        "LEI" to "RON",
        "RP" to "IDR",
        "RM" to "MYR",
        "RS" to "INR",
        "CHF" to "CHF",
        "KR" to "SEK",

        // Single-character symbols
        "$" to "USD",
        "£" to "GBP",
        "€" to "EUR",
        "¥" to "JPY",
        "円" to "JPY",
        "元" to "CNY",
        "₹" to "INR",
        "₩" to "KRW",
        "₽" to "RUB",
        "₺" to "TRY",
        "₴" to "UAH",
        "₪" to "ILS",
        "₫" to "VND",
        "฿" to "THB",
        "₱" to "PHP",
        "৳" to "BDT",
        "₦" to "NGN",
        "GH₵" to "GHS"
    )

    // Standard ISO 4217 Currency Codes supported
    val SUPPORTED_CURRENCIES = setOf(
        "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "HKD", "NZD",
        "SEK", "KRW", "SGD", "NOK", "MXN", "INR", "RUB", "ZAR", "TRY", "BRL",
        "TWD", "DKK", "PLN", "THB", "IDR", "HUF", "CZK", "ILS", "CLP", "PHP",
        "AED", "COP", "SAR", "MYR", "RON", "VND", "BGN", "EGP", "ARS", "QAR",
        "KWD", "BHD", "OMR", "PKR", "BDT", "NGN", "KES", "PEN", "UAH"
    )

    /**
     * Parses any input text (e.g. "45USD", "$22", "35¥", "£ 120.50", "Cost: 1,250 JPY")
     * and converts the extracted currency amount into EUR using the provided exchange rates map.
     */
    fun parseAndConvert(
        input: String?,
        ratesToEur: Map<String, Double>,
        symbolPrefix: Boolean = true
    ): ParsedCurrencyResult? {
        if (input.isNullOrBlank()) return null
        val cleanInput = input.trim()

        // 1. Try to extract amount and currency
        val parsed = extractAmountAndCurrency(cleanInput) ?: return null

        // 2. Get exchange rate (where ratesToEur maps "USD" -> rate, representing 1 EUR = rate USD)
        // Hence: eurAmount = amount / rate
        val rate = ratesToEur[parsed.currencyCode.uppercase(Locale.US)]
            ?: if (parsed.currencyCode.equals("EUR", ignoreCase = true)) 1.0 else null

        if (rate == null || rate <= 0.0) {
            return null
        }

        val eurAmount = parsed.amount / rate
        val roundedEur = (Math.round(eurAmount * 100.0) / 100.0)
        val formattedNumber = String.format(Locale.US, "%.2f", roundedEur)
        val formattedDisplay = if (symbolPrefix) "€$formattedNumber" else "$formattedNumber €"

        return ParsedCurrencyResult(
            amount = parsed.amount,
            sourceCurrency = parsed.currencyCode.uppercase(Locale.US),
            eurAmount = roundedEur,
            formattedEur = formattedDisplay
        )
    }

    private data class RawExtracted(val amount: Double, val currencyCode: String)

    private fun extractAmountAndCurrency(text: String): RawExtracted? {
        val trimmed = text.trim()

        // Check for 3-letter ISO code patterns (e.g. "45USD", "45 USD", "USD 45", "USD45")
        // Pattern 1: Number followed by ISO code (e.g. 45USD, 45.50 USD, 1,250.00 CAD)
        val numThenIso = Pattern.compile(
            """(?i)([0-9]+(?:[.,\s][0-9]{3})*(?:[.,][0-9]+)?)\s*([A-Za-z]{3})\b"""
        ).matcher(trimmed)
        if (numThenIso.find()) {
            val numStr = numThenIso.group(1)
            val codeStr = numThenIso.group(2)?.uppercase(Locale.US)
            if (codeStr != null && SUPPORTED_CURRENCIES.contains(codeStr)) {
                val value = parseNumber(numStr)
                if (value != null && value > 0) {
                    return RawExtracted(value, codeStr)
                }
            }
        }

        // Pattern 2: ISO code followed by Number (e.g. USD 45, CAD 1250, EUR 19.99)
        val isoThenNum = Pattern.compile(
            """(?i)\b([A-Za-z]{3})\s*([0-9]+(?:[.,\s][0-9]{3})*(?:[.,][0-9]+)?)"""
        ).matcher(trimmed)
        if (isoThenNum.find()) {
            val codeStr = isoThenNum.group(1)?.uppercase(Locale.US)
            val numStr = isoThenNum.group(2)
            if (codeStr != null && SUPPORTED_CURRENCIES.contains(codeStr)) {
                val value = parseNumber(numStr)
                if (value != null && value > 0) {
                    return RawExtracted(value, codeStr)
                }
            }
        }

        // Check for Currency Symbols (e.g. "$22", "35¥", "£120", "€45", "22$", "35.50¥")
        for ((symbol, code) in SYMBOL_MAP) {
            val escapedSymbol = Pattern.quote(symbol)

            // Symbol preceding number: e.g. "$22", "$ 22.50", "US$ 50"
            val prefixRegex = Pattern.compile(
                """(?i)$escapedSymbol\s*([0-9]+(?:[.,\s][0-9]{3})*(?:[.,][0-9]+)?)"""
            ).matcher(trimmed)
            if (prefixRegex.find()) {
                val numStr = prefixRegex.group(1)
                val value = parseNumber(numStr)
                if (value != null && value > 0) {
                    val resolvedCode = resolveAmbiguousSymbol(symbol, code, trimmed)
                    return RawExtracted(value, resolvedCode)
                }
            }

            // Symbol succeeding number: e.g. "35¥", "22$", "120 £"
            val suffixRegex = Pattern.compile(
                """(?i)([0-9]+(?:[.,\s][0-9]{3})*(?:[.,][0-9]+)?)\s*$escapedSymbol"""
            ).matcher(trimmed)
            if (suffixRegex.find()) {
                val numStr = suffixRegex.group(1)
                val value = parseNumber(numStr)
                if (value != null && value > 0) {
                    val resolvedCode = resolveAmbiguousSymbol(symbol, code, trimmed)
                    return RawExtracted(value, resolvedCode)
                }
            }
        }

        // Fallback: If only a raw number was selected (e.g. "45.00" or "22"),
        // assume USD if no other indicator found
        val rawNumRegex = Pattern.compile(
            """^([0-9]+(?:[.,\s][0-9]{3})*(?:[.,][0-9]+)?)$"""
        ).matcher(trimmed)
        if (rawNumRegex.find()) {
            val value = parseNumber(rawNumRegex.group(1))
            if (value != null && value > 0) {
                return RawExtracted(value, "USD")
            }
        }

        return null
    }

    private fun resolveAmbiguousSymbol(symbol: String, defaultCode: String, fullText: String): String {
        // ¥ can be JPY or CNY. If CNY or RMB or Yuan is mentioned, use CNY, else default to JPY
        if (symbol == "¥") {
            val upper = fullText.uppercase(Locale.US)
            if (upper.contains("CNY") || upper.contains("RMB") || upper.contains("YUAN")) {
                return "CNY"
            }
            return "JPY"
        }
        // kr can be SEK, NOK, DKK. Check if text mentions NOK or DKK, else default SEK
        if (symbol.equals("kr", ignoreCase = true)) {
            val upper = fullText.uppercase(Locale.US)
            if (upper.contains("NOK")) return "NOK"
            if (upper.contains("DKK")) return "DKK"
            return "SEK"
        }
        return defaultCode
    }

    /**
     * Parses formatted numeric strings into Double safely.
     * Handles:
     * - "1,234.56" -> 1234.56
     * - "1.234,56" -> 1234.56
     * - "1 250,50" -> 1250.50
     * - "45,50" -> 45.50
     * - "45.50" -> 45.50
     * - "22" -> 22.0
     */
    fun parseNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(" ", "")

        try {
            val hasComma = s.contains(",")
            val hasDot = s.contains(".")

            if (hasComma && hasDot) {
                val lastComma = s.lastIndexOf(',')
                val lastDot = s.lastIndexOf('.')
                return if (lastDot > lastComma) {
                    // Standard US format: 1,234.56 -> remove commas
                    s.replace(",", "").toDoubleOrNull()
                } else {
                    // European format: 1.234,56 -> remove dots, replace comma with dot
                    s.replace(".", "").replace(",", ".").toDoubleOrNull()
                }
            } else if (hasComma) {
                // Only comma: e.g. "45,50" or "1,250"
                val commaIndex = s.lastIndexOf(',')
                val digitsAfter = s.length - 1 - commaIndex
                return if (digitsAfter == 3 && s.indexOf(',') == commaIndex && commaIndex >= 1 && !s.contains(",")) {
                    // Ambiguous, could be 1,000. If 3 digits, treat as 1000 or 1.000
                    s.replace(",", "").toDoubleOrNull()
                } else {
                    // Decimal comma: "45,50" -> 45.50
                    s.replace(",", ".").toDoubleOrNull()
                }
            } else {
                // Standard dot or integer: "45.50" or "22"
                return s.toDoubleOrNull()
            }
        } catch (e: Exception) {
            return null
        }
    }
}
