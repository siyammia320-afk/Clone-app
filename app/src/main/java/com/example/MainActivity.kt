package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ClonedAppEntity
import com.example.engine.InstalledAppInfo
import com.example.ui.OmniSpaceViewModel
import com.example.ui.components.BatchCloneDialog
import com.example.ui.components.CreateSpaceDialog
import com.example.ui.components.ManageCloneDialog
import com.example.ui.screens.AppClonerScreen
import com.example.ui.screens.SpacesDashboardScreen
import com.example.ui.screens.VaultAndSystemScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                val omniViewModel: OmniSpaceViewModel = viewModel(
                    factory = OmniSpaceViewModel.Factory(application)
                )
                OmniSpaceMainApp(viewModel = omniViewModel)
            }
        }
    }
}

@Composable
fun OmniSpaceMainApp(viewModel: OmniSpaceViewModel) {
    val clonedApps by viewModel.clonedApps.collectAsStateWithLifecycle()
    val spaces by viewModel.spaces.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isScanningApps by viewModel.isScanningApps.collectAsStateWithLifecycle()
    val selectedSpaceId by viewModel.selectedSpaceId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val isCloning by viewModel.isCloning.collectAsStateWithLifecycle()
    val cloningProgressText by viewModel.cloningProgressText.collectAsStateWithLifecycle()
    val vaultPin by viewModel.vaultPin.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val workProfileStatus by viewModel.workProfileStatus.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var appToCloneModal by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var cloneToManageModal by remember { mutableStateOf<ClonedAppEntity?>(null) }
    var showCreateSpaceModal by remember { mutableStateOf(false) }

    // Handle back button on secondary tabs to return to Dual Space home
    BackHandler(enabled = currentTab != 0) {
        currentTab = 0
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OmniSpaceTopBar(
                totalClonesCount = clonedApps.size,
                onQuickCloneClick = { currentTab = 1 }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0D1320),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "My Spaces"
                        )
                    },
                    label = { Text("My Spaces", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF04131A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_spaces")
                )

                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ControlPointDuplicate,
                            contentDescription = "Clone Apps"
                        )
                    },
                    label = { Text("App Cloner (1–20x)", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF04131A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_cloner")
                )

                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = {
                        viewModel.refreshSystemStatus()
                        currentTab = 2
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "APK & Vault"
                        )
                    },
                    label = { Text("APK & Vault", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF04131A),
                        selectedTextColor = Color(0xFF00E5FF),
                        indicatorColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("nav_tab_vault")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> SpacesDashboardScreen(
                    clonedApps = clonedApps,
                    spaces = spaces,
                    selectedSpaceId = selectedSpaceId,
                    installedAppsCount = installedApps.size,
                    vaultPin = vaultPin,
                    isVaultUnlocked = isVaultUnlocked,
                    onSelectSpace = { viewModel.selectSpace(it) },
                    onOpenNewSpaceModal = { showCreateSpaceModal = true },
                    onNavigateToCloner = { currentTab = 1 },
                    onLaunchClone = { viewModel.launchClone(it) },
                    onInstallCloneApk = { viewModel.installStandaloneCloneApk(it) },
                    onManageClone = { cloneToManageModal = it },
                    onUnlockVault = { viewModel.attemptUnlockVault(it) }
                )

                1 -> AppClonerScreen(
                    installedApps = installedApps,
                    clonedApps = clonedApps,
                    isScanning = isScanningApps,
                    searchQuery = searchQuery,
                    showSystemApps = showSystemApps,
                    onSearchChange = { viewModel.updateSearchQuery(it) },
                    onToggleSystemApps = { viewModel.toggleShowSystemApps(it) },
                    onRefreshApps = { viewModel.scanDeviceApps() },
                    onSelectAppToClone = { appToCloneModal = it }
                )

                2 -> VaultAndSystemScreen(
                    clonedApps = clonedApps,
                    vaultPin = vaultPin,
                    workProfileStatus = workProfileStatus,
                    onSaveVaultPin = { viewModel.saveVaultPin(it) },
                    onLockVaultNow = { viewModel.lockVaultNow() },
                    onInstallCloneApk = { viewModel.installStandaloneCloneApk(it) },
                    onExportCloneApk = { viewModel.exportCloneApk(it) }
                )
            }
        }
    }

    // Batch Clone Creation Dialog
    appToCloneModal?.let { selectedApp ->
        val existingCount = clonedApps.count { it.originalPackageName == selectedApp.packageName }
        BatchCloneDialog(
            appInfo = selectedApp,
            existingClonesCount = existingCount,
            spaces = spaces,
            defaultSpaceId = selectedSpaceId,
            onDismiss = { appToCloneModal = null },
            onConfirmClone = { count, customName, accountPrefix, spaceId, badgeColor, buildApk ->
                appToCloneModal = null
                viewModel.createClonesBatch(
                    appInfo = selectedApp,
                    countToCreate = count,
                    customBaseName = customName,
                    accountTagPrefix = accountPrefix,
                    targetSpaceId = spaceId,
                    badgeColorHex = badgeColor,
                    generatePatchedApk = buildApk,
                    onComplete = {
                        viewModel.selectSpace(spaceId)
                        currentTab = 0
                    }
                )
            }
        )
    }

    // Clone Instance Management Dialog
    cloneToManageModal?.let { selectedClone ->
        ManageCloneDialog(
            clone = selectedClone,
            spaces = spaces,
            onDismiss = { cloneToManageModal = null },
            onLaunch = { viewModel.launchClone(selectedClone) },
            onInstallApk = { viewModel.installStandaloneCloneApk(selectedClone) },
            onShareApk = { viewModel.exportCloneApk(selectedClone) },
            onResetSandbox = { viewModel.clearCloneSandbox(selectedClone) },
            onDelete = { viewModel.deleteClone(selectedClone) },
            onSaveChanges = { newName, newAccTag, newSpace, newColor ->
                viewModel.updateCloneDetails(selectedClone, newName, newAccTag, newSpace, newColor)
            }
        )
    }

    // New Space Creation Dialog
    if (showCreateSpaceModal) {
        CreateSpaceDialog(
            onDismiss = { showCreateSpaceModal = false },
            onCreate = { name, subtitle, color, isVault ->
                viewModel.createNewSpace(name, subtitle, color, isVault)
            }
        )
    }

    // Progress Dialog when generating clones / rebuilding APK packages
    if (isCloning) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111827),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "OmniSpace Clone Engine",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cloningProgressText ?: "Generating isolated clone environment…",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OmniSpaceTopBar(
    totalClonesCount: Int,
    onQuickCloneClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090D16))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.16f))
                    .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "OmniSpace Logo",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "OmniSpace Cloner",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "$totalClonesCount Isolated Native Clones Ready",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        Surface(
            onClick = onQuickCloneClick,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.testTag("top_bar_quick_clone_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Clone",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
