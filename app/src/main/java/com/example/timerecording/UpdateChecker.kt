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

object UpdateChecker {

    private const val VERSION_URL = "https://raw.giteeusercontent.com/blackrune/time-recording/raw/master/version.json"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val handler = Handler(Looper.getMainLooper())

    /** 自动检查（静默模式）：发现新版本才弹窗，无新版本或出错时不打扰用户 */
    fun checkAuto(context: Context) {
        doCheck(context, isManual = false)
    }

    /** 手动检查：无论结果如何都给用户反馈 */
    fun checkManual(context: Context) {
        doCheck(context, isManual = true)
    }

    private fun doCheck(context: Context, isManual: Boolean) {
        Thread {
            try {
                val request = Request.Builder().url(VERSION_URL).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                response.close()

                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val remoteCode = json.optInt("versionCode", 0)
                    val remoteName = json.optString("versionName", "")
                    val downloadUrl = json.optString("downloadUrl", "")
                    val updateLog = json.optString("updateLog", "")

                    val localCode = BuildConfig.VERSION_CODE

                    if (remoteCode > localCode && downloadUrl.isNotEmpty()) {
                        handler.post { showUpdateDialog(context, remoteName, updateLog, downloadUrl) }
                    } else if (isManual) {
                        handler.post {
                            AlertDialog.Builder(context)
                                .setTitle("检查更新")
                                .setMessage("当前已是最新版本（v${BuildConfig.VERSION_NAME}）")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                } else if (isManual) {
                    handler.post { showManualError(context) }
                }
            } catch (e: Exception) {
                if (isManual) {
                    handler.post { showManualError(context) }
                }
            }
        }.start()
    }

    private fun showUpdateDialog(
        context: Context,
        versionName: String,
        updateLog: String,
        downloadUrl: String
    ) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本 $versionName")
            .setMessage(updateLog.ifEmpty { "修复了一些问题，建议更新。" })
            .setCancelable(false)
            .setPositiveButton("立即更新") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    context.startActivity(intent)
                } catch (_: Exception) { }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun showManualError(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("检查更新")
            .setMessage("网络异常，无法检查更新，请稍后重试")
            .setPositiveButton("确定", null)
            .show()
    }
}
