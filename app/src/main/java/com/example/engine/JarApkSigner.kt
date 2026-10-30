package com.example.engine

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure-Kotlin APK Packager, ZipAligner, V1 JAR Signer, and V2 APK Signature Scheme Signer.
 * Produces standalone, cryptographically signed, zipaligned Cloned APKs that install directly
 * via Android's native PackageInstaller on Android 7 through Android 16.
 */
object JarApkSigner {

    // Pre-generated self-signed 2048-bit RSA Certificate (DER Base64)
    private const val CERT_DER_B64 =
        "MIIDHTCCAgWgAwIBAgIIIDdZbJhGpy0wDQYJKoZIhvcNAQELBQAwPDELMAkGA1UEBhMCVVMxEjAQBgNVBAoTCU9tbmlTcGFjZTEZMBcGA1UEAxMQT21uaVNwYWNlIENsb25lcjAgFw0yNjEwMzAxODAzNDlaGA8yMDU0MDMxNzE4MDM0OVowPDELMAkGA1UEBhMCVVMxEjAQBgNVBAoTCU9tbmlTcGFjZTEZMBcGA1UEAxMQT21uaVNwYWNlIENsb25lcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMdWhdteSH+tsKMsSdBGt7tzJu+RCvDkiddDPhYNhPVoyt0gyOZd2d1OK5HuIWuWt07R0joceEv8B0ZLlok/S3FjbQfgis/Ome2Dv44xVU0qzpCBVkpXXw6ZI2iF/RqBEHg0IG/DgToIRAkSVhrcj3wzad38ibb1haNGz2k22ReBVjKIxCrx+X0Ry2jB57OHaa4oCVvouIp39cQBGNzh3+Igb66KtlTyCic1JMAa/PqvJQUxBk7TinKG2ua5g0HucTmcPMqEBvT0AqEuKLhfMemwF9FJPU+ryx7ztXWEGdCELIs0NLEw8h3UxWi94DiAWpZOn0n3cw/WOJhUPIlkgykCAwEAAaMhMB8wHQYDVR0OBBYEFNtdef4Yl0Lx9/IdXCApHsgTDm/eMA0GCSqGSIb3DQEBCwUAA4IBAQCULngmFAQAiTu5MPaMikJwPLmEaLPBHHlV2B/+T4A3VIWGcWzCYiUOysBpCG8rSp6AT+Lt2rLRx/3c6ekHoFGkVJ0d6XEEhX18dXOtzOATMZBUcReL1+inp4Ka2TvZWeZpkmy8mhVk6hhqcJmoHnNY3THYxSDuc/3PptKIWgYCKWpoF83pPyM7MUTcE5e+lXZZPZNICF+C4eHrsbdVMNbGloeXHKkYqp38umKIVxN/SDVCWVgMKHm8D/A+ubZC2W7XuCV7+8cXtQDoguXTZ9ErcS3aIHno/435agQ6HoBFkm2PdQMAvpBJ//NMg92e7Fb2v3ajvZtzwKiXQSczFdP1"

    // Matching PKCS#8 RSA Private Key (DER Base64)
    private const val KEY_PKCS8_B64 =
        "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDHVoXbXkh/rbCjLEnQRre7cybvkQrw5InXQz4WDYT1aMrdIMjmXdndTiuR7iFrlrdO0dI6HHhL/AdGS5aJP0txY20H4IrPzpntg7+OMVVNKs6QgVZKV18OmSNohf0agRB4NCBvw4E6CEQJElYa3I98M2nd/Im29YWjRs9pNtkXgVYyiMQq8fl9Ectoweezh2muKAlb6LiKd/XEARjc4d/iIG+uirZU8gonNSTAGvz6ryUFMQZO04pyhtrmuYNB7nE5nDzKhAb09AKhLii4XzHpsBfRST1Pq8se87V1hBnQhCyLNDSxMPId1MVoveA4gFqWTp9J93MP1jiYVDyJZIMpAgMBAAECggEAD6Q36685xB8Gcb9baWbrKt/NydWZRK7CLCyAFi3oZXNcG77jVMQ/gLCOFazYXX9dkec/9kReoiO8FGWBAfMAAf05xHj3m3fMN0lzwf4YAe6ed9NWSI4TjOsyTpTY9/fxqWT5uJtgLAQTBp00OXZeIfJM19jlm4AnhNjdFJXzsfbWIPsjGJJzGvhXpH9+K31H9sef1J84Y/Mkc2qt6rT4ZytUegZliBj162LiK7Wa4bOd//Ae/YmGldCza6UlDUjSWo9rnW04df5Ca9USbGiOvUs0Ykbv/VZ/zX5oX2RHmaB/X3FW1Q+50rXPyJp9Kha1DmMDpzbYn/z8NZvisy332wKBgQDvC61oGbfTtpVUNOKB4Jt1uhi1g5wG4WYekbW7XuuOuVyezXs7rGNzXu2hV5JMU9ASKtwCnSYaBPe0eCeiHYYHSwKNO2qLH2fw5qqEso/Z042XYtMjR7UsikuI5Pmem0opx97q/MbfFsvGEoX/wGIt4kSA0GGHriVFw2bZpiX/6wKBgQDVeeD0ySRChyrE3op2bUVWZIlC23VKO0AqwFay311iM84Diey6oKrDqvFwUAchPeIALqFupCLd9GCeKdf2LaYJPOR/jXcHPGJbWbcG8GFLmUlFvYXYG2dK0vj5xdlcLIjwXzAJsObhsoy4B4aEkbY5ZI9uYyjB2O/Jm7Lz302YOwKBgEoin/vqBfXV6Uf9QsYux2fieClLEMedfdM7M1ZR4NIZsJzbcz2VgB5WsXHZO9kKITyRKDxzSk7FfaDtprUDXP7EB52PcMKG0fCHSRjwW+adUotBreOCKLEQjs583OGQnZaJkxvjmzWy1y2trvsbShea1c3xUl4G5y5UpIAZpC1TAoGAIFKS6B1oexZCOIrFhkbnP7x7N/9IlBPClHaI6g598jeL0udYS622DWhdpDbJzZO0G3j0Am+hw9+TnX8SbvjccfuhUVrXEeZnshM0bj2JTcwI1yFqGZ2UlzS4dAEr9xPbLCINgPojCztgqSCikoIJiEkcmoczDZ7ON8V2ZAyEkC8CgYBC0X12MUkAf5f+xCcxILR2dAP7EaLxuuT+u3TeQcSqpTbd0S6SS94wGffr8/7dKYxfyqR54ptWWMSP0hhsXN92QcPs7t8wWJSBw1ZHGg/zNJ1lBNRpfDhpMQsRJ7W8hSavXggDNlrKpjP2pA0DVmt2W9Zq0Ar3VLI00zKNMn6Pjw=="

    // SubjectPublicKeyInfo (DER Base64) for APK Signature Scheme v2
    private const val PUB_KEY_DER_B64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAx1aF215If62woyxJ0Ea3u3Mm75EK8OSJ10M+Fg2E9WjK3SDI5l3Z3U4rke4ha5a3TtHSOhx4S/wHRkuWiT9LcWNtB+CKz86Z7YO/jjFVTSrOkIFWSldfDpkjaIX9GoEQeDQgb8OBOghECRJWGtyPfDNp3fyJtvWFo0bPaTbZF4FWMojEKvH5fRHLaMHns4dprigJW+i4inf1xAEY3OHf4iBvroq2VPIKJzUkwBr8+q8lBTEGTtOKcoba5rmDQe5xOZw8yoQG9PQCoS4ouF8x6bAX0Uk9T6vLHvO1dYQZ0IQsizQ0sTDyHdTFaL3gOIBalk6fSfdzD9Y4mFQ8iWSDKQIDAQAB"

    // PKCS#7 CMS SignedData prefix for CERT.RSA (immediately precedes the 256-byte SHA256withRSA signature)
    private const val PKCS7_PREFIX_B64 =
        "MIIEzAYJKoZIhvcNAQcCoIIEvTCCBLkCAQExDTALBglghkgBZQMEAgEwCwYJKoZIhvcNAQcBoIIDITCCAx0wggIFoAMCAQICCCA3WWyYRqctMA0GCSqGSIb3DQEBCwUAMDwxCzAJBgNVBAYTAlVTMRIwEAYDVQQKEwlPbW5pU3BhY2UxGTAXBgNVBAMTEE9tbmlTcGFjZSBDbG9uZXIwIBcNMjYxMDMwMTgwMzQ5WhgPMjA1NDAzMTcxODAzNDlaMDwxCzAJBgNVBAYTAlVTMRIwEAYDVQQKEwlPbW5pU3BhY2UxGTAXBgNVBAMTEE9tbmlTcGFjZSBDbG9uZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDHVoXbXkh/rbCjLEnQRre7cybvkQrw5InXQz4WDYT1aMrdIMjmXdndTiuR7iFrlrdO0dI6HHhL/AdGS5aJP0txY20H4IrPzpntg7+OMVVNKs6QgVZKV18OmSNohf0agRB4NCBvw4E6CEQJElYa3I98M2nd/Im29YWjRs9pNtkXgVYyiMQq8fl9Ectoweezh2muKAlb6LiKd/XEARjc4d/iIG+uirZU8gonNSTAGvz6ryUFMQZO04pyhtrmuYNB7nE5nDzKhAb09AKhLii4XzHpsBfRST1Pq8se87V1hBnQhCyLNDSxMPId1MVoveA4gFqWTp9J93MP1jiYVDyJZIMpAgMBAAGjITAfMB0GA1UdDgQWBBTbXXn+GJdC8ffyHVwgKR7IEw5v3jANBgkqhkiG9w0BAQsFAAOCAQEAlC54JhQEAIk7uTD2jIpCcDy5hGizwRx5Vdgf/k+AN1SFhnFswmIlDsrAaQhvK0qegE/i7dqy0cf93OnpB6BRpFSdHelxBIV9fHVzrczgEzGQVHEXi9fop6eCmtk72VnmaZJsvJoVZOoYanCZqB5zWN0x2MUg7nP9z6bSiFoGAilqaBfN6T8jOzFE3BOXvpV2WT2TSAhfguHh67G3VTDWxpaHlxypGKqd/LpiiFcTf0g1QllYDCh5vA/wPrm2Qtlu17gle/vHF7UA6ILl02fRK3Et2iB56P+N+WoEOh6ARZJtj3UDAL6QSf/zTIPdnuxW9r92o72bc8Col0EnMxXT9TGCAXEwggFtAgEBMEgwPDELMAkGA1UEBhMCVVMxEjAQBgNVBAoTCU9tbmlTcGFjZTEZMBcGA1UEAxMQT21uaVNwYWNlIENsb25lcgIIIDdZbJhGpy0wCwYJYIZIAWUDBAIBMA0GCSqGSIb3DQEBAQUABIIBAA=="

    private const val APK_SIG_BLOCK_V2_ID = 0x7109871a
    private const val SIG_RSA_PKCS1_V1_5_WITH_SHA256 = 0x0103
    private const val CHUNK_SIZE_BYTES = 1024 * 1024 // 1 MB

    private val privateKey: PrivateKey by lazy {
        val keyBytes = Base64.decode(KEY_PKCS8_B64, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
        }
    }

    /**
     * Builds a patched, zipaligned, V1 + V2 signed standalone Clone APK from [sourceApk]
     * (and optional [splitApks] for native .so libraries).
     */
    fun buildAndSignClonedApk(
        sourceApk: File,
        splitApks: List<File>,
        destApk: File,
        originalPackage: String,
        newPackage: String,
        customAppLabel: String,
        cloneNumber: Int
    ) {
        val tempUnsignedZip = File(destApk.parentFile, "${destApk.name}.unsigned.tmp")
        val entryDigests = LinkedHashMap<String, String>() // entryName -> Base64 SHA-256
        val seenEntries = HashSet<String>()

        CountingOutputStream(BufferedOutputStream(FileOutputStream(tempUnsignedZip))).use { cos ->
            ZipOutputStream(cos).use { zos ->
                val buffer = ByteArray(32 * 1024)

                fun copyZipEntriesFrom(apkFile: File, isSplitApk: Boolean) {
                    if (!apkFile.exists() || !apkFile.canRead()) return
                    ZipInputStream(BufferedInputStream(FileInputStream(apkFile))).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            // Skip old signatures and duplicate entries
                            if (entry.isDirectory ||
                                isSignatureFile(name) ||
                                (isSplitApk && !name.startsWith("lib/")) ||
                                !seenEntries.add(name)
                            ) {
                                entry = zis.nextEntry
                                continue
                            }

                            if (!isSplitApk && name == "AndroidManifest.xml") {
                                val rawManifest = zis.readBytes()
                                val patchedManifest = BinaryXmlPatcher.patchManifest(
                                    manifestBytes = rawManifest,
                                    originalPackageFallback = originalPackage,
                                    newPackage = newPackage,
                                    customAppLabel = customAppLabel,
                                    cloneNumber = cloneNumber
                                )
                                entryDigests[name] = sha256Base64(patchedManifest)
                                val newEntry = ZipEntry(name)
                                zos.putNextEntry(newEntry)
                                zos.write(patchedManifest)
                                zos.closeEntry()
                            } else if (entry.method == ZipEntry.STORED || name == "resources.arsc") {
                                val rawBytes = zis.readBytes()
                                entryDigests[name] = sha256Base64(rawBytes)

                                val crc = CRC32()
                                crc.update(rawBytes)

                                val newEntry = ZipEntry(name).apply {
                                    method = ZipEntry.STORED
                                    size = rawBytes.size.toLong()
                                    compressedSize = rawBytes.size.toLong()
                                    this.crc = crc.value
                                    time = 0L
                                }

                                // ZipAlign STORED entries (4096-byte align for .so, 4-byte align for resources.arsc / others)
                                zos.flush()
                                val alignment = if (name.endsWith(".so")) 4096L else 4L
                                val nameLen = name.toByteArray(StandardCharsets.UTF_8).size
                                val headerEnd = cos.bytesWritten + 30L + nameLen
                                val pad = ((alignment - (headerEnd % alignment)) % alignment).toInt()
                                if (pad > 0) {
                                    newEntry.extra = ByteArray(pad)
                                }

                                zos.putNextEntry(newEntry)
                                zos.write(rawBytes)
                                zos.closeEntry()
                            } else {
                                val md = MessageDigest.getInstance("SHA-256")
                                val newEntry = ZipEntry(name)
                                zos.putNextEntry(newEntry)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    md.update(buffer, 0, len)
                                    zos.write(buffer, 0, len)
                                }
                                zos.closeEntry()
                                entryDigests[name] = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                            }

                            entry = zis.nextEntry
                        }
                    }
                }

                // 1. Copy & patch base APK
                copyZipEntriesFrom(sourceApk, isSplitApk = false)

                // 2. Merge native .so libraries from any split APKs (split_config.arm64_v8a.apk, etc.)
                for (split in splitApks) {
                    copyZipEntriesFrom(split, isSplitApk = true)
                }

                // 3. Generate and write V1 Signature files (META-INF/MANIFEST.MF, CERT.SF, CERT.RSA)
                val manifestMfBytes = buildManifestMf(entryDigests)
                val certSfBytes = buildCertSf(manifestMfBytes, entryDigests)
                val certRsaBytes = buildCertRsa(certSfBytes)

                writeDeflatedEntry(zos, "META-INF/MANIFEST.MF", manifestMfBytes)
                writeDeflatedEntry(zos, "META-INF/CERT.SF", certSfBytes)
                writeDeflatedEntry(zos, "META-INF/CERT.RSA", certRsaBytes)
            }
        }

        // 4. Inject APK Signature Scheme v2 Block (ID 0x7109871a) before Central Directory
        injectV2SignatureBlock(tempUnsignedZip, destApk)
        tempUnsignedZip.delete()
    }

    private fun isSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        if (!upper.startsWith("META-INF/")) return false
        return upper == "META-INF/MANIFEST.MF" ||
            upper.endsWith(".SF") ||
            upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") ||
            upper.endsWith(".EC") ||
            upper.startsWith("META-INF/SIG-")
    }

    private fun writeDeflatedEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun buildManifestMf(entryDigests: Map<String, String>): ByteArray {
        val sb = StringBuilder()
        sb.append("Manifest-Version: 1.0\r\n")
        sb.append("Created-By: 1.0 (OmniSpace APK Cloner)\r\n\r\n")
        for ((name, digestB64) in entryDigests) {
            sb.append("Name: ").append(name).append("\r\n")
            sb.append("SHA-256-Digest: ").append(digestB64).append("\r\n\r\n")
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildCertSf(manifestMfBytes: ByteArray, entryDigests: Map<String, String>): ByteArray {
        val wholeManifestDigest = sha256Base64(manifestMfBytes)
        val sb = StringBuilder()
        sb.append("Signature-Version: 1.0\r\n")
        sb.append("Created-By: 1.0 (OmniSpace APK Cloner)\r\n")
        sb.append("SHA-256-Digest-Manifest: ").append(wholeManifestDigest).append("\r\n\r\n")

        for ((name, digestB64) in entryDigests) {
            val sectionText = "Name: $name\r\nSHA-256-Digest: $digestB64\r\n\r\n"
            val sectionDigest = sha256Base64(sectionText.toByteArray(StandardCharsets.UTF_8))
            sb.append("Name: ").append(name).append("\r\n")
            sb.append("SHA-256-Digest: ").append(sectionDigest).append("\r\n\r\n")
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildCertRsa(certSfBytes: ByteArray): ByteArray {
        val prefix = Base64.decode(PKCS7_PREFIX_B64, Base64.DEFAULT)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(certSfBytes)
        val signatureBytes = sig.sign()

        val out = ByteArray(prefix.size + signatureBytes.size)
        System.arraycopy(prefix, 0, out, 0, prefix.size)
        System.arraycopy(signatureBytes, 0, out, prefix.size, signatureBytes.size)
        return out
    }

    /**
     * Computes APK Signature Scheme v2 merkle digest over the 3 ZIP sections and inserts
     * the "APK Sig Block 42" (ID 0x7109871a) immediately before the ZIP Central Directory.
     */
    private fun injectV2SignatureBlock(inputZip: File, outputApk: File) {
        RandomAccessFile(inputZip, "r").use { raf ->
            val fileLen = raf.length()
            val eocdOffset = findZipEocdOffset(raf, fileLen)
            if (eocdOffset < 0) {
                inputZip.copyTo(outputApk, overwrite = true)
                return
            }

            val eocdSize = (fileLen - eocdOffset).toInt()
            val eocdBytes = ByteArray(eocdSize)
            raf.seek(eocdOffset)
            raf.readFully(eocdBytes)

            val eocdBuf = ByteBuffer.wrap(eocdBytes).order(ByteOrder.LITTLE_ENDIAN)
            val centralDirSize = eocdBuf.getInt(12).toLong() and 0xFFFFFFFFL
            val centralDirOffset = eocdBuf.getInt(16).toLong() and 0xFFFFFFFFL

            if (centralDirOffset <= 0 || centralDirOffset + centralDirSize > eocdOffset) {
                inputZip.copyTo(outputApk, overwrite = true)
                return
            }

            val centralDirBytes = ByteArray(centralDirSize.toInt())
            raf.seek(centralDirOffset)
            raf.readFully(centralDirBytes)

            // For V2 digest computation, EOCD's centralDirOffset field must be set to centralDirOffset
            val eocdForDigest = eocdBytes.clone()
            ByteBuffer.wrap(eocdForDigest).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(16, centralDirOffset.toInt())

            // Compute 1MB chunk digests across:
            // Section 1: [0 .. centralDirOffset)
            // Section 2: centralDirBytes
            // Section 3: eocdForDigest
            val chunkDigests = ArrayList<ByteArray>()
            val md = MessageDigest.getInstance("SHA-256")
            val chunkHeader = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)

            // Section 1 chunks (streamed from file)
            var remaining = centralDirOffset
            var readPos = 0L
            val chunkBuf = ByteArray(CHUNK_SIZE_BYTES)
            while (remaining > 0) {
                val toRead = minOf(remaining, CHUNK_SIZE_BYTES.toLong()).toInt()
                raf.seek(readPos)
                raf.readFully(chunkBuf, 0, toRead)
                md.reset()
                chunkHeader.clear()
                chunkHeader.put(0xa5.toByte())
                chunkHeader.putInt(toRead)
                md.update(chunkHeader.array(), 0, 5)
                md.update(chunkBuf, 0, toRead)
                chunkDigests.add(md.digest())
                readPos += toRead
                remaining -= toRead
            }

            // Section 2 & 3 chunks (in-memory)
            for (section in listOf(centralDirBytes, eocdForDigest)) {
                var off = 0
                while (off < section.size) {
                    val len = minOf(section.size - off, CHUNK_SIZE_BYTES)
                    md.reset()
                    chunkHeader.clear()
                    chunkHeader.put(0xa5.toByte())
                    chunkHeader.putInt(len)
                    md.update(chunkHeader.array(), 0, 5)
                    md.update(section, off, len)
                    chunkDigests.add(md.digest())
                    off += len
                }
            }

            // Compute top-level digest: SHA-256(0x5a || uint32_le(numChunks) || concat(chunkDigests))
            md.reset()
            chunkHeader.clear()
            chunkHeader.put(0x5a.toByte())
            chunkHeader.putInt(chunkDigests.size)
            md.update(chunkHeader.array(), 0, 5)
            for (d in chunkDigests) {
                md.update(d)
            }
            val topLevelDigest = md.digest()

            val sigBlock = buildApkSigBlockV2(topLevelDigest)
            val newCentralDirOffset = centralDirOffset + sigBlock.size
            eocdBuf.putInt(16, newCentralDirOffset.toInt())

            // Write final signed APK: [Section 1] + [APK Sig Block 42] + [Central Dir] + [Updated EOCD]
            BufferedOutputStream(FileOutputStream(outputApk)).use { out ->
                raf.seek(0L)
                var left = centralDirOffset
                while (left > 0) {
                    val n = minOf(left, chunkBuf.size.toLong()).toInt()
                    raf.readFully(chunkBuf, 0, n)
                    out.write(chunkBuf, 0, n)
                    left -= n
                }
                out.write(sigBlock)
                out.write(centralDirBytes)
                out.write(eocdBytes)
            }
        }
    }

    private fun buildApkSigBlockV2(topLevelDigest: ByteArray): ByteArray {
        val certDer = Base64.decode(CERT_DER_B64, Base64.DEFAULT)
        val pubKeyDer = Base64.decode(PUB_KEY_DER_B64, Base64.DEFAULT)

        // digest entry: uint32(algo) + lenPrefix(digest)
        val digestEntry = ByteBuffer.allocate(4 + 4 + topLevelDigest.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(SIG_RSA_PKCS1_V1_5_WITH_SHA256)
            putInt(topLevelDigest.size)
            put(topLevelDigest)
        }.array()
        val digestsList = lengthPrefixed(lengthPrefixed(digestEntry))
        val certsList = lengthPrefixed(lengthPrefixed(certDer))
        val additionalAttrs = ByteArray(4) // uint32(0)

        val signedData = concat(digestsList, certsList, additionalAttrs)

        // Sign signedData with RSA-2048 private key
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(signedData)
        val rsaSigBytes = sig.sign()

        val signatureEntry = ByteBuffer.allocate(4 + 4 + rsaSigBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(SIG_RSA_PKCS1_V1_5_WITH_SHA256)
            putInt(rsaSigBytes.size)
            put(rsaSigBytes)
        }.array()
        val signaturesList = lengthPrefixed(lengthPrefixed(signatureEntry))

        val signer = concat(
            lengthPrefixed(signedData),
            signaturesList,
            lengthPrefixed(pubKeyDer)
        )
        val signersList = lengthPrefixed(lengthPrefixed(signer))

        // ID-value pair: uint64(4 + signersList.size) + uint32(0x7109871a) + signersList
        val pairPayloadSize = 4 + signersList.size
        // Pad block so total APK Signing Block is a multiple of 4096 bytes if needed, or standard 8-byte aligned
        val pair = ByteBuffer.allocate(8 + pairPayloadSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(pairPayloadSize.toLong())
            putInt(APK_SIG_BLOCK_V2_ID)
            put(signersList)
        }.array()

        // Total size_of_block (excluding initial uint64): pair.size + 8 (second size_of_block) + 16 (magic)
        val sizeOfBlock = pair.size + 8L + 16L
        val magic = "APK Sig Block 42".toByteArray(StandardCharsets.UTF_8)

        return ByteBuffer.allocate(8 + pair.size + 8 + 16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(sizeOfBlock)
            put(pair)
            putLong(sizeOfBlock)
            put(magic)
        }.array()
    }

    private fun lengthPrefixed(data: ByteArray): ByteArray {
        return ByteBuffer.allocate(4 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(data.size)
            put(data)
        }.array()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val total = arrays.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (arr in arrays) {
            System.arraycopy(arr, 0, out, pos, arr.size)
            pos += arr.size
        }
        return out
    }

    private fun findZipEocdOffset(raf: RandomAccessFile, fileLen: Long): Long {
        val maxSearch = minOf(fileLen, 65557L).toInt()
        val buf = ByteArray(maxSearch)
        val startOffset = fileLen - maxSearch
        raf.seek(startOffset)
        raf.readFully(buf)
        for (i in maxSearch - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() &&
                buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() &&
                buf[i + 3] == 0x06.toByte()
            ) {
                return startOffset + i
            }
        }
        return -1L
    }

    private fun sha256Base64(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(md.digest(data), Base64.NO_WRAP)
    }
}
