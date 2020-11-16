package com.vicksam.ferapp.fer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import husaynhakeem.io.facedetector.FaceBounds

class FerViewModel : ViewModel() {

    private val emotions = MutableLiveData<Map<Int, String>>()
    fun emotions(): LiveData<Map<Int, String>> = emotions

    private var processing: Boolean = false

    fun onFacesDetected(faceBounds: List<FaceBounds>, faceBitmaps: List<Bitmap>) {
        synchronized(FerViewModel::class.java) {
            if (!processing) {
                processing = true
                Handler(Looper.getMainLooper()).post {
                    emotions.value = faceBounds.mapNotNull { it.id }
                        .zip(faceBitmaps)
                        .toMap()
                        .run { getEmotionsMap(this) }
                    processing = false
                }
            }
        }
    }

    /**
     * Given map of (faceId, faceBitmap), runs prediction on the model and
     * returns a map of (faceId, emotionLabel)
     */
    private fun getEmotionsMap(faceImages: Map<Int, Bitmap>): Map<Int, String> {
        val emotionLabels = faceImages.map { FerModel.classify(it.value) }
        return faceImages.keys.zip(emotionLabels).toMap()
    }
}