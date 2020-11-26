package husaynhakeem.io.facedetector

import android.graphics.*
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.GuardedBy
import com.google.android.gms.common.util.concurrent.HandlerExecutor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import husaynhakeem.io.facedetector.FaceDetectorUtils.calculateTextRotation
import husaynhakeem.io.facedetector.FaceDetectorUtils.toFaceBitmapList
import husaynhakeem.io.facedetector.FaceDetectorUtils.toFaceBoundsList
import husaynhakeem.io.facedetector.FaceDetectorUtils.toFaceRect
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetector(private val faceBoundsOverlay: FaceBoundsOverlay) {

    private var currentRotationAngle = 90

    private val mlkitFaceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .setMinFaceSize(MIN_FACE_SIZE)
                    .enableTracking()
                    .build()
    )

    /** Listener that gets notified when a face detection result is ready. */
    private var onFaceDetectionResultListener: OnFaceDetectionResultListener? = null

    /** Listener that gets notified when a rotation angle is changed. */
    private var onRotationChangedListener: OnRotationChangedListener? = null

    /** [Executor] used to run the face detection on a background thread.  */
    private lateinit var faceDetectionExecutor: ExecutorService

    /** [Executor] used to trigger the rendering of the detected face bounds on the UI thread. */
    private val mainExecutor = HandlerExecutor(Looper.getMainLooper())

    /** Controls access to [isProcessing], since it can be accessed from different threads. */
    private val lock = Object()

    @GuardedBy("lock")
    private var isProcessing = false

    init {
        faceBoundsOverlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View?) {
                faceDetectionExecutor = Executors.newSingleThreadExecutor()
            }

            override fun onViewDetachedFromWindow(view: View?) {
                if (::faceDetectionExecutor.isInitialized) {
                    faceDetectionExecutor.shutdown()
                }
            }
        })

        setOnRotationAngleChangedListener(object : OnRotationChangedListener {
            override fun onChanged(rotationAngle: Int, isFrontFacingCam: Boolean) {
                faceBoundsOverlay.updateTextRotationAngle(
                        calculateTextRotation(rotationAngle, isFrontFacingCam)
                )
            }
        })
    }

    /** Sets a listener to receive face detection result callbacks. */
    fun setOnFaceDetectionListener(listener: OnFaceDetectionResultListener) {
        onFaceDetectionResultListener = listener
    }

    /** Sets a listener to receive notifications about changes in rotation. */
    fun setOnRotationAngleChangedListener(listener: OnRotationChangedListener) {
        onRotationChangedListener = listener
    }

    /**
     * Kick-starts a face detection operation on a camera frame. If a previous face detection
     * operation is still ongoing, the frame is dropped until the face detector is no longer busy.
     */
    fun process(frame: Frame) {
        synchronized(lock) {
            if (!isProcessing) {
                isProcessing = true
                if (!::faceDetectionExecutor.isInitialized) {
                    val exception =
                            IllegalStateException("Cannot run face detection. Make sure the face " +
                                    "bounds overlay is attached to the current window.")
                    onError(exception)
                } else {
                    faceDetectionExecutor.execute { frame.detectFaces() }
                }
            }
        }
    }

    private fun Frame.detectFaces() {
        val data = data ?: return
        val inputImage = InputImage.fromByteArray(data, size.width, size.height, rotation, format)
        mlkitFaceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    synchronized(lock) {
                        isProcessing = false
                    }

                    // Extract a bounding box (rect) for each detected face
                    val faceRects = faces.map { it.toFaceRect(this) }
                    // Cut each face image from a frame
                    val faceBitmaps = faceRects.toFaceBitmapList(this)

                    val faceRectsWithIds = faces
                                .map { it.trackingId }
                                .zip(faceRects.map { RectF(it) })
                    // Transform the coordinates of the face rects so they are correctly rendered
                    // on the screen
                    val faceBounds = faceRectsWithIds.toFaceBoundsList(this, faceBoundsOverlay)

                    // Update listeners
                    onFaceDetectionResultListener?.onSuccess(faceBounds, faceBitmaps)
                    updateRotationListenerOnRotationChange()

                    mainExecutor.execute { faceBoundsOverlay.updateFaces(faceBounds) }
                }
                .addOnFailureListener { exception ->
                    synchronized(lock) {
                        isProcessing = false
                    }
                    onError(exception)
                }
    }

    private fun Frame.updateRotationListenerOnRotationChange() {
        if (rotation != currentRotationAngle) {
            currentRotationAngle = rotation
            onRotationChangedListener?.onChanged(rotation, isFrontFacingCam())
        }
    }

    private fun onError(exception: Exception) {
        onFaceDetectionResultListener?.onFailure(exception)
        Log.e(TAG, "An error occurred while running a face detection", exception)
    }

    /**
     * Interface containing callbacks that are invoked when the face detection process succeeds or
     * fails.
     */
    interface OnFaceDetectionResultListener {
        /**
         * Signals that the face detection process has successfully completed for a camera frame.
         * It also provides the result of the face detection for potential further processing.
         *
         * @param faceBounds Detected faces from a camera frame
         */
        fun onSuccess(faceBounds: List<FaceBounds>, faceBitmaps: List<Bitmap>) {}

        /**
         * Invoked when an error is encountered while attempting to detect faces in a camera frame.
         *
         * @param exception Encountered [Exception] while attempting to detect faces in a camera
         * frame.
         */
        fun onFailure(exception: Exception) {}
    }

    /**
     * Interface containing callbacks that are invoked when the frame rotation changes.
     */
    interface OnRotationChangedListener {
        /**
         * Signals a change in frame rotation.
         */
        fun onChanged(rotationAngle: Int, isFrontFacingCam: Boolean) {}
    }

    companion object {
        private const val TAG = "FaceDetector"
        private const val MIN_FACE_SIZE = 0.15F
    }
}