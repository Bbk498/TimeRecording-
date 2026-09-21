# 数据导入与导出功能设计方案

## 一、需求与目标

为 TimeRecording 增加数据备份与恢复能力，解决以下问题：

1. 用户更换手机或重装应用后，全部项目与计时记录无法找回
2. 数据仅存于应用私有数据库，无法导出用于 Excel 分析或长期归档
3. 无任何跨设备迁移手段

目标交付：

- 一键导出全部数据为 JSON 备份文件（可原样恢复）
- 一键导出全部数据为 CSV 表格（可用 Excel / WPS 打开分析）
- 从 JSON 备份文件导入数据，支持「合并」与「覆盖」两种模式
- 无需申请任何存储权限，兼容 Android 7.0 至最新版本

## 二、现状分析

### 2.1 当前无导入导出实现

经全量检索，代码库中不存在任何导入、导出、备份、分享相关实现：

| 检索项 | 结果 |
|--------|------|
| `导出` / `export` | 无匹配（仅 `android:exported` 属性） |
| `导入` / `import` | 无匹配（仅 Kotlin 的 `import` 关键字） |
| `FileProvider` | 未配置 |
| `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` | 未使用 |
| `res/xml/file_paths.xml` | 不存在 |

该功能属于**从零新建**，不是对已有功能的优化。

### 2.2 数据层现状

两个 Room 实体，一对多关系：

```
ProjectEntity (projects)
├── id: Long (PK, autoGenerate)
├── name: String
├── state: String          // idle / running / paused
├── sessionStartTime: Long // 当前会话开始时刻
├── currentStartTime: Long // running=本段起点 / paused=暂停时刻
└── currentElapsedMillis: Long

SessionEntity (sessions)   ← 仅已提交的历史记录
├── id: Long (PK, autoGenerate)
├── projectId: Long (FK → projects.id, ON DELETE CASCADE)
├── startTime: Long
├── endTime: Long
└── durationMillis: Long
```

关键事实：正在计时（`running`）或暂停（`paused`）的项目，其未提交的时长保存在 `ProjectEntity` 的临时字段中，**不在 `sessions` 表里**。只有点击「结束」且有效时长 ≥ 60 秒后，才会写入一条 `SessionEntity`。

数据读取入口为 `ProjectRepository.get()` 单例：

- `getAllProjects(): List<Project>` — 返回含 sessions 的完整列表，**可直接复用于导出**
- `getProject(projectId): Project?`
- `addProject(name)` / `renameProject` / `deleteProject`

写入入口：`ProjectDao.insert(project): Long`、`SessionDao.insert(session): Long`

### 2.3 设置页现状

`WallpaperFragment` 实际承担设置页职责（命名与实际不符，属历史遗留）。它 inflate `fragment_settings.xml`，用 `SettingsAdapter` 渲染 `SettingItem` 列表，通过 `actionId` 字符串分发点击事件：

```kotlin
SettingItem(iconRes, title, subtitle, actionId)
// actionId 现有取值："wallpaper" | "about" | "update"
```

新增功能只需追加 `SettingItem` 并在 `when` 中增加分支。

### 2.4 可用依赖

| 依赖 | 状态 | 用途 |
|------|------|------|
| Gson 2.10.1 | 已引入 | JSON 序列化/反序列化 |
| room-ktx 2.8.5 | 已引入 | 提供 `RoomDatabase.withTransaction { }` 事务支持 |
| activity-compose 1.8.0 | 已引入 | 传递带入 `androidx.activity`，提供 `registerForActivityResult` 与 SAF 契约 |
| fragment-ktx 1.6.2 | 已引入 | `Fragment.registerForActivityResult` |

**结论：无需新增任何依赖。**

## 三、方案总览

### 3.1 技术选型

| 环节 | 选型 | 理由 |
|------|------|------|
| 文件读写通道 | 存储访问框架（SAF） | 无需任何存储权限；Android 7.0+ 全兼容；用户自选保存位置（本地、网盘、U 盘）；不用维护 `FileProvider` |
| 导出契约 | `ActivityResultContracts.CreateDocument` | 系统弹出「另存为」，返回可写 `Uri` |
| 导入契约 | `ActivityResultContracts.OpenDocument` | 系统弹出文件选择器，返回可读 `Uri` |
| 序列化 | Gson（已有） | 无新增依赖 |
| 事务 | `db.withTransaction { }`（room-ktx 已有） | 导入过程原子化，中途失败自动回滚 |
| 线程 | `Dispatchers.IO` 协程 | 文件读写与批量入库不阻塞主线程 |

不引入 `FileProvider`、不申请 `WRITE_EXTERNAL_STORAGE`、不使用已废弃的 `Environment.getExternalStorageDirectory()`。

### 3.2 功能结构

设置页新增一项「数据导入与导出」，点击进入独立的 `DataTransferActivity`：

```
DataTransferActivity
├── 返回栏（复用 AboutActivity 的样式）
├── 数据概览
│   ├── 项目数：N 个
│   ├── 记录数：M 条
│   └── 累计时长：X 小时 Y 分
├── [导出为 JSON]  → 完整备份，可再次导入
├── [导出为 CSV]   → 表格文件，供 Excel 分析（不支持反向导入）
└── [从 JSON 导入] → 选择文件 → 选择模式 → 确认 → 执行
```

选择独立 Activity 而非在 Fragment 内直接处理，理由：

- 导入需要「选择模式 + 二次确认」两步交互，放在设置列表项的点击回调里会难以承载
- 需要展示数据概览，设置列表项只有标题与副标题两行
- 避免 `WallpaperFragment` 继续膨胀（它已承担壁纸、关于、更新三项分发）

## 四、导出设计

### 4.1 JSON 备份格式（schemaVersion 1）

```json
{
  "app": "TimeRecording",
  "schemaVersion": 1,
  "exportTime": 1789830000000,
  "exportTimeReadable": "2026-09-21 14:30:00",
  "projectCount": 3,
  "sessionCount": 42,
  "projects": [
    {
      "name": "写论文",
      "sessions": [
        {
          "startTime": 1789722000000,
          "endTime": 1789727400000,
          "durationMillis": 5400000
        }
      ]
    }
  ]
}
```

设计说明：

- 顶层写入 `app` 与 `schemaVersion`，导入时用于识别文件归属与兼容性判断
- 同时写入机器可读的 `exportTime` 与人类可读的 `exportTimeReadable`，便于用户直接打开文件确认备份时间
- 写入 `projectCount` / `sessionCount`，导入前可与实际解析结果比对，快速发现文件被截断
- **不导出** `id` 字段：项目与会话 ID 是数据库自增值，跨设备无语义，导入时重新分配
- **不导出** `state` / `sessionStartTime` / `currentStartTime` / `currentElapsedMillis`：这些是计时器的瞬时状态，导出后在另一台设备上恢复会导致计时错乱。详见 §6.2

### 4.2 CSV 表格格式

```csv
项目名称,开始时间,结束时间,时长(秒)
写论文,2026-09-19 09:00:00,2026-09-19 10:30:00,5400
写论文,2026-09-20 14:00:00,2026-09-20 15:12:30,4350
```

设计说明：

- 首个字符写入 UTF-8 BOM（`\uFEFF`），否则 Excel 打开中文项目名会乱码
- 行结束符使用 `\r\n`，符合 CSV 规范且 Excel 兼容性最好
- 时长额外提供「秒」列，方便用户在 Excel 中做求和、透视
- 项目名若含逗号、双引号或换行，按 CSV 规范用双引号包裹并将内部双引号转义为两个双引号
- 时间格式统一为 `yyyy-MM-dd HH:mm:ss`
- 会话按「项目名 ASC，开始时间 ASC」排序，便于阅读
- **CSV 仅支持导出，不支持导入**：CSV 无嵌套结构，无法表达 schema 版本，且用户极易在 Excel 中改动格式导致解析歧义。若后续有需求再单独评估

### 4.3 导出文件名

- JSON：`TimeRecording_backup_yyyyMMdd_HHmmss.json`
- CSV：`TimeRecording_export_yyyyMMdd_HHmmss.csv`

`CreateDocument` 契约的第一个参数即为建议文件名，用户在「另存为」界面可自行修改。

### 4.4 导出流程

```
点击「导出为 JSON」
  → 查询数据（Dispatchers.IO）
  → 若项目数为 0，Toast 提示「暂无数据可导出」并终止
  → 启动 CreateDocument 契约，传入建议文件名
  → 用户选定目标位置，回调返回 Uri
  → 在 IO 线程序列化并写入 contentResolver.openOutputStream(uri)
  → 主线程 Toast 提示「已导出 N 个项目、M 条记录」
```

写入使用 `use { }` 确保流正确关闭；捕获 `IOException` / `SecurityException` 并提示「导出失败，请重试或更换保存位置」。

### 4.5 边界处理

| 场景 | 处理 |
|------|------|
| 无任何项目 | Toast「暂无数据可导出」，不唤起文件选择器 |
| 有项目但无任何会话记录 | 允许导出，JSON 中 `sessionCount: 0`，`sessions: []` |
| 存在 running / paused 项目 | 正常导出该项目名及其已提交的 sessions；未提交的临时时长不导出（§6.2） |
| 用户在选择器里点取消 | 回调返回 `null`，静默返回，不提示错误 |
| 目标位置不可写 / 空间不足 | 捕获异常，Toast 提示失败原因 |

## 五、导入设计

### 5.1 导入模式

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| **合并**（默认推荐） | 保留现有数据；按项目名匹配，同名项目追加会话；已存在的相同会话跳过 | 从旧手机补充数据、多设备数据汇总 |
| **覆盖** | 清空全部现有项目与会话，再导入文件内容 | 换机后完整恢复、修复被误改的数据 |

选择「覆盖」时，确认对话框需明确警示「现有 N 个项目、M 条记录将被永久删除且无法撤销」，并需要二次确认。

### 5.2 合并模式的去重规则

- **项目匹配**：以 `name` 精确匹配（不做大小写或空白归一化，避免误合并）。同名则复用该项目 ID，不同名则新建项目
- **会话去重**：以 `startTime + endTime + durationMillis` 三元组为键。同一项目下三元组完全相同则判定为重复，跳过
- 去重键不含数据库 ID，因为跨设备 ID 无语义
- 合并后需统计并提示：新增项目数、新增会话数、跳过重复数

### 5.3 导入校验

解析后、写入前依次校验：

1. 文件非空且为合法 JSON —— 否则提示「文件格式不正确，请选择 TimeRecording 导出的 JSON 备份文件」
2. `app` 字段为 `"TimeRecording"` —— 不匹配时提示「该文件不是 TimeRecording 的备份文件」并终止
3. `schemaVersion` ≤ 1 —— 大于 1 时提示「备份文件来自更新版本的应用，请先升级应用」
4. `projects` 非空 —— 否则提示「备份文件中没有可导入的数据」
5. 逐条校验会话：`startTime > 0`、`endTime >= startTime`、`durationMillis >= 0`。不满足的条目跳过并计数
6. 容错：单个会话字段缺失或类型错误时跳过该条，不中断整个导入

校验通过后弹出确认对话框，展示解析结果：

```
即将导入
项目：3 个（新增 2 个，已存在 1 个）
记录：42 条（将导入 38 条，跳过重复 4 条）
无效条目：0 条

[取消]  [确认导入]
```

### 5.4 导入流程

```
点击「从 JSON 导入」
  → 启动 OpenDocument 契约（MIME: application/json, text/*, application/octet-stream）
  → 用户选定文件，回调返回 Uri
  → 弹出模式选择对话框（合并 / 覆盖）
  → IO 线程读取并解析、校验
  → 展示解析结果确认对话框
  → 用户确认后，在 db.withTransaction { } 中执行写入
  → 主线程 Toast 汇报最终结果
```

解析失败或校验不通过时在模式选择之后、确认框之前终止，不触碰数据库。

### 5.5 覆盖模式的原子性

覆盖模式必须在单个事务内完成「清空 + 写入」：

```kotlin
db.withTransaction {
    sessionDao.deleteAll()
    projectDao.deleteAll()
    // 重新写入
}
```

任一步骤抛异常，事务整体回滚，现有数据不受影响。禁止先清空再分步写入。

### 5.6 导入后的计时器状态

导入产生的所有项目一律设为 `state = "idle"`，且 `sessionStartTime` / `currentStartTime` / `currentElapsedMillis` 全部置 0。

原因：备份文件不包含计时器瞬时状态（§6.2），若强行恢复为 `running`，`recoverRunningTimers()` 会把「上次导出到本次导入之间」的全部时间误算为工作时长。

## 六、关键设计决策

### 6.1 为何不导出数据库 ID

`ProjectEntity.id` / `SessionEntity.id` 是 Room 自增值，仅在单个设备的数据库内有意义。导出 ID 后导入到另一设备，可能与现有 ID 冲突，或产生空洞。导入时统一重新分配 ID，避免冲突。

### 6.2 为何不导出进行中的计时状态

`state` / `sessionStartTime` / `currentStartTime` / `currentElapsedMillis` 表示「此刻正在计时」的瞬时状态。若导出时项目处于 `running`，备份文件记录的是导出那一刻的时间戳。用户可能在数天后才导入这份备份，此时：

- 应用启动时 `ProjectRepository.init` 会调用 `recoverRunningTimers()`，把 `currentStartTime` 到当前时间的差值累加入时长
- 结果是凭空多出数十小时的工作记录

因此备份只承载「已完成、已提交」的会话记录。这是数据保真与数据正确性之间必要的取舍，也是 `ProjectRepository` 中 `recoverRunningTimers()` 逻辑决定的硬约束。

### 6.3 为何不导出小于 1 分钟的会话

应用规则是「有效时长不足 60 秒不计入记录」，因此 `sessions` 表中本就不存在此类记录，导出时无需特殊过滤。导入时对备份文件中的会话**不做** 1 分钟过滤——若备份来自旧版本或其他工具，保留原始数据比按当前规则裁剪更尊重用户数据。

### 6.4 为何选择独立 Activity 承载

见 §3.2。补充一点：独立 Activity 让「数据概览」可以在导入完成后立即刷新，用户能直观看到导入前后的数量变化。

### 6.5 为何不引入 FileProvider

`FileProvider` 用于把应用私有文件通过 `content://` URI 共享给其他应用（如微信、邮件）。本方案使用 SAF `CreateDocument`，由用户主动选择目标位置，系统负责授权，应用无需暴露自己的文件。这既简化了实现，也避免了 `file_paths.xml` 配置错误导致的越权风险。

若后续需要「直接分享到微信」这类功能，再单独引入 `FileProvider` 与 `file_paths.xml`，可作为独立的二期需求。

## 七、需要新增的文件

| # | 文件 | 职责 |
|---|------|------|
| 1 | `app/src/main/java/com/example/timerecording/data/backup/BackupModels.kt` | 定义 JSON 序列化用的数据类：`BackupFile`、`BackupProject`、`BackupSession`，字段与 §4.1 格式一一对应，全部使用 `@SerializedName` 显式声明键名，避免混淆后字段名变化导致备份失效 |
| 2 | `app/src/main/java/com/example/timerecording/data/backup/BackupManager.kt` | 核心逻辑：`exportToJson()`、`exportToCsv()`、`parseAndValidate()`、`importData(mode)`。负责序列化、CSV 转义、校验、事务写入、结果统计 |
| 3 | `app/src/main/java/com/example/timerecording/DataTransferActivity.kt` | 界面与交互：数据概览加载、三个按钮、SAF 契约注册、模式选择与确认对话框、结果提示 |
| 4 | `app/src/main/res/layout/activity_data_transfer.xml` | 布局：返回栏 + 数据概览卡片 + 三个按钮，视觉风格与 `activity_about.xml` 保持一致 |

## 八、需要修改的文件

| # | 文件 | 改动内容 |
|---|------|---------|
| 1 | `app/src/main/java/com/example/timerecording/data/db/ProjectDao.kt` | 新增 `@Query("DELETE FROM projects") suspend fun deleteAll()`，供覆盖模式使用 |
| 2 | `app/src/main/java/com/example/timerecording/data/db/SessionDao.kt` | 新增 `@Query("DELETE FROM sessions") suspend fun deleteAll()`，供覆盖模式使用（虽可依赖外键级联，但显式删除语义更清晰） |
| 3 | `app/src/main/java/com/example/timerecording/WallpaperFragment.kt` | 在 `settings` 列表追加 `SettingItem(title = "数据导入与导出", subtitle = "备份或恢复你的计时记录", actionId = "transfer")`；在 `when` 分支追加 `"transfer" -> startActivity(Intent(requireContext(), DataTransferActivity::class.java))` |
| 4 | `app/src/main/AndroidManifest.xml` | 注册 `<activity android:name=".DataTransferActivity" />`；同时移除第 3 行的 `package="com.example.timerecording"` 属性（见 §9.3） |

`AppDatabase.kt` **无需修改**：无 schema 变更，数据库版本保持 1，不涉及迁移。`ProjectRepository.kt` **无需修改**：导出复用现有的 `getAllProjects()`；导入通过 `AppDatabase.getDatabase(context)` 拿 DAO 执行，或给 Repository 增加薄封装方法（见 §10 决策点 3）。

## 九、需要删除或清理的内容

### 9.1 确认删除（无任何引用）

| # | 文件 | 证据 | 建议 |
|---|------|------|------|
| 1 | `app/src/main/res/layout/fragment_wallpaper.xml` | 全量检索 `fragment_wallpaper` 无任何匹配。内容与 `activity_wallpaper_settings.xml` 几乎重复（仅标题文字不同） | 删除 |
| 2 | `app/src/main/res/menu/menu_detail.xml` | 全量检索 `R.menu` 无任何匹配，`DetailActivity` 使用 `setContentView` 而非菜单 | 删除 |

### 9.2 确认可移除的依赖

| # | 依赖 | 证据 | 建议 |
|---|------|------|------|
| 1 | `com.github.QuadFlask:colorpicker:0.0.15` | 全量检索 `colorpicker` / `ColorPicker` 无匹配。`DetailActivity.showRgbColorPicker()` 是手写的 `SeekBar` 实现 | 移除以减小 APK 体积 |
| 2 | `com.vanniktech:android-image-cropper:4.5.0` | `WallpaperSettingsActivity` 实际使用 `com.yalantis.ucrop.UCrop`，未使用 vanniktech 库 | 移除以减小 APK 体积 |
| 3 | `//implementation("com.github.PhilJay:MPAndroidChart:3.1.0")`（`build.gradle.kts` 第 65 行） | 注释掉的重复依赖行 | 删除注释行 |

### 9.3 `AndroidManifest.xml` 的 `package` 属性

当前构建输出警告：

```
package="com.example.timerecording" found in source AndroidManifest.xml
Setting the namespace via the package attribute is no longer supported, and the value is ignored.
Recommendation: remove package="com.example.timerecording" from the source AndroidManifest.xml
```

`namespace` 已在 `build.gradle.kts` 中正确声明，Manifest 中的 `package` 属性已被 AGP 忽略。移除后构建警告消失。此项与导入导出功能无关，但既然要改 Manifest，顺手清理。

### 9.4 建议但需要你确认的改动

| # | 项 | 说明 | 影响面 |
|---|----|------|--------|
| 1 | 重命名 `WallpaperFragment` → `SettingsFragment` | 该类实际是设置页，inflate 的是 `fragment_settings.xml`，命名误导。属于纯重命名重构 | 需同步修改 `MainActivity` 中的 `nav_wallpaper -> WallpaperFragment()` 一处引用 |
| 2 | 移除未使用的 Compose 依赖 | `build.gradle.kts` 中引入了 Compose BOM、material3、ui、ui-tooling、activity-compose 等，但 `compose = true` 仅用于构建开关，全项目无 `@Composable` 代码。移除可显著减小 APK 体积 | 需确认后续无 Compose 迁移计划 |
| 3 | `isMinifyEnabled = false` 的注释与实际相反 | 注释写「★ 开启代码压缩和混淆」，实际设为 `false`。属注释错误 | 仅改注释 |

上述三项**不包含在本方案执行范围内**，需你明确同意后再单独处理。

## 十、执行步骤

1. **备份模型** —— 新建 `data/backup/BackupModels.kt`，定义 `BackupFile` / `BackupProject` / `BackupSession`
2. **核心逻辑** —— 新建 `data/backup/BackupManager.kt`，实现 JSON 导出、CSV 导出、解析校验、事务导入、结果统计
3. **DAO 扩展** —— `ProjectDao` 与 `SessionDao` 各增加 `deleteAll()`
4. **界面布局** —— 新建 `res/layout/activity_data_transfer.xml`
5. **界面逻辑** —— 新建 `DataTransferActivity.kt`，接入 SAF 契约与对话框流程
6. **入口接入** —— 修改 `WallpaperFragment.kt`，追加设置项与分发分支
7. **清单注册** —— 修改 `AndroidManifest.xml`，注册 Activity 并移除 `package` 属性
8. **空文件防护** —— 导出前检查项目数，导入前检查文件非空
9. **编译验证** —— `gradlew assembleDebug`
10. **清理死代码**（§9.1、§9.2、§9.3）—— 删除无用布局与菜单、移除未用依赖、删除注释残留
11. **回归编译** —— 清理后再次 `gradlew assembleDebug` 确认无破坏

## 十一、验证要点

功能验证（建议在真机或模拟器上按顺序执行）：

- [ ] 无任何项目时点击导出，提示「暂无数据可导出」且不弹出文件选择器
- [ ] 有数据时导出 JSON，文件可在系统文件管理器中找到并正常打开
- [ ] 导出的 JSON 用文本编辑器打开，中文项目名未乱码，`schemaVersion` 为 1
- [ ] 导出 CSV 用 Excel 或 WPS 打开，中文项目名未乱码，列对齐正确
- [ ] 项目名中包含逗号时，CSV 转义正确（该行被双引号包裹）
- [ ] 覆盖模式导入刚导出的 JSON，数据量与原数据完全一致
- [ ] 合并模式导入同一份 JSON 两次，第二次提示全部跳过重复，数据不翻倍
- [ ] 合并模式下新建一个同名项目后再导入，会话被追加到已有项目而非新建重复项目
- [ ] 覆盖模式执行时应用被强杀，重启后数据仍为导入前的状态（事务回滚生效）
- [ ] 导入非 JSON 文件（如任意图片），提示「文件格式不正确」
- [ ] 导入 JSON 数组或缺少 `app` 字段的文件，提示「不是 TimeRecording 的备份文件」
- [ ] 手动把 `schemaVersion` 改为 2，提示「备份文件来自更新版本的应用」
- [ ] 导出时项目处于 running 状态，导出成功且导入后该项目为 idle，时长未凭空增加（§6.2 的核心验证）
- [ ] 文件选择器点取消，应用无异常、无错误提示
- [ ] 导入完成后返回，设置页与首页数据正常刷新

代码清理验证：

- [ ] 删除 `fragment_wallpaper.xml` 与 `menu_detail.xml` 后编译通过
- [ ] 移除 colorpicker 与 android-image-cropper 依赖后，`DetailActivity` 与 `WallpaperSettingsActivity` 功能正常（壁纸裁剪、条形图调色可用）
- [ ] 移除 Manifest 的 `package` 属性后，构建警告消失且应用正常运行

## 十二、风险与边界情况

| 风险 | 说明 | 应对 |
|------|------|------|
| 大型数据集性能 | 若会话数达数万条，逐条插入会较慢 | 导入在单个事务内批量执行；若实测超过 2 秒，改用 `@Insert` 的 List 重载批量插入 |
| 备份文件被手工修改 | 用户可能用文本编辑器改动 JSON 导致结构错误 | 逐条容错跳过并统计无效条目数，不因个别脏数据中断整体导入 |
| 同名不同实际含义的项目 | 合并模式按名称匹配，两家公司可能都有「日常」项目 | 确认对话框中展示匹配结果供用户判断；提供覆盖模式作为替代 |
| 低电量或强杀中断导入 | 事务保证原子性，不会留下半份数据 | `db.withTransaction { }` 自动回滚 |
| 导出文件含用户隐私 | 备份文件包含全部项目名与时间记录 | 文件由用户自行选择保存位置并管理；应用不主动上传任何数据 |
| `application/json` MIME 识别差异 | 部分文件管理器把 `.json` 报为 `application/octet-stream` | `OpenDocument` 的 MIME 数组同时包含 `application/json`、`text/*`、`application/octet-stream`；若实测仍无法选中文件，退化为 `arrayOf("*/*")` |

## 十三、待你确认的决策点

以下四项会影响实现方式，请确认后我再开始编写代码：

1. **是否需要 CSV 导入？** 当前方案只做 CSV 导出（§4.2 已说明理由）。若你需要从 Excel 回传数据，请告知，我会补充 CSV 解析与字段映射规则。

2. **合并模式的去重强度**：当前按「项目名 + 会话开始时间 + 结束时间 + 时长」四要素判重。是否需要放宽为「项目名 + 开始时间」判重，以便覆盖「同一次计时在两台设备上时长略有差异」的情况？

3. **导入逻辑放在哪里？** 方案 A：`DataTransferActivity` 直接通过 `AppDatabase.getDatabase(context)` 拿 DAO 操作（少改一个文件）；方案 B：在 `ProjectRepository` 上增加 `exportAll()` / `importAll()` 薄封装方法，保持「UI 层不直接接触 DAO」的现有分层原则。我推荐 B，与既有架构一致。

4. **是否执行 §9.4 的三项清理？** 即重命名 `WallpaperFragment`、移除未使用的 Compose 依赖、修正 `isMinifyEnabled` 注释。这三项不在核心需求内，需要你单独授权。

5. **确认是否删除 §9.1 的两个死文件与 §9.2 的两个未用依赖？** 这些清理与导入导出功能无关，但能减小 APK 体积。若你希望本次改动范围尽量聚焦，我也可以只做功能、不动清理。
