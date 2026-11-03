package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExchangeRateRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Default fallback rates (Base: 1 EUR)
    // Calibrated against recent market values
    private val defaultRates: Map<String, Double> = mapOf(
        "EUR" to 1.0,
        "USD" to 1.085,
        "GBP" to 0.855,
        "JPY" to 164.20,
        "CAD" to 1.485,
        "AUD" to 1.650,
        "CHF" to 0.942,
        "CNY" to 7.85,
        "INR" to 90.60,
        "KRW" to 1485.0,
        "BRL" to 6.10,
        "MXN" to 21.60,
        "SEK" to 11.45,
        "NOK" to 11.70,
        "DKK" to 7.46,
        "PLN" to 4.32,
        "TRY" to 37.40,
        "NZD" to 1.80,
        "SGD" to 1.45,
        "HKD" to 8.48,
        "THB" to 39.80,
        "IDR" to 17250.0,
        "AED" to 3.98,
        "SAR" to 4.07,
        "ILS" to 4.05,
        "CZK" to 25.20,
        "HUF" to 405.0,
        "RON" to 4.97,
        "BGN" to 1.956,
        "PHP" to 62.50,
        "MYR" to 4.80,
        "VND" to 27500.0,
        "ZAR" to 19.50,
        "TWD" to 35.10,
        "CLP" to 1020.0,
        "COP" to 4750.0,
        "EGP" to 53.00,
        "ARS" to 1080.0,
        "QAR" to 3.95,
        "KWD" to 0.334,
        "BHD" to 0.409,
        "OMR" to 0.418,
        "PKR" to 302.0,
        "BDT" to 130.0,
        "NGN" to 1750.0,
        "KES" to 140.0,
        "PEN" to 4.10,
        "UAH" to 45.00
    )

    private val _ratesState = MutableStateFlow<Map<String, Double>>(loadCachedRates())
    val ratesState: StateFlow<Map<String, Double>> = _ratesState.asStateFlow()

    private val _lastUpdatedTime = MutableStateFlow(prefs.getLong(KEY_LAST_UPDATED, 0L))
    val lastUpdatedTime: StateFlow<Long> = _lastUpdatedTime.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Auto-refresh in background if older than 6 hours
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATED, 0L)
        val sixHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)
        if (lastUpdate < sixHoursAgo) {
            CoroutineScope(Dispatchers.IO).launch {
                refreshRates(force = false)
            }
        }
    }

    /**
     * Synchronously returns current rates for instantaneous overlay display
     */
    fun getRates(): Map<String, Double> {
        return _ratesState.value
    }

    /**
     * Fetches fresh exchange rates from public exchange rate API.
     * Note: Only the base currency "EUR" is fetched; user input is NEVER transmitted!
     */
    suspend fun refreshRates(force: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        if (_isRefreshing.value && !force) {
            return@withContext Result.success(true)
        }

        _isRefreshing.value = true
        try {
            var fetchedRates: Map<String, Double>? = fetchFromOpenErApi()
            if (fetchedRates == null) {
                fetchedRates = fetchFromFrankfurterApi()
            }

            if (fetchedRates != null && fetchedRates.isNotEmpty()) {
                val combined = HashMap(defaultRates)
                combined.putAll(fetchedRates)
                combined["EUR"] = 1.0

                saveCachedRates(combined)
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_UPDATED, now).apply()

                _ratesState.value = combined
                _lastUpdatedTime.value = now
                _isRefreshing.value = false
                return@withContext Result.success(true)
            } else {
                _isRefreshing.value = false
                return@withContext Result.failure(Exception("Could not parse exchange rates"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh rates: ${e.message}")
            _isRefreshing.value = false
            return@withContext Result.failure(e)
        }
    }

    private fun fetchFromOpenErApi(): Map<String, Double>? {
        return try {
            val request = Request.Builder()
                .url("https://open.er-api.com/v6/latest/EUR")
                .header("User-Agent", "EURQuickConvert-Android/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("result") != "success") return null

                val ratesObj = json.optJSONObject("rates") ?: return null
                val map = mutableMapOf<String, Double>()
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = ratesObj.optDouble(key, 0.0)
                    if (value > 0.0) {
                        map[key.uppercase(Locale.US)] = value
                    }
                }
                map
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenER API call failed: ${e.message}")
            null
        }
    }

    private fun fetchFromFrankfurterApi(): Map<String, Double>? {
        return try {
            val request = Request.Builder()
                .url("https://api.frankfurter.dev/v1/latest?base=EUR")
                .header("User-Agent", "EURQuickConvert-Android/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val ratesObj = json.optJSONObject("rates") ?: return null
                val map = mutableMapOf<String, Double>()
                map["EUR"] = 1.0
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = ratesObj.optDouble(key, 0.0)
                    if (value > 0.0) {
                        map[key.uppercase(Locale.US)] = value
                    }
                }
                map
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frankfurter API call failed: ${e.message}")
            null
        }
    }

    private fun loadCachedRates(): Map<String, Double> {
        val cachedJson = prefs.getString(KEY_CACHED_RATES, null) ?: return defaultRates
        return try {
            val json = JSONObject(cachedJson)
            val map = HashMap(defaultRates)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optDouble(key, 0.0)
                if (value > 0.0) {
                    map[key.uppercase(Locale.US)] = value
                }
            }
            map
        } catch (e: Exception) {
            defaultRates
        }
    }

    private fun saveCachedRates(rates: Map<String, Double>) {
        try {
            val json = JSONObject()
            rates.forEach { (k, v) -> json.put(k, v) }
            prefs.edit().putString(KEY_CACHED_RATES, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache rates: ${e.message}")
        }
    }

    // --- User Preferences ---

    var isAutoCopyToClipboard: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COPY, value).apply()

    var isSymbolPrefix: Boolean
        get() = prefs.getBoolean(KEY_SYMBOL_PREFIX, true)
        set(value) = prefs.edit().putBoolean(KEY_SYMBOL_PREFIX, value).apply()

    var overlayDurationMs: Long
        get() = prefs.getLong(KEY_OVERLAY_DURATION, 2600L)
        set(value) = prefs.edit().putLong(KEY_OVERLAY_DURATION, value).apply()

    fun getFormattedLastUpdated(): String {
        val timestamp = _lastUpdatedTime.value
        if (timestamp <= 0L) return "Offline defaults"
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    companion object {
        private const val TAG = "ExchangeRateRepo"
        private const val PREFS_NAME = "eur_converter_prefs"
        private const val KEY_CACHED_RATES = "cached_rates_json"
        private const val KEY_LAST_UPDATED = "last_updated_timestamp"
        private const val KEY_AUTO_COPY = "pref_auto_copy"
        private const val KEY_SYMBOL_PREFIX = "pref_symbol_prefix"
        private const val KEY_OVERLAY_DURATION = "pref_overlay_duration"

        @Volatile
        private var INSTANCE: ExchangeRateRepository? = null

        fun getInstance(context: Context): ExchangeRateRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExchangeRateRepository(context).also { INSTANCE = it }
            }
        }
    }
}
