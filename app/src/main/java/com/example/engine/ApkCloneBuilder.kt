package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.ClonedAppEntity
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
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

    /**
     * Generates a clean, valid standalone package name for Clone #N (e.g. com.whatsapp.clone1).
     */
    fun generateClonedPackageName(originalPackage: String, cloneNumber: Int): String {
        val cleanNum = cloneNumber.coerceAtLeast(1)
        return "$originalPackage.clone$cleanNum"
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
     * Checks if the standalone cloned APK package (e.g. com.pkg.clone1) is currently installed on the phone.
     */
    fun isClonedPackageInstalled(context: Context, clonedPackageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    clonedPackageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(clonedPackageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds a real, cryptographically signed, zipaligned Direct Native Clone APK
     * with its own package name (e.g. com.pkg.clone1) and custom label.
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
        val resolvedName = customName.ifBlank { "${appInfo.appName} #$cloneNumber" }

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
              "customLabel": "${resolvedName.replace("\"", "'")}",
              "accountTag": "${accountTag.replace("\"", "'")}",
              "spaceId": "$spaceId",
              "virtualAndroidId": "$virtualDeviceId",
              "virtualModel": "$virtualModel",
              "isolatedDataRoot": "${sandboxRoot.absolutePath}",
              "createdAt": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        identityFile.writeText(identityJson, StandardCharsets.UTF_8)

        val clonesOutDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cloned_apks").apply {
            mkdirs()
        }
        val outputApkFile = File(clonesOutDir, "${appInfo.packageName}_clone_${cloneNumber}.apk")

        if (generateApkPackage && appInfo.sourceApkPath.isNotEmpty()) {
            val sourceFile = File(appInfo.sourceApkPath)
            val splitFiles = appInfo.splitApkPaths.map { File(it) }.filter { it.exists() && it.canRead() }
            if (sourceFile.exists() && sourceFile.canRead()) {
                runCatching {
                    JarApkSigner.buildAndSignClonedApk(
                        sourceApk = sourceFile,
                        splitApks = splitFiles,
                        destApk = outputApkFile,
                        originalPackage = appInfo.packageName,
                        newPackage = clonedPkg,
                        customAppLabel = resolvedName,
                        cloneNumber = cloneNumber
                    )
                }
            }
        }

        val totalBytes = calculateDirectorySize(sandboxRoot) +
            if (outputApkFile.exists()) outputApkFile.length() else 0L

        ClonedAppEntity(
            originalPackageName = appInfo.packageName,
            originalAppName = appInfo.appName,
            clonedPackageName = clonedPkg,
            cloneNumber = cloneNumber,
            customName = resolvedName,
            accountTag = accountTag.ifBlank { "Account #$cloneNumber" },
            spaceId = spaceId,
            badgeColorHex = badgeColorHex,
            virtualDeviceId = virtualDeviceId,
            virtualModelName = virtualModel,
            apkOutputPath = if (outputApkFile.exists()) outputApkFile.absolutePath else "",
            sandboxDirPath = sandboxRoot.absolutePath,
            storageBytes = totalBytes
        )
    }

    /**
     * Rebuilds (if not yet built) and signs the standalone Clone APK with V1+V2 signatures,
     * then launches Android's System Package Installer so the clone installs as a direct native app.
     */
    suspend fun ensureAndInstallCloneApk(
        context: Context,
        clone: ClonedAppEntity
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = getOrBuildSignedCloneApkFile(context, clone)
            withContext(Dispatchers.Main) {
                promptSystemPackageInstaller(context, outFile)
            }
            outFile
        }
    }

    suspend fun getOrBuildSignedCloneApkFile(
        context: Context,
        clone: ClonedAppEntity
    ): File = withContext(Dispatchers.IO) {
        val existing = if (clone.apkOutputPath.isNotEmpty()) File(clone.apkOutputPath) else null
        if (existing != null && existing.exists() && existing.length() > 1024L) {
            return@withContext existing
        }

        val pm = context.packageManager
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(clone.originalPackageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(clone.originalPackageName, 0)
        }
        val sourceApk = File(appInfo.sourceDir)
        val splitFiles = appInfo.splitSourceDirs?.map { File(it) }?.filter { it.exists() && it.canRead() }
            ?: emptyList()

        val clonesOutDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cloned_apks").apply {
            mkdirs()
        }
        val generated = File(clonesOutDir, "${clone.originalPackageName}_clone_${clone.cloneNumber}.apk")

        JarApkSigner.buildAndSignClonedApk(
            sourceApk = sourceApk,
            splitApks = splitFiles,
            destApk = generated,
            originalPackage = clone.originalPackageName,
            newPackage = clone.clonedPackageName,
            customAppLabel = clone.customName,
            cloneNumber = clone.cloneNumber
        )
        generated
    }

    fun promptSystemPackageInstaller(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    suspend fun shareCloneApk(context: Context, clone: ClonedAppEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outFile = getOrBuildSignedCloneApkFile(context, clone)
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outFile
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
                        Intent.createChooser(shareIntent, "Export Signed Clone APK: ${clone.customName}")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

    /**
     * Direct Native App Launch:
     * - If the cloned native package (e.g. com.whatsapp.clone1) is installed on the phone,
     *   launches that installed native clone directly!
     * - Otherwise opens CloneLauncherActivity to build/install & launch the native clone APK (NO WebView!).
     */
    fun launchClonedAppSession(context: Context, clone: ClonedAppEntity) {
        val pm = context.packageManager
        val directCloneLaunchIntent = pm.getLaunchIntentForPackage(clone.clonedPackageName)
        if (directCloneLaunchIntent != null) {
            directCloneLaunchIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(directCloneLaunchIntent)
            return
        }

        val intent = Intent(context, CloneLauncherActivity::class.java).apply {
            putExtra(CloneLauncherActivity.EXTRA_ORIGINAL_PKG, clone.originalPackageName)
            putExtra(CloneLauncherActivity.EXTRA_CLONED_PKG, clone.clonedPackageName)
            putExtra(CloneLauncherActivity.EXTRA_CLONE_NUMBER, clone.cloneNumber)
            putExtra(CloneLauncherActivity.EXTRA_CUSTOM_NAME, clone.customName)
            putExtra(CloneLauncherActivity.EXTRA_ACCOUNT_TAG, clone.accountTag)
            putExtra(CloneLauncherActivity.EXTRA_VIRTUAL_DEVICE_ID, clone.virtualDeviceId)
            putExtra(CloneLauncherActivity.EXTRA_SANDBOX_PATH, clone.sandboxDirPath)
            putExtra(CloneLauncherActivity.EXTRA_APK_OUTPUT_PATH, clone.apkOutputPath)
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
            File(sandboxDir, "cache").mkdirs()
            File(sandboxDir, "databases").mkdirs()
        }
        val apkLen = if (clone.apkOutputPath.isNotEmpty() && File(clone.apkOutputPath).exists()) {
            File(clone.apkOutputPath).length()
        } else 0L
        calculateDirectorySize(sandboxDir) + apkLen
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
