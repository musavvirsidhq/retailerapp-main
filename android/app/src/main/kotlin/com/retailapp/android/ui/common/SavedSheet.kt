@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.common

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Which bill PDF the sheet can attach; null for a payment receipt (there is no receipt PDF). */
data class PdfRef(val isSale: Boolean, val id: Int, val billNumber: String)

/** Everything the post-save sheet needs. [phone] null or blank hides the WhatsApp button. */
data class SavedShare(
    val title: String,
    val subtitle: String?,
    val partyName: String,
    val phone: String?,
    val message: String,
    val pdf: PdfRef?,
)

private val WhatsAppGreen = Color(0xFF25D366)

/**
 * Cycle 5 section 7: shown straight after a sale (or a collection) is saved, so the bill or
 * receipt reaches the customer in one tap instead of digging the sale out of the list later.
 */
@Composable
fun SavedSheet(share: SavedShare, onDone: () -> Unit) {
    // Back, swipe-down and Done can all close the sheet; only the first may act (onDone pops
    // the form off the back stack).
    var closed by remember { mutableStateOf(false) }
    val done = {
        if (!closed) {
            closed = true
            onDone()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // The PDF is fetched once and reused if the user taps both WhatsApp and Share.
    var pdfUri by remember { mutableStateOf<Uri?>(null) }

    fun withPdf(action: String, block: (Uri?) -> Result<Unit>) {
        val pdf = share.pdf
        scope.launch {
            busy = action
            error = null
            val uri = when {
                pdf == null -> Result.success(null)
                pdfUri != null -> Result.success(pdfUri)
                else -> BillShare.downloadPdf(pdf.isSale, pdf.id, pdf.billNumber).onSuccess { pdfUri = it }
            }
            uri.mapCatching { block(it).getOrThrow() }
                .onFailure { error = "Couldn't share: ${it.message}" }
            busy = null
        }
    }

    BackHandler(onBack = done)
    ModalBottomSheet(onDismissRequest = done, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successColor(), modifier = Modifier.size(40.dp))
            Text(share.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            share.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (hasPhone(share.phone)) {
                Button(
                    onClick = { withPdf("whatsapp") { uri -> BillShare.whatsApp(context, share.phone!!, share.message, uri) } },
                    enabled = busy == null,
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "whatsapp") {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(if (share.pdf != null) "  WhatsApp bill" else "  WhatsApp receipt")
                }
            } else {
                Text(
                    "${share.partyName} has no phone number, so WhatsApp isn't available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = {
                    if (share.pdf != null) {
                        withPdf("share") { uri -> BillShare.sharePdf(context, uri!!, share.pdf.billNumber, share.message) }
                    } else {
                        withPdf("share") { BillShare.shareText(context, share.message, share.title) }
                    }
                },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy == "share") {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(if (share.pdf != null) "  Share PDF" else "  Share receipt")
            }

            InlineError(error)

            TextButton(onClick = done, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
