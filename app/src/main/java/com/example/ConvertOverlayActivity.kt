package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurrencyParser
import com.example.data.ExchangeRateRepository
import kotlinx.coroutines.delay

class ConvertOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)

        // Read selected text from PROCESS_TEXT intent
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""

        val repository = ExchangeRateRepository.getInstance(applicationContext)
        val rates = repository.getRates()
        val isPrefix = repository.isSymbolPrefix
        val durationMs = repository.overlayDurationMs
        val autoCopy = repository.isAutoCopyToClipboard

        val conversion = CurrencyParser.parseAndConvert(
            input = selectedText,
            ratesToEur = rates,
            symbolPrefix = isPrefix
        )

        val displayText = conversion?.formattedEur ?: "—"

        // Auto-copy to clipboard if enabled and valid conversion
        if (conversion != null && autoCopy) {
            copyToClipboard(conversion.formattedEur)
        }

        // Return replacement text to caller if editable
        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        if (!isReadOnly && conversion != null) {
            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, conversion.formattedEur)
            }
            setResult(RESULT_OK, resultIntent)
        }

        setContent {
            TinyOverlayScreen(
                amountText = displayText,
                autoDismissDurationMs = if (conversion != null) durationMs else 1500L,
                onDismiss = { finish() },
                onTapped = {
                    if (conversion != null) {
                        copyToClipboard(conversion.formattedEur)
                        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            )
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("EUR Amount", text)
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {
            // Ignore clipboard errors
        }
    }
}

@Composable
fun TinyOverlayScreen(
    amountText: String,
    autoDismissDurationMs: Long,
    onDismiss: () -> Unit,
    onTapped: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        delay(autoDismissDurationMs)
        isVisible = false
        delay(220) // Wait for exit animation
        onDismiss()
    }

    // Outer full-screen container that intercepts touches outside the tiny overlay to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overlay_backdrop")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = scaleOut(targetScale = 0.85f) + fadeOut()
        ) {
            // The Tiny Overlay: ONLY displays the amount!
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 24.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0x66000000),
                        spotColor = Color(0x882563EB)
                    )
                    .testTag("overlay_pill_surface")
                    .semantics {
                        contentDescription = "Converted EUR amount $amountText"
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTapped()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = amountText,
                        color = Color(0xFF38BDF8), // Electric cyan/ice-blue
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.testTag("overlay_amount_text")
                    )
                }
            }
        }
    }
}
