package com.example.timerecording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GanttTimeAxisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")    // ★ 更深（原来是 #666666）
        textSize = 28f                          // ★ 更大（原来是 22f）
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true                   // ★ 加粗
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 与 GanttChartView 保持一致
        val chartTop = (GanttLayoutConstants.topLabelDp + GanttLayoutConstants.topPaddingDp) * density
        val chartBottom = height - GanttLayoutConstants.bottomPaddingDp * density
        val chartHeight = chartBottom - chartTop

        for (hour in 0..24 step 1) {
            val y = chartTop + chartHeight * hour / 24f
            // 时间文字（右对齐）
            val label = String.format("%02d:00", hour % 24)
            canvas.drawText(label, width - 12f, y + 8f, textPaint)
            // 右侧短刻度线
            canvas.drawLine(width - 6f, y, width.toFloat(), y, linePaint)
        }
    }
}