package com.glassous.aimage.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    imageUrl: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 旋转与缩放状态
    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = offset + panChange
    }

    fun saveImageToAlbum(context: Context, url: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val bitmap: Bitmap? = when {
                        url.startsWith("data:") -> {
                            // data URL: data:<mime>;base64,<data>
                            val comma = url.indexOf(',')
                            if (comma > 0) {
                                val base64 = url.substring(comma + 1)
                                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } else null
                        }
                        url.startsWith("content://") || url.startsWith("file://") -> {
                            context.contentResolver.openInputStream(Uri.parse(url))?.use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                        }
                        else -> {
                            java.net.URL(url).openStream().use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                        }
                    }
                    if (bitmap == null) return@withContext false

                    val filename = "AImage_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".jpg"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AImage")
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri == null) return@withContext false
                    resolver.openOutputStream(uri)?.use { out ->
                        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        out.flush()
                        ok
                    } ?: false
                } catch (se: SecurityException) {
                    false
                } catch (_: Exception) {
                    false
                }
            }
            Toast.makeText(context, if (result) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "图片预览",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(
                            imageVector = Icons.Filled.RotateRight,
                            contentDescription = "旋转图片"
                        )
                    }
                    IconButton(onClick = { saveImageToAlbum(context, imageUrl) }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "保存到本地"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            var loadError by remember(imageUrl) { mutableStateOf(false) }
            AsyncImage(
                model = imageUrl,
                contentDescription = "预览图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        rotationZ = rotation
                        clip = true
                    }
                    .transformable(transformState),
                contentScale = ContentScale.Fit,
                onError = { _ -> loadError = true },
                onSuccess = { _ -> loadError = false }
            )

            if (loadError) {
                Text(
                    text = "图片加载失败，请重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}