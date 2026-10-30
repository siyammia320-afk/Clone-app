package com.example.engine

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.ui.components.AppIconWithBadge
import com.example.ui.theme.MyApplicationTheme

/**
 * In-App Isolated Clone Container Activity.
 * Runs the cloned app directly INSIDE OmniSpace with its own isolated CookieVault,
 * DOM Storage, and Virtual Device User-Agent so each clone (#1, #2, ... #100) has its own
 * independent account login without ever opening the phone's main installed app.
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

    private var activeWebView: WebView? = null
    private var currentSandboxPath: String = ""
    private var currentLoadedUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
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
        currentSandboxPath = sandboxPath

        val cloneKey = "${originalPkg}_slot_${cloneNumber}_$virtualDeviceId"

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

        val endpointConfig = SandboxSessionManager.resolveAppEndpoint(
            packageName = originalPkg,
            appName = customName.substringBefore(" #"),
            sandboxDir = sandboxPath
        )

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                var isSessionMounted by remember { mutableStateOf(false) }
                var loadProgress by remember { mutableIntStateOf(10) }
                var currentUrl by remember { mutableStateOf(endpointConfig.entryUrl) }
                var isDesktopMode by remember { mutableStateOf(endpointConfig.requiresDesktopUaByDefault) }
                var isHeaderExpanded by remember { mutableStateOf(false) }
                var showUrlEditorDialog by remember { mutableStateOf(false) }

                LaunchedEffect(cloneKey) {
                    SandboxSessionManager.mountCloneSession(
                        cloneKey = cloneKey,
                        sandboxDir = sandboxPath,
                        currentUrl = endpointConfig.entryUrl
                    ) {
                        isSessionMounted = true
                    }
                }

                BackHandler {
                    val wv = activeWebView
                    if (wv != null && wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        SandboxSessionManager.persistCloneSession(sandboxPath, currentLoadedUrl)
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = Color(0xFF090D16)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Compact Top In-App Container Control Bar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        SandboxSessionManager.persistCloneSession(sandboxPath, currentLoadedUrl)
                                        finish()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Exit Clone to Dual Space",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                AppIconWithBadge(
                                    packageName = originalPkg,
                                    appName = customName,
                                    cloneNumber = cloneNumber,
                                    badgeColorHex = 0xFF00E5FFL,
                                    iconSize = 32.dp
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isHeaderExpanded = !isHeaderExpanded }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = customName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF00E676).copy(alpha = 0.18f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = accountTag,
                                                color = Color(0xFF00E676),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "In-App Clone #$cloneNumber • ID: $virtualDeviceId",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Back inside cloned app
                                IconButton(
                                    onClick = {
                                        if (activeWebView?.canGoBack() == true) {
                                            activeWebView?.goBack()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Navigate Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Mobile / Desktop Mode Toggle (Helpful for WhatsApp Web / Messenger)
                                IconButton(
                                    onClick = {
                                        isDesktopMode = !isDesktopMode
                                        activeWebView?.let { wv ->
                                            wv.settings.userAgentString =
                                                SandboxSessionManager.buildIsolatedUserAgent(
                                                    cloneNumber = cloneNumber,
                                                    virtualDeviceId = virtualDeviceId,
                                                    desktopMode = isDesktopMode
                                                )
                                            wv.settings.useWideViewPort = true
                                            wv.settings.loadWithOverviewMode = true
                                            wv.reload()
                                        }
                                        Toast.makeText(
                                            this@CloneLauncherActivity,
                                            if (isDesktopMode) "Switched to Desktop View" else "Switched to Mobile View",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDesktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                                        contentDescription = "Toggle Desktop/Mobile Mode",
                                        tint = if (isDesktopMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Reload button
                                IconButton(
                                    onClick = { activeWebView?.reload() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload Clone",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Expand/Collapse Sandbox Controls
                                IconButton(
                                    onClick = { isHeaderExpanded = !isHeaderExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHeaderExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "More Clone Options",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Expandable Sandbox Drawer for Resetting Account Session or Changing App Portal URL
                            AnimatedVisibility(visible = isHeaderExpanded) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF090D16))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    TextButton(
                                        onClick = { showUrlEditorDialog = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Set App URL",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 12.sp
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            SandboxSessionManager.wipeCloneSession(sandboxPath) {
                                                activeWebView?.clearCache(true)
                                                activeWebView?.clearHistory()
                                                activeWebView?.loadUrl(endpointConfig.entryUrl)
                                                Toast.makeText(
                                                    this@CloneLauncherActivity,
                                                    "Logged out & cleared session for $customName only!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            isHeaderExpanded = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockReset,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Logout / Reset Clone #$cloneNumber",
                                            color = Color(0xFFFF5252),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            if (loadProgress in 1..99) {
                                LinearProgressIndicator(
                                    progress = { loadProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color.Transparent
                                )
                            }
                        }

                        // Main In-App Clone Container Body
                        if (!isSessionMounted) {
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
                                        text = "Mounting Isolated Account Slot #$cloneNumber…",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Loading private cookies & storage for $customName",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.setAcceptCookie(true)
                                        cookieManager.setAcceptThirdPartyCookies(this, true)

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            loadsImagesAutomatically = true
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            setSupportZoom(true)
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            javaScriptCanOpenWindowsAutomatically = true
                                            mediaPlaybackRequiresUserGesture = false
                                            userAgentString = SandboxSessionManager.buildIsolatedUserAgent(
                                                cloneNumber = cloneNumber,
                                                virtualDeviceId = virtualDeviceId,
                                                desktopMode = isDesktopMode
                                            )
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val targetUri = request?.url ?: return false
                                                val scheme = targetUri.scheme?.lowercase() ?: ""
                                                // Keep ALL http/https navigation strictly inside the clone container
                                                // so it NEVER jumps out to the phone's external installed app!
                                                if (scheme == "http" || scheme == "https") {
                                                    return false
                                                }
                                                // Block intent:// or market:// deep links from escaping to the host phone app
                                                return true
                                            }

                                            override fun onPageStarted(
                                                view: WebView?,
                                                url: String?,
                                                favicon: Bitmap?
                                            ) {
                                                super.onPageStarted(view, url, favicon)
                                                if (!url.isNullOrBlank()) {
                                                    currentUrl = url
                                                    currentLoadedUrl = url
                                                }
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                if (!url.isNullOrBlank()) {
                                                    currentUrl = url
                                                    currentLoadedUrl = url
                                                }
                                                // Persist cookies exclusively into this clone's sandbox vault
                                                SandboxSessionManager.persistCloneSession(
                                                    sandboxDir = sandboxPath,
                                                    currentUrl = url
                                                )
                                            }
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(
                                                view: WebView?,
                                                newProgress: Int
                                            ) {
                                                loadProgress = newProgress
                                            }
                                        }

                                        activeWebView = this
                                        loadUrl(currentUrl)
                                    }
                                }
                            )
                        }
                    }
                }

                if (showUrlEditorDialog) {
                    var editedUrl by remember { mutableStateOf(currentUrl) }
                    Dialog(onDismissRequest = { showUrlEditorDialog = false }) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF111827),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF283548), RoundedCornerShape(20.dp))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Custom Portal URL for $customName",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = editedUrl,
                                    onValueChange = { editedUrl = it },
                                    label = { Text("https://…") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = { showUrlEditorDialog = false }) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val normalized = if (
                                                editedUrl.startsWith("http://") ||
                                                editedUrl.startsWith("https://")
                                            ) {
                                                editedUrl.trim()
                                            } else {
                                                "https://${editedUrl.trim()}"
                                            }
                                            SandboxSessionManager.saveCustomEndpoint(sandboxPath, normalized)
                                            currentUrl = normalized
                                            activeWebView?.loadUrl(normalized)
                                            showUrlEditorDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00E5FF),
                                            contentColor = Color(0xFF04131A)
                                        )
                                    ) {
                                        Text("Load in Clone", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentSandboxPath.isNotEmpty()) {
            SandboxSessionManager.persistCloneSession(currentSandboxPath, currentLoadedUrl)
        }
    }

    override fun onDestroy() {
        if (currentSandboxPath.isNotEmpty()) {
            SandboxSessionManager.persistCloneSession(currentSandboxPath, currentLoadedUrl)
        }
        activeWebView?.destroy()
        activeWebView = null
        super.onDestroy()
    }
}
