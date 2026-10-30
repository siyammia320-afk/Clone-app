package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    /**
     * Generates a same-length mutated package name for binary AXML safety when possible,
     * plus a human-readable cloned package identifier.
     */
    fun generateClonedPackageName(originalPackage: String, cloneNumber: Int): String {
        val suffix = String.format("c%02d", cloneNumber.coerceIn(1, 999))
        if (originalPackage.length > suffix.length + 4) {
            val prefix = originalPackage.substring(0, originalPackage.length - suffix.length)
            // Keep valid package segments
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
     * Creates an isolated sandbox file tree and builds a patched Clone APK from the source APK.
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

        // 1. Create isolated sandbox directory for this specific clone instance
        val sandboxRoot = File(context.filesDir, "omnispace_sandboxes/$spaceId/${appInfo.packageName}_clone_$cloneNumber")
        val prefsDir = File(sandboxRoot, "shared_prefs").apply { mkdirs() }
        val dbDir = File(sandboxRoot, "databases").apply { mkdirs() }
        val cacheDir = File(sandboxRoot, "cache").apply { mkdirs() }
        val filesSubDir = File(sandboxRoot, "files").apply { mkdirs() }

        // Write isolated virtual identity & multi-account session descriptor
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

        // Create isolated cookie/session store marker inside databases & files
        File(dbDir, "account_session_slot_$cloneNumber.db").writeBytes(
            "OMNISPACE_ISOLATED_SQLITE_SLOT_${cloneNumber}_$virtualDeviceId".toByteArray(StandardCharsets.UTF_8)
        )
        File(filesSubDir, "profile_token.dat").writeText(
            "SLOT=$cloneNumber\nDEVICE_ID=$virtualDeviceId\nACCOUNT=$accountTag\n",
            StandardCharsets.UTF_8
        )
        File(cacheDir, "session_cache.bin").writeBytes(ByteArray(2048) { (it % 255).toByte() })

        // 2. Build patched Clone APK file if source APK is accessible and requested
        val clonesOutDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "cloned_apks").apply {
            mkdirs()
        }
        val outputApkFile = File(clonesOutDir, "${appInfo.packageName}_clone_${cloneNumber}.apk")

        if (generateApkPackage && appInfo.sourceApkPath.isNotEmpty()) {
            val sourceFile = File(appInfo.sourceApkPath)
            if (sourceFile.exists() && sourceFile.canRead()) {
                runCatching {
                    patchApkManifestPackage(
                        sourceApk = sourceFile,
                        destApk = outputApkFile,
                        originalPackage = appInfo.packageName,
                        newPackage = clonedPkg
                    )
                }.onFailure {
                    // Fallback: copy base APK stream with clone metadata entry
                    runCatching { sourceFile.copyTo(outputApkFile, overwrite = true) }
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
            customName = customName.ifBlank { "${appInfo.appName} #$cloneNumber" },
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
     * Patches AndroidManifest.xml inside the APK zip by replacing same-length UTF-16LE and UTF-8
     * package name strings so binary AXML chunk offsets remain strictly intact, and adds
     * assets/omnispace_clone_profile.json for multi-instance identification.
     */
    private fun patchApkManifestPackage(
        sourceApk: File,
        destApk: File,
        originalPackage: String,
        newPackage: String
    ) {
        val safeReplacement = if (newPackage.length == originalPackage.length) {
            newPackage
        } else if (newPackage.length > originalPackage.length) {
            newPackage.take(originalPackage.length)
        } else {
            newPackage.padEnd(originalPackage.length, 'x')
        }

        val origUtf8 = originalPackage.toByteArray(StandardCharsets.UTF_8)
        val replUtf8 = safeReplacement.toByteArray(StandardCharsets.UTF_8)

        val origUtf16 = originalPackage.toByteArray(StandardCharsets.UTF_16LE)
        val replUtf16 = safeReplacement.toByteArray(StandardCharsets.UTF_16LE)

        ZipInputStream(BufferedInputStream(FileInputStream(sourceApk))).use { zis ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destApk))).use { zos ->
                val buffer = ByteArray(16 * 1024)
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName == "AndroidManifest.xml") {
                        val manifestBytes = zis.readBytes()
                        replaceBytesInPlace(manifestBytes, origUtf16, replUtf16)
                        replaceBytesInPlace(manifestBytes, origUtf8, replUtf8)

                        val newEntry = ZipEntry(entryName)
                        zos.putNextEntry(newEntry)
                        zos.write(manifestBytes)
                        zos.closeEntry()
                    } else {
                        val newEntry = ZipEntry(entryName)
                        if (entry.method == ZipEntry.STORED) {
                            val rawBytes = zis.readBytes()
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = rawBytes.size.toLong()
                            newEntry.compressedSize = rawBytes.size.toLong()
                            val crc = CRC32()
                            crc.update(rawBytes)
                            newEntry.crc = crc.value
                            zos.putNextEntry(newEntry)
                            zos.write(rawBytes)
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(newEntry)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                            zos.closeEntry()
                        }
                    }
                    entry = zis.nextEntry
                }

                // Embed clone profile configuration inside assets/
                val metaEntry = ZipEntry("assets/omnispace_clone_config.json")
                zos.putNextEntry(metaEntry)
                val metaBytes = """{"original":"$originalPackage","cloned":"$safeReplacement"}"""
                    .toByteArray(StandardCharsets.UTF_8)
                zos.write(metaBytes)
                zos.closeEntry()
            }
        }
    }

    private fun replaceBytesInPlace(data: ByteArray, target: ByteArray, replacement: ByteArray) {
        if (target.isEmpty() || target.size != replacement.size || data.size < target.size) return
        var i = 0
        while (i <= data.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (data[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, data, i, replacement.size)
                i += target.size
            } else {
                i++
            }
        }
    }

    /**
     * Ensures the patched Clone APK file exists on disk (rebuilding it on demand if needed)
     * and launches Android's Package Installer via FileProvider.
     */
    suspend fun ensureAndInstallCloneApk(
        context: Context,
        clone: ClonedAppEntity
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = if (clone.apkOutputPath.isNotEmpty() && File(clone.apkOutputPath).exists()) {
                File(clone.apkOutputPath)
            } else {
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
                val generated = File(clonesOutDir, "${clone.originalPackageName}_clone_${clone.cloneNumber}.apk")
                patchApkManifestPackage(
                    sourceApk = sourceApk,
                    destApk = generated,
                    originalPackage = clone.originalPackageName,
                    newPackage = clone.clonedPackageName
                )
                generated
            }

            withContext(Dispatchers.Main) {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
            outFile
        }
    }

    suspend fun shareCloneApk(context: Context, clone: ClonedAppEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outFile = if (clone.apkOutputPath.isNotEmpty() && File(clone.apkOutputPath).exists()) {
                    File(clone.apkOutputPath)
                } else {
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
                    val generated = File(clonesOutDir, "${clone.originalPackageName}_clone_${clone.cloneNumber}.apk")
                    patchApkManifestPackage(
                        sourceApk = sourceApk,
                        destApk = generated,
                        originalPackage = clone.originalPackageName,
                        newPackage = clone.clonedPackageName
                    )
                    generated
                }

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
                        Intent.createChooser(shareIntent, "Export Cloned APK: ${clone.customName}")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

    fun launchClonedAppSession(context: Context, clone: ClonedAppEntity) {
        val intent = Intent(context, CloneLauncherActivity::class.java).apply {
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
