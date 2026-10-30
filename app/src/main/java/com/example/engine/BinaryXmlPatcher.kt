package com.example.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin Binary AndroidManifest.xml (AXML) parser and patcher.
 * Safely clones an APK's manifest by:
 * 1. Changing <manifest package="..."> to [newPackage]
 * 2. Expanding any relative ".ClassName" in <application>, <activity>, <service>, <receiver>, <provider>
 *    to "[originalPackage].ClassName" so all classes in classes.dex resolve without ClassNotFoundException
 * 3. Updating <application android:label="..."> to [customAppLabel]
 * 4. Suffixing <provider android:authorities="..."> and custom package permissions with ".clone[cloneNumber]"
 *    so unlimited clones install side-by-side without provider/permission conflicts
 * 5. Setting targetSdkVersion to 28 and extractNativeLibs=true so V1 JAR signing is accepted on Android 7–16.
 */
object BinaryXmlPatcher {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102

    private const val UTF8_FLAG = 0x00000100
    private const val SORTED_FLAG = 0x00000001

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12

    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_AUTHORITIES = 0x01010018
    private const val ATTR_TARGET_SDK = 0x01010270
    private const val ATTR_EXTRACT_NATIVE_LIBS = 0x010104ea

    fun patchManifest(
        manifestBytes: ByteArray,
        originalPackageFallback: String,
        newPackage: String,
        customAppLabel: String,
        cloneNumber: Int
    ): ByteArray {
        val buf = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (manifestBytes.size < 36) return manifestBytes

        val fileType = buf.getShort(0).toInt() and 0xFFFF
        val fileHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        if (fileType != RES_XML_TYPE) return manifestBytes

        // 1. Locate String Pool Chunk (immediately at fileHeaderSize, typically offset 8)
        val spOffset = fileHeaderSize
        val spType = buf.getShort(spOffset).toInt() and 0xFFFF
        if (spType != RES_STRING_POOL_TYPE) return manifestBytes

        val spHeaderSize = buf.getShort(spOffset + 2).toInt() and 0xFFFF
        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val styleCount = buf.getInt(spOffset + 12)
        val spFlags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val stylesStart = buf.getInt(spOffset + 24)

        val isUtf8 = (spFlags and UTF8_FLAG) != 0

        // Decode existing strings from String Pool
        val strings = ArrayList<String>(stringCount + 64)
        val offsetsBase = spOffset + spHeaderSize
        val stringsDataBase = spOffset + stringsStart

        for (i in 0 until stringCount) {
            val strRelOffset = buf.getInt(offsetsBase + i * 4)
            val absOffset = stringsDataBase + strRelOffset
            val decoded = if (isUtf8) {
                decodeUtf8String(manifestBytes, absOffset)
            } else {
                decodeUtf16String(manifestBytes, absOffset)
            }
            strings.add(decoded)
        }

        // Preserve original style offsets and style data if present
        val styleOffsetsBytes = if (styleCount > 0) {
            val start = offsetsBase + stringCount * 4
            manifestBytes.copyOfRange(start, start + styleCount * 4)
        } else ByteArray(0)

        val styleDataBytes = if (styleCount > 0 && stylesStart > 0) {
            val start = spOffset + stylesStart
            val end = spOffset + spChunkSize
            if (start in 0..end && end <= manifestBytes.size) {
                manifestBytes.copyOfRange(start, end)
            } else ByteArray(0)
        } else ByteArray(0)

        // Helper to get or append a new string at the end of the String Pool
        fun addOrGetNewString(value: String): Int {
            // Only reuse if it's beyond the original string count so we don't collide with resource-mapped strings
            for (idx in stringCount until strings.size) {
                if (strings[idx] == value) return idx
            }
            strings.add(value)
            return strings.size - 1
        }

        // 2. Scan remaining chunks to read Resource ID Map (0x0180) and patch Start Elements (0x0102)
        val afterStringPoolOffset = spOffset + spChunkSize
        val tailBytes = manifestBytes.copyOfRange(afterStringPoolOffset, manifestBytes.size)
        val tailBuf = ByteBuffer.wrap(tailBytes).order(ByteOrder.LITTLE_ENDIAN)

        var resourceMap = IntArray(0)
        var detectedOriginalPackage = originalPackageFallback

        var pos = 0
        while (pos + 8 <= tailBytes.size) {
            val chunkType = tailBuf.getShort(pos).toInt() and 0xFFFF
            val chunkHeaderSize = tailBuf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = tailBuf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > tailBytes.size) break

            if (chunkType == RES_XML_RESOURCE_MAP_TYPE) {
                val count = (chunkSize - chunkHeaderSize) / 4
                resourceMap = IntArray(count)
                for (i in 0 until count) {
                    resourceMap[i] = tailBuf.getInt(pos + chunkHeaderSize + i * 4)
                }
            } else if (chunkType == RES_XML_START_ELEMENT_TYPE) {
                val elemNameIdx = tailBuf.getInt(pos + chunkHeaderSize + 4)
                val attrStart = tailBuf.getShort(pos + chunkHeaderSize + 8).toInt() and 0xFFFF
                val attrSize = tailBuf.getShort(pos + chunkHeaderSize + 10).toInt() and 0xFFFF
                val attrCount = tailBuf.getShort(pos + chunkHeaderSize + 12).toInt() and 0xFFFF

                val elemName = strings.getOrNull(elemNameIdx) ?: ""
                val attrsBase = pos + chunkHeaderSize + attrStart

                for (a in 0 until attrCount) {
                    val attrPos = attrsBase + a * attrSize
                    if (attrPos + 20 > pos + chunkSize) break

                    val attrNsIdx = tailBuf.getInt(attrPos)
                    val attrNameIdx = tailBuf.getInt(attrPos + 4)
                    val attrRawValIdx = tailBuf.getInt(attrPos + 8)
                    val typedDataType = tailBuf.get(attrPos + 15).toInt() and 0xFF
                    val typedData = tailBuf.getInt(attrPos + 16)

                    val attrName = strings.getOrNull(attrNameIdx) ?: ""
                    val attrResId = if (attrNameIdx in resourceMap.indices) resourceMap[attrNameIdx] else 0

                    val currentStrVal = when {
                        typedDataType == TYPE_STRING && typedData in strings.indices -> strings[typedData]
                        attrRawValIdx in strings.indices -> strings[attrRawValIdx]
                        else -> null
                    }

                    // Rule A: <manifest package="...">
                    if (elemName == "manifest" && attrName == "package" && currentStrVal != null) {
                        if (currentStrVal.isNotBlank()) {
                            detectedOriginalPackage = currentStrVal
                        }
                        val newPkgIdx = addOrGetNewString(newPackage)
                        tailBuf.putInt(attrPos + 8, newPkgIdx)
                        tailBuf.put(attrPos + 15, TYPE_STRING.toByte())
                        tailBuf.putInt(attrPos + 16, newPkgIdx)
                    }

                    // Rule B: <uses-sdk android:targetSdkVersion="..."> -> cap at 28 for V1 signature compatibility
                    if (elemName == "uses-sdk" && (attrResId == ATTR_TARGET_SDK || attrName == "targetSdkVersion")) {
                        if (typedData > 28) {
                            tailBuf.putInt(attrPos + 8, -1)
                            tailBuf.put(attrPos + 15, TYPE_INT_DEC.toByte())
                            tailBuf.putInt(attrPos + 16, 28)
                        }
                    }

                    // Rule C: <application / activity label & extractNativeLibs>
                    if (elemName in setOf("application", "activity", "activity-alias")) {
                        if (attrResId == ATTR_LABEL || attrName == "label") {
                            val newLabelIdx = addOrGetNewString(customAppLabel)
                            tailBuf.putInt(attrPos + 8, newLabelIdx)
                            tailBuf.put(attrPos + 15, TYPE_STRING.toByte())
                            tailBuf.putInt(attrPos + 16, newLabelIdx)
                        }
                    }
                    if (elemName == "application") {
                        if (attrResId == ATTR_EXTRACT_NATIVE_LIBS || attrName == "extractNativeLibs") {
                            tailBuf.putInt(attrPos + 8, -1)
                            tailBuf.put(attrPos + 15, TYPE_INT_BOOLEAN.toByte())
                            tailBuf.putInt(attrPos + 16, -1) // true
                        }
                    }

                    // Rule D: Expand relative class names (.MainActivity -> originalPackage.MainActivity)
                    if (elemName in setOf("application", "activity", "activity-alias", "service", "receiver", "provider")) {
                        val isClassNameAttr = (attrResId == ATTR_NAME || attrName == "name" || attrName == "targetActivity")
                        if (isClassNameAttr && currentStrVal != null && currentStrVal.isNotBlank()) {
                            val expanded = when {
                                currentStrVal.startsWith(".") -> "$detectedOriginalPackage$currentStrVal"
                                !currentStrVal.contains(".") -> "$detectedOriginalPackage.$currentStrVal"
                                else -> null
                            }
                            if (expanded != null) {
                                val expIdx = addOrGetNewString(expanded)
                                tailBuf.putInt(attrPos + 8, expIdx)
                                tailBuf.put(attrPos + 15, TYPE_STRING.toByte())
                                tailBuf.putInt(attrPos + 16, expIdx)
                            }
                        }
                    }

                    // Rule E: <provider android:authorities="..."> -> append .cloneN to avoid conflict
                    if (elemName == "provider" && (attrResId == ATTR_AUTHORITIES || attrName == "authorities")) {
                        if (currentStrVal != null && currentStrVal.isNotBlank()) {
                            val mutatedAuth = currentStrVal.split(";")
                                .joinToString(";") { part -> "${part.trim()}.clone$cloneNumber" }
                            val authIdx = addOrGetNewString(mutatedAuth)
                            tailBuf.putInt(attrPos + 8, authIdx)
                            tailBuf.put(attrPos + 15, TYPE_STRING.toByte())
                            tailBuf.putInt(attrPos + 16, authIdx)
                        }
                    }

                    // Rule F: Custom package permissions (<permission>, <uses-permission>)
                    if (elemName in setOf("permission", "uses-permission", "uses-permission-sdk-23")) {
                        if ((attrResId == ATTR_NAME || attrName == "name") && currentStrVal != null) {
                            if (currentStrVal.contains(detectedOriginalPackage)) {
                                val mutatedPerm = currentStrVal.replace(detectedOriginalPackage, newPackage)
                                val permIdx = addOrGetNewString(mutatedPerm)
                                tailBuf.putInt(attrPos + 8, permIdx)
                                tailBuf.put(attrPos + 15, TYPE_STRING.toByte())
                                tailBuf.putInt(attrPos + 16, permIdx)
                            }
                        }
                    }
                }
            }

            pos += chunkSize
        }

        // 3. Rebuild the String Pool Chunk with all original + newly appended strings
        val rebuiltStringPool = buildStringPoolChunk(
            spHeaderSize = spHeaderSize,
            originalHeaderBytes = manifestBytes.copyOfRange(spOffset, spOffset + spHeaderSize),
            strings = strings,
            styleCount = styleCount,
            styleOffsetsBytes = styleOffsetsBytes,
            styleDataBytes = styleDataBytes,
            isUtf8 = isUtf8,
            originalFlags = spFlags
        )

        // 4. Assemble final AXML file
        val totalSize = fileHeaderSize + rebuiltStringPool.size + tailBytes.size
        val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_TYPE.toShort())
        out.putShort(fileHeaderSize.toShort())
        out.putInt(totalSize)
        if (fileHeaderSize > 8) {
            out.put(manifestBytes, 8, fileHeaderSize - 8)
        }
        out.put(rebuiltStringPool)
        out.put(tailBytes)
        return out.array()
    }

    private fun buildStringPoolChunk(
        spHeaderSize: Int,
        originalHeaderBytes: ByteArray,
        strings: List<String>,
        styleCount: Int,
        styleOffsetsBytes: ByteArray,
        styleDataBytes: ByteArray,
        isUtf8: Boolean,
        originalFlags: Int
    ): ByteArray {
        val newStringCount = strings.size
        val stringDataOut = ByteArrayOutputStream()
        val stringOffsets = IntArray(newStringCount)

        for (i in 0 until newStringCount) {
            stringOffsets[i] = stringDataOut.size()
            val s = strings[i]
            if (isUtf8) {
                encodeUtf8String(s, stringDataOut)
            } else {
                encodeUtf16String(s, stringDataOut)
            }
        }

        // Pad stringDataOut to 4-byte alignment
        while (stringDataOut.size() % 4 != 0) {
            stringDataOut.write(0)
        }

        val stringDataBytes = stringDataOut.toByteArray()
        val newStringsStart = spHeaderSize + (newStringCount * 4) + styleOffsetsBytes.size
        val newStylesStart = if (styleCount > 0 && styleDataBytes.isNotEmpty()) {
            newStringsStart + stringDataBytes.size
        } else 0

        val totalChunkSize = newStringsStart + stringDataBytes.size + styleDataBytes.size
        val chunkBuf = ByteBuffer.allocate(totalChunkSize).order(ByteOrder.LITTLE_ENDIAN)

        // Write header
        chunkBuf.put(originalHeaderBytes)
        chunkBuf.putShort(0, RES_STRING_POOL_TYPE.toShort())
        chunkBuf.putShort(2, spHeaderSize.toShort())
        chunkBuf.putInt(4, totalChunkSize)
        chunkBuf.putInt(8, newStringCount)
        chunkBuf.putInt(12, styleCount)
        // Clear SORTED_FLAG because we appended strings at the end
        chunkBuf.putInt(16, originalFlags and SORTED_FLAG.inv())
        chunkBuf.putInt(20, newStringsStart)
        chunkBuf.putInt(24, newStylesStart)

        chunkBuf.position(spHeaderSize)
        for (offset in stringOffsets) {
            chunkBuf.putInt(offset)
        }
        if (styleOffsetsBytes.isNotEmpty()) {
            chunkBuf.put(styleOffsetsBytes)
        }
        chunkBuf.put(stringDataBytes)
        if (styleDataBytes.isNotEmpty()) {
            chunkBuf.put(styleDataBytes)
        }
        return chunkBuf.array()
    }

    private fun decodeUtf8String(data: ByteArray, offset: Int): String {
        var p = offset
        if (p >= data.size) return ""
        // 1. UTF-16 length (1 or 2 bytes)
        val u16b0 = data[p].toInt() and 0xFF
        p += if ((u16b0 and 0x80) != 0) 2 else 1
        if (p >= data.size) return ""

        // 2. UTF-8 byte length (1 or 2 bytes)
        val u8b0 = data[p].toInt() and 0xFF
        val utf8Len: Int
        if ((u8b0 and 0x80) != 0) {
            val u8b1 = data[p + 1].toInt() and 0xFF
            utf8Len = ((u8b0 and 0x7F) shl 8) or u8b1
            p += 2
        } else {
            utf8Len = u8b0
            p += 1
        }
        if (p + utf8Len > data.size || utf8Len < 0) return ""
        return String(data, p, utf8Len, StandardCharsets.UTF_8)
    }

    private fun encodeUtf8String(str: String, out: ByteArrayOutputStream) {
        val utf16Len = str.length
        val utf8Bytes = str.toByteArray(StandardCharsets.UTF_8)
        val utf8Len = utf8Bytes.size

        if (utf16Len >= 0x80) {
            out.write(0x80 or ((utf16Len shr 8) and 0x7F))
            out.write(utf16Len and 0xFF)
        } else {
            out.write(utf16Len)
        }

        if (utf8Len >= 0x80) {
            out.write(0x80 or ((utf8Len shr 8) and 0x7F))
            out.write(utf8Len and 0xFF)
        } else {
            out.write(utf8Len)
        }

        out.write(utf8Bytes)
        out.write(0) // Null terminator
    }

    private fun decodeUtf16String(data: ByteArray, offset: Int): String {
        var p = offset
        if (p + 2 > data.size) return ""
        val w0 = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
        val charLen: Int
        if ((w0 and 0x8000) != 0) {
            val w1 = (data[p + 2].toInt() and 0xFF) or ((data[p + 3].toInt() and 0xFF) shl 8)
            charLen = ((w0 and 0x7FFF) shl 16) or w1
            p += 4
        } else {
            charLen = w0
            p += 2
        }
        val byteLen = charLen * 2
        if (p + byteLen > data.size || byteLen < 0) return ""
        return String(data, p, byteLen, StandardCharsets.UTF_16LE)
    }

    private fun encodeUtf16String(str: String, out: ByteArrayOutputStream) {
        val charLen = str.length
        val utf16Bytes = str.toByteArray(StandardCharsets.UTF_16LE)
        if (charLen >= 0x8000) {
            val high = 0x8000 or ((charLen shr 16) and 0x7FFF)
            val low = charLen and 0xFFFF
            out.write(high and 0xFF)
            out.write((high shr 8) and 0xFF)
            out.write(low and 0xFF)
            out.write((low shr 8) and 0xFF)
        } else {
            out.write(charLen and 0xFF)
            out.write((charLen shr 8) and 0xFF)
        }
        out.write(utf16Bytes)
        out.write(0)
        out.write(0) // 2-byte null terminator
    }
}
