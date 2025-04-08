package biz.cunning.cunning_document_scanner.fallback

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import biz.cunning.cunning_document_scanner.R
import biz.cunning.cunning_document_scanner.fallback.utils.CameraUtil

class CameraActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private lateinit var cameraUtil: CameraUtil
    private var useOpenCV: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        useOpenCV = intent.getBooleanExtra("use_opencv", false)
        cameraUtil = CameraUtil(this)

        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "دسترسی به دوربین برای اسکن اسناد ضروری است",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        try {
            cameraUtil.startCamera(useOpenCV)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "خطا در راه‌اندازی دوربین: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraUtil.releaseCamera()
    }
}