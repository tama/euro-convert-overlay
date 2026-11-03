package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CurrencyParser
import com.example.data.ExchangeRateRepository
import com.example.data.ParsedCurrencyResult
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ExchangeRateRepository) : ViewModel() {

    val rates = repository.ratesState
    val isRefreshing = repository.isRefreshing

    private val _lastUpdated = MutableStateFlow(repository.getFormattedLastUpdated())
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    private val _testInput = MutableStateFlow("45USD")
    val testInput: StateFlow<String> = _testInput.asStateFlow()

    private val _conversionResult = MutableStateFlow<ParsedCurrencyResult?>(null)
    val conversionResult: StateFlow<ParsedCurrencyResult?> = _conversionResult.asStateFlow()

    private val _autoCopy = MutableStateFlow(repository.isAutoCopyToClipboard)
    val autoCopy: StateFlow<Boolean> = _autoCopy.asStateFlow()

    private val _symbolPrefix = MutableStateFlow(repository.isSymbolPrefix)
    val symbolPrefix: StateFlow<Boolean> = _symbolPrefix.asStateFlow()

    private val _overlayDurationMs = MutableStateFlow(repository.overlayDurationMs)
    val overlayDurationMs: StateFlow<Long> = _overlayDurationMs.asStateFlow()

    init {
        updateConversion(_testInput.value)
    }

    fun setTestInput(input: String) {
        _testInput.value = input
        updateConversion(input)
    }

    private fun updateConversion(input: String) {
        _conversionResult.value = CurrencyParser.parseAndConvert(
            input = input,
            ratesToEur = repository.getRates(),
            symbolPrefix = _symbolPrefix.value
        )
    }

    fun refreshRates(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.refreshRates(force = true)
            _lastUpdated.value = repository.getFormattedLastUpdated()
            updateConversion(_testInput.value)
            onResult(result.isSuccess)
        }
    }

    fun toggleAutoCopy(enabled: Boolean) {
        repository.isAutoCopyToClipboard = enabled
        _autoCopy.value = enabled
    }

    fun toggleSymbolPrefix(prefix: Boolean) {
        repository.isSymbolPrefix = prefix
        _symbolPrefix.value = prefix
        updateConversion(_testInput.value)
    }

    fun setOverlayDuration(durationMs: Long) {
        repository.overlayDurationMs = durationMs
        _overlayDurationMs.value = durationMs
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ExchangeRateRepository.getInstance(applicationContext)
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository) as T
            }
        })[MainViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainScreen(
                    viewModel = viewModel,
                    onLaunchOverlayTest = { textToConvert ->
                        val intent = Intent(this, ConvertOverlayActivity::class.java).apply {
                            action = Intent.ACTION_PROCESS_TEXT
                            type = "text/plain"
                            putExtra(Intent.EXTRA_PROCESS_TEXT, textToConvert)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLaunchOverlayTest: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val testInput by viewModel.testInput.collectAsState()
    val conversionResult by viewModel.conversionResult.collectAsState()
    val rates by viewModel.rates.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    val autoCopy by viewModel.autoCopy.collectAsState()
    val symbolPrefix by viewModel.symbolPrefix.collectAsState()
    val overlayDurationMs by viewModel.overlayDurationMs.collectAsState()

    val rotationAngle by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        label = "refresh_rotation"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "€",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "EUR Quick Convert",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshRates { success ->
                                val msg = if (success) {
                                    context.getString(R.string.rates_updated_success)
                                } else {
                                    context.getString(R.string.rates_update_failed)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("refresh_rates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh exchange rates",
                            modifier = Modifier.rotate(rotationAngle)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StatusHeroCard()
            }

            // Interactive Playground
            item {
                SandboxCard(
                    testInput = testInput,
                    onInputChanged = { viewModel.setTestInput(it) },
                    conversionResult = conversionResult,
                    onLaunchOverlay = { textToConvert -> onLaunchOverlayTest(textToConvert) }
                )
            }

            // How To Use Guide
            item {
                HowToUseCard()
            }

            // Exchange Rates Table
            item {
                ExchangeRatesDashboardCard(
                    rates = rates,
                    lastUpdated = lastUpdated,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        viewModel.refreshRates { success ->
                            val msg = if (success) "Rates updated!" else "Using cached rates"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Preferences
            item {
                SettingsCard(
                    autoCopy = autoCopy,
                    onAutoCopyChange = { viewModel.toggleAutoCopy(it) },
                    symbolPrefix = symbolPrefix,
                    onSymbolPrefixChange = { viewModel.toggleSymbolPrefix(it) },
                    durationMs = overlayDurationMs,
                    onDurationChange = { viewModel.setOverlayDuration(it) }
                )
            }

            // Privacy Guarantee
            item {
                PrivacyCard()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatusHeroCard() {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF0F172A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_hero_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)) // Green active dot
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Context Menu Active",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "100% On-Device",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Select text anywhere on Android & tap \"€ Convert to EUR\"",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Instantly converts amounts like 45USD, $22, 35¥, £120 into a tiny floating overlay.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SandboxCard(
    testInput: String,
    onInputChanged: (String) -> Unit,
    conversionResult: ParsedCurrencyResult?,
    onLaunchOverlay: (String) -> Unit
) {
    val presets = listOf("45USD", "$22", "35¥", "£120", "150 CAD", "45000 JPY", "85 CHF", "500 SEK", "$1,250.00")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sandbox_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Conversion Playground",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = testInput,
                onValueChange = onInputChanged,
                label = { Text("Enter or paste amount (e.g. $22, 45USD, 35¥)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_input_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick preset chips
            Text(
                text = "Quick Presets:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = testInput == preset,
                        onClick = { onInputChanged(preset) },
                        label = { Text(preset) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("preset_$preset")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Preview of the Tiny Overlay
            Text(
                text = "Overlay Preview (What displays on screen):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF030712)) // Dark mock screen background
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (conversionResult != null) {
                    // Exact replica of the Tiny Overlay
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF0F172A),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .testTag("preview_overlay_pill")
                            .clickable { onLaunchOverlay(testInput) }
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conversionResult.formattedEur,
                                color = Color(0xFF38BDF8),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("preview_amount_text")
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No currency detected in input",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (conversionResult != null) {
                    Text(
                        text = "Parsed: ${conversionResult.amount} ${conversionResult.sourceCurrency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                OutlinedButton(
                    onClick = { onLaunchOverlay(testInput) },
                    modifier = Modifier.testTag("test_overlay_window_button")
                ) {
                    Text("Test Floating Overlay")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Native Text Selection sandbox area
            Text(
                text = "Native Android Selection Test Box:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Long-press & highlight any price below to see \"Convert to EUR\" in the floating context menu:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Real Android TextView that triggers native Android FloatingToolbar / ActionMode
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.TextView(ctx).apply {
                        text = "Highlight to convert: $22 or 45USD or 35¥ or £120 or 1,250.00 CAD or 45000 JPY"
                        setTextIsSelectable(true)
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(android.graphics.Color.parseColor("#111827"))
                        setPadding(32, 28, 32, 28)
                        customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                // Pin Convert to EUR directly to the toolbar so it's always immediately visible
                                menu.add(android.view.Menu.NONE, 9991, 0, "€ Convert to EUR").apply {
                                    setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                                }
                                return true
                            }
                            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean = true
                            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                                if (item.itemId == 9991) {
                                    val start = Math.min(selectionStart, selectionEnd)
                                    val end = Math.max(selectionStart, selectionEnd)
                                    if (start >= 0 && end > start && end <= text.length) {
                                        val selected = text.subSequence(start, end).toString()
                                        onLaunchOverlay(selected)
                                    }
                                    mode.finish()
                                    return true
                                }
                                return false
                            }
                            override fun onDestroyActionMode(mode: android.view.ActionMode) {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .testTag("native_selectable_test_view")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3-dots Tip Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "💡",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Tip for external apps (Chrome, Notes, WhatsApp): If your floating context menu only shows Copy / Share / Select all, tap the three dots (⋮) on the right of the menu to reveal \"Convert to EUR\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HowToUseCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("how_to_use_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How It Works System-Wide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            StepItem(
                number = "1",
                title = "Select Any Price Text",
                description = "In Chrome, WhatsApp, Notes, Gmail, or any other app, highlight any price (e.g. \"45USD\", \"$22\", \"35¥\")."
            )

            Spacer(modifier = Modifier.height(12.dp))

            StepItem(
                number = "2",
                title = "Tap \"€ Convert to EUR\"",
                description = "In the floating selection menu that appears over the text, choose \"€ Convert to EUR\"."
            )

            Spacer(modifier = Modifier.height(12.dp))

            StepItem(
                number = "3",
                title = "Instant Overlay & Auto-Copy",
                description = "A tiny floating overlay immediately displays the exact EUR amount (e.g. \"€41.47\") and copies it to your clipboard."
            )
        }
    }
}

@Composable
fun StepItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ExchangeRatesDashboardCard(
    rates: Map<String, Double>,
    lastUpdated: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val highlightCurrencies = listOf(
        "USD" to "US Dollar",
        "GBP" to "British Pound",
        "JPY" to "Japanese Yen",
        "CAD" to "Canadian Dollar",
        "AUD" to "Australian Dollar",
        "CHF" to "Swiss Franc",
        "CNY" to "Chinese Yuan",
        "INR" to "Indian Rupee",
        "SEK" to "Swedish Krona",
        "KRW" to "South Korean Won",
        "BRL" to "Brazilian Real",
        "MXN" to "Mexican Peso"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exchange_rates_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current EUR Rates (Base: 1 €)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last updated: $lastUpdated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Rates Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                highlightCurrencies.forEach { (code, name) ->
                    val rate = rates[code] ?: 0.0
                    val formattedRate = if (rate >= 100.0) {
                        String.format(java.util.Locale.US, "%.1f", rate)
                    } else {
                        String.format(java.util.Locale.US, "%.3f", rate)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = code,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "1 € = $formattedRate $code",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    autoCopy: Boolean,
    onAutoCopyChange: (Boolean) -> Unit,
    symbolPrefix: Boolean,
    onSymbolPrefixChange: (Boolean) -> Unit,
    durationMs: Long,
    onDurationChange: (Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Overlay Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Auto-Copy Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Copy Converted Amount",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically copy the EUR amount to clipboard when activated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = autoCopy,
                    onCheckedChange = onAutoCopyChange,
                    modifier = Modifier.testTag("auto_copy_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Symbol Placement
            Column {
                Text(
                    text = "Symbol Format",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = symbolPrefix,
                        onClick = { onSymbolPrefixChange(true) },
                        label = { Text("Prefix (€41.67)") },
                        modifier = Modifier.testTag("format_prefix_chip")
                    )
                    FilterChip(
                        selected = !symbolPrefix,
                        onClick = { onSymbolPrefixChange(false) },
                        label = { Text("Suffix (41.67 €)") },
                        modifier = Modifier.testTag("format_suffix_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Overlay Duration
            Column {
                Text(
                    text = "Overlay Auto-Dismiss Duration",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        2000L to "2.0s",
                        2600L to "2.6s (Default)",
                        4000L to "4.0s"
                    ).forEach { (ms, label) ->
                        FilterChip(
                            selected = durationMs == ms,
                            onClick = { onDurationChange(ms) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("duration_${ms}_chip")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("privacy_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lightweight & Privacy-Focused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Zero User Data Transmitted: Selected text is never uploaded to any remote server or stored. All parsing, currency detection, and conversion calculations happen 100% locally on your device.\n• Minimal Network Footprint: Only public exchange rate tables (base EUR) are downloaded once every 6 hours and stored in local cache.\n• No Trackers or Ads: Clean, lightweight, open-standard Android architecture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
