package com.example.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.model.DeviceDiagnosticsSnapshot

class DeviceDiagnosticsManager(private val context: Context) {

    fun captureSnapshot(): DeviceDiagnosticsSnapshot {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10f

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val runtime = Runtime.getRuntime()
        val appHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val appHeapMaxMb = runtime.maxMemory() / (1024L * 1024L)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorageBytes = statFs.totalBytes.toDouble()
        val freeStorageBytes = statFs.availableBytes.toDouble()
        val gbDiv = 1024.0 * 1024.0 * 1024.0

        val (isOnline, netLabel) = readConnectivity()

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensorCount = sensorManager?.getSensorList(Sensor.TYPE_ALL)?.size ?: 0

        val hasTorch = hasBackTorch()

        return DeviceDiagnosticsSnapshot(
            deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            apiLevel = Build.VERSION.SDK_INT,
            batteryPercent = batteryPct.coerceIn(0, 100),
            isCharging = isCharging,
            batteryTempCelsius = tempCelsius,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            appHeapUsedMb = appHeapUsedMb,
            appHeapMaxMb = appHeapMaxMb,
            isLowMemory = memInfo.lowMemory,
            storageFreeGb = (freeStorageBytes / gbDiv).toFloat(),
            storageTotalGb = (totalStorageBytes / gbDiv).toFloat(),
            networkLabel = netLabel,
            isOnline = isOnline,
            sensorCount = sensorCount,
            hasTorchHardware = hasTorch,
        )
    }

    private fun readConnectivity(): Pair<Boolean, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false to "Offline"
        val network = cm.activeNetwork ?: return false to "Offline"
        val caps = cm.getNetworkCapabilities(network) ?: return false to "Offline"
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val label = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Linked"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular 4G/5G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            hasInternet -> "Connected"
            else -> "Offline"
        }
        return hasInternet to label
    }

    private fun hasBackTorch(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            cm.cameraIdList.any { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            false
        }
    }
}
