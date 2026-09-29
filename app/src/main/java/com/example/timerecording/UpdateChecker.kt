package com.example.timerecording

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 应用更新检查器（单例对象）
 *
 * 通过HTTP请求远端version.json，比对版本号判断是否有新版本。
 * 支持两种模式：自动检查（静默，仅发现新版本才弹窗）和手动检查（总是给用户反馈）。
 */
object UpdateChecker {

    // 远端版本信息JSON地址
    private const val VERSION_URL = "https://raw.giteeusercontent.com/blackrune/time-recording/raw/master/version.json"

    // OkHttp客户端，懒加载，设置5秒超时
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // 主线程Handler，用于在子线程网络请求完成后切回主线程更新UI
    private val handler = Handler(Looper.getMainLooper())

    /** 自动检查（静默模式）：发现新版本才弹窗，无新版本或出错时不打扰用户 */
    fun checkAuto(context: Context) {
        doCheck(context, isManual = false)
    }

    /** 手动检查：无论结果如何都给用户反馈 */
    fun checkManual(context: Context) {
        doCheck(context, isManual = true)
    }

    /**
     * 执行更新检查的核心逻辑
     *
     * @param context 上下文
     * @param isManual 是否为手动检查（决定无新版本/出错时是否提示用户）
     */
    private fun doCheck(context: Context, isManual: Boolean) {
        Thread {
            try {
                // 构建HTTP请求
                val request = Request.Builder().url(VERSION_URL).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                response.close()

                if (!body.isNullOrEmpty()) {
                    // 解析远端版本JSON
                    val json = JSONObject(body)
                    val remoteCode = json.optInt("versionCode", 0)
                    val remoteName = json.optString("versionName", "")
                    val downloadUrl = json.optString("downloadUrl", "")
                    val updateLog = json.optString("updateLog", "")

                    // 获取本地版本号
                    val localCode = BuildConfig.VERSION_CODE

                    if (remoteCode > localCode && downloadUrl.isNotEmpty()) {
                        // 远端版本号更高，切回主线程显示更新弹窗
                        handler.post { showUpdateDialog(context, remoteName, updateLog, downloadUrl) }
                    } else if (isManual) {
                        // 手动检查但已是最新版本，提示用户
                        handler.post {
                            AlertDialog.Builder(context)
                                .setTitle("检查更新")
                                .setMessage("当前已是最新版本（v${BuildConfig.VERSION_NAME}）")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                } else if (isManual) {
                    // 响应体为空，手动检查时报错
                    handler.post { showManualError(context) }
                }
            } catch (e: Exception) {
                // 网络异常，仅手动检查时提示
                if (isManual) {
                    handler.post { showManualError(context) }
                }
            }
        }.start()
    }

    /**
     * 显示发现新版本的更新弹窗
     *
     * @param versionName 新版本号名称
     * @param updateLog 更新日志
     * @param downloadUrl 下载链接
     */
    private fun showUpdateDialog(
        context: Context,
        versionName: String,
        updateLog: String,
        downloadUrl: String
    ) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本 $versionName")
            .setMessage(updateLog.ifEmpty { "修复了一些问题，建议更新。" })
            .setCancelable(false)     // 不可点击外部取消
            .setPositiveButton("立即更新") { _, _ ->
                try {
                    // 打开浏览器跳转下载链接
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    context.startActivity(intent)
                } catch (_: Exception) { }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    /**
     * 显示手动检查失败（网络异常）的提示弹窗
     */
    private fun showManualError(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("检查更新")
            .setMessage("网络异常，无法检查更新，请稍后重试")
            .setPositiveButton("确定", null)
            .show()
    }
}
