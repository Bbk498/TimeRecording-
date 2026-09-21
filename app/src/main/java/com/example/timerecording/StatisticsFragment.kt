package com.example.timerecording

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.timerecording.viewmodel.StatisticsViewModel

class StatisticsFragment : Fragment() {

    private val viewModel: StatisticsViewModel by viewModels()

    private var ganttView: GanttChartView? = null
    private var selectedDate = java.util.Calendar.getInstance()
    private var tvDate: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rootView = view.findViewById<LinearLayout>(R.id.statistics_root)
        val statusBarHeight = getStatusBarHeight()
        rootView.setPadding(
            rootView.paddingLeft,
            statusBarHeight,
            rootView.paddingRight,
            rootView.paddingBottom
        )

        tvDate = view.findViewById(R.id.tv_selected_date)
        val btnPrev = view.findViewById<android.widget.ImageButton>(R.id.btn_prev_day)
        val btnNext = view.findViewById<android.widget.ImageButton>(R.id.btn_next_day)
        val btnToday = view.findViewById<android.widget.ImageButton>(R.id.btn_today)
        val ganttContainer = view.findViewById<android.widget.FrameLayout>(R.id.gantt_container)

        ganttView = GanttChartView(requireContext())
        ganttContainer.addView(
            ganttView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        btnPrev.setOnClickListener {
            selectedDate.add(java.util.Calendar.DAY_OF_YEAR, -1)
            refreshChart()
        }

        btnNext.setOnClickListener {
            selectedDate.add(java.util.Calendar.DAY_OF_YEAR, 1)
            refreshChart()
        }

        btnToday.setOnClickListener {
            selectedDate = java.util.Calendar.getInstance()
            refreshChart()
            android.widget.Toast.makeText(
                requireContext(), "已回到今天", android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        tvDate?.setOnClickListener {
            showDatePickerDialog(selectedDate) { newCal ->
                selectedDate = newCal
                refreshChart()
            }
        }

        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            refreshChart(projects)
        }
    }

    private fun refreshChart(projects: List<Project>? = null) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        tvDate?.text = fmt.format(selectedDate.time)

        val data = projects ?: viewModel.projects.value ?: emptyList()
        ganttView?.setData(data, selectedDate)
    }

    private fun showDatePickerDialog(
        currentDate: java.util.Calendar,
        onConfirm: (java.util.Calendar) -> Unit
    ) {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }

        val etYear = android.widget.EditText(requireContext()).apply {
            hint = "年"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            setText(currentDate.get(java.util.Calendar.YEAR).toString())
        }

        val etMonth = android.widget.EditText(requireContext()).apply {
            hint = "月"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText((currentDate.get(java.util.Calendar.MONTH) + 1).toString())
        }

        val etDay = android.widget.EditText(requireContext()).apply {
            hint = "日"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(currentDate.get(java.util.Calendar.DAY_OF_MONTH).toString())
        }

        val gap = (8 * density).toInt()
        (etMonth.layoutParams as android.widget.LinearLayout.LayoutParams).marginStart = gap
        (etDay.layoutParams as android.widget.LinearLayout.LayoutParams).marginStart = gap

        layout.addView(etYear)
        layout.addView(etMonth)
        layout.addView(etDay)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择日期")
            .setView(layout)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val y = etYear.text.toString().toIntOrNull()
            val m = etMonth.text.toString().toIntOrNull()
            val d = etDay.text.toString().toIntOrNull()

            if (y == null || m == null || d == null) {
                android.widget.Toast.makeText(requireContext(), "请输入完整日期", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (m !in 1..12 || d !in 1..31) {
                android.widget.Toast.makeText(requireContext(), "月份或日期不合法", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cal = java.util.Calendar.getInstance()
            cal.set(y, m - 1, d, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)

            if (cal.get(java.util.Calendar.YEAR) != y ||
                cal.get(java.util.Calendar.MONTH) != m - 1 ||
                cal.get(java.util.Calendar.DAY_OF_MONTH) != d
            ) {
                android.widget.Toast.makeText(requireContext(), "日期不存在", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            onConfirm(cal)
            dialog.dismiss()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }
}
