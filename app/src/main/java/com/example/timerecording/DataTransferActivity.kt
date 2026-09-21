package com.example.timerecording

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.timerecording.data.backup.BackupFormat
import com.example.timerecording.data.backup.BackupManager
import com.example.timerecording.data.backup.BackupParseException
import com.example.timerecording.data.backup.ImportPlan
import com.example.timerecording.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataTransferActivity : AppCompatActivity() {

    private val repository by lazy { ProjectRepository.get(this) }

    private lateinit var tvProjectCount: TextView
    private lateinit var tvSessionCount: TextView
    private lateinit var tvTotalDuration: TextView

    /** 导出 JSON：系统「另存为」返回可写 Uri */
    private val createJsonDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeExport(it, BackupFormat.JSON) }
        }

    /** 导出 CSV：系统「另存为」返回可写 Uri */
    private val createCsvDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { writeExport(it, BackupFormat.CSV) }
        }

    /** 导入：系统文件选择器返回可读 Uri，格式由内容自动识别 */
    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readImportFile(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_transfer)

        applyStatusBarPadding()

        tvProjectCount = findViewById(R.id.tv_project_count)
        tvSessionCount = findViewById(R.id.tv_session_count)
        tvTotalDuration = findViewById(R.id.tv_total_duration)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_export_json).setOnClickListener { startExport(BackupFormat.JSON) }
        findViewById<Button>(R.id.btn_export_csv).setOnClickListener { startExport(BackupFormat.CSV) }
        findViewById<Button>(R.id.btn_import).setOnClickListener { openImportPicker() }

        refreshOverview()
    }

    // ------------------------------------------------------------ 数据概览

    private fun refreshOverview() {
        lifecycleScope.launch {
            val projects = withContext(Dispatchers.IO) { repository.getAllProjects() }
            val sessionCount = projects.sumOf { it.sessions.size }
            val totalMillis = projects.sumOf { project -> project.sessions.sumOf { it.durationMillis } }

            tvProjectCount.text = "${projects.size} 个"
            tvSessionCount.text = "$sessionCount 条"
            tvTotalDuration.text = formatTotalDuration(totalMillis / 1000)
        }
    }

    private fun formatTotalDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "$hours 小时 $minutes 分"
            minutes > 0 -> "$minutes 分"
            else -> "$totalSeconds 秒"
        }
    }

    // ------------------------------------------------------------ 导出

    private fun startExport(format: BackupFormat) {
        lifecycleScope.launch {
            val projectCount = withContext(Dispatchers.IO) { repository.getAllProjects().size }
            if (projectCount == 0) {
                toast("暂无数据可导出")
                return@launch
            }

            val fileName = buildFileName(format)
            when (format) {
                BackupFormat.JSON -> createJsonDocument.launch(fileName)
                BackupFormat.CSV -> createCsvDocument.launch(fileName)
            }
        }
    }

    private fun buildFileName(format: BackupFormat): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return when (format) {
            BackupFormat.JSON -> "TimeRecording_backup_$timestamp.json"
            BackupFormat.CSV -> "TimeRecording_export_$timestamp.csv"
        }
    }

    private fun writeExport(uri: Uri, format: BackupFormat) {
        lifecycleScope.launch {
            val payload = withContext(Dispatchers.IO) {
                try {
                    val exported = when (format) {
                        BackupFormat.JSON -> repository.exportJson()
                        BackupFormat.CSV -> repository.exportCsv()
                    }
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(exported.content.toByteArray(Charsets.UTF_8))
                        output.flush()
                    } ?: return@withContext null
                    exported
                } catch (e: Exception) {
                    null
                }
            }

            if (payload == null) {
                showAlert("导出失败", "无法写入所选位置，请重试或更换保存位置。")
            } else {
                showAlert(
                    "导出完成",
                    "已导出 ${payload.projectCount} 个项目、${payload.sessionCount} 条计时记录。"
                )
            }
        }
    }

    // ------------------------------------------------------------ 导入

    private fun openImportPicker() {
        openDocument.launch(
            arrayOf(
                "application/json",
                "text/*",
                "application/octet-stream"
            )
        )
    }

    private fun readImportFile(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
            }

            if (text.isNullOrBlank()) {
                showAlert("读取失败", "无法读取所选文件，请重试或更换文件。")
                return@launch
            }

            val plan = try {
                BackupManager.parse(text)
            } catch (e: BackupParseException) {
                showAlert("导入失败", e.message ?: "文件格式不正确，无法导入。")
                return@launch
            }

            askImportMode(plan)
        }
    }

    private fun askImportMode(plan: ImportPlan) {
        val formatName = if (plan.format == BackupFormat.JSON) "JSON 备份文件" else "CSV 表格文件"
        val sessionCount = plan.projects.sumOf { it.sessions.size }
        val density = resources.displayMetrics.density

        // AppCompat 的 AlertDialog 中 setMessage 与 setItems 互斥：
        // 一旦设置 message，AlertController 就不会把选项列表安装到对话框中，
        // 导致只剩按钮。因此这里用自定义视图承载识别结果，选项由按钮承载。
        val infoView = TextView(this).apply {
            text = buildString {
                append("已识别为 $formatName，包含 ${plan.projects.size} 个项目、$sessionCount 条记录。")
                append("\n\n")
                append("合并：保留现有数据，同名项目追加记录")
                append("\n")
                append("覆盖：清空现有数据后完整恢复")
            }
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#555555"))
            setLineSpacing(4f * density, 1f)
            setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
        }

        AlertDialog.Builder(this)
            .setTitle("选择导入方式")
            .setView(infoView)
            .setPositiveButton("合并") { _, _ -> confirmImport(plan, overwrite = false) }
            .setNegativeButton("覆盖") { _, _ -> confirmImport(plan, overwrite = true) }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun confirmImport(plan: ImportPlan, overwrite: Boolean) {
        lifecycleScope.launch {
            val activeTimerCount = withContext(Dispatchers.IO) {
                repository.getAllProjects().count { it.state != "idle" }
            }
            val summary = withContext(Dispatchers.IO) {
                repository.previewImport(plan, overwrite)
            }

            val totalProjects = summary.newProjects + summary.existingProjects
            val message = buildString {
                if (overwrite) {
                    appendLine("现有全部项目与计时记录将被永久删除，且无法撤销。")
                    if (activeTimerCount > 0) {
                        appendLine("当前有 $activeTimerCount 个正在计时或暂停的项目，其未结束的计时将一并丢失。")
                    }
                    appendLine()
                }
                appendLine("即将导入")
                appendLine("项目：$totalProjects 个（新增 ${summary.newProjects} 个，已存在 ${summary.existingProjects} 个）")
                append("记录：导入 ${summary.importedSessions} 条，跳过重复 ${summary.skippedDuplicates} 条")
                if (summary.invalidEntries > 0) {
                    append("\n无效条目：${summary.invalidEntries} 条已忽略")
                }
            }

            AlertDialog.Builder(this@DataTransferActivity)
                .setTitle(if (overwrite) "确认覆盖导入" else "确认合并导入")
                .setMessage(message)
                .setPositiveButton(if (overwrite) "确认覆盖" else "确认导入") { _, _ ->
                    executeImport(plan, overwrite)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun executeImport(plan: ImportPlan, overwrite: Boolean) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    repository.applyImport(plan, overwrite)
                } catch (e: Exception) {
                    null
                }
            }

            if (result == null) {
                showAlert("导入失败", "导入过程中出现错误，数据已回滚，现有数据未受影响。")
                return@launch
            }

            refreshOverview()
            showAlert(
                "导入完成",
                "新增项目 ${result.newProjects} 个，合并已有项目 ${result.existingProjects} 个。\n" +
                    "导入记录 ${result.importedSessions} 条，跳过重复 ${result.skippedDuplicates} 条。"
            )
        }
    }

    // ------------------------------------------------------------ 工具

    private fun applyStatusBarPadding() {
        val header = findViewById<LinearLayout>(R.id.transfer_header)
        header.setPadding(
            header.paddingStart,
            header.paddingTop + getStatusBarHeight(),
            header.paddingEnd,
            header.paddingBottom
        )
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    private fun showAlert(title: String, message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
