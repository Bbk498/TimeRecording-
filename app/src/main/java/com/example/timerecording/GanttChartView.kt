package com.example.timerecording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.max

class GanttChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var projects: List<Project> = emptyList()
    private var selectedDate: Calendar = Calendar.getInstance()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 2f
    }

    private val density = resources.displayMetrics.density

    // 绘制时的实际尺寸
    private var chartTop = 0f
    private var chartBottom = 0f
    private var chartHeight = 0f
    private var columnWidth = 0f

    private val colorPalette = listOf(
        Color.parseColor("#FF6200EE"),
        Color.parseColor("#FF03DAC5"),
        Color.parseColor("#FFFF5722"),
        Color.parseColor("#FF4CAF50"),
        Color.parseColor("#FF2196F3"),
        Color.parseColor("#FFE91E63"),
        Color.parseColor("#FF9C27B0"),
        Color.parseColor("#FFFFC107")
    )

    fun setData(projects: List<Project>, date: Calendar) {
        this.projects = projects
        this.selectedDate = date
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec).toFloat()

        chartTop = (GanttLayoutConstants.topLabelDp + GanttLayoutConstants.topPaddingDp) * density
        chartBottom = availableHeight - GanttLayoutConstants.bottomPaddingDp * density
        chartHeight = chartBottom - chartTop

        val minColumnWidth = GanttLayoutConstants.minColumnDp * density
        columnWidth = if (projects.isEmpty()) {
            max(minColumnWidth, availableWidth)
        } else {
            max(minColumnWidth, availableWidth / projects.size)
        }

        val totalWidth = columnWidth * projects.size
        val finalWidth = max(totalWidth, availableWidth)

        setMeasuredDimension(finalWidth.toInt(), availableHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (projects.isEmpty()) {
            textPaint.color = Color.parseColor("#999999")
            textPaint.textSize = 32f
            canvas.drawText("暂无项目数据", 60f, chartTop + 200f, textPaint)
            return
        }

        projects.forEachIndexed { index, project ->
            val columnLeft = columnWidth * index
            val columnCenterX = columnLeft + columnWidth / 2
            val color = colorPalette[index % colorPalette.size]

            // 横向网格线（贯穿每一列）
            for (hour in 0..24 step 1) {
                val y = chartTop + chartHeight * hour / 24f
                canvas.drawLine(columnLeft, y, columnLeft + columnWidth, y, axisPaint)
            }

            // 竖向彩色长条
            drawProjectColumn(canvas, project, columnCenterX, columnWidth, color)

            // 顶部项目名
            drawTopLabel(canvas, project.name, columnCenterX, columnWidth)

            // 列分隔线
            if (index > 0) {
                canvas.drawLine(columnLeft, chartTop, columnLeft, chartBottom, axisPaint)
            }
        }
    }

    private fun drawProjectColumn(
        canvas: Canvas,
        project: Project,
        columnCenterX: Float,
        columnWidth: Float,
        color: Int
    ) {
        val barWidth = columnWidth * GanttLayoutConstants.barWidthRatio
        val barLeft = columnCenterX - barWidth / 2
        val barRight = columnCenterX + barWidth / 2

        for (session in project.sessions) {
            val startCal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            if (!isSameDay(startCal, selectedDate)) continue

            val endCal = Calendar.getInstance().apply { timeInMillis = session.endTime }

            val startHour = startCal.get(Calendar.HOUR_OF_DAY) +
                    startCal.get(Calendar.MINUTE) / 60f +
                    startCal.get(Calendar.SECOND) / 3600f
            val endHour = endCal.get(Calendar.HOUR_OF_DAY) +
                    endCal.get(Calendar.MINUTE) / 60f +
                    endCal.get(Calendar.SECOND) / 3600f

            val barTop = chartTop + chartHeight * startHour / 24f
            val barBottom = chartTop + chartHeight * endHour / 24f

            barPaint.color = color
            val rect = RectF(barLeft, barTop, barRight, barBottom)
            canvas.drawRoundRect(rect, 45f, 45f, barPaint)
        }
    }

    private fun drawTopLabel(canvas: Canvas, name: String, centerX: Float, maxWidth: Float) {
        textPaint.color = Color.parseColor("#111111")   // ★ 更深（原来是 #333333）
        textPaint.textSize = 30f                         // ★ 更大（原来是 24f）
        textPaint.isFakeBoldText = true                  // ★ 加粗
        textPaint.textAlign = Paint.Align.CENTER

        val displayName = if (textPaint.measureText(name) > maxWidth - 10f) {
            ellipsizeText(name, maxWidth - 10f)
        } else name

        canvas.drawText(displayName, centerX, GanttLayoutConstants.topLabelDp * density - 12f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun ellipsizeText(text: String, maxWidth: Float): String {
        var result = text
        while (textPaint.measureText("$result…") > maxWidth && result.isNotEmpty()) {
            result = result.substring(0, result.length - 1)
        }
        return "$result…"
    }
}