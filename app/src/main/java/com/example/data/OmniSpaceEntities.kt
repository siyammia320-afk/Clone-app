package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloned_apps")
data class ClonedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPackageName: String,
    val originalAppName: String,
    val clonedPackageName: String,
    val cloneNumber: Int,
    val customName: String,
    val accountTag: String,
    val spaceId: String,
    val badgeColorHex: Long,
    val virtualDeviceId: String,
    val virtualModelName: String,
    val apkOutputPath: String,
    val sandboxDirPath: String,
    val storageBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long = 0L,
    val launchCount: Int = 0
)

@Entity(tableName = "space_categories")
data class SpaceCategoryEntity(
    @PrimaryKey val spaceId: String,
    val spaceName: String,
    val spaceSubtitle: String,
    val accentColorHex: Long,
    val isSecretVault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
