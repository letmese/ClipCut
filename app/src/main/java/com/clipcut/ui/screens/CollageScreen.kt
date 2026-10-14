package com.clipcut.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private enum class CollageLayout(val label: String, val cols: Int, val rows: Int) {
    GRID_2X2("2 x 2", 2, 2),
    GRID_3("3 across", 3, 1),
    HORIZONTAL_2("Side by side", 2, 1),
    VERTICAL_2("Stacked", 1, 2)
}

/**
 * CollageScreen — on-device photo & video collage builder: pick 2-4 photos, choose a
 * layout, render a stitched collage bitmap, and save to gallery.
 */
@Composable
fun CollageScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var collage by remember { mutableStateOf<Bitmap?>(null) }
    var layout by remember { mutableStateOf(CollageLayout.GRID_2X2) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val decoded = uris.mapNotNull { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) { null }
        }
        if (decoded.isNotEmpty()) {
            images = decoded.take(4)
            Toast.makeText(context, "Loaded ${images.size} photo(s)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No images decoded", Toast.LENGTH_SHORT).show()
        }
    }

    fun render() {
        val src = images
        if (src.isEmpty()) return
        val cols = layout.cols
        val rows = layout.rows
        val cellW = 1200 / cols
        val cellH = (if (rows == 1) 1200 / cols else 1200 / rows) * 2
        val out = Bitmap.createBitmap(cellW * cols, cellH * rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val gap = 12
        val cellW2 = cellW - gap
        val cellH2 = cellH - gap
        src.take(cols * rows).forEachIndexed { idx, bmp ->
            val cx = (idx % cols) * cellW
            val cy = (idx / cols) * cellH
            val rect = Rect(cx + gap / 2, cy + gap / 2, cx + cellW2, cy + cellH2)
            val scale = maxOf(rect.width().toFloat() / bmp.width, rect.height().toFloat() / bmp.height)
            val sw = (bmp.width * scale).toInt()
            val sh = (bmp.height * scale).toInt()
            val sx = (sw - rect.width()) / 2
            val sy = (sh - rect.height()) / 2
            canvas.drawBitmap(bmp, Rect(sx, sy, sx + rect.width(), sy + rect.height()), rect, paint)
        }
        collage = out
    }

    LaunchedEffect(images, layout) { render() }

    fun save() {
        val bmp = collage ?: return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "clipcut_collage_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipCut")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            Toast.makeText(context, "Collage saved to Pictures/ClipCut", Toast.LENGTH_SHORT).show()
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
                    Text("Collage Maker", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick", tint = AccentPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Pick 2-4", color = AccentPrimary)
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
                val result = collage
                if (result != null) {
                    Image(
                        bitmap = result.asImageBitmap(),
                        contentDescription = "Collage preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Pick photos to build a collage", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Layout", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollageLayout.entries.forEach { l ->
                    FilterChip(
                        selected = l == layout,
                        onClick = { layout = l },
                        label = { Text(l.label, fontSize = 13.sp) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Button(
                    onClick = { save() },
                    enabled = collage != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Save Collage to Gallery", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
