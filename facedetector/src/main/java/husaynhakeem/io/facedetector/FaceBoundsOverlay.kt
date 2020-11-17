package husaynhakeem.io.facedetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat


/**
 * A [View] that renders the results of a face detection operation. It receives a list of face
 * bounds (represented by a list of [RectF]) and draws them, along with their tracking ids.
 */
class FaceBoundsOverlay @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    View(ctx, attrs) {

    private val facesBounds = mutableListOf<FaceBounds>()
    private val emotionLabels = mutableMapOf<Int, String>()
    private val anchorPaint = Paint()
    private val idPaint = Paint()
    private val boundsPaint = Paint()

    init {
        anchorPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)

        idPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
        idPaint.textSize = 40f

        boundsPaint.style = Paint.Style.STROKE
        boundsPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
        boundsPaint.strokeWidth = 4f
    }

    internal fun updateFaces(bounds: List<FaceBounds>) {
        facesBounds.clear()
        facesBounds.addAll(bounds)
        invalidate()
    }

    fun updateEmotionLabels(emotions: Map<Int, String>) {
        emotionLabels.clear()
        emotionLabels.putAll(emotions)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        facesBounds.forEach { faceBounds ->
            if (DRAW_ID) {
                canvas.drawAnchor(faceBounds.box.center())
                canvas.drawId(faceBounds.id.toString(), faceBounds.box.center())
            }
            emotionLabels[faceBounds.id]?.let {
                canvas.drawEmotionLabel(it, faceBounds.box.center())
            }
            canvas.drawBounds(faceBounds.box)
        }
    }

    /** Draws an anchor (dot) at the center of a face. */
    private fun Canvas.drawAnchor(center: PointF) {
        drawCircle(center.x, center.y, ANCHOR_RADIUS, anchorPaint)
    }

    /** Draws (Writes) the face's id. */
    private fun Canvas.drawId(faceId: String, center: PointF) {
        drawText("face id $faceId", center.x - ID_OFFSET, center.y + ID_OFFSET, idPaint)
    }

    /** Draws (Writes) the emotion label. */
    private fun Canvas.drawEmotionLabel(emotion: String, center: PointF) {
        drawText(emotion, center.x, center.y, idPaint)
    }

    /** Draws bounds around a face as a rectangle. */
    private fun Canvas.drawBounds(box: RectF) {
        drawRect(box, boundsPaint)
    }

    private fun RectF.center(): PointF {
        val centerX = left + (right - left) / 2
        val centerY = top + (bottom - top) / 2
        return PointF(centerX, centerY)
    }

    companion object {
        private const val ANCHOR_RADIUS = 10f
        private const val DRAW_ID = false
        private const val ID_OFFSET = 50f
    }
}