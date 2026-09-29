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

/**
 * 统计页 Fragment。
 *
 * 职责：
 * - 以甘特图（[GanttChartView]）展示选定日期内各项目的计时时段；
 * - 支持按天前进/后退、回到今天、点击日期文本弹出日期选择器；
 * - 通过 [StatisticsViewModel] 观察项目数据并自动刷新图表。
 */
class StatisticsFragment : Fragment() {

    private val viewModel: StatisticsViewModel by viewModels()     // 统计页视图模型

    private var ganttView: GanttChartView? = null                   // 甘特图自定义视图
    private var selectedDate = java.util.Calendar.getInstance()    // 当前选中的日期
    private var tvDate: TextView? = null                            // 显示当前日期的文本控件

    /** 加载统计页布局 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    /**
     * 视图创建完成后初始化甘特图、日期控件、翻页按钮并观察数据。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 根布局顶部设置状态栏高度的 padding，避免内容被状态栏遮挡
        val rootView = view.findViewById<LinearLayout>(R.id.statistics_root)
        val statusBarHeight = getStatusBarHeight()
        rootView.setPadding(
            rootView.paddingLeft,
            statusBarHeight,
            rootView.paddingRight,
            rootView.paddingBottom
        )

        // 绑定日期文本、翻页按钮和甘特图容器
        tvDate = view.findViewById(R.id.tv_selected_date)
        val btnPrev = view.findViewById<android.widget.ImageButton>(R.id.btn_prev_day)
        val btnNext = view.findViewById<android.widget.ImageButton>(R.id.btn_next_day)
        val btnToday = view.findViewById<android.widget.ImageButton>(R.id.btn_today)
        val ganttContainer = view.findViewById<android.widget.FrameLayout>(R.id.gantt_container)

        // 创建甘特图视图并添加到容器
        ganttView = GanttChartView(requireContext())
        ganttContainer.addView(
            ganttView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 前一天按钮：日期减一天并刷新图表
        btnPrev.setOnClickListener {
            selectedDate.add(java.util.Calendar.DAY_OF_YEAR, -1)
            refreshChart()
        }

        // 后一天按钮：日期加一天并刷新图表
        btnNext.setOnClickListener {
            selectedDate.add(java.util.Calendar.DAY_OF_YEAR, 1)
            refreshChart()
        }

        // 回到今天按钮：重置日期为今天并刷新
        btnToday.setOnClickListener {
            selectedDate = java.util.Calendar.getInstance()
            refreshChart()
            android.widget.Toast.makeText(
                requireContext(), "已回到今天", android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        // 点击日期文本弹出日期选择对话框
        tvDate?.setOnClickListener {
            showDatePickerDialog(selectedDate) { newCal ->
                selectedDate = newCal
                refreshChart()
            }
        }

        // 观察项目数据变化，数据更新后刷新图表
        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            refreshChart(projects)
        }
    }

    /**
     * 刷新甘特图：更新日期文本显示，并把数据传入甘特图重绘。
     *
     * @param projects 可选的项目数据；为空时取 ViewModel 当前值
     */
    private fun refreshChart(projects: List<Project>? = null) {
        // 更新日期文本
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        tvDate?.text = fmt.format(selectedDate.time)

        // 取数据并传给甘特图
        val data = projects ?: viewModel.projects.value ?: emptyList()
        ganttView?.setData(data, selectedDate)
    }

    /**
     * 弹出自定义日期选择对话框（年/月/日输入框），校验合法性后回调确认日期。
     *
     * @param currentDate 当前选中日期，用于回填输入框
     * @param onConfirm    校验通过后的回调，返回新的 Calendar
     */
    private fun showDatePickerDialog(
        currentDate: java.util.Calendar,
        onConfirm: (java.util.Calendar) -> Unit
    ) {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        // 水平排列的年/月/日输入框容器
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }

        // 年输入框（权重较大）
        val etYear = android.widget.EditText(requireContext()).apply {
            hint = "年"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            setText(currentDate.get(java.util.Calendar.YEAR).toString())
        }

        // 月输入框
        val etMonth = android.widget.EditText(requireContext()).apply {
            hint = "月"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText((currentDate.get(java.util.Calendar.MONTH) + 1).toString())
        }

        // 日输入框
        val etDay = android.widget.EditText(requireContext()).apply {
            hint = "日"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(currentDate.get(java.util.Calendar.DAY_OF_MONTH).toString())
        }

        // 设置月、日输入框的左间距
        val gap = (8 * density).toInt()
        (etMonth.layoutParams as android.widget.LinearLayout.LayoutParams).marginStart = gap
        (etDay.layoutParams as android.widget.LinearLayout.LayoutParams).marginStart = gap

        layout.addView(etYear)
        layout.addView(etMonth)
        layout.addView(etDay)

        // 构建对话框（按钮用 null，手动绑定点击以做校验）
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择日期")
            .setView(layout)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 手动绑定确定按钮，避免校验未通过就关闭对话框
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val y = etYear.text.toString().toIntOrNull()
            val m = etMonth.text.toString().toIntOrNull()
            val d = etDay.text.toString().toIntOrNull()

            // 输入完整性校验
            if (y == null || m == null || d == null) {
                android.widget.Toast.makeText(requireContext(), "请输入完整日期", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 范围合法性校验
            if (m !in 1..12 || d !in 1..31) {
                android.widget.Toast.makeText(requireContext(), "月份或日期不合法", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 构造 Calendar 并做真实日期存在性校验（如 2 月 30 日）
            val cal = java.util.Calendar.getInstance()
            cal.set(y, m - 1, d, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)

            // 如果 Calendar 自动归一化后值变化，说明日期不存在
            if (cal.get(java.util.Calendar.YEAR) != y ||
                cal.get(java.util.Calendar.MONTH) != m - 1 ||
                cal.get(java.util.Calendar.DAY_OF_MONTH) != d
            ) {
                android.widget.Toast.makeText(requireContext(), "日期不存在", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 校验通过，回调并关闭对话框
            onConfirm(cal)
            dialog.dismiss()
        }
    }

    /** 获取系统状态栏高度（像素），无法获取时回退为 24dp */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }
}
