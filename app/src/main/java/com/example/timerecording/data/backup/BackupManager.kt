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

    const val APP_TAG = "TimeRecording"
    const val SCHEMA_VERSION = 1

    private const val BOM = "\uFEFF"
    private const val CSV_HEADER = "项目名称,开始时间,结束时间,时长(秒)"
    private const val CSV_LINE_SEPARATOR = "\r\n"

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

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
                        .sortedBy { it.startTime }
                        .map { BackupSession(it.startTime, it.endTime, it.durationMillis) }
                )
            }
        )
        return gson.toJson(backup)
    }

    /** 生成 CSV 表格文本，供 Excel / WPS 打开 */
    fun toCsv(projects: List<Project>): String {
        val builder = StringBuilder()
        builder.append(BOM)
        builder.append(CSV_HEADER).append(CSV_LINE_SEPARATOR)

        projects
            .flatMap { project -> project.sessions.map { project to it } }
            .sortedWith(compareBy({ it.first.name }, { it.second.startTime }))
            .forEach { (project, session) ->
                builder
                    .append(escapeCsvField(sanitizeForCsv(project.name))).append(',')
                    .append(dateTimeFormat.format(Date(session.startTime))).append(',')
                    .append(dateTimeFormat.format(Date(session.endTime))).append(',')
                    .append(session.durationMillis / 1000).append(CSV_LINE_SEPARATOR)
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
        val text = rawText.removePrefix(BOM).trim()
        if (text.isEmpty()) {
            throw BackupParseException("文件内容为空，无法导入")
        }
        return if (text.startsWith("{")) parseJson(text) else parseCsv(text)
    }

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

        if (rootObject.stringOrNull("app") != APP_TAG) {
            throw BackupParseException("该文件不是 TimeRecording 的备份文件")
        }

        val schemaVersion = rootObject.intOrNull("schemaVersion") ?: 0
        if (schemaVersion > SCHEMA_VERSION) {
            throw BackupParseException("备份文件来自更新版本的应用，请先升级应用再导入")
        }

        val projectsArray = rootObject.get("projects")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: throw BackupParseException("备份文件中没有可导入的数据")
        if (projectsArray.size() == 0) {
            throw BackupParseException("备份文件中没有可导入的数据")
        }

        var invalidEntries = 0
        val projects = mutableListOf<BackupProject>()

        for (element in projectsArray) {
            if (!element.isJsonObject) {
                invalidEntries++
                continue
            }
            val projectObject = element.asJsonObject
            val name = projectObject.stringOrNull("name")?.trim().orEmpty()
            if (name.isEmpty()) {
                invalidEntries++
                continue
            }

            val sessions = mutableListOf<BackupSession>()
            val sessionsArray = projectObject.get("sessions")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray

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

                    if (isValidSession(startTime, endTime, durationMillis)) {
                        sessions.add(BackupSession(startTime, endTime, durationMillis))
                    } else {
                        invalidEntries++
                    }
                }
            }

            projects.add(BackupProject(name, sessions))
        }

        if (projects.isEmpty()) {
            throw BackupParseException("备份文件中没有有效的项目数据")
        }

        return ImportPlan(projects, BackupFormat.JSON, invalidEntries)
    }

    private fun parseCsv(text: String): ImportPlan {
        val lines = text.split(Regex("\r\n|\n|\r")).filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw BackupParseException("文件内容为空，无法导入")
        }

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
        val groupedSessions = linkedMapOf<String, MutableList<BackupSession>>()

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

            val startTime = parseDateTime(fields[1].trim())
            val endTime = parseDateTime(fields[2].trim())
            if (startTime == null || endTime == null || endTime < startTime || startTime <= 0) {
                invalidEntries++
                continue
            }

            val parsedDuration = if (fields.size >= 4) parseDurationMillis(fields[3].trim()) else null
            val durationMillis = parsedDuration?.takeIf { it > 0 } ?: (endTime - startTime)
            if (durationMillis <= 0) {
                invalidEntries++
                continue
            }

            groupedSessions.getOrPut(name) { mutableListOf() }
                .add(BackupSession(startTime, endTime, durationMillis))
        }

        if (groupedSessions.isEmpty()) {
            throw BackupParseException("CSV 文件中没有有效的计时记录")
        }

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
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes -> {
                    if (char == '"') {
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
                char == '"' -> inQuotes = true
                char == ',' -> {
                    fields.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(char)
            }
            index++
        }
        fields.add(current.toString())

        return fields
    }

    /** 解析日期时间，同时兼容纯毫秒时间戳 */
    private fun parseDateTime(raw: String): Long? {
        if (raw.isEmpty()) return null

        raw.toLongOrNull()?.let { timestamp ->
            if (timestamp > 0) return timestamp
        }

        for (pattern in dateTimePatterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.getDefault())
                format.isLenient = false
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

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val primitive = get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive
            ?: return null
        return if (primitive.isNumber) primitive.asLong else primitive.asString.toLongOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val primitive = get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive
            ?: return null
        return if (primitive.isNumber) primitive.asInt else primitive.asString.toIntOrNull()
    }
}
