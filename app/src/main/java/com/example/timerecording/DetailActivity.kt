package com.example.timerecording

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.timerecording.viewmodel.DetailViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 项目详情 Activity。
 *
 * 职责：
 * - 显示项目名称、总计时；
 * - 用 RecyclerView 展示该项目下所有计时会话记录；
 * - 用柱状图（MPAndroidChart）展示每日累计小时数；
 * - 提供条形图颜色自定义功能（RGB 滑块选择器）。
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var viewModel: DetailViewModel           // 详情页视图模型
    private var currentProject: Project? = null               // 当前加载的项目

    /**
     * Activity 创建入口：读取项目 ID，初始化 ViewModel 并加载项目数据。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // 从 Intent 读取项目 ID，无效则提示并退出
        val projectId = intent.getLongExtra("project_id", -1)
        if (projectId == -1L) {
            Toast.makeText(this, "数据加载失败，请返回重试", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[DetailViewModel::class.java]

        // 观察项目数据：数据为空时退出，否则显示项目信息
        viewModel.project.observe(this) { project ->
            if (project == null) {
                Toast.makeText(this, "项目数据为空", Toast.LENGTH_SHORT).show()
                finish()
                return@observe
            }
            currentProject = project
            displayProject(project)
        }

        // 触发项目数据加载
        viewModel.loadProject(projectId)
    }

    /**
     * 显示项目信息：名称、总时长、会话列表、柱状图。
     */
    private fun displayProject(project: Project) {
        val tvName = findViewById<TextView>(R.id.tv_detail_project_name)
        val tvTotal = findViewById<TextView>(R.id.tv_detail_total_time)
        val rvSessions = findViewById<RecyclerView>(R.id.rv_sessions)
        val barChart = findViewById<BarChart>(R.id.bc_daily_chart)

        // 设置项目名称
        tvName.text = project.name
        // 计算并显示总计时（所有会话时长之和）
        val totalSeconds = project.sessions.sumOf { it.durationMillis } / 1000
        tvTotal.text = "总计时: ${formatDuration(totalSeconds)}"

        // 配置会话列表，按开始时间倒序排列
        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = SessionAdapter(project.sessions.sortedByDescending { it.startTime })

        // 配置每日时长柱状图
        setupBarChart(barChart, project)

        // 颜色自定义按钮：点击弹出 RGB 滑块选择器
        val btnCustomColor = findViewById<Button>(R.id.btn_custom_color)
        btnCustomColor.setOnClickListener {
            showRgbColorPicker()
        }

        // 读取已保存的图表颜色并应用到按钮背景
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedColor = prefs.getInt("chart_color", android.graphics.Color.parseColor("#FF6200EE"))
        btnCustomColor.backgroundTintList = android.content.res.ColorStateList.valueOf(savedColor)
    }

    /**
     * 使用新颜色重新绘制柱状图并更新按钮颜色。
     */
    private fun refreshChartWithNewColor(color: Int) {
        val barChart = findViewById<BarChart>(R.id.bc_daily_chart)
        val btnCustomColor = findViewById<Button>(R.id.btn_custom_color)
        val project = currentProject
        if (project != null) {
            setupBarChart(barChart, project, color)
            btnCustomColor.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    /**
     * 弹出 RGB 三通道滑块颜色选择器，用于自定义柱状图颜色。
     * 滑动滑块时实时预览颜色，确定后保存到 SharedPreferences 并刷新图表。
     */
    private fun showRgbColorPicker() {
        // 读取当前保存的颜色作为初始值
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val currentColor = prefs.getInt("chart_color", android.graphics.Color.parseColor("#FF6200EE"))

        var red = android.graphics.Color.red(currentColor)
        var green = android.graphics.Color.green(currentColor)
        var blue = android.graphics.Color.blue(currentColor)

        // 构建对话框：确定时保存颜色并刷新图表
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择条形图颜色")
            .setPositiveButton("确定") { _, _ ->
                val newColor = android.graphics.Color.rgb(red, green, blue)
                prefs.edit().putInt("chart_color", newColor).apply()
                refreshChartWithNewColor(newColor)
            }
            .setNegativeButton("取消", null)
            .create()

        // 对话框内容布局（垂直排列）
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        // 颜色预览区域
        val previewView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                bottomMargin = 32
            }
            setBackgroundColor(currentColor)
        }
        layout.addView(previewView)

        // 红色通道滑块
        val redSeekBar = SeekBar(this).apply {
            max = 255
            progress = red
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }
        val redLabel = TextView(this).apply {
            text = "红色: $red"
            textSize = 16f
        }
        layout.addView(redLabel)
        layout.addView(redSeekBar)

        // 绿色通道滑块
        val greenSeekBar = SeekBar(this).apply {
            max = 255
            progress = green
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }
        val greenLabel = TextView(this).apply {
            text = "绿色: $green"
            textSize = 16f
        }
        layout.addView(greenLabel)
        layout.addView(greenSeekBar)

        // 蓝色通道滑块
        val blueSeekBar = SeekBar(this).apply {
            max = 255
            progress = blue
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }
        val blueLabel = TextView(this).apply {
            text = "蓝色: $blue"
            textSize = 16f
        }
        layout.addView(blueLabel)
        layout.addView(blueSeekBar)

        // 滑块变化监听：实时更新颜色值、标签文字和预览背景
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when (seekBar) {
                    redSeekBar -> {
                        red = progress
                        redLabel.text = "红色: $red"
                    }
                    greenSeekBar -> {
                        green = progress
                        greenLabel.text = "绿色: $green"
                    }
                    blueSeekBar -> {
                        blue = progress
                        blueLabel.text = "蓝色: $blue"
                    }
                }
                // 实时刷新预览
                val newColor = android.graphics.Color.rgb(red, green, blue)
                previewView.setBackgroundColor(newColor)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        redSeekBar.setOnSeekBarChangeListener(listener)
        greenSeekBar.setOnSeekBarChangeListener(listener)
        blueSeekBar.setOnSeekBarChangeListener(listener)

        dialog.setView(layout)
        dialog.show()
    }

    /**
     * 配置并渲染每日时长柱状图。
     *
     * 将项目所有会话按日期聚合，统计每天累计小时数，
     * 填充首尾日期间的空白天，最后配置坐标轴、颜色和交互后刷新图表。
     *
     * @param barChart   柱状图控件
     * @param project    当前项目数据
     * @param customColor 可选的自定义颜色；为空时读取已保存的颜色
     */
    private fun setupBarChart(barChart: BarChart, project: Project, customColor: Int? = null) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        // 按日期累加每个会话的时长（毫秒）
        val dailyMap = mutableMapOf<String, Long>()

        for (session in project.sessions) {
            val dateStr = dateFormat.format(Date(session.startTime))
            dailyMap[dateStr] = dailyMap.getOrDefault(dateStr, 0L) + session.durationMillis
        }

        val sortedDates = dailyMap.keys.sorted()
        // 填充首尾日期之间的所有日期（无记录的日期显示0，不画长条）
        val allDates = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(sortedDates.first())
        val endDate = dateFormat.parse(sortedDates.last())
        while (!calendar.time.after(endDate)) {
            allDates.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 构造柱状图数据条目：横轴索引，纵轴小时数
        val entries = mutableListOf<BarEntry>()
        allDates.forEachIndexed { index, date ->
            val millis = dailyMap[date] ?: 0L
            val hours = millis / 1000f / 3600f
            entries.add(BarEntry(index.toFloat(), hours))
        }

        // 无数据时隐藏图表
        if (entries.isEmpty()) {
            barChart.visibility = View.GONE
            return
        }

        // 数据集与颜色设置
        val dataSet = BarDataSet(entries, "")
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val chartColor = customColor ?: prefs.getInt("chart_color", android.graphics.Color.parseColor("#FF6200EE"))
        dataSet.setColor(chartColor)
        dataSet.valueTextSize = 12f
        dataSet.setDrawValues(true)

        // 数值格式化：显示小时（仅对非零值显示）
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0f) String.format("%.1fh", value) else ""
            }
        }

        val barData = BarData(dataSet)
        barData.setBarWidth(0.8f)
        barChart.data = barData

        // Y轴等分线配置（单位：小时）：根据最大值动态计算上界
        val maxHours = entries.maxOf { it.y }
        val yAxisMax = if (maxHours >= 1f) {
            Math.ceil(maxHours.toDouble()).toFloat()
        } else if (maxHours > 0f) {
            (Math.ceil(maxHours * 2.0).toFloat() / 2f)
        } else {
            1f
        }

        // 左侧 Y 轴：设置范围、刻度数、格式
        val axisLeft = barChart.axisLeft
        axisLeft.axisMinimum = 0f
        axisLeft.axisMaximum = yAxisMax
        val labelCount = yAxisMax.toInt() + 1
        axisLeft.setLabelCount(labelCount, true)
        axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.0f", value)
            }
        }
        axisLeft.setDrawGridLines(true)
        axisLeft.gridColor = Color.LTGRAY
        axisLeft.gridLineWidth = 1f

        // 禁用右侧Y轴
        barChart.axisRight.isEnabled = false

        // 图例隐藏
        barChart.legend.isEnabled = false

        // X轴日期简化为 月-日 格式
        val shortDates = allDates.map {
            val parts = it.split("-")
            "${parts[1]}-${parts[2]}"
        }

        // X 轴：底部显示日期，可拖拽缩放
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(shortDates)
        xAxis.granularity = 1f
        xAxis.labelCount = allDates.size
        xAxis.axisMinimum = -0.5f
        xAxis.axisMaximum = entries.size.toFloat() - 0.5f
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.textSize = 9f

        // 交互配置：可拖拽、缩放、双指缩放，最多可见 7 天
        barChart.setDragEnabled(true)
        barChart.setScaleEnabled(true)
        barChart.setPinchZoom(true)
        barChart.setVisibleXRangeMaximum(8f)

        // 隐藏描述文字，启用 Y 轴动画
        barChart.description.isEnabled = false
        barChart.animateY(1000)
        barChart.invalidate()
    }

    /**
     * 将总秒数格式化为可读时长字符串。
     * - 大于等于 1 小时：显示 H:MM:SS
     * - 不足 1 小时：显示 MM:SS
     */
    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
