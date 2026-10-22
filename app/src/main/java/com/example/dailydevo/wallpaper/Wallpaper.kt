package com.example.dailydevo.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.WindowManager
import com.example.dailydevo.data.VerseEntity
import java.io.IOException

data class WallpaperContent(val verse: VerseEntity, val text: String, val languageLabel: String, val sourceNotice: String? = null)
sealed interface ApplyResult {
    data object Applied : ApplyResult
    data class Failed(val reason: String) : ApplyResult
}

class WallpaperRenderer(private val context: Context) {
    fun render(content: WallpaperContent, dark: Boolean): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(720)
        val height = metrics.heightPixels.coerceAtLeast(1280)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = if (dark) Color.rgb(25, 34, 44) else Color.rgb(246, 241, 232)
        val ink = if (dark) Color.rgb(246, 241, 232) else Color.rgb(44, 49, 49)
        val gold = if (dark) Color.rgb(204, 169, 112) else Color.rgb(158, 113, 53)
        canvas.drawColor(paper)
        val widthF = width.toFloat()
        val safe = widthF * .12f
        val ornament = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; alpha = 65; style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas.drawCircle(widthF - safe, height * .21f, widthF * .34f, ornament)
        canvas.drawCircle(widthF - safe, height * .21f, widthF * .28f, ornament)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; textSize = widthF * .035f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        canvas.drawText(content.verse.collection.uppercase(), safe, height * .25f, title)
        val versePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = widthF * if (content.text.length > 100) .048f else .064f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL); textAlign = Paint.Align.CENTER
        }
        val verseText = content.text.ifBlank { content.verse.originalText }
        val maxWidth = width - (safe * 2).toInt()
        val layout = if (Build.VERSION.SDK_INT >= 23) android.text.StaticLayout.Builder.obtain(verseText, 0, verseText.length, android.text.TextPaint(versePaint), maxWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setLineSpacing(widthF * .012f, 1f).setIncludePad(true)
            .setMaxLines(8).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        else @Suppress("DEPRECATION") android.text.StaticLayout(verseText, android.text.TextPaint(versePaint), maxWidth, android.text.Layout.Alignment.ALIGN_CENTER, 1.08f, widthF * .012f, true)
        // Keep the main text above typical home-screen icon rows.
        val desiredCenter = height * .44f
        canvas.save()
        canvas.translate(widthF / 2f, (desiredCenter - layout.height / 2f).coerceAtLeast(height * .31f))
        layout.draw(canvas)
        canvas.restore()
        val attribution = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; textSize = widthF * .034f; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        canvas.drawText("— ${content.verse.source} ${content.verse.reference} —", widthF / 2f, height * .70f, attribution)
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 130; textSize = widthF * .026f; textAlign = Paint.Align.CENTER }
        canvas.drawText("A moment for reflection", widthF / 2f, height * .75f, footer)
        return bitmap
    }
}

class WallpaperApplier(context: Context) {
    private val manager = WallpaperManager.getInstance(context)
    fun apply(bitmap: Bitmap, target: String): ApplyResult = try {
        val flags = when (target) {
            "lock" -> WallpaperManager.FLAG_LOCK
            "both" -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM
        }
        manager.setBitmap(bitmap, null, true, flags)
        ApplyResult.Applied
    } catch (e: SecurityException) {
        ApplyResult.Failed("Android denied the wallpaper change: ${e.localizedMessage ?: "security restriction"}")
    } catch (e: IOException) {
        ApplyResult.Failed("Wallpaper could not be written: ${e.localizedMessage ?: "storage error"}")
    } catch (e: IllegalArgumentException) {
        ApplyResult.Failed("This wallpaper target is unsupported: ${e.localizedMessage ?: target}")
    } catch (e: RuntimeException) {
        ApplyResult.Failed("Wallpaper change failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
    }
}
