package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClonedAppEntity
import com.example.data.SpaceCategoryEntity
import com.example.engine.ApkCloneBuilder
import com.example.ui.components.AppIconWithBadge

@Composable
fun SpacesDashboardScreen(
    clonedApps: List<ClonedAppEntity>,
    spaces: List<SpaceCategoryEntity>,
    selectedSpaceId: String,
    installedAppsCount: Int,
    vaultPin: String?,
    isVaultUnlocked: Boolean,
    onSelectSpace: (String) -> Unit,
    onOpenNewSpaceModal: () -> Unit,
    onNavigateToCloner: () -> Unit,
    onLaunchClone: (ClonedAppEntity) -> Unit,
    onInstallCloneApk: (ClonedAppEntity) -> Unit,
    onManageClone: (ClonedAppEntity) -> Unit,
    onUnlockVault: (String) -> Boolean
) {
    val activeSpace = spaces.find { it.spaceId == selectedSpaceId }
    val filteredClones = remember(clonedApps, selectedSpaceId) {
        if (selectedSpaceId == "ALL") {
            clonedApps
        } else {
            clonedApps.filter { it.spaceId == selectedSpaceId }
        }
    }

    val totalStorage = remember(clonedApps) {
        clonedApps.sumOf { it.storageBytes }
    }

    val requiresVaultPin = activeSpace?.isSecretVault == true &&
        !vaultPin.isNullOrEmpty() &&
        !isVaultUnlocked

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("spaces_dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Multi-Space Overview Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F2942),
                                Color(0xFF151C33),
                                Color(0xFF1F1638)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OMNISPACE MULTI-ENGINE",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Unlimited Native App Cloner",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = onNavigateToCloner,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF04131A)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("hero_add_clone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clone App", fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPill(
                            title = "Active Clones",
                            value = "${clonedApps.size}",
                            accent = Color(0xFF00E5FF),
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            title = "Phone Apps",
                            value = "$installedAppsCount",
                            accent = Color(0xFF00E676),
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            title = "Sandbox Data",
                            value = ApkCloneBuilder.formatBytes(totalStorage),
                            accent = Color(0xFF7C4DFF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Space Category Selector Row
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Isolated Spaces",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "+ New Space",
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenNewSpaceModal() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("create_space_button")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedSpaceId == "ALL",
                        onClick = { onSelectSpace("ALL") },
                        label = {
                            Text(
                                text = "All Clones (${clonedApps.size})",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF),
                            selectedLabelColor = Color(0xFF04131A)
                        )
                    )

                    spaces.forEach { space ->
                        val countInSpace = clonedApps.count { it.spaceId == space.spaceId }
                        FilterChip(
                            selected = selectedSpaceId == space.spaceId,
                            onClick = { onSelectSpace(space.spaceId) },
                            leadingIcon = if (space.isSecretVault) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Vault Lock",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = "${space.spaceName} ($countInSpace)",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(space.accentColorHex),
                                selectedLabelColor = Color(0xFF04131A)
                            )
                        )
                    }
                }
            }
        }

        // If Secret Vault is locked, show PIN unlock card
        if (requiresVaultPin) {
            item {
                VaultPinLockCard(onUnlockVault = onUnlockVault)
            }
        } else if (filteredClones.isEmpty()) {
            // Empty State with Clear Action
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Cloned Apps in This Space Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select any installed app from your phone and create 1x, 5x, 10x, or unlimited isolated clones with separate login accounts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateToCloner,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF04131A)
                            ),
                            modifier = Modifier.testTag("empty_state_clone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select App to Clone (1x–20x)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Active Cloned App Cards
            items(filteredClones, key = { it.id }) { clone ->
                ClonedAppCard(
                    clone = clone,
                    spaceName = spaces.find { it.spaceId == clone.spaceId }?.spaceName ?: "Space 1",
                    onLaunch = { onLaunchClone(clone) },
                    onInstallApk = { onInstallCloneApk(clone) },
                    onManage = { onManageClone(clone) }
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF090D16).copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = title,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ClonedAppCard(
    clone: ClonedAppEntity,
    spaceName: String,
    onLaunch: () -> Unit,
    onInstallApk: () -> Unit,
    onManage: () -> Unit
) {
    val badgeColor = Color(clone.badgeColorHex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .clickable { onLaunch() }
            .testTag("cloned_app_card_${clone.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIconWithBadge(
                    packageName = clone.originalPackageName,
                    appName = clone.customName,
                    cloneNumber = clone.cloneNumber,
                    badgeColorHex = clone.badgeColorHex,
                    iconSize = 52.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = clone.customName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = spaceName,
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = clone.accountTag,
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Pkg: ${clone.clonedPackageName} • ID: ${clone.virtualDeviceId}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onManage) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Manage Clone Settings",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onLaunch,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF04131A)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Clone #${clone.cloneNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = onInstallApk,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Install Clone APK",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultPinLockCard(onUnlockVault: (String) -> Boolean) {
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Vault Locked",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Secret Vault is Locked",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                text = "Enter your Vault PIN to access private cloned apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = {
                    pinInput = it
                    errorText = null
                },
                label = { Text("Enter PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (errorText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = errorText!!, color = Color(0xFFFF5252), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    if (!onUnlockVault(pinInput)) {
                        errorText = "Incorrect PIN. Try again."
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock Secret Vault", fontWeight = FontWeight.Bold)
            }
        }
    }
}
