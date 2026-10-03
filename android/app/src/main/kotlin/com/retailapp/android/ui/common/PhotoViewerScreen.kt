@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.retailapp.android.ui.common

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.retailapp.android.data.model.Attachment
import com.retailapp.android.data.remote.AttachmentEntity
import com.retailapp.android.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

class PhotoViewerViewModel(private val entity: AttachmentEntity, private val entityId: Int) : ViewModel() {
    class Factory(private val entity: AttachmentEntity, private val id: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PhotoViewerViewModel(entity, id) as T
    }

    var photos by mutableStateOf<List<Attachment>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            NetworkModule.safeCall { NetworkModule.attachmentApi.list(entity.path, entityId) }
                .onSuccess { photos = it }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }
}

/**
 * Full-screen proof-photo viewer for one sale/purchase/payment: swipe between its photos,
 * pinch or double-tap to zoom, and share a photo straight back to the customer or supplier.
 * [title] describes the record ("Sale S-0142 · ₹6,000") so the photo is never out of context.
 */
@Composable
fun PhotoViewerScreen(
    entity: AttachmentEntity,
    entityId: Int,
    startIndex: Int,
    title: String,
    onBack: () -> Unit,
) {
    val viewModel: PhotoViewerViewModel = viewModel(
        key = "viewer-${entity.name}-$entityId",
        factory = PhotoViewerViewModel.Factory(entity, entityId),
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareError by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(initialPage = startIndex) { viewModel.photos.size }
    val current = viewModel.photos.getOrNull(pagerState.currentPage)

    fun share(photo: Attachment) {
        scope.launch {
            shareError = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(NetworkModule.absoluteUrl(photo.url)).build()
                    NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                        File(dir, "proof_${photo.id}.jpg").apply {
                            outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = photo.content_type
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share photo"))
            } catch (e: Exception) {
                shareError = "Couldn't share the photo: ${e.message}"
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (viewModel.photos.isNotEmpty()) {
                            Text(
                                "${pagerState.currentPage + 1} of ${viewModel.photos.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { share(current) }) { Icon(Icons.Default.Share, contentDescription = "Share photo") }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                viewModel.errorMessage != null ->
                    ErrorBox(viewModel.errorMessage!!, onRetry = viewModel::load)
                viewModel.photos.isEmpty() ->
                    Text("No photos on this record.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    ZoomableImage(NetworkModule.absoluteUrl(viewModel.photos[page].url))
                }
            }

            if (current != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                ) {
                    val uploader = current.uploaded_by_name.ifBlank { "someone" }
                    Text("Uploaded by $uploader", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(displayDateTime(current.created_at), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    shareError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/**
 * Pinch/double-tap zoom. Panning is only claimed while zoomed in, so at 1x a horizontal swipe
 * still reaches the pager and moves to the next photo.
 */
@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    AsyncImage(
        model = url,
        imageLoader = NetworkModule.imageLoader,
        contentDescription = "Photo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .transformable(state = state, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
