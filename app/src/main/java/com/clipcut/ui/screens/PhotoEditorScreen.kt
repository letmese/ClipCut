package com.clipcut.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.ui.theme.*

/**
 * PhotoEditorScreen — on-device photo editor studio:
 * adjust brightness / contrast / saturation, apply sepia or invert, preview instantly,
 * and save the high-res result to the device gallery.
 */
@Composable
fun PhotoEditorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var original by rememberSaveable { mutableStateOf<Bitmap?>(null) }
    var preview by rememberSaveable { mutableStateOf<Bitmap?>(null) }
    var brightness by rememberSaveable { mutableStateOf(0f) }
    var contrast by rememberSaveable { mutableStateOf(1f) }
    var saturation by rememberSaveable { mutableStateOf(1f) }
    var sepia by rememberSaveable { mutableStateOf(false) }
    var invert by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) { null }
            if (bmp != null) {
                original = bmp
                preview = bmp
                Toast.makeText(context, "Image loaded (${bmp.width}x${bmp.height})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not decode image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun process() {
        val src = original ?: return
        val out = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = ColorMatrix()

        // Contrast (scale around 128 midpoint)
        val c = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, 128f * (1f - contrast),
            0f, contrast, 0f, 0f, 128f * (1f - contrast),
            0f, 0f, contrast, 0f, 128f * (1f - contrast),
            0f, 0f, 0f, 1f, 0f
        ))
        m.postConcat(c)

        // Saturation
        m.postConcat(ColorMatrix().apply { setSaturation(saturation) })

        // Brightness
        val bScale = 1f + brightness / 100f
        val b = ColorMatrix(floatArrayOf(
            bScale, 0f, 0f, 0f, brightness * 1.4f,
            0f, bScale, 0f, 0f, brightness * 1.4f,
            0f, 0f, bScale, 0f, brightness * 1.4f,
            0f, 0f, 0f, 1f, 0f
        ))
        m.postConcat(b)

        // Sepia
        if (sepia) {
            val s = ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(s)
        }

        // Invert
        if (invert) {
            val i = ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(i)
        }

        paint.colorFilter = ColorMatrixColorFilter(m)
        canvas.drawBitmap(src, 0f, 0f, paint)
        preview = out
    }

    LaunchedEffect(brightness, contrast, saturation, sepia, invert) {
        if (original != null) process()
    }

    fun saveToGallery() {
        val bmp = preview ?: return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "clipcut_photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipCut")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            Toast.makeText(context, "Saved to Pictures/ClipCut", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Surface(color = Surface1, tonalElevation = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Photo Studio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick", tint = AccentPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Pick Photo", color = AccentPrimary)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).background(Surface2, RoundedCornerShape(16.dp)).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val current = preview
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No image loaded", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap 'Pick Photo' above to begin editing", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SliderRow("Brightness", brightness, -100f, 100f) { brightness = it }
            SliderRow("Contrast", contrast, 0.3f, 2.5f) { contrast = it }
            SliderRow("Saturation", saturation, 0f, 2f) { saturation = it }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = sepia,
                    onClick = { sepia = !sepia },
                    label = { Text("Sepia", fontSize = 13.sp) }
                )
                FilterChip(
                    selected = invert,
                    onClick = { invert = !invert },
                    label = { Text("Invert", fontSize = 13.sp) }
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Button(
                    onClick = { saveToGallery() },
                    enabled = preview != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save", tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Gallery", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChanged: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value.toString().take(4), color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onChanged, valueRange = min..max)
    }
}
