package com.raihan.testappprinter.printer

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class TsplCommand {
    private val outputStream = ByteArrayOutputStream()
    private val charset = Charset.forName("GB18030")

    fun addCommand(cmd: String): TsplCommand {
        outputStream.write("$cmd\r\n".toByteArray(charset))
        return this
    }

    /**
     * Set label size in mm
     */
    fun size(widthMm: Int, heightMm: Int): TsplCommand {
        return addCommand("SIZE $widthMm mm, $heightMm mm")
    }

    /**
     * Set gap between labels in mm
     */
    fun gap(gapMm: Int, offsetMm: Int = 0): TsplCommand {
        return addCommand("GAP $gapMm mm, $offsetMm mm")
    }

    /**
     * Clear image buffer
     */
    fun cls(): TsplCommand {
        return addCommand("CLS")
    }

    /**
     * Set print direction (0 or 1)
     */
    fun direction(direction: Int = 1): TsplCommand {
        return addCommand("DIRECTION $direction")
    }

    /**
     * Add text
     */
    fun text(
        x: Int,
        y: Int,
        font: String = "3",
        rotation: Int = 0,
        xMultiplication: Int = 1,
        yMultiplication: Int = 1,
        content: String
    ): TsplCommand {
        return addCommand("TEXT $x,$y,\"$font\",$rotation,$xMultiplication,$yMultiplication,\"$content\"")
    }

    /**
     * Add QR Code
     */
    fun qrCode(
        x: Int,
        y: Int,
        content: String,
        eccLevel: String = "M",
        cellWidth: Int = 5
    ): TsplCommand {
        return addCommand("QRCODE $x,$y,$eccLevel,$cellWidth,A,0,\"$content\"")
    }

    /**
     * Add Barcode 128
     */
    fun barcode(
        x: Int,
        y: Int,
        content: String,
        height: Int = 80
    ): TsplCommand {
        return addCommand("BARCODE $x,$y,\"128\",$height,1,0,2,4,\"$content\"")
    }

    /**
     * Add BITMAP image data
     * TSPL Format: BITMAP X, Y, widthInBytes, heightInDots, mode, bitmapData...
     * mode: 0 (OVERWRITE), 1 (OR), 2 (XOR)
     * For TSPL, in each byte, 0 = black dot, 1 = white dot.
     */
    fun bitmap(x: Int, y: Int, bitmap: Bitmap): TsplCommand {
        val width = bitmap.width
        val height = bitmap.height
        val widthInBytes = (width + 7) / 8

        val header = "BITMAP $x,$y,$widthInBytes,$height,0,".toByteArray(charset)
        outputStream.write(header)

        val imageBytes = ByteArray(widthInBytes * height)
        var byteIdx = 0

        for (h in 0 until height) {
            for (wByte in 0 until widthInBytes) {
                var currentByte = 0
                for (bit in 0 until 8) {
                    val pixelX = wByte * 8 + bit
                    var isWhite = true
                    if (pixelX < width) {
                        val color = bitmap.getPixel(pixelX, h)
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
                        isWhite = luminance >= 128
                    }
                    if (isWhite) {
                        currentByte = currentByte or (1 shl (7 - bit))
                    }
                }
                imageBytes[byteIdx++] = currentByte.toByte()
            }
        }

        outputStream.write(imageBytes)
        outputStream.write("\r\n".toByteArray(charset))
        return this
    }

    /**
     * Print command: PRINT m, n
     */
    fun print(sets: Int = 1, copies: Int = 1): TsplCommand {
        return addCommand("PRINT $sets,$copies")
    }

    fun build(): ByteArray {
        return outputStream.toByteArray()
    }
}
