package com.example.timerecording

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

class DetailActivity : AppCompatActivity() {

    private lateinit var viewModel: DetailViewModel
    private var currentProject: Project? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val projectId = intent.getLongExtra("project_id", -1)
        if (projectId == -1L) {
            Toast.makeText(this, "数据加载失败，请返回重试", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[DetailViewModel::class.java]

        viewModel.project.observe(this) { project ->
            if (project == null) {
                Toast.makeText(this, "项目数据为空", Toast.LENGTH_SHORT).show()
                finish()
                return@observe
            }
            currentProject = project
            displayProject(project)
        }

        viewModel.loadProject(projectId)
    }

    private fun displayProject(project: Project) {
        val tvName = findViewById<TextView>(R.id.tv_detail_project_name)
        val tvTotal = findViewById<TextView>(R.id.tv_detail_total_time)
        val rvSessions = findViewById<RecyclerView>(R.id.rv_sessions)
        val barChart = findViewById<BarChart>(R.id.bc_daily_chart)

        tvName.text = project.name
        val totalSeconds = project.sessions.sumOf { it.durationMillis } / 1000
        tvTotal.text = "总计时: ${formatDuration(totalSeconds)}"

        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = SessionAdapter(project.sessions)

        setupBarChart(barChart, project)

        val btnCustomColor = findViewById<Button>(R.id.btn_custom_color)
        btnCustomColor.setOnClickListener {
            showRgbColorPicker()
        }

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedColor = prefs.getInt("chart_color", android.graphics.Color.parseColor("#FF6200EE"))
        btnCustomColor.backgroundTintList = android.content.res.ColorStateList.valueOf(savedColor)
    }

    private fun refreshChartWithNewColor(color: Int) {
        val barChart = findViewById<BarChart>(R.id.bc_daily_chart)
        val btnCustomColor = findViewById<Button>(R.id.btn_custom_color)
        val project = currentProject
        if (project != null) {
            setupBarChart(barChart, project, color)
            btnCustomColor.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun showRgbColorPicker() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val currentColor = prefs.getInt("chart_color", android.graphics.Color.parseColor("#FF6200EE"))

        var red = android.graphics.Color.red(currentColor)
        var green = android.graphics.Color.green(currentColor)
        var blue = android.graphics.Color.blue(currentColor)

        val dialog = AlertDialog.Builder(this)
            .setTitle("选择条形图颜色")
            .setPositiveButton("确定") { _, _ ->
                val newColor = android.graphics.Color.rgb(red, green, blue)
                prefs.edit().putInt("chart_color", newColor).apply()
                refreshChartWithNewColor(newColor)
            }
            .setNegativeButton("取消", null)
            .create()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

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

    private fun setupBarChart(barChart: BarChart, project: Project, customColor: Int? = null) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dailyMap = mutableMapOf<String, Long>()

        for (session in project.sessions) {
            val dateStr = dateFormat.format(Date(session.startTime))
            dailyMap[dateStr] = dailyMap.getOrDefault(dateStr, 0L) + session.durationMillis
        }

        val sortedDates = dailyMap.keys.sorted()
        val entries = mutableListOf<BarEntry>()
        sortedDates.forEachIndexed { index, date ->
            val millis = dailyMap[date] ?: 0L
            val seconds = millis / 1000f
            entries.add(BarEntry(index.toFloat(), seconds))
        }

        if (entries.isEmpty()) {
            barChart.visibility = View.GONE
            return
        }

        val dataSet = BarDataSet(entries, "")
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val chartColor = customColor ?: prefs.getInt("chart_color", android.graphics.Color.parseColor("#FF6200EE"))
        dataSet.setColor(chartColor)
        dataSet.valueTextSize = 12f
        dataSet.setDrawValues(true)

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value >= 3600) {
                    String.format("%.1fh", value / 3600)
                } else {
                    String.format("%.0fs", value)
                }
            }
        }

        val barData = BarData(dataSet)
        barData.setBarWidth(0.8f)
        barChart.data = barData

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(sortedDates)
        xAxis.granularity = 1f
        xAxis.labelCount = sortedDates.size
        xAxis.axisMinimum = -0.5f
        if (entries.size < 10) {
            xAxis.axisMaximum = 10f
        }

        barChart.setDragEnabled(true)
        barChart.setScaleEnabled(true)
        barChart.setPinchZoom(true)
        barChart.setVisibleXRangeMaximum(10f)

        barChart.description.isEnabled = false
        barChart.animateY(1000)
        barChart.invalidate()
    }

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
