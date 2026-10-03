package com.retailapp.android.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.retailapp.android.data.model.Product

/**
 * Cycle 5 barcode/SKU scanning for the sale and purchase forms. Returns a function that opens
 * the Google code scanner (it runs inside Play services, so no camera permission is needed).
 *
 * A code that exactly matches a product's SKU calls [onMatch], gives a short vibration and opens
 * the scanner again straight away, so a counter can scan item after item until the user closes
 * it. Anything else stops scanning and goes to [onNoMatch].
 */
@Composable
fun rememberSkuScanner(products: List<Product>, onMatch: (Product) -> Unit, onNoMatch: (code: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentProducts by rememberUpdatedState(products)
    val currentOnMatch by rememberUpdatedState(onMatch)
    val currentOnNoMatch by rememberUpdatedState(onNoMatch)
    return remember(context) {
        val scanner = GmsBarcodeScanning.getClient(context)
        lateinit var scan: () -> Unit
        scan = {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val code = barcode.rawValue?.trim().orEmpty()
                    val product = currentProducts.find { !it.isArchived && it.Sku.trim().equals(code, ignoreCase = true) }
                    when {
                        product != null -> {
                            vibrate(context)
                            currentOnMatch(product)
                            scan()
                        }
                        code.isNotEmpty() -> currentOnNoMatch(code)
                    }
                }
                // Cancelling (back / close) is the normal way out and needs no message.
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Scanner not available: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        scan
    }
}

private fun vibrate(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(60)
        }
    } catch (e: Exception) {
        // No vibrator - the added line on the form is feedback enough.
    }
}

/**
 * Scanner wiring for a bill form: returns the function the scan button calls, and shows the
 * "No product with code …" dialog (Search / Add product) itself. [onAddProduct] is null for
 * staff - only an admin can add a product with the scanned code as its SKU.
 */
@Composable
fun rememberBillScanner(
    products: List<Product>,
    onAdd: (Product) -> Unit,
    onAddProduct: ((code: String) -> Unit)?,
): () -> Unit {
    var noMatch by remember { mutableStateOf<String?>(null) }
    var searchFor by remember { mutableStateOf<String?>(null) }
    val scan = rememberSkuScanner(products, onMatch = onAdd, onNoMatch = { noMatch = it })

    noMatch?.let { code ->
        AlertDialog(
            onDismissRequest = { noMatch = null },
            title = { Text("Not found") },
            text = { Text("No product with code $code") },
            confirmButton = {
                TextButton(onClick = {
                    noMatch = null
                    searchFor = code
                }) { Text("Search") }
            },
            dismissButton = {
                Row {
                    if (onAddProduct != null) {
                        TextButton(onClick = {
                            noMatch = null
                            onAddProduct(code)
                        }) { Text("Add product") }
                    }
                    TextButton(onClick = { noMatch = null }) { Text("Cancel") }
                }
            },
        )
    }
    searchFor?.let { code ->
        SearchablePickerDialog(
            title = "Product",
            options = products.filter { !it.isArchived },
            optionLabel = { it.Name },
            optionDetail = { productDetail(it) },
            initialQuery = code,
            onSelect = onAdd,
            onDismiss = { searchFor = null },
        )
    }
    return scan
}

/** The barcode icon placed next to "+ Add item" on the sale and purchase forms. */
@Composable
fun ScanButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
    }
}
