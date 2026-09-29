package com.retailapp.android.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.retailapp.android.RetailApp
import com.retailapp.android.data.model.Attachment
import com.retailapp.android.data.model.DeleteAttachmentRequest
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.data.remote.NetworkModule
import com.retailapp.android.session.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

const val MAX_PHOTOS = 5

/**
 * Shrinks a picked/captured photo before upload: at most 1600px on the long edge, JPEG 80,
 * rotated upright from its EXIF orientation. A phone camera photo goes from several MB to a few
 * hundred KB, which keeps uploads quick on a shop's mobile data.
 */
object ImageCompressor {
    private const val MAX_EDGE = 1600

    fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        var bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read the image")

        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longEdge
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }

        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        }

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }
}

object PhotoUploader {
    /** Compresses and uploads each photo in turn. Returns the ones that failed. */
    suspend fun upload(
        entity: AttachmentEntity,
        id: Int,
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Uri> {
        val failed = mutableListOf<Uri>()
        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size)
            val ok = try {
                val bytes = withContext(Dispatchers.IO) { ImageCompressor.compress(RetailApp.instance, uri) }
                val part = MultipartBody.Part.createFormData(
                    "file",
                    "photo.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType()),
                )
                NetworkModule.safeCall { NetworkModule.attachmentApi.upload(entity.path, id, part) }.isSuccess
            } catch (e: Exception) {
                false
            }
            if (!ok) failed += uri
        }
        onProgress(uris.size, uris.size)
        return failed
    }
}

/**
 * Photos that didn't upload right after a sale/purchase/payment was saved. The record is saved
 * either way; its detail screen picks these up and offers a retry (Cycle 4 section 6.3).
 */
object PendingUploads {
    private val pending = mutableMapOf<String, List<Uri>>()

    private fun key(entity: AttachmentEntity, id: Int) = "${entity.name}:$id"

    fun put(entity: AttachmentEntity, id: Int, uris: List<Uri>) {
        if (uris.isNotEmpty()) pending[key(entity, id)] = uris
    }

    fun take(entity: AttachmentEntity, id: Int): List<Uri> = pending.remove(key(entity, id)).orEmpty()
}

/** A content:// URI for a fresh camera capture file under cache/photos (see file_paths.xml). */
private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * "Add photo" button that offers Camera or Gallery and reports the chosen URIs. [remaining]
 * caps how many more can be added so a record never goes over [MAX_PHOTOS].
 */
@Composable
fun AddPhotoButton(remaining: Int, onPicked: (List<Uri>) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null) onPicked(listOf(Uri.parse(uri)))
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris.take(remaining))
    }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { menuOpen = true }, enabled = enabled && remaining > 0) {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (remaining > 0) "  Add photo" else "  $MAX_PHOTOS photos max")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Camera") },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    try {
                        val uri = newCaptureUri(context)
                        captureUri = uri.toString()
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        // No camera app, or storage unavailable - the gallery option still works.
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Gallery") },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

@Composable
private fun Thumbnail(model: Any, onClick: (() -> Unit)?, onRemove: (() -> Unit)?, dimmed: Boolean = false) {
    Box(modifier = Modifier.size(84.dp)) {
        AsyncImage(
            model = model,
            imageLoader = NetworkModule.imageLoader,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            alpha = if (dimmed) 0.4f else 1f,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Photo picker for a form that hasn't been saved yet (new payment/sale/purchase). Photos are
 * held as local URIs and uploaded by the form once the record exists.
 */
@Composable
fun PhotoPickerRow(photos: List<Uri>, onPhotosChange: (List<Uri>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Photos (optional)", style = MaterialTheme.typography.titleSmall)
                Text("Receipt, UPI screenshot, cheque or bill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AddPhotoButton(remaining = MAX_PHOTOS - photos.size, onPicked = { onPhotosChange((photos + it).distinct().take(MAX_PHOTOS)) })
        }
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos) { _, uri ->
                    Thumbnail(model = uri, onClick = null, onRemove = { onPhotosChange(photos - uri) })
                }
            }
        }
    }
}

class AttachmentsViewModel(private val entity: AttachmentEntity, private val entityId: Int) : ViewModel() {
    class Factory(private val entity: AttachmentEntity, private val id: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AttachmentsViewModel(entity, id) as T
    }

    var attachments by mutableStateOf<List<Attachment>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var uploadProgress by mutableStateOf<String?>(null)
        private set

    /** Photos that failed to upload and can be retried. */
    var failed by mutableStateOf(PendingUploads.take(entity, entityId))
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            NetworkModule.safeCall { NetworkModule.attachmentApi.list(entity.path, entityId) }
                .onSuccess { attachments = it; errorMessage = null }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || uploadProgress != null) return
        viewModelScope.launch {
            errorMessage = null
            val stillFailed = PhotoUploader.upload(entity, entityId, uris) { done, total ->
                uploadProgress = if (done < total) "Uploading ${done + 1} of $total…" else null
            }
            uploadProgress = null
            failed = stillFailed
            load()
        }
    }

    fun retryFailed() = upload(failed)

    fun discardFailed() {
        failed = emptyList()
    }

    fun delete(attachment: Attachment, reason: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            NetworkModule.safeCall {
                NetworkModule.attachmentApi.delete(entity.path, entityId, attachment.id, DeleteAttachmentRequest(reason))
            }
                .onSuccess { attachments = attachments - attachment; onDone(true) }
                .onFailure { errorMessage = it.message; onDone(false) }
        }
    }
}

/**
 * Proof photos already saved on a record: thumbnails (tap to open the full-screen viewer), an
 * "Add photo" button, a retry banner for photos that failed to upload, and - for a Company
 * Admin only, matching the backend - delete with a reason.
 */
@Composable
fun AttachmentsSection(
    entity: AttachmentEntity,
    entityId: Int,
    onOpenPhoto: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    canAdd: Boolean = true,
) {
    val viewModel: AttachmentsViewModel = viewModel(
        key = "attachments-${entity.name}-$entityId",
        factory = AttachmentsViewModel.Factory(entity, entityId),
    )
    var pendingDelete by remember { mutableStateOf<Attachment?>(null) }

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Photos (${viewModel.attachments.size})", style = MaterialTheme.typography.titleMedium)
                if (canAdd) {
                    AddPhotoButton(
                        remaining = MAX_PHOTOS - viewModel.attachments.size,
                        enabled = viewModel.uploadProgress == null,
                        onPicked = viewModel::upload,
                    )
                }
            }

            if (viewModel.failed.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "  ${viewModel.failed.size} photo(s) not uploaded",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retryFailed, enabled = viewModel.uploadProgress == null) { Text("Retry") }
                    IconButton(onClick = viewModel::discardFailed) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
                }
            }

            viewModel.uploadProgress?.let { progress ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(progress, style = MaterialTheme.typography.bodySmall)
                }
            }

            when {
                viewModel.isLoading && viewModel.attachments.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                viewModel.attachments.isEmpty() && viewModel.failed.isEmpty() ->
                    Text(
                        "No photos yet. Add the bill, receipt or payment screenshot so it can be checked later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(viewModel.attachments, key = { _, a -> a.id }) { index, attachment ->
                        Thumbnail(
                            model = NetworkModule.absoluteUrl(attachment.url),
                            onClick = { onOpenPhoto(index) },
                            onRemove = if (Session.isCompanyAdmin) ({ pendingDelete = attachment }) else null,
                        )
                    }
                    itemsIndexed(viewModel.failed) { _, uri ->
                        Thumbnail(model = uri, onClick = null, onRemove = null, dimmed = true)
                    }
                }
            }
            InlineError(viewModel.errorMessage)
        }
    }

    pendingDelete?.let { attachment ->
        DeletePhotoDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = { reason -> viewModel.delete(attachment, reason) { if (it) pendingDelete = null } },
        )
    }
}

@Composable
private fun DeletePhotoDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text("Delete this photo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Photos are proof of payment. The deletion is recorded in the audit log with your reason.")
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { onConfirm(reason.trim()) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
