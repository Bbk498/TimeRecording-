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

class WallpaperSettingsActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private val prefs by lazy {
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> startCrop(uri) }
        }
    }

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
            val error = UCrop.getError(result.data!!)
            Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_settings)

        ivPreview = findViewById(R.id.iv_wallpaper_preview)
        val btnPick = findViewById<Button>(R.id.btn_pick_wallpaper)
        val btnClear = findViewById<Button>(R.id.btn_clear_wallpaper)

        val savedPath = prefs.getString("wallpaper_path", null)
        if (savedPath != null) {
            ivPreview.setImageURI(Uri.fromFile(File(savedPath)))
        }

        btnPick.setOnClickListener {
            pickImageLauncher.launch(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            )
        }

        btnClear.setOnClickListener {
            prefs.edit().remove("wallpaper_path").apply()
            ivPreview.setImageDrawable(null)
            Toast.makeText(this, "壁纸已清除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(
            File(cacheDir, "cropped_wallpaper_${System.currentTimeMillis()}.jpg")
        )
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val gcd = gcd(screenWidth, screenHeight)
        val ratioX = (screenWidth / gcd).toFloat()
        val ratioY = (screenHeight / gcd).toFloat()

        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(ratioX, ratioY)
            .withMaxResultSize(screenWidth, screenHeight)
            .start(this, cropImageLauncher)
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun saveWallpaperPath(uri: Uri) {
        val destFile = File(filesDir, "wallpaper.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        prefs.edit().putString("wallpaper_path", destFile.absolutePath).apply()
    }
}