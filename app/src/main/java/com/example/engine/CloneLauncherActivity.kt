package com.example.engine

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.ClonedAppEntity
import com.example.ui.components.AppIconWithBadge
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import kotlinx.coroutines.launch

/**
 * Direct Native Clone APK Launcher & Installer Activity (Zero WebView / Zero Website).
 * - If the cloned native package (e.g. com.whatsapp.clone1) is already installed on the device,
 *   it immediately opens that native cloned app!
 * - If not installed yet, it builds & signs the standalone native APK (V1 + V2 signature + patched AXML)
 *   and opens Android's System Package Installer so the clone runs as a 100% real native Android app.
 */
class CloneLauncherActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORIGINAL_PKG = "extra_original_pkg"
        const val EXTRA_CLONED_PKG = "extra_cloned_pkg"
        const val EXTRA_CLONE_NUMBER = "extra_clone_number"
        const val EXTRA_CUSTOM_NAME = "extra_custom_name"
        const val EXTRA_ACCOUNT_TAG = "extra_account_tag"
        const val EXTRA_VIRTUAL_DEVICE_ID = "extra_virtual_device_id"
        const val EXTRA_SANDBOX_PATH = "extra_sandbox_path"
        const val EXTRA_APK_OUTPUT_PATH = "extra_apk_output_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val originalPkg = intent.getStringExtra(EXTRA_ORIGINAL_PKG) ?: ""
        val clonedPkg = intent.getStringExtra(EXTRA_CLONED_PKG) ?: ""
        val cloneNumber = intent.getIntExtra(EXTRA_CLONE_NUMBER, 1)
        val customName = intent.getStringExtra(EXTRA_CUSTOM_NAME) ?: "Clone #$cloneNumber"
        val accountTag = intent.getStringExtra(EXTRA_ACCOUNT_TAG) ?: "Account #$cloneNumber"
        val virtualDeviceId = intent.getStringExtra(EXTRA_VIRTUAL_DEVICE_ID) ?: "os01"
        val sandboxPath = intent.getStringExtra(EXTRA_SANDBOX_PATH) ?: filesDir.absolutePath
        val apkOutputPath = intent.getStringExtra(EXTRA_APK_OUTPUT_PATH) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(
                ActivityManager.TaskDescription.Builder()
                    .setLabel("$customName ($accountTag)")
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("$customName ($accountTag)"))
        }

        val dummyEntity = ClonedAppEntity(
            originalPackageName = originalPkg,
            originalAppName = customName,
            clonedPackageName = clonedPkg,
            cloneNumber = cloneNumber,
            customName = customName,
            accountTag = accountTag,
            spaceId = "space_1",
            badgeColorHex = 0xFF00E5FFL,
            virtualDeviceId = virtualDeviceId,
            virtualModelName = "Native Clone",
            apkOutputPath = apkOutputPath,
            sandboxDirPath = sandboxPath,
            storageBytes = 0L
        )

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                val scope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current

                var isBuildingApk by remember { mutableStateOf(true) }
                var builtApkFile by remember { mutableStateOf<File?>(null) }
                var buildError by remember { mutableStateOf<String?>(null) }
                var isCloneInstalled by remember {
                    mutableStateOf(ApkCloneBuilder.isClonedPackageInstalled(this, clonedPkg))
                }
                var autoPromptedInstall by remember { mutableStateOf(false) }

                // Re-check installation state whenever user returns from System Package Installer
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val nowInstalled = ApkCloneBuilder.isClonedPackageInstalled(
                                this@CloneLauncherActivity,
                                clonedPkg
                            )
                            isCloneInstalled = nowInstalled
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(Unit) {
                    if (ApkCloneBuilder.isClonedPackageInstalled(this@CloneLauncherActivity, clonedPkg)) {
                        isCloneInstalled = true
                        isBuildingApk = false
                        launchInstalledClonePackage(clonedPkg)
                    } else {
                        isBuildingApk = true
                        runCatching {
                            ApkCloneBuilder.getOrBuildSignedCloneApkFile(
                                context = this@CloneLauncherActivity,
                                clone = dummyEntity
                            )
                        }.onSuccess { file ->
                            builtApkFile = file
                            isBuildingApk = false
                            if (!autoPromptedInstall) {
                                autoPromptedInstall = true
                                ApkCloneBuilder.promptSystemPackageInstaller(
                                    this@CloneLauncherActivity,
                                    file
                                )
                            }
                        }.onFailure { err ->
                            buildError = err.localizedMessage ?: "Failed to build signed Clone APK"
                            isBuildingApk = false
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = Color(0xFF080B12)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF142238), Color(0xFF080B12))
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF283548), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppIconWithBadge(
                                    packageName = originalPkg,
                                    appName = customName,
                                    cloneNumber = cloneNumber,
                                    badgeColorHex = 0xFF00E5FFL,
                                    iconSize = 68.dp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = customName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Direct Native Clone #$cloneNumber • $accountTag",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF090D16))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isCloneInstalled) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.VerifiedUser
                                            },
                                            contentDescription = null,
                                            tint = if (isCloneInstalled) Color(0xFF00E676) else Color(0xFF00E5FF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCloneInstalled) {
                                                "Status: Direct Native Clone Installed & Ready"
                                            } else {
                                                "Status: Standalone Native APK Signed (V1 + V2)"
                                            },
                                            color = if (isCloneInstalled) Color(0xFF00E676) else Color(0xFF00E5FF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Clone Package: $clonedPkg",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Original Package: $originalPkg",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    builtApkFile?.let { f ->
                                        Text(
                                            text = "Signed APK Size: ${ApkCloneBuilder.formatBytes(f.length())}",
                                            color = Color(0xFF00E676),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                if (isBuildingApk) {
                                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Rebuilding & signing direct native APK ($clonedPkg)…\nPlease wait a moment.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else if (buildError != null) {
                                    Text(
                                        text = buildError!!,
                                        color = Color(0xFFFF5252),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(onClick = { finish() }) {
                                        Text("Back")
                                    }
                                } else if (isCloneInstalled) {
                                    Button(
                                        onClick = { launchInstalledClonePackage(clonedPkg) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00E676),
                                            contentColor = Color(0xFF04131A)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RocketLaunch,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Open Installed $customName",
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { finish() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Back to OmniSpace")
                                    }
                                } else {
                                    Text(
                                        text = "Install this signed clone APK once to run '$customName' as a 100% direct native app with its own separate login account:",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            builtApkFile?.let { file ->
                                                ApkCloneBuilder.promptSystemPackageInstaller(
                                                    this@CloneLauncherActivity,
                                                    file
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00E5FF),
                                            contentColor = Color(0xFF04131A)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InstallMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Install Direct Clone APK (#$cloneNumber)",
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    ApkCloneBuilder.shareCloneApk(
                                                        this@CloneLauncherActivity,
                                                        dummyEntity.copy(
                                                            apkOutputPath = builtApkFile?.absolutePath ?: ""
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Save/Share APK")
                                        }

                                        OutlinedButton(
                                            onClick = { finish() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Back")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchInstalledClonePackage(clonedPkg: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(clonedPkg)
        if (launchIntent != null) {
            launchIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(launchIntent)
            finish()
        } else {
            Toast.makeText(
                this,
                "Clone package $clonedPkg is not installed yet. Please tap 'Install Direct Clone APK' first.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
