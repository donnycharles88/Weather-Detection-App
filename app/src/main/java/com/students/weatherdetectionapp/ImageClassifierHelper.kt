package com.students.weatherdetectionapp

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureTimeMillis

class ImageClassifierHelper(
    var threshold: Float = 0.5f, // Naikkan threshold jika diperlukan
    var maxResults: Int = 4,
    val context: Context,
    val classifierListener: ClassifierListener?
) {
    private var tfliteInterpreter: Interpreter? = null

    init {
        setupImageClassifier()
    }

    private fun setupImageClassifier() {
        try {
            // Load model TFLite
            val modelFile = FileUtil.loadMappedFile(context, "weather_model.tflite")
            tfliteInterpreter = Interpreter(modelFile)
        } catch (e: Exception) {
            classifierListener?.onError("Failed to load weather_model.tflite")
            Log.e(TAG, e.message.toString())
        }
    }

    /**
     * Fungsi untuk mendeteksi apakah gambar merupakan non-cuaca.
     */
    private fun isNonWeatherImage(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var whiteCount = 0
        for (pixel in pixels) {
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            // Cek apakah piksel mendekati warna putih
            if (red > 200 && green > 200 && blue > 200) {
                whiteCount++
            }
        }

        val whitePercentage = whiteCount.toFloat() / pixels.size
        return whitePercentage > 0.8f // Anggap sebagai non-cuaca jika >80% putih
    }

    fun classifyImage(bitmap: Bitmap) {
        if (tfliteInterpreter == null) {
            setupImageClassifier()
        }

        // Deteksi input non-cuaca
        if (isNonWeatherImage(bitmap)) {
            classifierListener?.onError("Input is not a weather-related image")
            return
        }

        // Preprocess image
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // Convert bitmap to ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(224 * 224)
        processedImage.bitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f) // Red
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)  // Green
                byteBuffer.putFloat((value and 0xFF) / 255.0f)         // Blue
            }
        }

        // Prepare input tensor
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)

        // Run inference
        val outputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 4), DataType.FLOAT32) // Adjust size based on your model's output
        val inferenceTime = measureTimeMillis {
            tfliteInterpreter?.run(inputFeature0.buffer, outputFeature0.buffer)
        }

        // Parse output
        val resultArray = outputFeature0.floatArray
        val results = listOf(
            Classification("cloudy", resultArray[0]),
            Classification("rain", resultArray[1]),
            Classification("shine", resultArray[2]),
            Classification("sunrise", resultArray[3])
        ).filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(maxResults)

        // Return results
        classifierListener?.onResults(results, inferenceTime)
    }

    fun close() {
        tfliteInterpreter?.close()
        tfliteInterpreter = null
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(
            results: List<Classification>,
            inferenceTime: Long
        )
    }

    data class Classification(
        val label: String,
        val score: Float
    )

    companion object {
        private const val TAG = "ImageClassifierHelper"
    }
}