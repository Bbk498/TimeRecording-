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

/**
 * 甘特图视图
 *
 * 自定义View，以竖向柱状条形式绘制每个项目在一天24小时内的计时会话。
 * 每个项目占一列，列内根据会话的起止时间（小时）绘制圆角矩形色块。
 * 每个项目分配不同颜色，顶部显示项目名称。
 *
 * @param context 上下文
 * @param attrs 属性集
 * @param defStyleAttr 默认样式属性
 */
class GanttChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 当前显示的项目列表
    private var projects: List<Project> = emptyList()
    // 当前选中的日期
    private var selectedDate: Calendar = Calendar.getInstance()

    // 绘制计时色块的画笔
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 绘制文字的画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
    }
    // 绘制网格线的画笔
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 2f
    }

    // 屏幕像素密度，用于dp转px
    private val density = resources.displayMetrics.density

    // 绘制时的实际尺寸（在onMeasure中计算）
    private var chartTop = 0f       // 图表区域顶部Y坐标
    private var chartBottom = 0f   // 图表区域底部Y坐标
    private var chartHeight = 0f   // 图表区域高度
    private var columnWidth = 0f   // 每列宽度

    // 项目颜色调色板，循环使用
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

    /**
     * 设置图表数据并触发重绘
     *
     * @param projects 要显示的项目列表
     * @param date 选中日期
     */
    fun setData(projects: List<Project>, date: Calendar) {
        this.projects = projects
        this.selectedDate = date
        requestLayout()
        invalidate()
    }

    /**
     * 测量视图尺寸
     * 计算图表区域边界和每列宽度，支持根据项目数量自适应宽度
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec).toFloat()

        // 计算图表上下边界
        chartTop = (GanttLayoutConstants.topLabelDp + GanttLayoutConstants.topPaddingDp) * density
        chartBottom = availableHeight - GanttLayoutConstants.bottomPaddingDp * density
        chartHeight = chartBottom - chartTop

        // 计算列宽：取最小列宽和可用宽度/项目数中的较大值
        val minColumnWidth = GanttLayoutConstants.minColumnDp * density
        columnWidth = if (projects.isEmpty()) {
            max(minColumnWidth, availableWidth)
        } else {
            max(minColumnWidth, availableWidth / projects.size)
        }

        // 总宽度为所有列宽之和，至少不小于可用宽度
        val totalWidth = columnWidth * projects.size
        val finalWidth = max(totalWidth, availableWidth)

        setMeasuredDimension(finalWidth.toInt(), availableHeight.toInt())
    }

    /**
     * 绘制图表内容
     * 逐列绘制：横向网格线、计时色块、项目名标签、列分隔线
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 无数据时显示空状态提示
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

            // 横向网格线（贯穿每一列，每小时一条）
            for (hour in 0..24 step 1) {
                val y = chartTop + chartHeight * hour / 24f
                canvas.drawLine(columnLeft, y, columnLeft + columnWidth, y, axisPaint)
            }

            // 竖向彩色计时长条
            drawProjectColumn(canvas, project, columnCenterX, columnWidth, color)

            // 顶部项目名
            drawTopLabel(canvas, project.name, columnCenterX, columnWidth)

            // 列分隔线（第一列不画）
            if (index > 0) {
                canvas.drawLine(columnLeft, chartTop, columnLeft, chartBottom, axisPaint)
            }
        }
    }

    /**
     * 绘制单个项目的计时色块列
     * 遍历项目所有会话，仅绘制与选中日期同天的会话
     *
     * @param canvas 画布
     * @param project 项目数据
     * @param columnCenterX 列中心X坐标
     * @param columnWidth 列宽
     * @param color 色块颜色
     */
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
            // 跳过非选中日期的会话
            if (!isSameDay(startCal, selectedDate)) continue

            val endCal = Calendar.getInstance().apply { timeInMillis = session.endTime }

            // 将起止时间转换为小时浮点数（含分钟和秒的精确度）
            val startHour = startCal.get(Calendar.HOUR_OF_DAY) +
                    startCal.get(Calendar.MINUTE) / 60f +
                    startCal.get(Calendar.SECOND) / 3600f
            val endHour = endCal.get(Calendar.HOUR_OF_DAY) +
                    endCal.get(Calendar.MINUTE) / 60f +
                    endCal.get(Calendar.SECOND) / 3600f

            // 根据小时值计算色块的上下Y坐标
            val barTop = chartTop + chartHeight * startHour / 24f
            val barBottom = chartTop + chartHeight * endHour / 24f

            barPaint.color = color
            val rect = RectF(barLeft, barTop, barRight, barBottom)
            // 绘制圆角矩形色块
            canvas.drawRoundRect(rect, 45f, 45f, barPaint)
        }
    }

    /**
     * 绘制顶部项目名称标签
     * 文字超长时自动省略，居中对齐
     *
     * @param canvas 画布
     * @param name 项目名称
     * @param centerX 列中心X坐标
     * @param maxWidth 列最大宽度
     */
    private fun drawTopLabel(canvas: Canvas, name: String, centerX: Float, maxWidth: Float) {
        textPaint.color = Color.parseColor("#111111")   // 更深的文字颜色
        textPaint.textSize = 30f
        textPaint.isFakeBoldText = true                  // 加粗
        textPaint.textAlign = Paint.Align.CENTER

        // 文字超长时截断并加省略号
        val displayName = if (textPaint.measureText(name) > maxWidth - 10f) {
            ellipsizeText(name, maxWidth - 10f)
        } else name

        canvas.drawText(displayName, centerX, GanttLayoutConstants.topLabelDp * density - 12f, textPaint)
        // 恢复默认对齐方式
        textPaint.textAlign = Paint.Align.LEFT
    }

    /**
     * 判断两个Calendar是否为同一天
     *
     * @param cal1 日期1
     * @param cal2 日期2
     * @return 同一天返回true
     */
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 文字省略处理：从尾部逐字截取直到宽度满足要求，末尾加省略号
     *
     * @param text 原始文字
     * @param maxWidth 最大允许宽度
     * @return 截断后带省略号的文字
     */
    private fun ellipsizeText(text: String, maxWidth: Float): String {
        var result = text
        while (textPaint.measureText("$result…") > maxWidth && result.isNotEmpty()) {
            result = result.substring(0, result.length - 1)
        }
        return "$result…"
    }
}
