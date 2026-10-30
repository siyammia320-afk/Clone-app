package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClonedAppEntity
import com.example.engine.SystemDualProfileStatus
import com.example.engine.WorkProfileHelper
import com.example.ui.components.AppIconWithBadge

@Composable
fun VaultAndSystemScreen(
    clonedApps: List<ClonedAppEntity>,
    vaultPin: String?,
    workProfileStatus: SystemDualProfileStatus,
    onSaveVaultPin: (String?) -> Unit,
    onLockVaultNow: () -> Unit,
    onInstallCloneApk: (ClonedAppEntity) -> Unit,
    onExportCloneApk: (ClonedAppEntity) -> Unit
) {
    val context = LocalContext.current
    var newPin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_and_system_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Secret Vault PIN Lock Management
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF283548), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Secret Vault PIN Security",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = if (vaultPin.isNullOrEmpty()) {
                                    "Status: No PIN Set (Set a PIN to lock Secret Vault space)"
                                } else {
                                    "Status: PIN Active • Secret Vault Protected"
                                },
                                color = if (vaultPin.isNullOrEmpty()) Color(0xFF94A3B8) else Color(0xFF00E676),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("Enter 4–8 digit Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_pin_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (newPin.isNotBlank()) {
                                    onSaveVaultPin(newPin)
                                    newPin = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF04131A)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save PIN", fontWeight = FontWeight.Bold)
                        }

                        if (!vaultPin.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = onLockVaultNow,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Lock Vault Now", color = Color(0xFFFFAB00))
                            }
                            OutlinedButton(
                                onClick = { onSaveVaultPin(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remove PIN", color = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Native Android Managed Work Profile Engine
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF283548), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Work Profile Dual Engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = workProfileStatus.crossProfileLabel,
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Android DevicePolicyManager creates an OS-level secondary user profile, while the APK Package Re-builder below lets you install 10x or unlimited standalone clones with distinct package names.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { WorkProfileHelper.launchWorkProfileProvisioning(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C4DFF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provision_work_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (workProfileStatus.isWorkProfileActive) {
                                "Re-Sync Android Work Profile"
                            } else {
                                "Provision Android Work Profile"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section 3: Standalone Cloned APK Package Builder List
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Standalone Patched Clone APKs (${clonedApps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        if (clonedApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF111827))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "No cloned packages generated yet. Go to the App Cloner tab to create 1 to 20+ clones of any installed app.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(clonedApps, key = { "apk_${it.id}" }) { clone ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        AppIconWithBadge(
                            packageName = clone.originalPackageName,
                            appName = clone.customName,
                            cloneNumber = clone.cloneNumber,
                            badgeColorHex = clone.badgeColorHex,
                            iconSize = 44.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${clone.customName} (${clone.accountTag})",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "New Pkg: ${clone.clonedPackageName}",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = { onExportCloneApk(clone) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export APK",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                        Button(
                            onClick = { onInstallCloneApk(clone) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                contentColor = Color(0xFF04131A)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
