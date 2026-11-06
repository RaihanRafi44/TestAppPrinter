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
     * Scaled precisely to fit targetHeightMm * 8 dots.
     */
    fun createSamplePhotoboothStrip(width: Int = 800, targetHeightMm: Int = 150): Bitmap {
        val targetHeightDots = targetHeightMm * 8
        val baseWidth = 800
        val baseHeight = 1600

        val unscaledBitmap = Bitmap.createBitmap(baseWidth, baseHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(unscaledBitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true }

        // Header
        paint.color = Color.BLACK
        paint.textSize = 44f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PHOTOBOOTH TEST", baseWidth / 2f, 75f, paint)

        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Xprinter XP-410B / XP-4601B (203 DPI)", baseWidth / 2f, 115f, paint)

        // Draw 3 Photo Frames
        val photoCount = 3
        val frameWidth = 680
        val frameHeight = 360
        val frameLeft = (baseWidth - frameWidth) / 2
        val spacing = 30
        var currentY = 150

        val strokePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        for (i in 1..photoCount) {
            canvas.drawRect(
                frameLeft.toFloat(),
                currentY.toFloat(),
                (frameLeft + frameWidth).toFloat(),
                (currentY + frameHeight).toFloat(),
                strokePaint
            )

            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            val centerX = frameLeft + frameWidth / 2f
            val centerY = currentY + frameHeight / 2f
            val maxRadius = frameHeight / 2.3f

            for (r in 10 downTo 1) {
                fillPaint.color = if (r % 2 == 0) Color.DKGRAY else Color.LTGRAY
                canvas.drawCircle(centerX, centerY, maxRadius * (r / 10f), fillPaint)
            }

            fillPaint.color = Color.BLACK
            canvas.drawCircle(centerX, centerY - 20f, 50f, fillPaint)
            canvas.drawCircle(centerX, centerY + 90f, 95f, fillPaint)

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
        canvas.drawText("THANK YOU FOR USING PHOTOBOOTH!", baseWidth / 2f, currentY + 50f, paint)

        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Date: $dateStr", baseWidth / 2f, currentY + 85f, paint)

        val scaledBitmap = if (baseHeight != targetHeightDots || baseWidth != width) {
            Bitmap.createScaledBitmap(unscaledBitmap, width, targetHeightDots, true)
        } else {
            unscaledBitmap
        }

        return applyFloydSteinbergDithering(scaledBitmap)
    }
}
