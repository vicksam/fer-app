package husaynhakeem.io.facedetector

import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.face.Face
import java.io.ByteArrayOutputStream

object FaceDetectorUtils {

    /**
     *
     * Transforms a Face object into a bounding box (rect) that contains it. The rect dimensions
     * are as if the frame was already rotated. The method also makes sure that coordinates match
     * the constraints. And fixes them if the rect comes out of the screen.
     *
     * The frame received from the frame processor is not rotated, you only get a frame and a
     * rotation angle. So only after applying this rotation to the frame, you get a picture
     * the way you see it on the screen. On the other hand, bounding box of a face that comes out of
     * ml kit face detector is as the frame was already rotated.
     *
     */
    fun Face.toFaceRect(frame: Frame): Rect = boundingBox.run {
        // In order to correctly display the face bounds, the orientation of the processed image
        // (frame) and that of the overlay have to match. Which is why the dimensions of
        // the analyzed image need to considered as if the image was already rotated.
        val reverseDimens = frame.rotation == 90 || frame.rotation == 270
        val width = if (reverseDimens) frame.size.height else frame.size.width
        val height = if (reverseDimens) frame.size.width else frame.size.height

        val faceWidth: Int = width()
        val faceHeight: Int = height()

        var faceX: Int = left
        var faceY: Int = top

        // Constraints - bounding box cannot come out of the screen
        val beyondRightPixels = faceX + faceWidth - width
        if (beyondRightPixels > 0)
            faceX -= beyondRightPixels

        val beyondTopPixels = faceY + faceHeight - height
        if (beyondTopPixels > 0)
            faceY -= beyondTopPixels

        if (faceX < 0)
            faceX = 0

        if (faceY < 0)
            faceY = 0

        return Rect(faceX, faceY, faceX + faceWidth, faceY + faceHeight)
    }

    /**
     * Cuts out faces from a picture in the frame, using coordinates of the face rects in the list.
     * Returns a list of bitmaps, each one containing a face image.
     */
    fun List<Rect>.toFaceBitmapList(frame: Frame): List<Bitmap> {
        var bitmap = frame.toBitmap()

        // Rotation
        val rotationMatrix = Matrix().apply { setRotate(frame.rotation.toFloat()) }

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)

        return try {
            map { rect ->
                Bitmap.createBitmap(
                        bitmap,
                        rect.left,
                        rect.top,
                        rect.width(),
                        rect.height()
                )
            }
        } catch (exception: IllegalArgumentException) {
            Log.d(
                    this.javaClass.name,
                    "Problem during bitmap creation. Check your parameters.",
                    exception
            )
            listOf()
        }
    }

    /**
     * Translates coordinates of face rects from a frame into overlay view. So using new coordinates,
     * bounding boxes will be correctly displayed on the screen.
     *
     * When using front camera, the image that is displayed on the screen needs to be mirrored,
     * so the user has a mirror-like effect. Depending on rotation angle, different mirroring
     * (or flipping) needs to be applied.
     *
     * The frame and the overlay view (usually) have different resolutions, so face rect needs to
     * be scaled accordingly.
     *
     * In case the device is kept horizontally, the pcicture needs to be rotated to vertical position
     * by 90 degrees. Because I decided to enforce horizontal mode and adapt the displayed picture
     * to the device rotation, it is still displayed as it was in vertical mode.
     *
     */
    fun List<Pair<Int?, RectF>>.toFaceBoundsList(
            frame: Frame,
            overlayView: FaceBoundsOverlay
    ): List<FaceBounds> {
        // In order to correctly display the face bounds, the orientation of the processed image
        // (frame) and that of the overlay have to match. Which is why the dimensions of
        // the analyzed image are reversed if its rotation is 90 or 270.
        val reverseDimens = frame.rotation == 90 || frame.rotation == 270
        val width = if (reverseDimens) frame.size.height else frame.size.width
        val height = if (reverseDimens) frame.size.width else frame.size.height

        val overlayWidth = if (reverseDimens) overlayView.width else overlayView.height
        val overlayHeight = if (reverseDimens) overlayView.height else overlayView.width

        // Since the analyzed image (frame) probably has a different resolution (width and height)
        // compared to the overlay view, compute by how much you need to scale the bounding box
        // so that it is displayed correctly on the overlay.
        val scaleX = overlayWidth.toFloat() / width
        val scaleY = overlayHeight.toFloat() / height

        // Flipping
        if (frame.isFrontFacingCam()) {
            if (frame.rotation == 0 || frame.rotation == 270) {
                // Horizontal flip
                val horizontalFlipM = Matrix().apply { preScale(-1f, 1f) }
                forEach { horizontalFlipM.mapRect(it.second) }
                // After horizontal flip with respect to the origin (0, 0), image is in the
                // IV quarter. By moving its every point to the right by the frame width,
                // it is again in the I quarter.
                // In other words, some coordinates are negative after the flip. This way, all
                // become positive while the rotation and rectangle position are kept.
                for (i in indices) {
                    this[i].second.offset(width.toFloat(), 0f)
                }
            }
            else if (frame.rotation == 90 || frame.rotation == 180) {
                // Vertical flip
                val verticalFlipM = Matrix().apply { preScale(1f, -1f) }
                forEach { verticalFlipM.mapRect(it.second) }
                // Move it from II quarter (after flip), back to the I quarter (positive coordinates)
                for (i in indices) {
                    this[i].second.offset(0f, height.toFloat())
                }
            }
        } else {
            if (frame.rotation == 180 || frame.rotation == 270) {
                // Vertical and horizontal flip at the same time
                val hvFlipM = Matrix().apply { preScale(-1f, -1f) }
                forEach { hvFlipM.mapRect(it.second) }
                // Move it from III quarter (after flip), back to the I quarter (positive coordinates)
                for (i in indices) {
                    this[i].second.offset(width.toFloat(), height.toFloat())
                }
            }
        }

        // Scaling
        val scalingMatrix = Matrix().apply { preScale(scaleX, scaleY) }
        forEach { scalingMatrix.mapRect(it.second) }

        // In case of horizontal rotation, bring it back to vertical position
        if (frame.rotation == 0 || frame.rotation == 180) {
            for (i in indices) {
                this[i].second.rotateRectBy90()
                // After 90 rotation around the origin (0, 0), rect is in the II quarter
                // By moving it up by the image height, it is again in the I quarter
                this[i].second.offset(overlayHeight.toFloat(), 0f)
            }
        }

        // Reshape the rect
        // Face rectangle is measured on the frame. But the ratio of overlay view might be
        // different. In this case, it will change its shape from a square on a frame into
        // a rectangle on screen. This transform will bring its shape back to square on the screen.
        for (i in indices) {
            this[i].second.apply {
                val edge = (width() + height()) / 2
                left = centerX() - edge / 2
                right = centerX() + edge / 2
                top = centerY() - edge / 2
                bottom = centerY() + edge / 2
            }
        }

        return map { FaceBounds(it.first, it.second) }
    }

    /** Calculates angle for rotating text, so it is aligned with the device rotation */
    fun calculateTextRotation(rotationAngle: Int, isFrontFacingCam: Boolean) =
        if (isFrontFacingCam) {
            when (rotationAngle) {
                0 -> 90
                90 -> 180
                180 -> 270
                270 -> 0
                else -> 0
            }
        } else {
            when (rotationAngle) {
                0 -> 90
                90 -> 0
                180 -> -90
                270 -> -180
                else -> 0
            }
        }

    /** Writes picture from the current frame into a bitmap. */
    private fun Frame.toBitmap(): Bitmap {
        val out = ByteArrayOutputStream()
        val yuvImage = YuvImage(
                this.data,
                ImageFormat.NV21,
                size.width,
                size.height,
                null
        )
        yuvImage.compressToJpeg(
                Rect(
                        0,
                        0,
                        size.width,
                        size.height
                ), 90, out
        )
        val imageBytes: ByteArray = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /** Rotates rect around the origin. Returns its new coordinates after the transformation */
    private fun RectF.rotateRectBy90() {
        val degrees = 90f
        val rectPoints = floatArrayOf(left, top, right, bottom)

        val rotationM = Matrix().apply { setRotate(degrees) }
        rotationM.mapPoints(rectPoints)

        // After such rotation, the coordinates are correct, but orientation of points have flipped.
        // So I flip the coordinates here, so they are properly rearranged for left, bottom, top and
        // right.
        // e.g. the bottom-left point was point A and the top-right point was C. But after rotating
        // rect by 90 degrees, it was flipped vertically and is in the II quarter. So now the
        // bottom-left point is B and the top-right point is D.
        this.set(rectPoints[2], rectPoints[1], rectPoints[0], rectPoints[3])
    }
}