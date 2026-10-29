package com.example.data.files

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** Prints caption lines on a dark band across the bottom of a photo, so they stay with it when shared. */
object PhotoStamp {
    fun draw(source: Bitmap, lines: List<String>): Bitmap {
        val bitmap = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true).also { source.recycle() }
        val canvas = Canvas(bitmap)
        val textSize = (bitmap.width.coerceAtMost(bitmap.height) / 22f).coerceAtLeast(18f)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val pad = textSize * 0.5f
        val lineHeight = textSize * 1.25f
        val bandTop = bitmap.height - (lines.size * lineHeight + pad * 2)
        val band = Paint().apply { color = android.graphics.Color.BLACK; alpha = 150 }
        canvas.drawRect(0f, bandTop, bitmap.width.toFloat(), bitmap.height.toFloat(), band)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, pad, bandTop + pad + textSize + i * lineHeight, text)
        }
        return bitmap
    }
}
