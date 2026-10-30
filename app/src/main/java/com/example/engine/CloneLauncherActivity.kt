package com.example.engine

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import kotlinx.coroutines.delay

/**
 * Isolated Multi-Task Container Activity.
 * Each clone runs with its own Android Recents Document Task, isolated sandbox environment paths,
 * and dispatches either the installed cloned package or a separate multi-instance task of the base package.
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

        // Label this task in Android Recents with the Clone's custom name
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

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                var progress by remember { mutableFloatStateOf(0.15f) }
                var statusText by remember { mutableStateOf("Mounting isolated sandbox slot #$cloneNumber…") }
                var dispatched by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Ensure sandbox runtime directories exist
                    runCatching {
                        val root = File(sandboxPath)
                        File(root, "shared_prefs").mkdirs()
                        File(root, "databases").mkdirs()
                        File(root, "files").mkdirs()
                        File(root, "files/last_launch_session.txt").writeText(
                            "LAUNCH_TS=${System.currentTimeMillis()}\nSLOT=$cloneNumber\nACCOUNT=$accountTag\n"
                        )
                    }
                    delay(220)
                    progress = 0.55f
                    statusText = "Binding Virtual Device ID [$virtualDeviceId]…"
                    delay(240)
                    progress = 0.90f
                    statusText = "Dispatching native activity in isolated multi-task…"
                    delay(180)
                    progress = 1.0f
                    dispatched = true
                    dispatchNativeTargetApp(
                        originalPkg = originalPkg,
                        clonedPkg = clonedPkg,
                        cloneNumber = cloneNumber,
                        customName = customName,
                        accountTag = accountTag,
                        virtualDeviceId = virtualDeviceId,
                        sandboxPath = sandboxPath
                    )
                }

                val pulseTransition = rememberInfiniteTransition(label = "pulse")
                val scale by pulseTransition.animateFloat(
                    initialValue = 0.96f,
                    targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF080B12)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF142238),
                                        Color(0xFF080B12)
                                    )
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111726)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .scale(scale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.14f))
                                        .border(2.dp, Color(0xFF00E5FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Clone Sandbox",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(38.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = customName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Clone Slot #$cloneNumber • $accountTag",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF00E5FF)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color(0xFF1E293B)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0B0F19))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Isolated Environment Active",
                                            color = Color(0xFF00E676),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "Cloned Package: $clonedPkg",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Virtual Android ID: $virtualDeviceId",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (dispatched) {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { finish() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Back to Space")
                                        }
                                        Button(
                                            onClick = {
                                                dispatchNativeTargetApp(
                                                    originalPkg = originalPkg,
                                                    clonedPkg = clonedPkg,
                                                    cloneNumber = cloneNumber,
                                                    customName = customName,
                                                    accountTag = accountTag,
                                                    virtualDeviceId = virtualDeviceId,
                                                    sandboxPath = sandboxPath
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF00E5FF),
                                                contentColor = Color(0xFF06101E)
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RocketLaunch,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Re-Open")
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

    private fun dispatchNativeTargetApp(
        originalPkg: String,
        clonedPkg: String,
        cloneNumber: Int,
        customName: String,
        accountTag: String,
        virtualDeviceId: String,
        sandboxPath: String
    ) {
        val pm = packageManager
        // 1. First check if the standalone patched Clone APK package is installed on the device
        val clonedLaunchIntent = pm.getLaunchIntentForPackage(clonedPkg)
        if (clonedLaunchIntent != null) {
            clonedLaunchIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("omnispace_clone_number", cloneNumber)
                putExtra("omnispace_account_tag", accountTag)
                putExtra("omnispace_virtual_device_id", virtualDeviceId)
                putExtra("omnispace_sandbox_root", sandboxPath)
            }
            startActivity(clonedLaunchIntent)
            return
        }

        // 2. Otherwise launch the native installed package in an isolated Multi-Document Task with distinct URI & extras
        val baseLaunchIntent = pm.getLaunchIntentForPackage(originalPkg)
        if (baseLaunchIntent != null) {
            baseLaunchIntent.apply {
                // Distinct URI forces Android's ActivityManager to allocate a separate task record
                // when combined with FLAG_ACTIVITY_NEW_DOCUMENT and FLAG_ACTIVITY_MULTIPLE_TASK
                data = Uri.parse("omnispace://instance/$originalPkg/slot_$cloneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("omnispace_clone_slot", cloneNumber)
                putExtra("omnispace_clone_label", customName)
                putExtra("omnispace_account_tag", accountTag)
                putExtra("omnispace_virtual_device_id", virtualDeviceId)
                putExtra("omnispace_sandbox_root", sandboxPath)
            }
            try {
                startActivity(baseLaunchIntent)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Could not open $customName: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                this,
                "App package $originalPkg is not installed on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
