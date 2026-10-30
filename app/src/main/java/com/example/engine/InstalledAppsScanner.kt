package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceApkPath: String,
    val splitApkPaths: List<String> = emptyList(),
    val apkSizeBytes: Long,
    val targetSdkVersion: Int,
    val isSystemApp: Boolean
)

object InstalledAppsScanner {
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun scanLaunchableApps(context: Context): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val seenPackages = HashSet<String>()
            val result = mutableListOf<InstalledAppInfo>()

            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val pkgName = activityInfo.packageName ?: continue
                // Skip our own host package and already-cloned packages (.clone1, .clone2, etc.)
                if (pkgName == context.packageName ||
                    pkgName.contains(".clone") ||
                    !seenPackages.add(pkgName)
                ) continue

                try {
                    val appInfo = activityInfo.applicationInfo
                    val label = appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                        ?: resolveInfo.loadLabel(pm)?.toString()
                        ?: pkgName

                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkgName, 0)
                    }

                    val versionName = pkgInfo.versionName ?: "1.0"
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }

                    val sourceDir = appInfo.sourceDir ?: ""
                    val splitDirs = appInfo.splitSourceDirs?.toList() ?: emptyList()
                    val apkSize = if (sourceDir.isNotEmpty()) {
                        runCatching { File(sourceDir).length() }.getOrDefault(0L)
                    } else 0L

                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                    if (!iconCache.containsKey(pkgName)) {
                        runCatching {
                            val drawable = appInfo.loadIcon(pm)
                            iconCache[pkgName] = drawableToImageBitmap(drawable)
                        }
                    }

                    result.add(
                        InstalledAppInfo(
                            packageName = pkgName,
                            appName = label,
                            versionName = versionName,
                            versionCode = versionCode,
                            sourceApkPath = sourceDir,
                            splitApkPaths = splitDirs,
                            apkSizeBytes = apkSize,
                            targetSdkVersion = appInfo.targetSdkVersion,
                            isSystemApp = isSystem
                        )
                    )
                } catch (_: Exception) {
                    // Skip inaccessible packages gracefully
                }
            }

            result.sortedWith(
                compareBy<InstalledAppInfo> { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )
        }

    fun getAppIcon(context: Context, packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = drawableToImageBitmap(drawable)
            iconCache[packageName] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.coerceIn(64, 192)
        val height = drawable.intrinsicHeight.coerceIn(64, 192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
