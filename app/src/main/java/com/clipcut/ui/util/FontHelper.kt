package com.clipcut.ui.util

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import com.clipcut.core.model.FontFamilyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * FontHelper provides cached resolution of FontFamilyOption to both native
 * Android Typeface (for OpenGL / Canvas rendering) and Compose FontFamily (for UI previews),
 * with intelligent script-aware fallback for Urdu and Arabic scripts.
 */
object FontHelper {
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val fontFamilyCache = ConcurrentHashMap<FontFamilyOption, FontFamily>()

    /**
     * Detects if the given text contains any Arabic or Urdu Unicode characters
     * (covering Arabic, Arabic Supplement, Extended-A/B, and Presentation Forms).
     */
    fun containsArabicOrUrdu(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        for (i in 0 until text.length) {
            val code = text[i].code
            if (code in 0x0600..0x08FF || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF) {
                return true
            }
        }
        return false
    }

    /**
     * Detects Urdu-specific characters like ٹ, ڈ, ڑ, ں, ے, ہ, گ, چ, پ
     */
    fun isUrduSpecific(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        for (i in 0 until text.length) {
            val code = text[i].code
            if (code == 0x0679 || code == 0x0688 || code == 0x0691 || code == 0x06BA ||
                code == 0x06D2 || code == 0x06C1 || code == 0x06AF || code == 0x0686 || code == 0x067E) {
                return true
            }
        }
        return false
    }

    fun isArabicOrUrduFont(option: FontFamilyOption): Boolean {
        return option == FontFamilyOption.URDU_NASTALIQ ||
               option == FontFamilyOption.ARABIC_AMIRI ||
               option == FontFamilyOption.ARABIC_MODERN ||
               option == FontFamilyOption.ARABIC_KUFI
    }

    /**
     * Resolves the font option with fallback if text contains Arabic/Urdu script
     * and the selected font cannot render Arabic/Urdu glyphs.
     */
    fun resolveFontOption(option: FontFamilyOption, sampleText: CharSequence? = null): FontFamilyOption {
        if (!sampleText.isNullOrEmpty() && containsArabicOrUrdu(sampleText)) {
            if (!isArabicOrUrduFont(option)) {
                return if (isUrduSpecific(sampleText)) {
                    FontFamilyOption.URDU_NASTALIQ
                } else {
                    FontFamilyOption.ARABIC_MODERN
                }
            }
        }
        return option
    }

    /**
     * Returns an appropriate preview string for font selection widgets.
     */
    fun getPreviewSample(option: FontFamilyOption, userText: String? = null): String {
        if (!userText.isNullOrBlank()) {
            return userText.take(12)
        }
        return when (option) {
            FontFamilyOption.URDU_NASTALIQ -> "نستعلیق Urdu"
            FontFamilyOption.ARABIC_AMIRI -> "اميري خط"
            FontFamilyOption.ARABIC_MODERN -> "عربي Modern"
            FontFamilyOption.ARABIC_KUFI -> "كوفي Bold"
            else -> "Style Aa"
        }
    }

    fun getTypeface(context: Context, option: FontFamilyOption, sampleText: CharSequence? = null): Typeface {
        val effectiveOption = resolveFontOption(option, sampleText)
        val asset = effectiveOption.assetPath ?: return when (effectiveOption) {
            FontFamilyOption.SERIF -> Typeface.SERIF
            FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
            FontFamilyOption.SANS_SERIF_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        return typefaceCache.getOrPut(asset) {
            try {
                Typeface.createFromAsset(context.assets, asset)
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }
        }
    }

    fun getFontFamily(context: Context, option: FontFamilyOption, sampleText: CharSequence? = null): FontFamily {
        val effectiveOption = resolveFontOption(option, sampleText)
        return fontFamilyCache.getOrPut(effectiveOption) {
            val tf = getTypeface(context, effectiveOption)
            try {
                FontFamily(tf)
            } catch (_: Throwable) {
                when (effectiveOption) {
                    FontFamilyOption.SERIF -> FontFamily.Serif
                    FontFamilyOption.MONOSPACE -> FontFamily.Monospace
                    FontFamilyOption.SANS_SERIF_CONDENSED -> FontFamily.SansSerif
                    else -> FontFamily.Default
                }
            }
        }
    }
}
