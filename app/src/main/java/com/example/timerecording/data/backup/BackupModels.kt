package com.example.timerecording.data.backup

import com.google.gson.annotations.SerializedName

/** 备份文件顶层结构，直接对应导出的 JSON 格式 */
data class BackupFile(
    @SerializedName("app") val app: String = BackupManager.APP_TAG,
    @SerializedName("schemaVersion") val schemaVersion: Int = BackupManager.SCHEMA_VERSION,
    @SerializedName("exportTime") val exportTime: Long = 0,
    @SerializedName("exportTimeReadable") val exportTimeReadable: String = "",
    @SerializedName("projectCount") val projectCount: Int = 0,
    @SerializedName("sessionCount") val sessionCount: Int = 0,
    @SerializedName("projects") val projects: List<BackupProject> = emptyList()
)

/** 备份中的项目，仅承载名称与会话记录 */
data class BackupProject(
    @SerializedName("name") val name: String = "",
    @SerializedName("sessions") val sessions: List<BackupSession> = emptyList()
)

/** 备份中的单条计时会话 */
data class BackupSession(
    @SerializedName("startTime") val startTime: Long = 0,
    @SerializedName("endTime") val endTime: Long = 0,
    @SerializedName("durationMillis") val durationMillis: Long = 0
)

/** 备份文件格式 */
enum class BackupFormat {
    JSON,
    CSV
}

/** 解析完成、尚未写入数据库的导入计划 */
data class ImportPlan(
    val projects: List<BackupProject>,
    val format: BackupFormat,
    val invalidEntries: Int
)

/** 导入统计，同时用于导入前的预览与导入后的结果汇报 */
data class ImportSummary(
    val newProjects: Int,
    val existingProjects: Int,
    val importedSessions: Int,
    val skippedDuplicates: Int,
    val invalidEntries: Int
)

/** 导出结果，包含文件内容与数据量统计，避免重复查询数据库 */
data class ExportPayload(
    val content: String,
    val projectCount: Int,
    val sessionCount: Int
)

/** 解析或校验失败时抛出，message 为可直接展示给用户的中文提示 */
class BackupParseException(message: String) : Exception(message)
