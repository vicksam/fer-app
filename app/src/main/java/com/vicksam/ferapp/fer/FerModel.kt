package com.vicksam.ferapp.fer

import android.content.Context
import android.graphics.Bitmap
import com.vicksam.ferapp.utils.BitmapUtils.toGrayscale
import com.vicksam.ferapp.utils.BitmapUtils.toGrayscaleByteBuffer
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

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

    fun classify(inputImage: Bitmap): String {
        val input = Bitmap.createScaledBitmap(
            inputImage,
            INPUT_IMAGE_WIDTH,
            INPUT_IMAGE_HEIGHT,
            true
        )
            .toGrayscale()
            .toGrayscaleByteBuffer()

        return predict(input).toPrediction().toLabel()
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

    /**
     * Runs model to get prediction, returns logits
     */
    private fun predict(input: ByteBuffer): FloatArray {
        // Byte buffer that will take in model output
        val outputByteBuffer = ByteBuffer
            // 4 bytes per each pixel (float takes 4 pixels)
            .allocateDirect(4 * N_CLASSES)
            .order(ByteOrder.nativeOrder())

        // Run model
        interpreter.run(input, outputByteBuffer)

        // Array that will take in output
        val logits = FloatArray(N_CLASSES)

        // Read model output into output buffer
        val labelsBuffer = outputByteBuffer.run {
            rewind()
            asFloatBuffer()
        }

        // Put model output into output array
        labelsBuffer.get(logits)

        return logits
    }

    /**
     * Turns logits to the most probable class
     */
    private fun FloatArray.toPrediction(): Int {
        // Softmax
        var probabilities = this.map { exp(it.toDouble()) }
            .run { map { it / sum() } }
        val test = probabilities.sum().toInt() == 1

        // Class with max probability
        return probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
    }

    /**
     * Turns class index to text label
     */
    private fun Int.toLabel() = labels[this]
}