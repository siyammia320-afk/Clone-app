package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class OmniSpaceRepository(
    private val dao: OmniSpaceDao,
    context: Context
) {
    private val prefs = context.getSharedPreferences("omnispace_security_prefs", Context.MODE_PRIVATE)

    val allClonedApps: Flow<List<ClonedAppEntity>> = dao.getAllClonedApps()
    val allSpaces: Flow<List<SpaceCategoryEntity>> = dao.getAllSpaces()

    suspend fun ensureDefaultSpaces() {
        if (dao.getSpaceCount() == 0) {
            val defaults = listOf(
                SpaceCategoryEntity(
                    spaceId = "space_1",
                    spaceName = "Space 1 • Primary",
                    spaceSubtitle = "Daily second accounts & social clones",
                    accentColorHex = 0xFF00E5FFL,
                    isSecretVault = false,
                    createdAt = 1L
                ),
                SpaceCategoryEntity(
                    spaceId = "space_2",
                    spaceName = "Space 2 • Business",
                    spaceSubtitle = "Work & business multi-accounts",
                    accentColorHex = 0xFF7C4DFFL,
                    isSecretVault = false,
                    createdAt = 2L
                ),
                SpaceCategoryEntity(
                    spaceId = "space_gaming",
                    spaceName = "Space 3 • Multi-Farm",
                    spaceSubtitle = "10x batch clones & gaming accounts",
                    accentColorHex = 0xFF00E676L,
                    isSecretVault = false,
                    createdAt = 3L
                ),
                SpaceCategoryEntity(
                    spaceId = "space_vault",
                    spaceName = "Secret Vault",
                    spaceSubtitle = "PIN-locked private cloned apps",
                    accentColorHex = 0xFFFF5252L,
                    isSecretVault = true,
                    createdAt = 4L
                )
            )
            defaults.forEach { dao.insertSpace(it) }
        }
    }

    suspend fun getNextCloneNumber(packageName: String): Int {
        val currentMax = dao.getMaxCloneNumberForPackage(packageName) ?: 0
        return currentMax + 1
    }

    suspend fun getClonesForPackage(packageName: String): List<ClonedAppEntity> {
        return dao.getClonesForPackage(packageName)
    }

    suspend fun insertClone(clone: ClonedAppEntity): Long {
        return dao.insertClonedApp(clone)
    }

    suspend fun updateClone(clone: ClonedAppEntity) {
        dao.updateClonedApp(clone)
    }

    suspend fun deleteClone(id: Long) {
        dao.deleteClonedAppById(id)
    }

    suspend fun createCustomSpace(name: String, subtitle: String, colorHex: Long, isVault: Boolean) {
        val id = "space_custom_${System.currentTimeMillis()}"
        dao.insertSpace(
            SpaceCategoryEntity(
                spaceId = id,
                spaceName = name.trim(),
                spaceSubtitle = subtitle.trim().ifEmpty { "Custom isolated multi-account space" },
                accentColorHex = colorHex,
                isSecretVault = isVault
            )
        )
    }

    fun getVaultPin(): String? = prefs.getString("vault_pin", null)

    fun setVaultPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            prefs.edit().remove("vault_pin").apply()
        } else {
            prefs.edit().putString("vault_pin", pin.trim()).apply()
        }
    }

    fun isGlobalLockEnabled(): Boolean = prefs.getBoolean("global_lock_enabled", false)

    fun setGlobalLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("global_lock_enabled", enabled).apply()
    }
}
