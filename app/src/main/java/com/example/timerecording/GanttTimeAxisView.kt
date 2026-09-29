package com.example.timerecording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 甘特图时间轴视图
 *
 * 自定义View，在甘特图右侧绘制24小时时间刻度。
 * 与GanttChartView共享GanttLayoutConstants，保证纵向坐标对齐。
 * 每小时一条刻度线和时间文字标签（如"08:00"）。
 *
 * @param context 上下文
 * @param attrs 属性集
 * @param defStyleAttr 默认样式属性
 */
class GanttTimeAxisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 屏幕像素密度，用于dp转px
    private val density = resources.displayMetrics.density

    // 绘制时间文字的画笔（右对齐、加粗）
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        textSize = 28f
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    // 绘制刻度线的画笔
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 2f
    }

    /**
     * 绘制时间轴
     * 按小时（0~24）在右侧绘制时间文字标签和短刻度线
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 与 GanttChartView 保持一致的纵向坐标
        val chartTop = (GanttLayoutConstants.topLabelDp + GanttLayoutConstants.topPaddingDp) * density
        val chartBottom = height - GanttLayoutConstants.bottomPaddingDp * density
        val chartHeight = chartBottom - chartTop

        for (hour in 0..24 step 1) {
            val y = chartTop + chartHeight * hour / 24f
            // 时间文字（右对齐），格式如"08:00"
            val label = String.format("%02d:00", hour % 24)
            canvas.drawText(label, width - 12f, y + 8f, textPaint)
            // 右侧短刻度线
            canvas.drawLine(width - 6f, y, width.toFloat(), y, linePaint)
        }
    }
}
