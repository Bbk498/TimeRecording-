package com.example.timerecording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * 壁纸设置 Activity。
 *
 * 职责：
 * - 从系统相册选择图片；
 * - 使用 UCrop 按屏幕比例裁剪图片；
 * - 将裁剪后的壁纸保存到应用内部目录并记录路径；
 * - 支持清除已设壁纸；
 * - 预览当前已设置的壁纸。
 */
class WallpaperSettingsActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView           // 壁纸预览图控件
    private val prefs by lazy {                          // SharedPreferences，存储壁纸路径
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    // 图片选择结果回调：选好后进入裁剪流程
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> startCrop(uri) }
        }
    }

    // 裁剪结果回调：成功则保存壁纸并更新预览，失败则提示错误
    private val cropImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = UCrop.getOutput(result.data!!)
            croppedUri?.let { uri ->
                saveWallpaperPath(uri)
                ivPreview.setImageURI(uri)
                Toast.makeText(this, "壁纸已保存", Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            // 裁剪出错
            val error = UCrop.getError(result.data!!)
            Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Activity 创建入口：绑定控件、加载已存壁纸预览、设置按钮监听。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_settings)

        // 绑定预览图、选择和清除按钮
        ivPreview = findViewById(R.id.iv_wallpaper_preview)
        val btnPick = findViewById<Button>(R.id.btn_pick_wallpaper)
        val btnClear = findViewById<Button>(R.id.btn_clear_wallpaper)

        // 若已设置壁纸，加载显示预览
        val savedPath = prefs.getString("wallpaper_path", null)
        if (savedPath != null) {
            ivPreview.setImageURI(Uri.fromFile(File(savedPath)))
        }

        // 选择壁纸按钮：唤起系统相册
        btnPick.setOnClickListener {
            pickImageLauncher.launch(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            )
        }

        // 清除壁纸按钮：移除路径并清空预览
        btnClear.setOnClickListener {
            prefs.edit().remove("wallpaper_path").apply()
            ivPreview.setImageDrawable(null)
            Toast.makeText(this, "壁纸已清除", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 启动 UCrop 裁剪，裁剪比例按屏幕宽高比设置。
     */
    private fun startCrop(sourceUri: Uri) {
        // 裁剪结果输出到缓存目录
        val destinationUri = Uri.fromFile(
            File(cacheDir, "cropped_wallpaper_${System.currentTimeMillis()}.jpg")
        )
        // 获取屏幕宽高，计算最简宽高比
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val gcd = gcd(screenWidth, screenHeight)
        val ratioX = (screenWidth / gcd).toFloat()
        val ratioY = (screenHeight / gcd).toFloat()

        // 配置 UCrop 并启动裁剪
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(ratioX, ratioY)
            .withMaxResultSize(screenWidth, screenHeight)
            .start(this, cropImageLauncher)
    }

    /** 求两数最大公约数（递归实现），用于化简宽高比 */
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /**
     * 将裁剪后的壁纸图片复制到应用内部目录，并保存路径到 SharedPreferences。
     */
    private fun saveWallpaperPath(uri: Uri) {
        // 目标文件：应用内部 filesDir/wallpaper.jpg
        val destFile = File(filesDir, "wallpaper.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        // 持久化壁纸路径
        prefs.edit().putString("wallpaper_path", destFile.absolutePath).apply()
    }
}