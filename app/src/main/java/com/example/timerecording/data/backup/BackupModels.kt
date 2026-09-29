package com.example.timerecording.data.backup

import com.google.gson.annotations.SerializedName

/**
 * 备份文件顶层结构，直接对应导出的 JSON 格式。
 * 包含文件元信息（应用标识、版本、导出时间）及项目数据。
 */
data class BackupFile(
    @SerializedName("app") val app: String = BackupManager.APP_TAG,                        // 应用标识，用于校验文件来源
    @SerializedName("schemaVersion") val schemaVersion: Int = BackupManager.SCHEMA_VERSION,  // 备份格式版本号
    @SerializedName("exportTime") val exportTime: Long = 0,                                  // 导出时间戳（毫秒）
    @SerializedName("exportTimeReadable") val exportTimeReadable: String = "",              // 导出时间可读字符串
    @SerializedName("projectCount") val projectCount: Int = 0,                              // 项目总数
    @SerializedName("sessionCount") val sessionCount: Int = 0,                              // 会话总数
    @SerializedName("projects") val projects: List<BackupProject> = emptyList()             // 项目列表
)

/** 备份中的项目，仅承载名称与会话记录，不含计时状态 */
data class BackupProject(
    @SerializedName("name") val name: String = "",                                          // 项目名称
    @SerializedName("sessions") val sessions: List<BackupSession> = emptyList()             // 该项目的会话列表
)

/** 备份中的单条计时会话 */
data class BackupSession(
    @SerializedName("startTime") val startTime: Long = 0,        // 会话开始时间戳（毫秒）
    @SerializedName("endTime") val endTime: Long = 0,            // 会话结束时间戳（毫秒）
    @SerializedName("durationMillis") val durationMillis: Long = 0  // 会话持续时长（毫秒）
)

/** 备份文件格式枚举，标识导入文件的来源格式 */
enum class BackupFormat {
    JSON,   // JSON 格式备份
    CSV     // CSV 格式备份
}

/** 解析完成、尚未写入数据库的导入计划，包含有效数据和无效条目计数 */
data class ImportPlan(
    val projects: List<BackupProject>,     // 解析得到的项目列表
    val format: BackupFormat,              // 源文件格式
    val invalidEntries: Int                // 解析过程中跳过的无效条目数
)

/** 导入统计，同时用于导入前的预览与导入后的结果汇报 */
data class ImportSummary(
    val newProjects: Int,                  // 新增项目数
    val existingProjects: Int,             // 已存在（同名）项目数
    val importedSessions: Int,             // 成功导入的会话数
    val skippedDuplicates: Int,            // 因重复跳过的会话数
    val invalidEntries: Int                // 解析阶段发现的无效条目数
)

/** 导出结果，包含文件内容与数据量统计，避免重复查询数据库 */
data class ExportPayload(
    val content: String,                   // 导出的文件文本内容
    val projectCount: Int,                 // 涉及的项目数
    val sessionCount: Int                  // 涉及的会话数
)

/** 解析或校验失败时抛出，message 为可直接展示给用户的中文提示 */
class BackupParseException(message: String) : Exception(message)
