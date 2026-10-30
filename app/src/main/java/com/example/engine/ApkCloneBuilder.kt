package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.ClonedAppEntity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkCloneBuilder {

    private val virtualDeviceModels = listOf(
        "Pixel 9 Pro • Isolated",
        "Galaxy S25 Ultra • Dual",
        "OnePlus 13 • Virtual",
        "Xiaomi 15 Pro • Sandbox",
        "ROG Phone 9 • MultiSpace",
        "Xperia 1 VI • Container"
    )

    val badgeColorPalette = listOf(
        0xFF00E5FFL, // Cyan
        0xFF7C4DFFL, // Deep Violet
        0xFF00E676L, // Neon Green
        0xFFFF9100L, // Amber Orange
        0xFFFF4081L, // Magenta Pink
        0xFF2979FFL, // Electric Blue
        0xFFFFD600L, // Gold
        0xFFFF5252L  // Crimson Red
    )

    fun generateClonedPackageName(originalPackage: String, cloneNumber: Int): String {
        val suffix = String.format("c%02d", cloneNumber.coerceIn(1, 999))
        if (originalPackage.length > suffix.length + 4) {
            val prefix = originalPackage.substring(0, originalPackage.length - suffix.length)
            val sanitizedPrefix = if (prefix.endsWith(".")) {
                prefix.dropLast(1) + "x"
            } else {
                prefix
            }
            return sanitizedPrefix + suffix
        }
        return "$originalPackage.clone$cloneNumber"
    }

    fun generateVirtualDeviceId(cloneNumber: Int): String {
        val random = SecureRandom()
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "os${cloneNumber.toString().padStart(2, '0')}$hex"
    }

    fun pickDeviceModel(cloneNumber: Int): String {
        return virtualDeviceModels[(cloneNumber - 1).coerceAtLeast(0) % virtualDeviceModels.size]
    }

    /**
     * Creates an isolated sandbox file tree for the cloned app instance.
     */
    suspend fun buildCloneInstance(
        context: Context,
        appInfo: InstalledAppInfo,
        cloneNumber: Int,
        customName: String,
        accountTag: String,
        spaceId: String,
        badgeColorHex: Long,
        generateApkPackage: Boolean
    ): ClonedAppEntity = withContext(Dispatchers.IO) {
        val clonedPkg = generateClonedPackageName(appInfo.packageName, cloneNumber)
        val virtualDeviceId = generateVirtualDeviceId(cloneNumber)
        val virtualModel = pickDeviceModel(cloneNumber)

        val sandboxRoot = File(context.filesDir, "omnispace_sandboxes/$spaceId/${appInfo.packageName}_clone_$cloneNumber")
        val prefsDir = File(sandboxRoot, "shared_prefs").apply { mkdirs() }
        val dbDir = File(sandboxRoot, "databases").apply { mkdirs() }
        val cacheDir = File(sandboxRoot, "cache").apply { mkdirs() }
        val filesSubDir = File(sandboxRoot, "files").apply { mkdirs() }

        val identityFile = File(prefsDir, "omnispace_virtual_identity.json")
        val identityJson = """
            {
              "originalPackage": "${appInfo.packageName}",
              "clonedPackage": "$clonedPkg",
              "cloneNumber": $cloneNumber,
              "customLabel": "${customName.replace("\"", "'")}",
              "accountTag": "${accountTag.replace("\"", "'")}",
              "spaceId": "$spaceId",
              "virtualAndroidId": "$virtualDeviceId",
              "virtualModel": "$virtualModel",
              "isolatedDataRoot": "${sandboxRoot.absolutePath}",
              "createdAt": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        identityFile.writeText(identityJson, StandardCharsets.UTF_8)

        File(dbDir, "account_session_slot_$cloneNumber.db").writeBytes(
            "OMNISPACE_ISOLATED_SQLITE_SLOT_${cloneNumber}_$virtualDeviceId".toByteArray(StandardCharsets.UTF_8)
        )
        File(filesSubDir, "profile_token.dat").writeText(
            "SLOT=$cloneNumber\nDEVICE_ID=$virtualDeviceId\nACCOUNT=$accountTag\n",
            StandardCharsets.UTF_8
        )
        File(cacheDir, "session_cache.bin").writeBytes(ByteArray(2048) { (it % 255).toByte() })

        val totalBytes = calculateDirectorySize(sandboxRoot)

        ClonedAppEntity(
            originalPackageName = appInfo.packageName,
            originalAppName = appInfo.appName,
            clonedPackageName = clonedPkg,
            cloneNumber = cloneNumber,
            customName = customName.ifBlank { "${appInfo.appName} #$cloneNumber" },
            accountTag = accountTag.ifBlank { "Account #$cloneNumber" },
            spaceId = spaceId,
            badgeColorHex = badgeColorHex,
            virtualDeviceId = virtualDeviceId,
            virtualModelName = virtualModel,
            apkOutputPath = "",
            sandboxDirPath = sandboxRoot.absolutePath,
            storageBytes = totalBytes
        )
    }

    /**
     * Pins a direct Home Screen Launcher icon for this specific clone (#1, #2, ... #10)
     * with a numbered badge overlay so the user can see and open the clone directly from their phone launcher!
     */
    fun pinCloneShortcutToHomeScreen(context: Context, clone: ClonedAppEntity) {
        val launchIntent = Intent(context, CloneLauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("omnispace://clone/${clone.originalPackageName}/${clone.cloneNumber}/${clone.id}")
            putExtra(CloneLauncherActivity.EXTRA_ORIGINAL_PKG, clone.originalPackageName)
            putExtra(CloneLauncherActivity.EXTRA_CLONED_PKG, clone.clonedPackageName)
            putExtra(CloneLauncherActivity.EXTRA_CLONE_NUMBER, clone.cloneNumber)
            putExtra(CloneLauncherActivity.EXTRA_CUSTOM_NAME, clone.customName)
            putExtra(CloneLauncherActivity.EXTRA_ACCOUNT_TAG, clone.accountTag)
            putExtra(CloneLauncherActivity.EXTRA_VIRTUAL_DEVICE_ID, clone.virtualDeviceId)
            putExtra(CloneLauncherActivity.EXTRA_SANDBOX_PATH, clone.sandboxDirPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val badgedBitmap = createBadgedShortcutBitmap(
            context = context,
            packageName = clone.originalPackageName,
            cloneNumber = clone.cloneNumber,
            badgeColorHex = clone.badgeColorHex
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val shortcutInfo = ShortcutInfo.Builder(
                    context,
                    "omnispace_clone_${clone.originalPackageName}_${clone.cloneNumber}_${clone.id}"
                )
                    .setShortLabel(clone.customName)
                    .setLongLabel("${clone.customName} (${clone.accountTag})")
                    .setIcon(Icon.createWithBitmap(badgedBitmap))
                    .setIntent(launchIntent)
                    .build()

                val requested = shortcutManager.requestPinShortcut(shortcutInfo, null)
                if (requested) {
                    Toast.makeText(
                        context,
                        "Adding '${clone.customName}' icon to your Phone Home Screen!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        }

        // Legacy fallback broadcast for older launchers
        @Suppress("DEPRECATION")
        val legacyIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra("duplicate", false)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, clone.customName)
            putExtra(Intent.EXTRA_SHORTCUT_ICON, badgedBitmap)
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
        }
        context.sendBroadcast(legacyIntent)
        Toast.makeText(
            context,
            "Shortcut sent to Home Screen for ${clone.customName}!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun createBadgedShortcutBitmap(
        context: Context,
        packageName: String,
        cloneNumber: Int,
        badgeColorHex: Long
    ): Bitmap {
        val size = 192
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val baseDrawable: Drawable? = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()

        if (baseDrawable != null) {
            if (baseDrawable is BitmapDrawable && baseDrawable.bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(baseDrawable.bitmap, size, size, true)
                canvas.drawBitmap(scaled, 0f, 0f, null)
            } else {
                baseDrawable.setBounds(0, 0, size, size)
                baseDrawable.draw(canvas)
            }
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 36f, 36f, bgPaint)
        }

        // Draw numbered clone badge at bottom-right corner
        val badgeText = "#$cloneNumber"
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColorHex.toInt()
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF090D16.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF050A14.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val badgeRect = RectF(size - 92f, size - 58f, size - 4f, size - 4f)
        canvas.drawRoundRect(badgeRect, 26f, 26f, badgePaint)
        canvas.drawRoundRect(badgeRect, 26f, 26f, borderPaint)

        val textY = badgeRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(badgeText, badgeRect.centerX(), textY, textPaint)

        return result
    }

    suspend fun ensureAndInstallCloneApk(
        context: Context,
        clone: ClonedAppEntity
    ): Result<File> = withContext(Dispatchers.Main) {
        runCatching {
            pinCloneShortcutToHomeScreen(context, clone)
            File(clone.sandboxDirPath)
        }
    }

    suspend fun shareCloneApk(context: Context, clone: ClonedAppEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(clone.originalPackageName, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(clone.originalPackageName, 0)
                }
                val sourceApk = File(appInfo.sourceDir)
                val clonesOutDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cloned_apks").apply {
                    mkdirs()
                }
                val exported = File(clonesOutDir, "${clone.originalPackageName}_clone_${clone.cloneNumber}.apk")
                if (!exported.exists()) {
                    sourceApk.copyTo(exported, overwrite = true)
                }

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exported
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "${clone.customName} (${clone.clonedPackageName}.apk)"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Export APK: ${clone.customName}")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

    fun launchClonedAppSession(context: Context, clone: ClonedAppEntity) {
        val intent = Intent(context, CloneLauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("omnispace://clone/${clone.originalPackageName}/${clone.cloneNumber}/${clone.id}")
            putExtra(CloneLauncherActivity.EXTRA_ORIGINAL_PKG, clone.originalPackageName)
            putExtra(CloneLauncherActivity.EXTRA_CLONED_PKG, clone.clonedPackageName)
            putExtra(CloneLauncherActivity.EXTRA_CLONE_NUMBER, clone.cloneNumber)
            putExtra(CloneLauncherActivity.EXTRA_CUSTOM_NAME, clone.customName)
            putExtra(CloneLauncherActivity.EXTRA_ACCOUNT_TAG, clone.accountTag)
            putExtra(CloneLauncherActivity.EXTRA_VIRTUAL_DEVICE_ID, clone.virtualDeviceId)
            putExtra(CloneLauncherActivity.EXTRA_SANDBOX_PATH, clone.sandboxDirPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to launch ${clone.customName}: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    suspend fun resetCloneSandbox(clone: ClonedAppEntity): Long = withContext(Dispatchers.IO) {
        val sandboxDir = File(clone.sandboxDirPath)
        if (sandboxDir.exists()) {
            File(sandboxDir, "cache").deleteRecursively()
            File(sandboxDir, "databases").deleteRecursively()
            File(sandboxDir, "shared_prefs/cookie_vault.json").delete()
            File(sandboxDir, "cache").mkdirs()
            File(sandboxDir, "databases").mkdirs()
        }
        calculateDirectorySize(sandboxDir)
    }

    suspend fun removeCloneFiles(clone: ClonedAppEntity) = withContext(Dispatchers.IO) {
        runCatching {
            if (clone.sandboxDirPath.isNotEmpty()) {
                File(clone.sandboxDirPath).deleteRecursively()
            }
            if (clone.apkOutputPath.isNotEmpty()) {
                File(clone.apkOutputPath).delete()
            }
        }
    }

    fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var result = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) result += file.length()
        }
        return result
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }
}
