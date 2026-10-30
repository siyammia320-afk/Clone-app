package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClonedAppEntity
import com.example.engine.ApkCloneBuilder
import com.example.engine.InstalledAppInfo
import com.example.ui.components.AppIconWithBadge

@Composable
fun AppClonerScreen(
    installedApps: List<InstalledAppInfo>,
    clonedApps: List<ClonedAppEntity>,
    isScanning: Boolean,
    searchQuery: String,
    showSystemApps: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onSelectAppToClone: (InstalledAppInfo) -> Unit
) {
    val cloneCountsByPkg = remember(clonedApps) {
        clonedApps.groupingBy { it.originalPackageName }.eachCount()
    }

    val filteredApps = remember(installedApps, searchQuery, showSystemApps) {
        installedApps.filter { app ->
            val matchesSystem = showSystemApps || !app.isSystemApp
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesSystem && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header banner explaining 1x to Unlimited direct app cloning
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF111827))
                .border(1.dp, Color(0xFF283548), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phone Installed Apps (${filteredApps.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Tap any app to create 1x, 5x, 10x, or unlimited isolated native clones.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onRefreshApps,
                    modifier = Modifier.testTag("refresh_installed_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan Installed Apps",
                        tint = Color(0xFF00E5FF)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search and User/System filter bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search installed apps or package name…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Search"
                        )
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_installed_apps_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = !showSystemApps,
                onClick = { onToggleSystemApps(false) },
                label = { Text("User & Launchable Apps") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00E5FF),
                    selectedLabelColor = Color(0xFF04131A)
                )
            )
            FilterChip(
                selected = showSystemApps,
                onClick = { onToggleSystemApps(true) },
                label = { Text("Include System Apps") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF7C4DFF),
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isScanning && installedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scanning installed apps on your device…",
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("installed_apps_list"),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appInfo ->
                    val activeClones = cloneCountsByPkg[appInfo.packageName] ?: 0
                    InstalledAppRowCard(
                        appInfo = appInfo,
                        activeCloneCount = activeClones,
                        onCloneClick = { onSelectAppToClone(appInfo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledAppRowCard(
    appInfo: InstalledAppInfo,
    activeCloneCount: Int,
    onCloneClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .clickable { onCloneClick() }
            .testTag("installed_app_item_${appInfo.packageName}"),
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
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                cloneNumber = if (activeCloneCount > 0) activeCloneCount else null,
                badgeColorHex = 0xFF00E676L,
                iconSize = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appInfo.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeCloneCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$activeCloneCount Cloned",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Text(
                    text = appInfo.packageName,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "v${appInfo.versionName} • ${ApkCloneBuilder.formatBytes(appInfo.apkSizeBytes)} • SDK ${appInfo.targetSdkVersion}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onCloneClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF04131A)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("clone_button_${appInfo.packageName}")
            ) {
                Icon(
                    imageVector = Icons.Default.ControlPointDuplicate,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (activeCloneCount == 0) "Clone" else "+ More",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
