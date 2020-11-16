package com.vicksam.ferapp.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BitmapUtils {

    fun Bitmap.toGrayscale(): Bitmap = BitmapUtilsJava.toGrayscale(this)

    /**
     * Converts a bitmap with 4 color channels and outputs byte buffer with 1 channel
     *
     * Extracts one color value for each pixel using grayscale conversion formula. Then it
     * normalizes color range from [0, 255] to [0, 1] and puts every resulting float into byte
     * buffer.
     */
    fun Bitmap.toGrayscaleByteBuffer(): ByteBuffer {
        // Every float value needs 4 bytes of memory, that's why I multiply it by 4 at the beginning
        val mImgData: ByteBuffer = ByteBuffer.allocateDirect(4 * width * height)
        mImgData.order(ByteOrder.nativeOrder())

        val pixels = IntArray(width * height)
        this.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            // After grayscale conversion, every channel shares the same color value
            // but I stay with the original conversion formula (in case grayscale conversion isn't there)
            val grayscaleValue = 0.2989 * Color.red(pixel) + 0.5870 * Color.green(pixel) + 0.1140 * Color.blue(pixel)
            // Normalize color range
            mImgData.putFloat(grayscaleValue.toFloat() / 255.0f)
        }

        return mImgData
    }
}