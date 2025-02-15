package com.secondmemory.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class SystemSettingsHelper(private val context: Context) {
    
    fun openBatteryOptimizationSettings() {
        when (Build.MANUFACTURER.lowercase()) {
            "huawei", "honor" -> openHuaweiSettings()
            "xiaomi", "redmi" -> openXiaomiSettings()
            "oppo" -> openOppoSettings()
            "vivo" -> openVivoSettings()
            "samsung" -> openSamsungSettings()
            else -> openDefaultBatterySettings()
        }
    }

    fun openAutoStartSettings() {
        when (Build.MANUFACTURER.lowercase()) {
            "huawei", "honor" -> openHuaweiAutoStart()
            "xiaomi", "redmi" -> openXiaomiAutoStart()
            "oppo" -> openOppoAutoStart()
            "vivo" -> openVivoAutoStart()
            "samsung" -> openSamsungAutoStart()
            else -> openAppSettings()
        }
    }

    private fun openHuaweiSettings() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openDefaultBatterySettings()
        }
    }

    private fun openXiaomiSettings() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openDefaultBatterySettings()
        }
    }

    private fun openOppoSettings() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openDefaultBatterySettings()
        }
    }

    private fun openVivoSettings() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openDefaultBatterySettings()
        }
    }

    private fun openSamsungSettings() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openDefaultBatterySettings()
        }
    }

    private fun openDefaultBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        }
    }

    private fun openHuaweiAutoStart() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openXiaomiAutoStart() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openOppoAutoStart() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openVivoAutoStart() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openSamsungAutoStart() {
        try {
            val intent = Intent()
            intent.setComponent(android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ))
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
}
