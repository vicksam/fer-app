package com.vicksam.ferapp.model

import android.content.Context
import android.graphics.Bitmap
import com.vicksam.ferapp.utils.BitmapUtils.toGrayscale
import com.vicksam.ferapp.utils.BitmapUtils.toGrayscaleByteBuffer
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val MODEL_FILE_NAME = "fer_model.tflite"
private const val LABELS_FILE_NAME = "fer_model.names"

private const val INPUT_IMAGE_WIDTH = 48
private const val INPUT_IMAGE_HEIGHT = 48

private const val N_CLASSES = 8

/**
 * Fer model class
 *
 * You need to call load before using the model
 */
object FerModel {

    private lateinit var interpreter: Interpreter
    private lateinit var labels: ArrayList<String>

    fun load(context: Context): Interpreter {
        if (!this::interpreter.isInitialized) {
            synchronized(FerModel::class.java) {
                interpreter = loadModelFromAssets(context)
                labels = loadLabelsFromAssets(context)
            }
        }
        return interpreter
    }

    fun classify(inputImage: Bitmap) {
        val input = Bitmap.createScaledBitmap(
            inputImage,
            INPUT_IMAGE_WIDTH,
            INPUT_IMAGE_HEIGHT,
            true
        )
            .toGrayscale()
            .toGrayscaleByteBuffer()

        val probabilities = getPredictedProbabilities(input)
    }

    private fun loadModelFromAssets(context: Context): Interpreter {
        val model = context.assets.open(MODEL_FILE_NAME).readBytes()
        val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
        buffer.put(model)
        return Interpreter(buffer)
    }

    private fun loadLabelsFromAssets(context: Context): ArrayList<String> =
        ArrayList<String>().apply {
            context.assets.open(LABELS_FILE_NAME)
                .bufferedReader()
                .forEachLine { add(it) }
        }

    private fun getPredictedProbabilities(input: ByteBuffer): FloatArray {
        // Byte buffer that will take in model output
        val outputByteBuffer = ByteBuffer
            // 4 bytes per each pixel (float takes 4 pixels)
            .allocateDirect(4 * N_CLASSES)
            .order(ByteOrder.nativeOrder())

        // Run model
        interpreter.run(input, outputByteBuffer)

        // Array that will take in output
        val probabilities = FloatArray(N_CLASSES)

        // Read model output into output buffer
        val labelsBuffer = outputByteBuffer.run {
            rewind()
            asFloatBuffer()
        }

        // Put model output into output array
        labelsBuffer.get(probabilities)

        return probabilities.clone()
    }
}