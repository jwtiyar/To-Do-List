package io.github.jwtiyar.simplertask.ui.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.github.jwtiyar.simplertask.R
import io.github.jwtiyar.simplertask.data.local.entity.Task
import kotlin.math.abs

class TaskSwipeCallback(
    private val context: Context,
    private val onSwipeComplete: (Task) -> Unit,
    private val onSwipeDelete: (Task) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val completeIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_check_circle_24dp)
    private val deleteIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_delete_24dp)

    private val completeBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.priority_low) // Green for complete
    }

    private val deleteBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.priority_high) // Red for delete
    }

    private val iconPaint = Paint().apply {
        color = Color.WHITE
        textSize = 14f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Icon margins and sizes
    private val iconMargin = 32f
    private val iconSize = 48f

    // Haptic feedback
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun performHapticFeedback() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50)
            }
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false // We don't support drag & drop

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        val adapter = viewHolder.bindingAdapter as? TaskPagingAdapter ?: return
        val task = adapter.getTaskAtPosition(position) ?: return

        // Provide haptic feedback
        performHapticFeedback()

        when (direction) {
            ItemTouchHelper.RIGHT -> onSwipeComplete(task)
            ItemTouchHelper.LEFT -> onSwipeDelete(task)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        // Draw background
        when {
            dX > 0 -> { // Swiping right - Complete
                val background = RectF(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
                c.drawRect(background, completeBackgroundPaint)

                // Draw complete icon and text
                val iconTop = itemView.top + (itemHeight - iconSize) / 2
                val iconLeft = itemView.left + iconMargin

                completeIcon?.let { icon ->
                    val iconRight = iconLeft + iconSize
                    val iconBottom = iconTop + iconSize
                    icon.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                    icon.draw(c)
                }

                // Draw "Complete" text
                val textX = iconLeft + iconSize + 16f
                val textY = itemView.top + itemHeight / 2f + 5f
                c.drawText("Complete", textX, textY, iconPaint)
            }
            dX < 0 -> { // Swiping left - Delete
                val background = RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                c.drawRect(background, deleteBackgroundPaint)

                // Draw delete icon and text
                val iconTop = itemView.top + (itemHeight - iconSize) / 2
                val iconRight = itemView.right - iconMargin

                deleteIcon?.let { icon ->
                    val iconLeft = iconRight - iconSize
                    val iconBottom = iconTop + iconSize
                    icon.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                    icon.draw(c)
                }

                // Draw "Delete" text
                val textX = iconRight - iconSize - 16f
                val textY = itemView.top + itemHeight / 2f + 5f
                c.drawText("Delete", textX, textY, iconPaint)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 2

    private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
        c?.drawRect(left, top, right, bottom, Paint().apply { color = Color.TRANSPARENT })
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val adapter = viewHolder.bindingAdapter as? TaskPagingAdapter ?: return 0
        val task = adapter.getTaskAtPosition(viewHolder.adapterPosition) ?: return 0

        // Don't allow swipe actions on completed tasks
        return if (task.isCompleted) {
            0 // No swipe directions allowed
        } else {
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        }
    }
}