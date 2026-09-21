# 跨天计时自动分段方案

## 目标

当计时会话跨越午夜（00:00:00）时，自动处理以避免单个 Session 记录横跨两天：

1. **暂停状态跨天**：若项目在暂停状态下跨过午夜（用户暂停后未恢复/结束直到第二天），以暂停时刻作为会话结束时间，生成一条 Session 记录，然后将项目重置为 idle。
2. **运行状态跨天**：若项目在运行状态下跨过午夜，在午夜处切分为两条记录——昨天一段（从会话开始到午夜），今天一段（从午夜到当前，计时继续运行）。

## 现状分析

### 当前计时状态机

| 状态 | sessionStartTime | currentStartTime | currentElapsedMillis |
|------|-----------------|-----------------|---------------------|
| idle | 0 | 0 | 0 |
| running | 会话开始时间 | 当前运行段开始时间（start/continue 时设为 now） | 之前各运行段累计时长 |
| paused | **0（被清零）** | **0（被清零）** | 全部累计时长 |

### 发现的现有 Bug

`pauseTimer` 在暂停时将 `sessionStartTime` 和 `currentStartTime` 都清零：

```kotlin
// ProjectRepository.kt — 当前代码
suspend fun pauseTimer(projectId: Long, currentElapsedMillis: Long) {
    projectDao.updateTimerState(
        projectId = projectId,
        state = "paused",
        sessionStartTime = 0,        // ← Bug：丢失了会话开始时间
        currentStartTime = 0,        // ← Bug：丢失了暂停时刻
        currentElapsedMillis = currentElapsedMillis
    )
}
```

这导致两个问题：
- 暂停后恢复（continue）时，`sessionStartTime` 传入 0，会话开始时间永久丢失
- 结束（end）时，Session 记录的 `startTime` 为 0，`endTime` 为当前时间——时间范围错误
- 无法实现跨天处理，因为暂停时刻信息已被清零

### 当前 endTimer 的结束时间问题

```kotlin
// ProjectRepository.kt — 当前代码
suspend fun endTimer(projectId: Long, sessionStartTime: Long, currentElapsedMillis: Long): Boolean {
    val now = System.currentTimeMillis()
    sessionDao.insert(SessionEntity(
        startTime = sessionStartTime,   // 暂停后此值为 0（Bug）
        endTime = now,                   // 暂停状态下应为暂停时刻，而非 now
        durationMillis = currentElapsedMillis
    ))
}
```

暂停状态下结束计时时，`endTime` 使用 `now` 而非暂停时刻，导致甘特图上的时间段会延伸到结束操作的时刻（包含未实际工作的时间）。

### 当前数据流

```
用户操作 → HomeViewModel.startStopTimer()
  → "idle" → repository.startTimer(id, now)
      → DB: state=running, sessionStartTime=now, currentStartTime=now, elapsed=0
  → "running" → repository.pauseTimer(id, newElapsed)
      → DB: state=paused, sessionStartTime=0, currentStartTime=0, elapsed=newElapsed  ← Bug
  → "paused" → repository.continueTimer(id, project.sessionStartTime, project.currentElapsedMillis)
      → DB: state=running, sessionStartTime=0(传入值), currentStartTime=now, elapsed=unchanged  ← Bug
```

### Repository 初始化流程

```kotlin
init {
    CoroutineScope(Dispatchers.IO).launch {
        DataMigration.migrateIfNeeded(...)
        recoverRunningTimers()   // 恢复被杀进程的 running 计时器
    }
}
```

`recoverRunningTimers()` 会将 `currentStartTime` 更新为 `System.currentTimeMillis()`，这会覆盖原始的运行段开始时间。因此跨天检查必须在 `recoverRunningTimers()` 之前执行。

## 修改方案

### 文件 1：`ProjectRepository.kt`

路径：`app/src/main/java/com/example/timerecording/data/repository/ProjectRepository.kt`

#### 1.1 修复 `pauseTimer` — 保留 sessionStartTime，用 currentStartTime 存储暂停时刻

```kotlin
suspend fun pauseTimer(projectId: Long, sessionStartTime: Long, currentElapsedMillis: Long) {
    projectDao.updateTimerState(
        projectId = projectId,
        state = "paused",
        sessionStartTime = sessionStartTime,                  // 保留原值，不再清零
        currentStartTime = System.currentTimeMillis(),        // 存储暂停时刻
        currentElapsedMillis = currentElapsedMillis
    )
}
```

`currentStartTime` 字段语义变更：
- running 状态：当前运行段的开始时间（不变）
- paused 状态：暂停时刻（原为 0）

#### 1.2 修复 `endTimer` — 新增 endTime 参数

```kotlin
suspend fun endTimer(
    projectId: Long,
    sessionStartTime: Long,
    currentElapsedMillis: Long,
    endTime: Long
): Boolean {
    if (currentElapsedMillis < 60_000) {
        projectDao.resetTimer(projectId)
        return false
    }
    sessionDao.insert(
        SessionEntity(
            projectId = projectId,
            startTime = sessionStartTime,
            endTime = endTime,
            durationMillis = currentElapsedMillis
        )
    )
    projectDao.resetTimer(projectId)
    return true
}
```

#### 1.3 新增 `checkAndSplitCrossDaySessions()` 方法

```kotlin
suspend fun checkAndSplitCrossDaySessions() {
    val nonIdle = projectDao.getNonIdleProjects()
    val todayStart = getStartOfToday()

    for (p in nonIdle) {
        if (isSameDay(p.sessionStartTime, todayStart)) continue

        when (p.state) {
            "paused" -> {
                // 情况1：暂停状态跨天
                // 暂停时刻 = currentStartTime（修复后存储的值）
                val pauseTime = p.currentStartTime
                val duration = p.currentElapsedMillis
                if (duration >= 60_000) {
                    sessionDao.insert(SessionEntity(
                        projectId = p.id,
                        startTime = p.sessionStartTime,
                        endTime = pauseTime,
                        durationMillis = duration
                    ))
                }
                projectDao.resetTimer(p.id)
            }
            "running" -> {
                // 情况2：运行状态跨天，在午夜切分
                val midnight = todayStart
                val yesterdayDuration = if (p.currentStartTime < midnight) {
                    // 当前运行段从昨天开始
                    p.currentElapsedMillis + (midnight - p.currentStartTime)
                } else {
                    // 当前运行段从今天开始（暂停后恢复的情况）
                    p.currentElapsedMillis
                }

                if (yesterdayDuration >= 60_000) {
                    sessionDao.insert(SessionEntity(
                        projectId = p.id,
                        startTime = p.sessionStartTime,
                        endTime = midnight,
                        durationMillis = yesterdayDuration
                    ))
                }

                // 今天的新会话从午夜开始，计时继续
                val newCurrentStartTime = if (p.currentStartTime < midnight) midnight else p.currentStartTime
                projectDao.updateTimerState(
                    projectId = p.id,
                    state = "running",
                    sessionStartTime = midnight,
                    currentStartTime = newCurrentStartTime,
                    currentElapsedMillis = 0
                )
            }
        }
    }
}
```

辅助方法：

```kotlin
private fun getStartOfToday(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}
```

#### 1.4 调整 init 块 — 跨天检查在恢复计时器之前执行

```kotlin
init {
    CoroutineScope(Dispatchers.IO).launch {
        DataMigration.migrateIfNeeded(context, projectDao, sessionDao)
        checkAndSplitCrossDaySessions()   // ← 新增：先处理跨天
        recoverRunningTimers()            // ← 然后恢复同日计时器
    }
}
```

### 文件 2：`ProjectDao.kt`

路径：`app/src/main/java/com/example/timerecording/data/db/ProjectDao.kt`

新增查询方法：

```kotlin
@Query("SELECT * FROM projects WHERE state != 'idle'")
suspend fun getNonIdleProjects(): List<ProjectEntity>
```

### 文件 3：`HomeViewModel.kt`

路径：`app/src/main/java/com/example/timerecording/viewmodel/HomeViewModel.kt`

#### 3.1 修复 `startStopTimer` — pauseTimer 传入 sessionStartTime

```kotlin
"running" -> {
    val elapsed = System.currentTimeMillis() - project.currentStartTime
    val newElapsed = project.currentElapsedMillis + elapsed
    repository.pauseTimer(project.id, project.sessionStartTime, newElapsed)
}
```

#### 3.2 修复 `endTimer` — 传入正确的 endTime

```kotlin
fun endTimer(project: Project, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
        var currentElapsed = project.currentElapsedMillis
        var endTime = System.currentTimeMillis()
        if (project.state == "running") {
            currentElapsed += System.currentTimeMillis() - project.currentStartTime
        } else if (project.state == "paused") {
            endTime = project.currentStartTime   // 暂停时刻作为结束时间
        }
        val saved = repository.endTimer(project.id, project.sessionStartTime, currentElapsed, endTime)
        onResult(saved)
    }
}
```

#### 3.3 修复 `deleteProject` — pauseTimer 传入 sessionStartTime

```kotlin
fun deleteProject(project: Project) {
    viewModelScope.launch {
        if (project.state == "running") {
            val elapsed = System.currentTimeMillis() - project.currentStartTime
            val newElapsed = project.currentElapsedMillis + elapsed
            repository.pauseTimer(project.id, project.sessionStartTime, newElapsed)
        }
        repository.deleteProject(project.id)
    }
}
```

#### 3.4 新增 `checkCrossDaySessions` 方法

```kotlin
fun checkCrossDaySessions() {
    viewModelScope.launch {
        repository.checkAndSplitCrossDaySessions()
    }
}
```

### 文件 4：`HomeFragment.kt`

路径：`app/src/main/java/com/example/timerecording/HomeFragment.kt`

在 `updateRunnable` 中添加日期变化检测，午夜过后立即触发跨天检查：

```kotlin
private var lastCheckDay = -1

private val updateRunnable = object : Runnable {
    override fun run() {
        if (projectList.any { it.state == "running" }) {
            adapter.notifyDataSetChanged()
        }

        // 检测日期变化，触发跨天分段
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (today != lastCheckDay) {
            lastCheckDay = today
            viewModel.checkCrossDaySessions()
        }

        handler.postDelayed(this, 1000)
    }
}
```

在 `onResume` 中也触发一次检查（处理从后台恢复的情况）：

```kotlin
override fun onResume() {
    super.onResume()
    viewModel.checkCrossDaySessions()   // ← 新增
    if (projectList.any { it.state == "running" }) {
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }
    adapter.notifyDataSetChanged()
}
```

### 文件 5：`TimerService.kt`

路径：`app/src/main/java/com/example/timerecording/service/TimerService.kt`

在 `updateRunnable` 中添加日期变化检测，处理应用在后台运行时的跨天场景：

```kotlin
private var lastCheckDay = -1

private val updateRunnable = object : Runnable {
    override fun run() {
        updateNotification()

        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (today != lastCheckDay) {
            lastCheckDay = today
            scope.launch {
                ProjectRepository.get(this@TimerService).checkAndSplitCrossDaySessions()
            }
        }

        handler.postDelayed(this, 1000)
    }
}
```

在 `onCreate` 中初始化 `lastCheckDay`：

```kotlin
override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, buildNotification("正在计时", "准备中..."))
    lastCheckDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    handler.post(updateRunnable)
}
```

## 不需要修改的文件

| 文件 | 原因 |
|------|------|
| `ProjectEntity.kt` | 无需新增字段，复用 currentStartTime 存储暂停时刻 |
| `SessionEntity.kt` | 无需新增字段 |
| `SessionDao.kt` | 无需新增方法 |
| `AppDatabase.kt` | 无 schema 变更，数据库版本不变 |
| `DetailViewModel.kt` | 只读数据，跨天分段后自动看到正确数据 |
| `StatisticsViewModel.kt` | 只读数据 |
| `GanttChartView.kt` | 已按日期筛选 Session，分段后每个 Session 都在同一天内，自动正确显示 |
| `DataMigration.kt` | 与跨天逻辑无关 |
| `Project.kt` / `Session.kt` | 数据类无需修改 |

## 执行步骤

1. **ProjectDao.kt** — 添加 `getNonIdleProjects()` 查询
2. **ProjectRepository.kt** — 修复 `pauseTimer` 签名和实现；修复 `endTimer` 添加 `endTime` 参数；新增 `checkAndSplitCrossDaySessions()`、`getStartOfToday()`、`isSameDay()` 辅助方法；调整 init 执行顺序
3. **HomeViewModel.kt** — 更新 `startStopTimer`、`endTimer`、`deleteProject` 的调用参数；添加 `checkCrossDaySessions()` 方法
4. **HomeFragment.kt** — 在 `updateRunnable` 和 `onResume` 中添加跨天检查
5. **TimerService.kt** — 在 `updateRunnable` 中添加跨天检查
6. **编译验证** — `gradlew assembleDebug`

## 验证要点

- [ ] 暂停后恢复再结束，Session 的 startTime 不再为 0
- [ ] 暂停状态下结束，endTime 为暂停时刻而非当前时间
- [ ] 运行状态跨午夜，自动生成昨天的 Session 记录，今天继续计时
- [ ] 暂停状态跨午夜，自动生成 Session 记录并重置为 idle
- [ ] 甘特图上同一天内显示分段后的 Session
- [ ] 应用被杀后重启，跨天计时器被正确分段
- [ ] 未跨天的计时器不受影响

## 已知限制

- 若会话连续跨越多天（如应用被杀 3 天后才重启），只会按"昨天 vs 今天"拆分一次，昨天的 Session 可能仍横跨多天。这种情况极少见，可在后续迭代中通过循环拆分解决。
- 若计时器在午夜前运行、午夜后暂停，跨天检查会将整段记录为一条 Session（end 为暂停时刻），不会在午夜处拆分。这是边缘场景，用户需求中"一直在暂停状态"指的是午夜时处于暂停状态的情况。
