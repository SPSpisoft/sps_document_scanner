package biz.cunning.cunning_document_scanner

import android.os.Build
import android.util.Log

class DeviceChecker {
    companion object {
        private const val TAG = "DeviceChecker"
        
        // لیست سازنده‌های دستگاه‌های مشکل‌دار
        private val problematicManufacturers = setOf(
            "samsung",
            "huawei",
            "honor"
        )
        
        // لیست مدل‌های مشکل‌دار
        private val problematicModels = setOf(
            "SM-A515F",  // Samsung Galaxy A51
            "SM-G970F",  // Samsung Galaxy S10e
            "SM-G973F"   // Samsung Galaxy S10
        )
        
        fun shouldUseOpenCV(): Boolean {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL
            
            Log.d(TAG, "Device info - Manufacturer: $manufacturer, Model: $model, Android version: ${Build.VERSION.SDK_INT}")
            
            // اگر نسخه اندروید 13 یا بالاتر است
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // اگر سازنده دستگاه در لیست مشکل‌دار است
                if (problematicManufacturers.contains(manufacturer)) {
                    Log.d(TAG, "Using OpenCV due to problematic manufacturer")
                    return true
                }
                
                // اگر مدل دستگاه در لیست مشکل‌دار است
                if (problematicModels.contains(model)) {
                    Log.d(TAG, "Using OpenCV due to problematic model")
                    return true
                }
            }
            
            return false
        }
    }
} 