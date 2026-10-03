package com.retailapp.android.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.retailapp.android.RetailApp
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** True when [phone] has something WhatsApp or the dialer can use. */
fun hasPhone(phone: String?): Boolean = phone?.any(Char::isDigit) == true

/** Digits for wa.me / WhatsApp's jid. A bare 10-digit number is taken as Indian. */
fun whatsAppDigits(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length == 10) "91$digits" else digits
}

/**
 * Bill PDF download and the share / WhatsApp intents, shared by the bill screen and the Cycle 5
 * "saved" sheet. Everything here can throw (no app for the intent, disk full, ...), so callers
 * get a [Result] or a Boolean instead of a crash.
 */
object BillShare {
    /** Downloads a sale or purchase bill PDF into the cache and returns a shareable content:// Uri. */
    suspend fun downloadPdf(isSale: Boolean, id: Int, billNumber: String): Result<Uri> =
        NetworkModule.safeCall {
            if (isSale) NetworkModule.billApi.getSalePdf(id) else NetworkModule.billApi.getPurchasePdf(id)
        }.mapCatching { body ->
            val app = RetailApp.instance
            val dir = File(app.cacheDir, "bills").apply { mkdirs() }
            val file = File(dir, "$billNumber.pdf")
            // The body is @Streaming, so reading it is network I/O and must stay off the main thread.
            withContext(Dispatchers.IO) {
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        }

    /** The system share sheet for a PDF (and optional text). */
    fun sharePdf(context: Context, pdf: Uri, title: String, text: String? = null): Result<Unit> = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdf)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    /** The system share sheet for plain text (a payment receipt has no PDF). */
    fun shareText(context: Context, text: String, title: String): Result<Unit> = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Opens WhatsApp (or WhatsApp Business) straight to [phone]'s chat with [text] and, if given,
     * the [pdf] attached. The user still taps Send - WhatsApp never lets an app send on its own.
     * Falls back to a wa.me link (text only) when neither app takes the attachment.
     */
    fun whatsApp(context: Context, phone: String, text: String, pdf: Uri? = null): Result<Unit> {
        val digits = whatsAppDigits(phone)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                setPackage(pkg)
                type = if (pdf != null) "application/pdf" else "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (pdf != null) {
                    putExtra(Intent.EXTRA_STREAM, pdf)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // Not a public extra, but WhatsApp honours it to open this chat directly
                // instead of its contact picker.
                putExtra("jid", "$digits@s.whatsapp.net")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return Result.success(Unit)
            } catch (e: ActivityNotFoundException) {
                // Not installed - try the next one.
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        return runCatching {
            val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(text)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
