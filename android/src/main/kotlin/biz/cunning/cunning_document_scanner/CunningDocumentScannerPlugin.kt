package biz.cunning.cunning_document_scanner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import androidx.core.app.ActivityCompat
import biz.cunning.cunning_document_scanner.fallback.DocumentScannerActivity
import biz.cunning.cunning_document_scanner.fallback.constants.DocumentScannerExtra
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

/** CunningDocumentScannerPlugin */
class CunningDocumentScannerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private var delegate: PluginRegistry.ActivityResultListener? = null
    private var binding: ActivityPluginBinding? = null
    private var pendingResult: Result? = null
    private lateinit var activity: Activity
    private val START_DOCUMENT_ACTIVITY: Int = 0x362738
    private val START_DOCUMENT_FB_ACTIVITY: Int = 0x362737
    private val TAG = "CunningDocumentScanner"

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "cunning_document_scanner")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "getPictures") {
            try {
                if (!checkPermissions()) {
                    result.error("PERMISSION_DENIED", "Required permissions not granted", null)
                    return
                }
                
                val noOfPages = call.argument<Int>("noOfPages") ?: 50
                val isGalleryImportAllowed = call.argument<Boolean>("isGalleryImportAllowed") ?: false
                this.pendingResult = result
                
                // بررسی اینکه آیا باید از OpenCV استفاده کنیم
                if (DeviceChecker.shouldUseOpenCV()) {
                    Log.d(TAG, "Using OpenCV for document scanning")
                    startOpenCVScan(noOfPages)
                } else {
                    Log.d(TAG, "Using ML Kit for document scanning")
                    startScan(noOfPages, isGalleryImportAllowed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in getPictures: ${e.message}", e)
                result.error("ERROR", "Failed to start scanning: ${e.message}", null)
            }
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        addActivityResultListener(binding)
    }

    private fun addActivityResultListener(binding: ActivityPluginBinding) {
        this.binding = binding
        if (this.delegate == null) {
            this.delegate = PluginRegistry.ActivityResultListener { requestCode, resultCode, data ->
                try {
                    if (requestCode != START_DOCUMENT_ACTIVITY && requestCode != START_DOCUMENT_FB_ACTIVITY) {
                        return@ActivityResultListener false
                    }
                    Log.d(TAG, "Activity result - requestCode: $requestCode, resultCode: $resultCode")
                    
                    var handled = false
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            when (requestCode) {
                                START_DOCUMENT_ACTIVITY -> {
                                    val error = data?.getStringExtra("error")
                                    if (error != null) {
                                        Log.e(TAG, "Scanner error: $error")
                                        pendingResult?.error("ERROR", error, null)
                                    } else {
                                        try {
                                            val scanningResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                data?.getParcelableExtra("extra_scanning_result", GmsDocumentScanningResult::class.java)
                                            } else {
                                                @Suppress("DEPRECATION")
                                                data?.getParcelableExtra("extra_scanning_result")
                                            }
                                            
                                            if (scanningResult != null) {
                                                val successResponse = scanningResult.pages?.map { page ->
                                                    page.imageUri.toString().removePrefix("file://")
                                                }?.toList()
                                                pendingResult?.success(successResponse)
                                            } else {
                                                Log.e(TAG, "No scanning result found")
                                                pendingResult?.error("ERROR", "No scanning result found", null)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error processing scanning result", e)
                                            pendingResult?.error("ERROR", "Failed to process scanning result: ${e.message}", null)
                                        }
                                    }
                                    handled = true
                                }
                                START_DOCUMENT_FB_ACTIVITY -> {
                                    val error = data?.getStringExtra("error")
                                    if (error != null) {
                                        Log.e(TAG, "Fallback scanner error: $error")
                                        pendingResult?.error("ERROR", error, null)
                                    } else {
                                        val croppedImageResults = data?.getStringArrayListExtra("croppedImageResults")
                                        if (croppedImageResults != null) {
                                            val successResponse = croppedImageResults.map { uri ->
                                                uri.removePrefix("file://")
                                            }.toList()
                                            pendingResult?.success(successResponse)
                                        } else {
                                            Log.e(TAG, "No cropped images returned")
                                            pendingResult?.error("ERROR", "No cropped images returned", null)
                                        }
                                    }
                                    handled = true
                                }
                            }
                        }
                        Activity.RESULT_CANCELED -> {
                            Log.d(TAG, "User cancelled scanning")
                            pendingResult?.success(emptyList<String>())
                            handled = true
                        }
                    }

                    if (handled) {
                        pendingResult = null
                    }
                    return@ActivityResultListener handled
                } catch (e: Exception) {
                    Log.e(TAG, "Error in activity result", e)
                    pendingResult?.error("ERROR", "Failed to process result: ${e.message}", null)
                    return@ActivityResultListener true
                }
            }
        } else {
            binding.removeActivityResultListener(this.delegate!!)
        }
        binding.addActivityResultListener(delegate!!)
    }

    /**
     * create intent to launch document scanner and set custom options
     */
    private fun createDocumentScanIntent(noOfPages: Int): Intent {
        return Intent(activity, DocumentScannerActivity::class.java).apply {
            putExtra(DocumentScannerExtra.EXTRA_MAX_NUM_DOCUMENTS, noOfPages)
        }
    }

    /**
     * add document scanner result handler and launch the document scanner
     */
    private fun startScan(noOfPages: Int, isGalleryImportAllowed: Boolean) {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(isGalleryImportAllowed)
                .setPageLimit(noOfPages)
                .setResultFormats(RESULT_FORMAT_JPEG)
                .setScannerMode(SCANNER_MODE_FULL)
                .build()
            
            val scanner = GmsDocumentScanning.getClient(options)
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    try {
                        activity.startIntentSenderForResult(
                            intentSender,
                            START_DOCUMENT_ACTIVITY,
                            null,
                            0,
                            0,
                            0
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e(TAG, "Failed to start document scanner", e)
                        pendingResult?.error("ERROR", "Failed to start document scanner: ${e.message}", null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get document scanner intent", e)
                    if (e is MlKitException) {
                        val intent = createDocumentScanIntent(noOfPages)
                        try {
                            ActivityCompat.startActivityForResult(
                                activity,
                                intent,
                                START_DOCUMENT_FB_ACTIVITY,
                                null
                            )
                        } catch (e: ActivityNotFoundException) {
                            pendingResult?.error("ERROR", "Failed to start fallback scanner", null)
                        }
                    } else {
                        pendingResult?.error("ERROR", "Failed to start scanner: ${e.message}", null)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startScan", e)
            pendingResult?.error("ERROR", "Failed to start scan: ${e.message}", null)
        }
    }

    private fun startOpenCVScan(noOfPages: Int) {
        try {
            val intent = createDocumentScanIntent(noOfPages).apply {
                putExtra("use_opencv", true)
            }
            ActivityCompat.startActivityForResult(
                activity,
                intent,
                START_DOCUMENT_FB_ACTIVITY,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting OpenCV scan", e)
            pendingResult?.error("ERROR", "Failed to start OpenCV scan: ${e.message}", null)
        }
    }

    override fun onDetachedFromActivity() {
        this.delegate?.let { this.binding?.removeActivityResultListener(it) }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            return ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }
}
