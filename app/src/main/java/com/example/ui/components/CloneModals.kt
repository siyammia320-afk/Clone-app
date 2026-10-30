package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ClonedAppEntity
import com.example.data.SpaceCategoryEntity
import com.example.engine.ApkCloneBuilder
import com.example.engine.InstalledAppInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchCloneDialog(
    appInfo: InstalledAppInfo,
    existingClonesCount: Int,
    spaces: List<SpaceCategoryEntity>,
    defaultSpaceId: String,
    onDismiss: () -> Unit,
    onConfirmClone: (
        count: Int,
        customName: String,
        accountPrefix: String,
        spaceId: String,
        badgeColorHex: Long,
        buildApk: Boolean
    ) -> Unit
) {
    var cloneCount by remember { mutableIntStateOf(1) }
    var customName by remember { mutableStateOf(appInfo.appName) }
    var accountPrefix by remember { mutableStateOf("") }
    var selectedSpaceId by remember {
        mutableStateOf(
            if (defaultSpaceId == "ALL") {
                spaces.firstOrNull()?.spaceId ?: "space_1"
            } else {
                defaultSpaceId
            }
        )
    }
    var selectedColor by remember {
        mutableLongStateOf(
            ApkCloneBuilder.badgeColorPalette[existingClonesCount % ApkCloneBuilder.badgeColorPalette.size]
        )
    }
    var buildStandaloneApk by remember { mutableStateOf(true) }

    val nextStartNumber = existingClonesCount + 1
    val presetCounts = listOf(1, 2, 3, 5, 10, 15, 20)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF283548), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header with Real App Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIconWithBadge(
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        cloneNumber = nextStartNumber,
                        badgeColorHex = selectedColor,
                        iconSize = 56.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appInfo.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = appInfo.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Existing clones: $existingClonesCount • Next: #$nextStartNumber",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(14.dp))

                // How many clones selector (1x to 20x batch or unlimited total)
                Text(
                    text = "How many clones to create now? (Unlimited total supported)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    presetCounts.forEach { preset ->
                        FilterChip(
                            selected = cloneCount == preset,
                            onClick = { cloneCount = preset },
                            label = {
                                Text(
                                    text = "${preset}x",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF),
                                selectedLabelColor = Color(0xFF04131A)
                            ),
                            modifier = Modifier.testTag("batch_preset_$preset")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fine-grained +/- counter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090D16))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (cloneCount == 1) {
                            "Creating Clone #$nextStartNumber"
                        } else {
                            "Batch: Clone #$nextStartNumber to #${nextStartNumber + cloneCount - 1}"
                        },
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (cloneCount > 1) cloneCount-- },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Clone Count",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "$cloneCount",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        IconButton(
                            onClick = { if (cloneCount < 50) cloneCount++ },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Clone Count",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Clone Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = accountPrefix,
                    onValueChange = { accountPrefix = it },
                    label = { Text("Account Note / ID Tag (e.g. Personal, 2nd ID, Shop)") },
                    placeholder = { Text("Account #$nextStartNumber") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_account_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Target Space Selection
                Text(
                    text = "Assign to Space",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    spaces.forEach { space ->
                        FilterChip(
                            selected = selectedSpaceId == space.spaceId,
                            onClick = { selectedSpaceId = space.spaceId },
                            label = { Text(space.spaceName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(space.accentColorHex),
                                selectedLabelColor = Color(0xFF04131A)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Badge Color Picker
                Text(
                    text = "Clone Badge Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ApkCloneBuilder.badgeColorPalette.forEach { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected Color",
                                    tint = Color(0xFF050A14),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Standalone Patched APK option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090D16))
                        .clickable { buildStandaloneApk = !buildStandaloneApk }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = buildStandaloneApk,
                        onCheckedChange = { buildStandaloneApk = it }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Generate Separate Package APK",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "New package ID: ${
                                ApkCloneBuilder.generateClonedPackageName(
                                    appInfo.packageName,
                                    nextStartNumber
                                )
                            }",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirmClone(
                                cloneCount,
                                customName,
                                accountPrefix,
                                selectedSpaceId,
                                selectedColor,
                                buildStandaloneApk
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF04131A)
                        ),
                        modifier = Modifier.testTag("confirm_create_clone_button")
                    ) {
                        Text(
                            text = if (cloneCount == 1) "Create Clone #$nextStartNumber" else "Create $cloneCount Clones",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageCloneDialog(
    clone: ClonedAppEntity,
    spaces: List<SpaceCategoryEntity>,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onInstallApk: () -> Unit,
    onShareApk: () -> Unit,
    onResetSandbox: () -> Unit,
    onDelete: () -> Unit,
    onSaveChanges: (String, String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf(clone.customName) }
    var accountTag by remember { mutableStateOf(clone.accountTag) }
    var spaceId by remember { mutableStateOf(clone.spaceId) }
    var badgeColor by remember { mutableLongStateOf(clone.badgeColorHex) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF283548), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconWithBadge(
                        packageName = clone.originalPackageName,
                        appName = clone.customName,
                        cloneNumber = clone.cloneNumber,
                        badgeColorHex = badgeColor,
                        iconSize = 54.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = clone.customName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Clone #${clone.cloneNumber} • ${clone.clonedPackageName}",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Device ID: ${clone.virtualDeviceId} • ${ApkCloneBuilder.formatBytes(clone.storageBytes)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Direct Actions Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onLaunch()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF04131A)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open App", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onInstallApk()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install APK", color = Color(0xFF00E676))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Clone Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = accountTag,
                    onValueChange = { accountTag = it },
                    label = { Text("Logged-in Account Note / Phone / Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Move to Space",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    spaces.forEach { sp ->
                        FilterChip(
                            selected = spaceId == sp.spaceId,
                            onClick = { spaceId = sp.spaceId },
                            label = { Text(sp.spaceName, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Utility Buttons: Export APK, Reset Account Session, Delete Clone
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShareApk()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export APK", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onResetSandbox()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Session", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Clone", color = Color(0xFFFF5252))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onSaveChanges(name, accountTag, spaceId, badgeColor)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF04131A)
                            )
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Long, Boolean) -> Unit
) {
    var spaceName by remember { mutableStateOf("") }
    var spaceSubtitle by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(ApkCloneBuilder.badgeColorPalette[0]) }
    var isVault by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF111827),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF283548), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Create New Isolated Space",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = spaceName,
                    onValueChange = { spaceName = it },
                    label = { Text("Space Name (e.g. Space 4 • Crypto / VIP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = spaceSubtitle,
                    onValueChange = { spaceSubtitle = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ApkCloneBuilder.badgeColorPalette.take(6).forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (selectedColor == colorHex) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isVault = !isVault }
                ) {
                    Checkbox(checked = isVault, onCheckedChange = { isVault = it })
                    Text(
                        text = "Protect this Space with Secret Vault PIN Lock",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (spaceName.isNotBlank()) {
                                onCreate(spaceName, spaceSubtitle, selectedColor, isVault)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF04131A)
                        )
                    ) {
                        Text("Create Space", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
