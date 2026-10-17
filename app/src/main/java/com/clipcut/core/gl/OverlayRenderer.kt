package com.clipcut.core.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import com.clipcut.core.model.FontFamilyOption
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TextAnimation
import com.clipcut.core.model.BrandKitConfig
import com.clipcut.core.model.BrandLogoStyle
import com.clipcut.core.model.BrandIconType

/**
 * OverlayRenderer — paints overlays (text + styling) into a reusable
 * Bitmap then uploads it as a GL texture.
 */
class OverlayRenderer(private val context: Context) {
    private var width: Int = 1920
    private var height: Int = 1080
    private var overlayTexId: Int = -1
    val textureId: Int get() = overlayTexId

    private var reusableBitmap: Bitmap? = null
    private var reusableCanvas: Canvas? = null
    private var flippedBitmap: Bitmap? = null
    private var flippedCanvas: Canvas? = null
    private val flipMatrix = android.graphics.Matrix()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun init() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        overlayTexId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private var bgTextureId: Int = -1
    private var currentBgUri: String? = null

    fun isBackgroundOverlay(overlay: OverlayItem): Boolean {
        val uri = overlay.imageUri ?: overlay.text
        return uri.contains("overlays/backgrounds/") || uri.contains("human_seg")
    }

    fun isSkyOverlay(overlay: OverlayItem): Boolean {
        val uri = overlay.imageUri ?: overlay.text
        return !isBackgroundOverlay(overlay) && (uri.contains("overlays/sky/") || uri.contains("PRESET_SYK"))
    }

    fun isFrameOverlay(overlay: OverlayItem): Boolean {
        val uri = overlay.imageUri ?: overlay.text
        return !isBackgroundOverlay(overlay) && (
            uri.contains("overlays/frames/") ||
            uri.contains("Camera") || uri.contains("Phone") ||
            uri.contains("Photo") || uri.contains("Heart") ||
            uri.contains("Circular") || uri.contains("Water") ||
            uri.contains("Square") || uri.contains("Rectangle")
        )
    }

    fun getBackgroundTexture(activeBg: OverlayItem?): Int {
        if (activeBg == null) return -1
        val uri = activeBg.imageUri ?: activeBg.text
        val bmp = getOrLoadBitmap(uri) ?: return -1
        if (bgTextureId == -1) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            bgTextureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        if (currentBgUri != uri) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            currentBgUri = uri
        }
        return bgTextureId
    }

    private val imageCache = mutableMapOf<String, Bitmap>()
    private val typefaceCache = mutableMapOf<String, Typeface>()

    private fun getTypefaceFor(option: FontFamilyOption): Typeface {
        return com.clipcut.ui.util.FontHelper.getTypeface(context, option)
    }

    private val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private fun getOrLoadBitmap(uriStr: String): Bitmap? {
        val cached = imageCache[uriStr]
        if (cached != null && !cached.isRecycled) return cached

        return try {
            val bmp = if (uriStr.startsWith("asset://") || (!uriStr.contains("://") && (uriStr.endsWith(".png") || uriStr.endsWith(".webp")))) {
                val clean = uriStr.removePrefix("asset://").removePrefix("/")
                context.assets.open(clean).use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            } else if (uriStr.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            } else {
                val path = if (uriStr.startsWith("file://")) android.net.Uri.parse(uriStr).path ?: uriStr.removePrefix("file://") else uriStr
                android.graphics.BitmapFactory.decodeFile(path)
            }
            if (bmp != null) {
                val maxDim = maxOf(bmp.width, bmp.height)
                val finalBmp = if (maxDim > 640) {
                    val s = 640f / maxDim.toFloat()
                    Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true).also {
                        if (it != bmp) bmp.recycle()
                    }
                } else bmp
                imageCache[uriStr] = finalBmp
                finalBmp
            } else null
        } catch (t: Throwable) {
            com.clipcut.core.log.AppLog.w("OverlayRenderer", "Failed loading image for blur overlay: ${t.message}")
            null
        }
    }

    fun renderOverlays(
        overlays: List<OverlayItem>,
        blurTracks: List<com.clipcut.core.model.BlurTrackItem> = emptyList(),
        currentPtsUs: Long,
        viewportWidth: Int,
        viewportHeight: Int,
        editingBlurId: String? = null,
        editingBlurX: Float? = null,
        editingBlurY: Float? = null,
        editingBlurW: Float? = null,
        editingBlurH: Float? = null,
        editingBlurRot: Float? = null,
        editingOverlayId: String? = null,
        editingOverlayX: Float? = null,
        editingOverlayY: Float? = null,
        editingOverlayScale: Float? = null,
        editingOverlayRot: Float? = null,
        brandKitConfig: com.clipcut.core.model.BrandKitConfig? = null
    ) {
        if (overlayTexId == -1) return

        if (width != viewportWidth || height != viewportHeight || reusableBitmap == null) {
            width = viewportWidth.coerceAtLeast(1)
            height = viewportHeight.coerceAtLeast(1)
            reusableBitmap?.recycle()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            reusableBitmap = bmp
            reusableCanvas = Canvas(bmp)
            flippedBitmap?.recycle()
            flippedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val bmp = reusableBitmap ?: return
        val canvas = reusableCanvas ?: return

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val activeOverlays = overlays.filter { !isBackgroundOverlay(it) && it.id != "brand_kit_watermark" && currentPtsUs in it.timelineStartUs..it.timelineEndUs }
        val activeImageBlurs = blurTracks.filter {
            it.blurType == com.clipcut.core.model.BlurType.IMAGE && currentPtsUs in it.timelineStartUs..it.timelineEndUs
        }
        val activePaintBlurs = blurTracks.filter {
            it.paintStrokes.isNotEmpty() && currentPtsUs in it.timelineStartUs..it.timelineEndUs
        }

        val hasBrandKit = brandKitConfig != null && (brandKitConfig.watermarkText.isNotBlank() || brandKitConfig.logoUri != null)
        if (activeOverlays.isEmpty() && activeImageBlurs.isEmpty() && activePaintBlurs.isEmpty() && !hasBrandKit) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            return
        }

        activeOverlays.forEach { o ->
            val isEditingThis = (o.id == editingOverlayId && editingOverlayX != null)
            val (baseX, baseY, baseScale, baseRot) = interpolatedTransform(o, currentPtsUs)
            val x = if (isEditingThis) editingOverlayX!! else baseX
            val y = if (isEditingThis) (editingOverlayY ?: baseY) else baseY
            val scale = if (isEditingThis) (editingOverlayScale ?: baseScale) else baseScale
            val rot = if (isEditingThis) (editingOverlayRot ?: baseRot) else baseRot
            val cx = (x * width).toInt()
            val cy = (y * height).toInt()

            var animAlpha = 1f
            var animOffsetX = 0f
            var animOffsetY = 0f
            var animScaleMul = 1f
            if (o.animation != TextAnimation.NONE && o.animation != TextAnimation.TYPEWRITER) {
                val animProgress = ((currentPtsUs - o.timelineStartUs).toFloat() / (o.animationDurationMs * 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                when (o.animation) {
                    TextAnimation.FADE_IN -> animAlpha = animProgress
                    TextAnimation.SLIDE_LEFT -> {
                        animOffsetX = (1f - animProgress) * 250f * (width / 1080f)
                        animAlpha = animProgress
                    }
                    TextAnimation.SLIDE_UP -> {
                        animOffsetY = (1f - animProgress) * 150f * (height / 1920f)
                        animAlpha = animProgress
                    }
                    TextAnimation.SCALE_IN -> {
                        animScaleMul = 0.2f + 0.8f * animProgress
                        animAlpha = animProgress
                    }
                    TextAnimation.BOUNCE -> {
                        val b = if (animProgress < 0.6f) animProgress * 1.8f else (1.0f + 0.15f * kotlin.math.sin(animProgress * 3.14159f))
                        animScaleMul = b.coerceIn(0.1f, 1.25f)
                    }
                    else -> {}
                }
            }

            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.color = o.textColor.toInt()
            textPaint.alpha = (255 * animAlpha.coerceIn(0f, 1f)).toInt()
            textPaint.textSize = o.textSizeSp * scale * animScaleMul * (width / 1080f)
            textPaint.isFakeBoldText = o.textBold
            textPaint.textSkewX = if (o.textItalic) -0.25f else 0f
            textPaint.typeface = getTypefaceFor(o.fontFamily)
            if (o.dropShadow) {
                textPaint.setShadowLayer(8f * (width / 1080f), 3f * (width / 1080f), 3f * (width / 1080f), 0xCC000000.toInt())
            }

            val text = applyAnimation(o, currentPtsUs)
            val assetPath = when {
                text.startsWith("asset://") || (!text.contains(" ") && (text.endsWith(".png") || text.endsWith(".webp"))) -> text
                o.imageUri != null && (o.imageUri.startsWith("asset://") || o.imageUri.endsWith(".png") || o.imageUri.endsWith(".webp")) -> o.imageUri
                else -> null
            }
            if (assetPath != null) {
                val bmp = getOrLoadBitmap(assetPath)
                if (bmp != null) {
                    val isSky = isSkyOverlay(o)
                    val isFrame = isFrameOverlay(o)

                    val crop = o.cropRect
                    val srcRect: android.graphics.Rect? = if (crop != null && crop.isValid && bmp.width > 0 && bmp.height > 0) {
                        val sLeft = (crop.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                        val sTop = (crop.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                        val sRight = (crop.right * bmp.width).toInt().coerceIn(sLeft + 1, bmp.width)
                        val sBottom = (crop.bottom * bmp.height).toInt().coerceIn(sTop + 1, bmp.height)
                        android.graphics.Rect(sLeft, sTop, sRight, sBottom)
                    } else null

                    val effectiveBmpW = (srcRect?.width() ?: bmp.width).toFloat().coerceAtLeast(1f)
                    val effectiveBmpH = (srcRect?.height() ?: bmp.height).toFloat().coerceAtLeast(1f)
                    val bmpAspect = effectiveBmpW / effectiveBmpH

                    canvas.save()
                    canvas.translate(cx.toFloat() + animOffsetX, cy.toFloat() + animOffsetY)
                    canvas.rotate(rot)

                    val rect: android.graphics.RectF
                    if (isSky) {
                        // Sky Replacement: Responsive bounding box that can be freely repositioned, scaled, and cropped
                        val skyW = width.toFloat() * scale * animScaleMul
                        val skyH = skyW / bmpAspect
                        rect = android.graphics.RectF(-skyW / 2f, -skyH / 2f, skyW / 2f, skyH / 2f)

                        val targetAlpha = (255 * animAlpha.coerceIn(0f, 1f)).toInt()
                        val saveLayerCount = canvas.saveLayer(rect, null)
                        stickerPaint.alpha = targetAlpha
                        stickerPaint.xfermode = null
                        canvas.drawBitmap(bmp, srcRect, rect, stickerPaint)

                        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                            shader = android.graphics.LinearGradient(
                                0f, rect.top + rect.height() * 0.45f, 0f, rect.bottom,
                                android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        }
                        canvas.drawRect(rect, maskPaint)
                        canvas.restoreToCount(saveLayerCount)
                    } else {
                        if (isFrame) {
                            val canvasAspect = width.toFloat() / height.toFloat()
                            val fw: Float
                            val fh: Float
                            if (canvasAspect > bmpAspect) {
                                fh = height.toFloat() * scale * animScaleMul
                                fw = fh * bmpAspect
                            } else {
                                fw = width.toFloat() * scale * animScaleMul
                                fh = fw / bmpAspect
                            }
                            rect = android.graphics.RectF(-fw / 2f, -fh / 2f, fw / 2f, fh / 2f)
                        } else {
                            val baseSz = 260f * (width / 1080f)
                            val iconW = if (bmpAspect >= 1f) baseSz * scale * animScaleMul else (baseSz * bmpAspect) * scale * animScaleMul
                            val iconH = if (bmpAspect >= 1f) (baseSz / bmpAspect) * scale * animScaleMul else baseSz * scale * animScaleMul
                            rect = android.graphics.RectF(-iconW / 2f, -iconH / 2f, iconW / 2f, iconH / 2f)
                        }
                        val targetAlpha = (255 * animAlpha.coerceIn(0f, 1f)).toInt()
                        stickerPaint.alpha = targetAlpha
                        stickerPaint.xfermode = null
                        canvas.drawBitmap(bmp, srcRect, rect, stickerPaint)
                    }
                    canvas.restore()
                }
            } else if (text.isNotEmpty()) {
                val fm = textPaint.fontMetrics
                val textWidth = textPaint.measureText(text)
                val textHeight = fm.descent - fm.ascent
                val drawX = (cx + animOffsetX) - textWidth / 2
                val drawY = (cy + animOffsetY) - textHeight / 2 - fm.ascent

                if (o.backgroundColor != 0L) {
                    bgPaint.color = o.backgroundColor.toInt()
                    bgPaint.alpha = (Color.alpha(o.backgroundColor.toInt()) * animAlpha).toInt()
                    val pad = o.backgroundPaddingPx.toFloat()
                    canvas.drawRect(
                        (cx + animOffsetX) - textWidth / 2 - pad, (cy + animOffsetY) - textHeight / 2 - pad,
                        (cx + animOffsetX) + textWidth / 2 + pad, (cy + animOffsetY) + textHeight / 2 + pad, bgPaint
                    )
                }
                canvas.save()
                canvas.rotate(rot, (cx + animOffsetX), (cy + animOffsetY))

                // Draw stroke outline if specified
                if (o.strokeWidthPx > 0f && o.strokeColor != 0L) {
                    val strokePaint = Paint(textPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = o.strokeWidthPx * (width / 1080f)
                        color = o.strokeColor.toInt()
                        alpha = (255 * animAlpha.coerceIn(0f, 1f)).toInt()
                        clearShadowLayer()
                    }
                    canvas.drawText(text, drawX, drawY, strokePaint)
                }

                canvas.drawText(text, drawX, drawY, textPaint)
                canvas.restore()
            }
        }

        // Render Blur Image / Sticker / Emoji overlays
        for (blurItem in activeImageBlurs) {
            val isEditing = (blurItem.id == editingBlurId && editingBlurX != null)
            val tf = blurItem.getTransformAt(currentPtsUs)
            val cx = (if (isEditing) editingBlurX!! else tf.normalizedX) * width
            val cy = (if (isEditing) (editingBlurY ?: tf.normalizedY) else tf.normalizedY) * height
            val bw = (if (isEditing) (editingBlurW ?: tf.width) else tf.width) * width
            val bh = (if (isEditing) (editingBlurH ?: tf.height) else tf.height) * height
            val rot = if (isEditing) (editingBlurRot ?: tf.rotation) else tf.rotation

            canvas.save()
            canvas.translate(cx, cy)
            if (rot != 0f) {
                canvas.rotate(rot)
            }

            val uri = blurItem.customImageUri ?: "emoji://sunglasses"
            if (uri.startsWith("emoji://")) {
                val emojiStr = when (uri.removePrefix("emoji://").lowercase()) {
                    "sunglasses" -> "😎"
                    "clown" -> "🤡"
                    "mask" -> "😷"
                    "cat" -> "🐱"
                    "monkey" -> "🙈"
                    "censor" -> "🚫"
                    "star" -> "⭐️"
                    "zipper" -> "🤐"
                    "robot" -> "🤖"
                    "dog" -> "🐶"
                    "heart" -> "❤️"
                    "fire" -> "🔥"
                    "poop" -> "💩"
                    else -> "😎"
                }
                val fontSize = minOf(bw, bh) * 0.90f
                stickerPaint.textSize = fontSize
                val fontMetrics = stickerPaint.fontMetrics
                val baseline = -(fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(emojiStr, 0f, baseline, stickerPaint)
            } else {
                val bmp = getOrLoadBitmap(uri)
                if (bmp != null) {
                    val dstRect = android.graphics.RectF(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f)
                    if (blurItem.shape == com.clipcut.core.model.BlurShape.OVAL) {
                        val path = android.graphics.Path().apply { addOval(dstRect, android.graphics.Path.Direction.CW) }
                        canvas.clipPath(path)
                    }
                    canvas.drawBitmap(bmp, null, dstRect, null)
                }
            }
            canvas.restore()
        }

        // Draw finger-painted censor & blackout strokes with full style support
        val strokePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        for (blurItem in activePaintBlurs) {
            val isChroma = blurItem.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT ||
                blurItem.paintStrokes.any { it.style.startsWith("CHROMA") || it.style.startsWith("STUDIO") }
            val studioUri = blurItem.customImageUri
            val studioBmp = if (!studioUri.isNullOrBlank()) getOrLoadBitmap(studioUri) else null
            val studioShader: android.graphics.BitmapShader? = if (studioBmp != null) {
                android.graphics.BitmapShader(studioBmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
                    val matrix = android.graphics.Matrix()
                    val sx = width.toFloat() / studioBmp.width.toFloat()
                    val sy = height.toFloat() / studioBmp.height.toFloat()
                    val s = maxOf(sx, sy)
                    matrix.setScale(s, s)
                    val dx = (width - studioBmp.width * s) / 2f
                    val dy = (height - studioBmp.height * s) / 2f
                    matrix.postTranslate(dx, dy)
                    setLocalMatrix(matrix)
                }
            } else null

            val isInverted = blurItem.intensity < 0f
            if (isChroma && isInverted && studioShader != null) {
                val fullPaint = android.graphics.Paint().apply {
                    shader = studioShader
                    isAntiAlias = true
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fullPaint)
                val cutPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                }
                for (stroke in blurItem.paintStrokes) {
                    if (stroke.points.isEmpty()) continue
                    val strokePx = (stroke.strokeWidthNorm * width).coerceAtLeast(6f)
                    cutPaint.strokeWidth = strokePx
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
                    for (i in 1 until stroke.points.size) {
                        path.lineTo(stroke.points[i].x * width, stroke.points[i].y * height)
                    }
                    canvas.drawPath(path, cutPaint)
                }
                continue
            }

            for (stroke in blurItem.paintStrokes) {
                if (stroke.points.isEmpty()) continue
                val strokePx = (stroke.strokeWidthNorm * width).coerceAtLeast(6f)
                strokePaint.strokeWidth = strokePx
                strokePaint.shader = null
                strokePaint.xfermode = null

                val path = android.graphics.Path()
                val first = stroke.points.first()
                path.moveTo(first.x * width, first.y * height)
                if (stroke.points.size == 1) {
                    path.lineTo(first.x * width + 0.1f, first.y * height + 0.1f)
                } else {
                    for (i in 1 until stroke.points.size) {
                        val pt = stroke.points[i]
                        path.lineTo(pt.x * width, pt.y * height)
                    }
                }

                when (stroke.style.uppercase()) {
                    "BLACKOUT" -> {
                        strokePaint.color = 0xFF000000.toInt()
                        strokePaint.alpha = 255
                        strokePaint.maskFilter = null
                        strokePaint.pathEffect = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        canvas.drawPath(path, strokePaint)
                    }
                    "MOSAIC" -> {
                        val blockSize = strokePx * 0.85f
                        val mosaicPaint = android.graphics.Paint().apply {
                            color = 0xEE202020.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val gridPaint = android.graphics.Paint().apply {
                            color = 0x44666666.toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.5f
                        }
                        for (pt in stroke.points) {
                            val qx = (kotlin.math.floor((pt.x * width) / blockSize) * blockSize)
                            val qy = (kotlin.math.floor((pt.y * height) / blockSize) * blockSize)
                            val rect = android.graphics.RectF(qx, qy, qx + blockSize, qy + blockSize)
                            canvas.drawRect(rect, mosaicPaint)
                            canvas.drawRect(rect, gridPaint)
                        }
                    }
                    "FROSTED" -> {
                        strokePaint.color = 0xF0181818.toInt()
                        strokePaint.alpha = 240
                        strokePaint.pathEffect = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        try {
                            strokePaint.maskFilter = android.graphics.BlurMaskFilter((strokePx / 3f).coerceAtLeast(2f), android.graphics.BlurMaskFilter.Blur.NORMAL)
                        } catch (_: Throwable) { strokePaint.maskFilter = null }
                        canvas.drawPath(path, strokePaint)

                        val hlPaint = android.graphics.Paint(strokePaint).apply {
                            color = 0x66FFFFFF.toInt()
                            strokeWidth = strokePx * 0.35f
                            maskFilter = null
                        }
                        canvas.drawPath(path, hlPaint)
                    }
                    "BLUR" -> {
                        strokePaint.color = 0xCC2C2C2C.toInt()
                        strokePaint.pathEffect = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        try {
                            strokePaint.maskFilter = android.graphics.BlurMaskFilter((strokePx / 2f).coerceAtLeast(2f), android.graphics.BlurMaskFilter.Blur.NORMAL)
                        } catch (_: Throwable) { strokePaint.maskFilter = null }
                        canvas.drawPath(path, strokePaint)
                    }
                    "REDACTED" -> {
                        strokePaint.color = 0xFF050505.toInt()
                        strokePaint.alpha = 255
                        strokePaint.maskFilter = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.SQUARE
                        strokePaint.pathEffect = null
                        canvas.drawPath(path, strokePaint)

                        val redLine = android.graphics.Paint().apply {
                            color = 0xFFD32F2F.toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3f * (width / 1080f)
                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
                        }
                        canvas.drawPath(path, redLine)
                    }
                    "CAUTION" -> {
                        strokePaint.color = 0xFFFFD600.toInt()
                        strokePaint.alpha = 255
                        strokePaint.maskFilter = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.SQUARE
                        strokePaint.pathEffect = null
                        canvas.drawPath(path, strokePaint)

                        val stripePaint = android.graphics.Paint(strokePaint).apply {
                            color = 0xFF000000.toInt()
                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(strokePx, strokePx), 0f)
                        }
                        canvas.drawPath(path, stripePaint)
                    }
                    "WHITEOUT" -> {
                        strokePaint.color = 0xFFFFFFFF.toInt()
                        strokePaint.alpha = 255
                        strokePaint.maskFilter = null
                        strokePaint.pathEffect = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        canvas.drawPath(path, strokePaint)
                    }
                    "CHROMA_GREEN", "GREEN_SCREEN" -> {
                        strokePaint.color = 0xFF00E676.toInt()
                        strokePaint.alpha = 255
                        strokePaint.maskFilter = null
                        strokePaint.pathEffect = null
                        strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        canvas.drawPath(path, strokePaint)
                    }
                    "STUDIO_OVERLAY", "STUDIO_BG" -> {
                        if (studioShader != null) {
                            strokePaint.shader = studioShader
                            strokePaint.alpha = 255
                            strokePaint.maskFilter = null
                            strokePaint.pathEffect = null
                            strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            canvas.drawPath(path, strokePaint)
                            strokePaint.shader = null
                        } else {
                            strokePaint.color = 0xFF00E676.toInt()
                            strokePaint.alpha = 255
                            strokePaint.maskFilter = null
                            strokePaint.pathEffect = null
                            strokePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            canvas.drawPath(path, strokePaint)
                        }
                    }
                    "ERASER" -> {
                        val eraserPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            strokeWidth = strokePx
                            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                        }
                        canvas.drawPath(path, eraserPaint)
                    }
                    else -> {
                        if (stroke.isBlackout) {
                            strokePaint.color = 0xFF000000.toInt()
                            strokePaint.maskFilter = null
                            strokePaint.pathEffect = null
                        } else {
                            strokePaint.color = 0xEE1E1E1E.toInt()
                            try {
                                strokePaint.maskFilter = android.graphics.BlurMaskFilter((strokePx / 2f).coerceAtLeast(2f), android.graphics.BlurMaskFilter.Blur.NORMAL)
                            } catch (_: Throwable) { strokePaint.maskFilter = null }
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }
        }

        // Render VN Creator Brand Kit Signature Watermark
        if (brandKitConfig != null && (brandKitConfig.watermarkText.isNotBlank() || brandKitConfig.logoUri != null)) {
            renderBrandKitWatermark(canvas, brandKitConfig, width, height)
        }

        // Upload: GLUtils.texImage2D stores row 0 at texture V=0 (bottom).
        // Canvas row 0 is visual top. Flip bitmap once vertically so texture V=0
        // corresponds to visual top. Now every caller uses IDENTITY tex-matrix!
        if (flippedBitmap == null || flippedBitmap?.width != width || flippedBitmap?.height != height) {
            flippedBitmap?.recycle()
            val newBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            flippedBitmap = newBmp
            flippedCanvas = Canvas(newBmp)
        }
        val dst = flippedBitmap ?: return
        val dstCanvas = flippedCanvas ?: Canvas(dst).also { flippedCanvas = it }
        dstCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        flipMatrix.reset()
        flipMatrix.setScale(1f, -1f)
        flipMatrix.postTranslate(0f, height.toFloat())
        dstCanvas.drawBitmap(bmp, flipMatrix, null)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, dst, 0)
    }

    private fun interpolatedTransform(o: OverlayItem, ptsUs: Long): FloatArray {
        if (o.keyframes.isEmpty()) return floatArrayOf(o.normalizedX, o.normalizedY, o.scale, o.rotation)
        val sorted = o.keyframes.sortedBy { it.timeOffsetUs }
        val relUs = ptsUs - o.timelineStartUs
        if (relUs <= sorted.first().timeOffsetUs) {
            val f = sorted.first()
            return floatArrayOf(f.normalizedX, f.normalizedY, f.scale, f.rotation)
        }
        if (relUs >= sorted.last().timeOffsetUs) {
            val f = sorted.last()
            return floatArrayOf(f.normalizedX, f.normalizedY, f.scale, f.rotation)
        }
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (relUs in a.timeOffsetUs..b.timeOffsetUs) {
                val t = (relUs - a.timeOffsetUs).toFloat() / (b.timeOffsetUs - a.timeOffsetUs).coerceAtLeast(1L)
                return floatArrayOf(
                    a.normalizedX + (b.normalizedX - a.normalizedX) * t,
                    a.normalizedY + (b.normalizedY - a.normalizedY) * t,
                    a.scale + (b.scale - a.scale) * t,
                    a.rotation + (b.rotation - a.rotation) * t
                )
            }
        }
        return floatArrayOf(o.normalizedX, o.normalizedY, o.scale, o.rotation)
    }

    private fun applyAnimation(o: OverlayItem, ptsUs: Long): String {
        if (o.animation == TextAnimation.NONE) return o.text
        val rel = (ptsUs - o.timelineStartUs).toFloat() / (o.animationDurationMs * 1000f).coerceAtLeast(1f)
        return when (o.animation) {
            TextAnimation.TYPEWRITER -> o.text.take((o.text.length * rel.coerceIn(0f, 1f)).toInt())
            else -> o.text
        }
    }

    private fun renderBrandKitWatermark(
        canvas: Canvas,
        config: BrandKitConfig,
        w: Int,
        h: Int
    ) {
        val brandText = config.watermarkText.ifEmpty { "ClipCut Creator" }
        val tagline = config.tagline.trim()
        val hasTagline = tagline.isNotEmpty()

        val baseScale = (w / 1080f) * config.watermarkScale.coerceIn(0.5f, 2.5f)
        val opacityAlpha = (config.opacity.coerceIn(0.15f, 1.0f) * 255).toInt()
        val brandColorInt = ((config.brandColor and 0x00FFFFFFL) or ((opacityAlpha.toLong()) shl 24)).toInt()

        val primaryTf = getTypefaceFor(config.fontFamily)

        val primaryTextSize = 28f * baseScale
        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(opacityAlpha, 255, 255, 255)
            textSize = primaryTextSize
            typeface = primaryTf
        }
        val textWidth = primaryPaint.measureText(brandText)

        val tagTextSize = 16f * baseScale
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((opacityAlpha * 0.75f).toInt(), 220, 225, 235)
            textSize = tagTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val tagWidth = if (hasTagline) tagPaint.measureText(tagline) else 0f

        var iconBmp: Bitmap? = null
        if (!config.logoUri.isNullOrBlank()) {
            iconBmp = getOrLoadBitmap(config.logoUri)
        }
        val hasIcon = config.iconType != BrandIconType.NONE || iconBmp != null
        val iconSize = (if (hasTagline) 34f else 28f) * baseScale

        val padH = 18f * baseScale
        val padV = 10f * baseScale
        val iconSpacing = if (hasIcon) (10f * baseScale + iconSize) else 0f
        val contentW = maxOf(textWidth, tagWidth) + iconSpacing
        val totalW = contentW + padH * 2f
        val totalH = if (hasTagline) (primaryTextSize + tagTextSize + 16f * baseScale + padV * 2f) else (primaryTextSize + 18f * baseScale + padV * 2f)

        val (normX, normY) = config.watermarkPosition
        val posX = normX * w
        val posY = normY * h

        val left = when (config.watermarkPosIndex) {
            1, 3 -> posX
            2, 4 -> posX - totalW
            else -> posX - totalW / 2f
        }.coerceIn(12f * baseScale, w - totalW - 12f * baseScale)

        val top = when (config.watermarkPosIndex) {
            1, 2, 5 -> posY
            else -> posY - totalH
        }.coerceIn(12f * baseScale, h - totalH - 12f * baseScale)

        val badgeRect = android.graphics.RectF(left, top, left + totalW, top + totalH)

        when (config.logoStyle) {
            BrandLogoStyle.NEON_PILL -> {
                val corner = totalH / 2f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((opacityAlpha * 0.85f).toInt(), 13, 17, 23)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(badgeRect, corner, corner, bgPaint)

                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brandColorInt
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * baseScale
                }
                canvas.drawRoundRect(badgeRect, corner, corner, glowPaint)
            }
            BrandLogoStyle.GLASS_CARD -> {
                val corner = 14f * baseScale
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((opacityAlpha * 0.70f).toInt(), 26, 32, 44)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(badgeRect, corner, corner, bgPaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((opacityAlpha * 0.35f).toInt(), 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f * baseScale
                }
                canvas.drawRoundRect(badgeRect, corner, corner, borderPaint)
            }
            BrandLogoStyle.MINIMAL_WATERMARK -> {
                primaryPaint.setShadowLayer(4f * baseScale, 0f, 2f * baseScale, Color.argb(opacityAlpha, 0, 0, 0))
                tagPaint.setShadowLayer(3f * baseScale, 0f, 1.5f * baseScale, Color.argb(opacityAlpha, 0, 0, 0))
            }
            BrandLogoStyle.CINEMA_STAMP -> {
                val corner = 4f * baseScale
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((opacityAlpha * 0.90f).toInt(), 8, 8, 12)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(badgeRect, corner, corner, bgPaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brandColorInt
                    style = Paint.Style.STROKE
                    strokeWidth = 1.8f * baseScale
                }
                canvas.drawRoundRect(badgeRect, corner, corner, borderPaint)
            }
            BrandLogoStyle.BADGE_CREST -> {
                val corner = 12f * baseScale
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((opacityAlpha * 0.88f).toInt(), 18, 20, 28)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(badgeRect, corner, corner, bgPaint)
                val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brandColorInt
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(android.graphics.RectF(badgeRect.left, badgeRect.top, badgeRect.left + 5f * baseScale, badgeRect.bottom), 2f, 2f, accentPaint)
            }
            BrandLogoStyle.OUTRO_SIGNATURE -> {
                primaryPaint.setShadowLayer(3f * baseScale, 0f, 2f * baseScale, Color.argb(opacityAlpha, 0, 0, 0))
                val lineY = badgeRect.bottom - padV
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brandColorInt
                    strokeWidth = 2.5f * baseScale
                }
                canvas.drawLine(badgeRect.left + padH, lineY, badgeRect.right - padH, lineY, linePaint)
            }
        }

        val iconCenterY = badgeRect.centerY()
        val iconLeft = badgeRect.left + padH
        if (hasIcon) {
            if (iconBmp != null && !iconBmp.isRecycled) {
                val destRect = android.graphics.RectF(
                    iconLeft,
                    iconCenterY - iconSize / 2f,
                    iconLeft + iconSize,
                    iconCenterY + iconSize / 2f
                )
                val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = opacityAlpha
                }
                canvas.drawBitmap(iconBmp, null, destRect, bmpPaint)
            } else {
                val circleRadius = iconSize / 2f
                val circleCenter = iconLeft + circleRadius
                val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brandColorInt
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(circleCenter, iconCenterY, circleRadius, iconBgPaint)

                val glyph = config.iconType.glyph
                if (glyph.isNotEmpty()) {
                    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(opacityAlpha, 15, 18, 24)
                        textSize = circleRadius * 1.35f
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }
                    val fm = glyphPaint.fontMetrics
                    val glyphY = iconCenterY - (fm.descent + fm.ascent) / 2f
                    canvas.drawText(glyph, circleCenter, glyphY, glyphPaint)
                }
            }
        }

        val textStartX = if (hasIcon) (iconLeft + iconSize + 10f * baseScale) else (badgeRect.left + padH)

        if (hasTagline) {
            val textTopY = badgeRect.top + padV + primaryTextSize * 0.85f
            canvas.drawText(brandText, textStartX, textTopY, primaryPaint)
            val tagY = textTopY + tagTextSize * 1.25f
            canvas.drawText(tagline, textStartX, tagY, tagPaint)
        } else {
            val fm = primaryPaint.fontMetrics
            val textY = badgeRect.centerY() - (fm.descent + fm.ascent) / 2f
            canvas.drawText(brandText, textStartX, textY, primaryPaint)
        }
    }

    fun release() {
        reusableBitmap?.recycle()
        reusableBitmap = null
        reusableCanvas = null
        flippedBitmap?.recycle()
        flippedBitmap = null
        flippedCanvas = null
        imageCache.values.forEach { try { it.recycle() } catch (_: Throwable) {} }
        imageCache.clear()
        if (overlayTexId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(overlayTexId), 0)
            overlayTexId = -1
        }
        if (bgTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(bgTextureId), 0)
            bgTextureId = -1
            currentBgUri = null
        }
    }
}
