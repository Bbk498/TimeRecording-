package com.example.timerecording.data.backup

import com.example.timerecording.Project
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份文件的序列化与解析。
 *
 * 该对象只负责纯数据的格式转换与校验，不接触数据库。
 * 数据库写入由 [com.example.timerecording.data.repository.ProjectRepository] 负责。
 */
object BackupManager {

    const val APP_TAG = "TimeRecording"        // 备份文件中的应用标识，用于校验文件来源
    const val SCHEMA_VERSION = 1                // 备份格式版本号，用于兼容性校验

    private const val BOM = "\uFEFF"            // UTF-8 BOM 标记，确保 CSV 在 Excel 中正确识别编码
    private const val CSV_HEADER = "项目名称,开始时间,结束时间,时长(秒)"  // CSV 表头
    private const val CSV_LINE_SEPARATOR = "\r\n"  // CSV 换行符（兼容 Windows 标准）

    // Gson 实例：美化输出 + 禁用 HTML 转义，保证 JSON 可读性
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    // 日期时间格式化器，用于导出时生成可读时间字符串
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** CSV 导入时依次尝试的日期时间格式 */
    private val dateTimePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy-M-d H:mm:ss",
        "yyyy-M-d H:mm"
    )

    // ------------------------------------------------------------------ 导出

    /** 生成 JSON 备份文本 */
    fun toJson(projects: List<Project>, exportTime: Long): String {
        val backup = BackupFile(
            app = APP_TAG,
            schemaVersion = SCHEMA_VERSION,
            exportTime = exportTime,
            exportTimeReadable = dateTimeFormat.format(Date(exportTime)),
            projectCount = projects.size,
            sessionCount = projects.sumOf { it.sessions.size },
            projects = projects.map { project ->
                BackupProject(
                    name = project.name,
                    sessions = project.sessions
                        .sortedBy { it.startTime }  // 会话按开始时间排序
                        .map { BackupSession(it.startTime, it.endTime, it.durationMillis) }
                )
            }
        )
        return gson.toJson(backup)
    }

    /** 生成 CSV 表格文本，供 Excel / WPS 打开 */
    fun toCsv(projects: List<Project>): String {
        val builder = StringBuilder()
        builder.append(BOM)                              // 写入 BOM 确保编码识别
        builder.append(CSV_HEADER).append(CSV_LINE_SEPARATOR)

        // 将所有项目的会话展平为行，按项目名和开始时间排序
        projects
            .flatMap { project -> project.sessions.map { project to it } }
            .sortedWith(compareBy({ it.first.name }, { it.second.startTime }))
            .forEach { (project, session) ->
                builder
                    .append(escapeCsvField(sanitizeForCsv(project.name))).append(',')  // 项目名称（转义）
                    .append(dateTimeFormat.format(Date(session.startTime))).append(',')  // 开始时间
                    .append(dateTimeFormat.format(Date(session.endTime))).append(',')    // 结束时间
                    .append(session.durationMillis / 1000).append(CSV_LINE_SEPARATOR)    // 时长（秒）
            }

        return builder.toString()
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 解析备份文本，自动识别 JSON 与 CSV 格式。
     *
     * @throws BackupParseException 校验失败时抛出，message 可直接展示给用户
     */
    fun parse(rawText: String): ImportPlan {
        val text = rawText.removePrefix(BOM).trim()  // 去除 BOM 和首尾空白
        if (text.isEmpty()) {
            throw BackupParseException("文件内容为空，无法导入")
        }
        // 以 "{" 开头判定为 JSON，否则按 CSV 解析
        return if (text.startsWith("{")) parseJson(text) else parseCsv(text)
    }

    /**
     * 解析 JSON 格式的备份文件。
     * 逐步校验文件标识、版本号、数据结构，跳过无效条目并统计数量。
     */
    private fun parseJson(text: String): ImportPlan {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            throw BackupParseException("JSON 格式不正确，无法解析备份文件")
        }

        if (!root.isJsonObject) {
            throw BackupParseException("JSON 格式不正确，无法解析备份文件")
        }
        val rootObject = root.asJsonObject

        // 校验文件来源标识
        if (rootObject.stringOrNull("app") != APP_TAG) {
            throw BackupParseException("该文件不是 TimeRecording 的备份文件")
        }

        // 校验版本号，高版本备份不允许导入
        val schemaVersion = rootObject.intOrNull("schemaVersion") ?: 0
        if (schemaVersion > SCHEMA_VERSION) {
            throw BackupParseException("备份文件来自更新版本的应用，请先升级应用再导入")
        }

        // 获取 projects 数组，不存在或为空则报错
        val projectsArray = rootObject.get("projects")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: throw BackupParseException("备份文件中没有可导入的数据")
        if (projectsArray.size() == 0) {
            throw BackupParseException("备份文件中没有可导入的数据")
        }

        var invalidEntries = 0
        val projects = mutableListOf<BackupProject>()

        // 遍历项目数组，逐个解析
        for (element in projectsArray) {
            if (!element.isJsonObject) {
                invalidEntries++
                continue
            }
            val projectObject = element.asJsonObject
            val name = projectObject.stringOrNull("name")?.trim().orEmpty()
            // 项目名为空视为无效
            if (name.isEmpty()) {
                invalidEntries++
                continue
            }

            val sessions = mutableListOf<BackupSession>()
            val sessionsArray = projectObject.get("sessions")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray

            // 遍历会话数组，逐个解析并校验
            if (sessionsArray != null) {
                for (sessionElement in sessionsArray) {
                    if (!sessionElement.isJsonObject) {
                        invalidEntries++
                        continue
                    }
                    val sessionObject = sessionElement.asJsonObject
                    val startTime = sessionObject.longOrNull("startTime") ?: 0L
                    val endTime = sessionObject.longOrNull("endTime") ?: 0L
                    val durationMillis = sessionObject.longOrNull("durationMillis") ?: 0L

                    // 校验会话有效性，有效则添加，无效则计数
                    if (isValidSession(startTime, endTime, durationMillis)) {
                        sessions.add(BackupSession(startTime, endTime, durationMillis))
                    } else {
                        invalidEntries++
                    }
                }
            }

            projects.add(BackupProject(name, sessions))
        }

        // 全部项目都无效时报错
        if (projects.isEmpty()) {
            throw BackupParseException("备份文件中没有有效的项目数据")
        }

        return ImportPlan(projects, BackupFormat.JSON, invalidEntries)
    }

    /**
     * 解析 CSV 格式的备份文件。
     * 支持带表头或不带表头的 CSV，按项目名分组汇总会话。
     */
    private fun parseCsv(text: String): ImportPlan {
        // 按多种换行符分割，过滤空行
        val lines = text.split(Regex("\r\n|\n|\r")).filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw BackupParseException("文件内容为空，无法导入")
        }

        // 检测并跳过表头行
        var dataStartIndex = 0
        val firstLine = lines[0]
        if (firstLine.contains("项目名")) {
            if (!firstLine.contains("开始") || !firstLine.contains("结束")) {
                throw BackupParseException("CSV 表头不正确，应包含：项目名称、开始时间、结束时间")
            }
            dataStartIndex = 1
        }
        if (lines.size <= dataStartIndex) {
            throw BackupParseException("CSV 文件中没有数据行")
        }

        var invalidEntries = 0
        // 按项目名分组的会话列表，保持插入顺序
        val groupedSessions = linkedMapOf<String, MutableList<BackupSession>>()

        // 逐行解析数据
        for (index in dataStartIndex until lines.size) {
            val fields = splitCsvLine(lines[index])
            if (fields.size < 3) {
                invalidEntries++
                continue
            }

            val name = fields[0].trim()
            if (name.isEmpty()) {
                invalidEntries++
                continue
            }

            // 解析开始时间和结束时间
            val startTime = parseDateTime(fields[1].trim())
            val endTime = parseDateTime(fields[2].trim())
            // 校验时间有效性：结束时间不能早于开始时间
            if (startTime == null || endTime == null || endTime < startTime || startTime <= 0) {
                invalidEntries++
                continue
            }

            // 解析时长，若未提供或无效则按结束-开始时间计算
            val parsedDuration = if (fields.size >= 4) parseDurationMillis(fields[3].trim()) else null
            val durationMillis = parsedDuration?.takeIf { it > 0 } ?: (endTime - startTime)
            if (durationMillis <= 0) {
                invalidEntries++
                continue
            }

            // 按项目名分组收集会话
            groupedSessions.getOrPut(name) { mutableListOf() }
                .add(BackupSession(startTime, endTime, durationMillis))
        }

        if (groupedSessions.isEmpty()) {
            throw BackupParseException("CSV 文件中没有有效的计时记录")
        }

        // 转换为 BackupProject 列表，会话按开始时间排序
        val projects = groupedSessions.map { (name, sessions) ->
            BackupProject(name, sessions.sortedBy { it.startTime })
        }

        return ImportPlan(projects, BackupFormat.CSV, invalidEntries)
    }

    /**
     * 会话有效性校验。
     *
     * 不再按「不足 1 分钟不记录」的规则过滤——若备份来自旧版本或其他工具，
     * 保留原始数据比按当前规则裁剪更尊重用户数据。
     */
    private fun isValidSession(startTime: Long, endTime: Long, durationMillis: Long): Boolean {
        return startTime > 0 && endTime >= startTime && durationMillis >= 0
    }

    // ------------------------------------------------------------------ CSV 工具

    /**
     * CSV 是扁平结构，字段内含换行会破坏行结构。
     * 导出时把换行替换为空格，逗号与双引号由 [escapeCsvField] 负责转义。
     */
    private fun sanitizeForCsv(value: String): String {
        return value.replace('\r', ' ').replace('\n', ' ')
    }

    /** CSV 字段转义：若包含逗号或双引号，则用双引号包裹并将内部双引号翻倍 */
    private fun escapeCsvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"')
        return if (needsQuoting) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /** 按 CSV 规范切分一行，正确处理双引号包裹与转义的双引号 */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false   // 是否处于双引号包裹中
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes -> {
                    if (char == '"') {
                        // 连续两个双引号表示转义的双引号字符
                        if (index + 1 < line.length && line[index + 1] == '"') {
                            current.append('"')
                            index++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        current.append(char)
                    }
                }
                char == '"' -> inQuotes = true       // 遇到双引号开始包裹
                char == ',' -> {                      // 逗号在非包裹状态下为字段分隔符
                    fields.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(char)
            }
            index++
        }
        fields.add(current.toString())  // 添加最后一个字段

        return fields
    }

    /** 解析日期时间，同时兼容纯毫秒时间戳 */
    private fun parseDateTime(raw: String): Long? {
        if (raw.isEmpty()) return null

        // 先尝试当作纯毫秒时间戳解析
        raw.toLongOrNull()?.let { timestamp ->
            if (timestamp > 0) return timestamp
        }

        // 依次尝试预定义的日期时间格式
        for (pattern in dateTimePatterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.getDefault())
                format.isLenient = false  // 严格模式，不自动修正非法日期
                val date = format.parse(raw)
                if (date != null) return date.time
            } catch (e: Exception) {
                // 尝试下一种格式
            }
        }
        return null
    }

    /** 解析时长（秒），兼容 "5400"、"5400.0"、"5400秒" 等写法 */
    private fun parseDurationMillis(raw: String): Long? {
        val cleaned = raw
            .removeSuffix("秒")
            .removeSuffix("s")
            .removeSuffix("S")
            .trim()
        val seconds = cleaned.toDoubleOrNull() ?: return null
        if (seconds < 0) return null
        return (seconds * 1000).toLong()
    }

    // ------------------------------------------------------------------ JSON 取值工具

    /** 安全获取 JsonObject 中的字符串值，类型不匹配时返回 null */
    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    /** 安全获取 JsonObject 中的 Long 值，支持数字或数字字符串 */
    private fun JsonObject.longOrNull(key: String): Long? {
        val primitive = get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive
            ?: return null
        return if (primitive.isNumber) primitive.asLong else primitive.asString.toLongOrNull()
    }

    /** 安全获取 JsonObject 中的 Int 值，支持数字或数字字符串 */
    private fun JsonObject.intOrNull(key: String): Int? {
        val primitive = get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive
            ?: return null
        return if (primitive.isNumber) primitive.asInt else primitive.asString.toIntOrNull()
    }
}
