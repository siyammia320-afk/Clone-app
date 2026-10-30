package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ClonedAppEntity
import com.example.data.OmniSpaceDatabase
import com.example.data.OmniSpaceRepository
import com.example.data.SpaceCategoryEntity
import com.example.engine.ApkCloneBuilder
import com.example.engine.InstalledAppInfo
import com.example.engine.InstalledAppsScanner
import com.example.engine.SystemDualProfileStatus
import com.example.engine.WorkProfileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OmniSpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OmniSpaceRepository

    val clonedApps: StateFlow<List<ClonedAppEntity>>
    val spaces: StateFlow<List<SpaceCategoryEntity>>

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isScanningApps = MutableStateFlow(true)
    val isScanningApps: StateFlow<Boolean> = _isScanningApps.asStateFlow()

    private val _selectedSpaceId = MutableStateFlow("ALL")
    val selectedSpaceId: StateFlow<String> = _selectedSpaceId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _isCloning = MutableStateFlow(false)
    val isCloning: StateFlow<Boolean> = _isCloning.asStateFlow()

    private val _cloningProgressText = MutableStateFlow<String?>(null)
    val cloningProgressText: StateFlow<String?> = _cloningProgressText.asStateFlow()

    private val _vaultPin = MutableStateFlow<String?>(null)
    val vaultPin: StateFlow<String?> = _vaultPin.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _workProfileStatus = MutableStateFlow(
        SystemDualProfileStatus(
            isWorkProfileActive = false,
            isProvisioningSupported = false,
            profileUsersCount = 1,
            crossProfileLabel = "Checking system profiles…"
        )
    )
    val workProfileStatus: StateFlow<SystemDualProfileStatus> = _workProfileStatus.asStateFlow()

    init {
        val dao = OmniSpaceDatabase.getDatabase(application).omniSpaceDao()
        repository = OmniSpaceRepository(dao, application)

        clonedApps = repository.allClonedApps.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        spaces = repository.allSpaces.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        _vaultPin.value = repository.getVaultPin()
        _isVaultUnlocked.value = _vaultPin.value.isNullOrEmpty()

        viewModelScope.launch {
            repository.ensureDefaultSpaces()
            refreshSystemStatus()
            scanDeviceApps()
        }
    }

    fun scanDeviceApps() {
        viewModelScope.launch {
            _isScanningApps.value = true
            val context = getApplication<Application>()
            val scanned = InstalledAppsScanner.scanLaunchableApps(context)
            _installedApps.value = scanned
            _isScanningApps.value = false
        }
    }

    fun refreshSystemStatus() {
        _workProfileStatus.value = WorkProfileHelper.checkStatus(getApplication())
    }

    fun selectSpace(spaceId: String) {
        _selectedSpaceId.value = spaceId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    /**
     * Creates 1 to N clones of an installed app in batch (supports unlimited total clones per app).
     */
    fun createClonesBatch(
        appInfo: InstalledAppInfo,
        countToCreate: Int,
        customBaseName: String,
        accountTagPrefix: String,
        targetSpaceId: String,
        badgeColorHex: Long,
        generatePatchedApk: Boolean,
        onComplete: () -> Unit = {}
    ) {
        if (_isCloning.value) return
        viewModelScope.launch {
            _isCloning.value = true
            val context = getApplication<Application>()
            val safeCount = countToCreate.coerceIn(1, 50)
            val startCloneNum = repository.getNextCloneNumber(appInfo.packageName)

            for (i in 0 until safeCount) {
                val cloneNum = startCloneNum + i
                val label = if (safeCount == 1 && customBaseName.isNotBlank()) {
                    customBaseName.trim()
                } else {
                    "${customBaseName.ifBlank { appInfo.appName }} #$cloneNum"
                }
                val accTag = if (accountTagPrefix.isBlank()) {
                    "Account #$cloneNum"
                } else if (safeCount == 1) {
                    accountTagPrefix.trim()
                } else {
                    "${accountTagPrefix.trim()} #$cloneNum"
                }

                _cloningProgressText.value =
                    "Building Clone #$cloneNum (${i + 1}/$safeCount) for ${appInfo.appName}…"

                // Only generate full patched APK binary on the first clone in huge batches to keep batch instant,
                // while generating on-demand when Install/Export is tapped.
                val buildApkNow = generatePatchedApk && (safeCount <= 3 || i == 0)

                val entity = ApkCloneBuilder.buildCloneInstance(
                    context = context,
                    appInfo = appInfo,
                    cloneNumber = cloneNum,
                    customName = label,
                    accountTag = accTag,
                    spaceId = targetSpaceId,
                    badgeColorHex = badgeColorHex,
                    generateApkPackage = buildApkNow
                )
                repository.insertClone(entity)
            }

            _cloningProgressText.value = null
            _isCloning.value = false
            Toast.makeText(
                context,
                "Created $safeCount clone(s) of ${appInfo.appName}!",
                Toast.LENGTH_SHORT
            ).show()
            onComplete()
        }
    }

    fun launchClone(clone: ClonedAppEntity) {
        viewModelScope.launch {
            val updated = clone.copy(
                lastLaunchedAt = System.currentTimeMillis(),
                launchCount = clone.launchCount + 1
            )
            repository.updateClone(updated)
            ApkCloneBuilder.launchClonedAppSession(getApplication(), updated)
        }
    }

    fun installStandaloneCloneApk(clone: ClonedAppEntity) {
        viewModelScope.launch {
            _isCloning.value = true
            _cloningProgressText.value =
                "Building & signing standalone package ${clone.clonedPackageName}.apk…"
            val context = getApplication<Application>()
            val result = ApkCloneBuilder.ensureAndInstallCloneApk(context, clone)
            _cloningProgressText.value = null
            _isCloning.value = false

            result.onSuccess { apkFile ->
                repository.updateClone(
                    clone.copy(
                        apkOutputPath = apkFile.absolutePath,
                        storageBytes = apkFile.length()
                    )
                )
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "APK Package Installer error: ${err.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun exportCloneApk(clone: ClonedAppEntity) {
        viewModelScope.launch {
            _isCloning.value = true
            _cloningProgressText.value = "Preparing ${clone.clonedPackageName}.apk for export…"
            val context = getApplication<Application>()
            val result = ApkCloneBuilder.shareCloneApk(context, clone)
            _cloningProgressText.value = null
            _isCloning.value = false

            result.onFailure { err ->
                Toast.makeText(
                    context,
                    "Export failed: ${err.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun updateCloneDetails(
        clone: ClonedAppEntity,
        newName: String,
        newAccountTag: String,
        newSpaceId: String,
        newBadgeColorHex: Long
    ) {
        viewModelScope.launch {
            repository.updateClone(
                clone.copy(
                    customName = newName.trim().ifEmpty { clone.customName },
                    accountTag = newAccountTag.trim().ifEmpty { "Account #${clone.cloneNumber}" },
                    spaceId = newSpaceId,
                    badgeColorHex = newBadgeColorHex
                )
            )
            Toast.makeText(getApplication(), "Updated ${newName.trim()}", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearCloneSandbox(clone: ClonedAppEntity) {
        viewModelScope.launch {
            val newSize = ApkCloneBuilder.resetCloneSandbox(clone)
            val newVirtualId = ApkCloneBuilder.generateVirtualDeviceId(clone.cloneNumber)
            repository.updateClone(
                clone.copy(
                    storageBytes = newSize,
                    virtualDeviceId = newVirtualId,
                    launchCount = 0
                )
            )
            Toast.makeText(
                getApplication(),
                "Sandbox session & Device ID reset for ${clone.customName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deleteClone(clone: ClonedAppEntity) {
        viewModelScope.launch {
            ApkCloneBuilder.removeCloneFiles(clone)
            repository.deleteClone(clone.id)
            Toast.makeText(
                getApplication(),
                "Deleted ${clone.customName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun createNewSpace(name: String, subtitle: String, colorHex: Long, isSecretVault: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createCustomSpace(name, subtitle, colorHex, isSecretVault)
            Toast.makeText(getApplication(), "Created space: $name", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveVaultPin(pin: String?) {
        repository.setVaultPin(pin)
        _vaultPin.value = repository.getVaultPin()
        _isVaultUnlocked.value = _vaultPin.value.isNullOrEmpty()
    }

    fun attemptUnlockVault(enteredPin: String): Boolean {
        val expected = _vaultPin.value
        if (expected == null || enteredPin == expected) {
            _isVaultUnlocked.value = true
            return true
        }
        return false
    }

    fun lockVaultNow() {
        if (!_vaultPin.value.isNullOrEmpty()) {
            _isVaultUnlocked.value = false
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OmniSpaceViewModel(application) as T
        }
    }
}
