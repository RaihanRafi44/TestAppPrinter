package com.raihan.testappprinter.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageProcessor {

    /**
     * Converts a bitmap to a 1-bit monochrome bitmap using Floyd-Steinberg error diffusion dithering.
     */
    fun applyFloydSteinbergDithering(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        original.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                gray[y][x] = (0.299f * r + 0.587f * g + 0.114f * b)
            }
        }

        val outPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldVal = gray[y][x]
                val newVal = if (oldVal < 128f) 0f else 255f
                val error = oldVal - newVal

                outPixels[y * width + x] = if (newVal == 0f) Color.BLACK else Color.WHITE

                if (x + 1 < width) {
                    gray[y][x + 1] += error * (7f / 16f)
                }
                if (y + 1 < height) {
                    if (x > 0) {
                        gray[y + 1][x - 1] += error * (3f / 16f)
                    }
                    gray[y + 1][x] += error * (5f / 16f)
                    if (x + 1 < width) {
                        gray[y + 1][x + 1] += error * (1f / 16f)
                    }
                }
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Generates a sample Photobooth strip image (width: 800 dots ~ 100mm @ 203dpi)
     */
    fun createSamplePhotoboothStrip(width: Int = 800): Bitmap {
        val frameHeight = (width * 0.65).toInt()
        val headerHeight = 120
        val footerHeight = 150
        val photoCount = 3
        val spacing = 25

        val totalHeight = headerHeight + (photoCount * frameHeight) + ((photoCount + 1) * spacing) + footerHeight

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Header
        paint.color = Color.BLACK
        paint.textSize = 44f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PHOTOBOOTH TEST", width / 2f, 65f, paint)

        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Xprinter XP-410B / XP-4601B (203 DPI)", width / 2f, 100f, paint)

        // Draw 3 Photo Frames
        var currentY = headerHeight + spacing
        val frameLeft = 50
        val frameRight = width - 50
        val frameWidth = frameRight - frameLeft

        val strokePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        for (i in 1..photoCount) {
            // Frame Border
            canvas.drawRect(
                frameLeft.toFloat(),
                currentY.toFloat(),
                frameRight.toFloat(),
                (currentY + frameHeight).toFloat(),
                strokePaint
            )

            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            val centerX = (frameLeft + frameRight) / 2f
            val centerY = currentY + frameHeight / 2f
            val maxRadius = frameWidth / 2.3f

            for (r in 10 downTo 1) {
                fillPaint.color = if (r % 2 == 0) Color.DKGRAY else Color.LTGRAY
                canvas.drawCircle(centerX, centerY, maxRadius * (r / 10f), fillPaint)
            }

            // Subject
            fillPaint.color = Color.BLACK
            canvas.drawCircle(centerX, centerY - 25f, 55f, fillPaint)
            canvas.drawCircle(centerX, centerY + 100f, 100f, fillPaint)

            // Frame Number Text
            paint.textSize = 28f
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.WHITE
            canvas.drawText("POSE #$i", frameLeft + 20f, currentY + 45f, paint)

            currentY += frameHeight + spacing
        }

        // Footer
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("THANK YOU FOR USING PHOTOBOOTH!", width / 2f, currentY + 45f, paint)

        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Date: $dateStr", width / 2f, currentY + 80f, paint)

        return applyFloydSteinbergDithering(bitmap)
    }
}
