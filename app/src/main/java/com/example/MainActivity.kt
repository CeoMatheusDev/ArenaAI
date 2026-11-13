package com.example

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.ArenaPrimary
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ARENA_HOME_URL = "https://arena.ai/agent"

class MainActivity : ComponentActivity() {

    private val webViewState = ArenaWebViewState()
    internal var activeWebView: WebView? = null
    private var openExtensionsOnLaunch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannels(this)

        // Load persisted ArenaAI/settings.json and guarantee ArenaAI directory structure exists
        val initialSettings = ArenaSettingsRepository.loadSettings(this)
        ArenaSettingsRepository.ensureAddonWorkspaceInitialized(this, initialSettings)
        applySettingsToState(initialSettings)

        if (initialSettings.backgroundServiceEnabled) {
            NotificationHelper.startBackgroundService(this)
        }

        openExtensionsOnLaunch = intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_EXTENSIONS, false) == true

        setContent {
            MyApplicationTheme {
                ArenaAppScreen(
                    webViewState = webViewState,
                    initialSettings = initialSettings,
                    openExtensionsInitially = openExtensionsOnLaunch,
                    onConsumeOpenExtensions = { openExtensionsOnLaunch = false },
                    onWebViewAttached = { activeWebView = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_EXTENSIONS, false)) {
            openExtensionsOnLaunch = true
        }
    }

    private fun applySettingsToState(settings: ArenaSettings) {
        webViewState.isFullscreen = settings.fullscreen
        webViewState.notifyOnFinish = settings.notifyOnFinish
        webViewState.backgroundServiceEnabled = settings.backgroundServiceEnabled
        webViewState.keepScreenAwakeOnOpen = settings.keepScreenAwakeOnOpen
        webViewState.keepScreenAwakeOnGenerating = settings.keepScreenAwakeOnGenerating
        webViewState.extensionsAutoLoad = settings.extensionsAutoLoad
        webViewState.autoScanAddonsOnStartup = settings.autoScanAddonsOnStartup
        webViewState.autoLoginPromptEnabled = settings.autoLoginPromptEnabled
        webViewState.connectedEmail = settings.connectedEmail
    }

    override fun onResume() {
        super.onResume()
        webViewState.isInBackground = false
        activeWebView?.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        webViewState.isInBackground = true
        if (webViewState.isGenerating || webViewState.backgroundServiceEnabled) {
            NotificationHelper.startBackgroundService(this)
            activeWebView?.resumeTimers()
        }
    }

    override fun onDestroy() {
        if (!webViewState.backgroundServiceEnabled && !webViewState.isGenerating) {
            NotificationHelper.stopBackgroundService(this)
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaAppScreen(
    webViewState: ArenaWebViewState,
    initialSettings: ArenaSettings,
    openExtensionsInitially: Boolean,
    onConsumeOpenExtensions: () -> Unit,
    onWebViewAttached: (WebView) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val isNetworkOnline by rememberNetworkStatus()

    var currentSettings by remember { mutableStateOf(initialSettings) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var popupWebViewMessage by remember { mutableStateOf<Message?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showPermissionBanner by remember { mutableStateOf(false) }
    var pendingImportType by remember { mutableStateOf(AddonType.EXTENSION) }
    var pendingPickDirectoryCallback by remember { mutableStateOf<((String?) -> Unit)?>(null) }

    fun persistSettings(updated: ArenaSettings) {
        currentSettings = updated
        webViewState.isFullscreen = updated.fullscreen
        webViewState.notifyOnFinish = updated.notifyOnFinish
        webViewState.backgroundServiceEnabled = updated.backgroundServiceEnabled
        webViewState.keepScreenAwakeOnOpen = updated.keepScreenAwakeOnOpen
        webViewState.keepScreenAwakeOnGenerating = updated.keepScreenAwakeOnGenerating
        webViewState.extensionsAutoLoad = updated.extensionsAutoLoad
        webViewState.autoScanAddonsOnStartup = updated.autoScanAddonsOnStartup
        webViewState.autoLoginPromptEnabled = updated.autoLoginPromptEnabled
        webViewState.connectedEmail = updated.connectedEmail
        coroutineScope.launch(Dispatchers.IO) {
            ArenaSettingsRepository.saveSettings(context, updated)
        }
    }

    fun syncActiveAddonsInSettings(addons: List<ArenaAddon>, baseSettings: ArenaSettings): ArenaSettings {
        val activeExtIds = addons.filter { it.type == AddonType.EXTENSION && it.compatible && it.isEnabled }.map { it.id }
        val activePlugIds = addons.filter { it.type == AddonType.PLUGIN && it.compatible && it.isEnabled }.map { it.id }
        return baseSettings.copy(
            activeExtensions = activeExtIds,
            activePlugins = activePlugIds
        )
    }

    fun runAddonScan(notifyUser: Boolean = true) {
        if (webViewState.isScanningAddons) return
        webViewState.isScanningAddons = true
        if (notifyUser) {
            NotificationHelper.showScanningAddonsNotification(context)
        }
        coroutineScope.launch(Dispatchers.IO) {
            ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context, currentSettings)
            val scanned = ArenaAddonManager.scanAllAddons(context, currentSettings)
            val compatibleAddons = scanned.filter { it.compatible }
            val extCount = compatibleAddons.count { it.type == AddonType.EXTENSION }
            val pluginCount = compatibleAddons.count { it.type == AddonType.PLUGIN }
            val syncedSettings = syncActiveAddonsInSettings(scanned, currentSettings)
            ArenaSettingsRepository.saveSettings(context, syncedSettings)

            withContext(Dispatchers.Main) {
                currentSettings = syncedSettings
                webViewState.addons = scanned
                webViewState.isScanningAddons = false
                if (notifyUser) {
                    if (compatibleAddons.isNotEmpty()) {
                        NotificationHelper.showAddonsFoundNotification(context, extCount, pluginCount)
                    } else {
                        NotificationHelper.cancelScanningNotification(context)
                    }
                }
                if (webViewState.extensionsAutoLoad) {
                    val script = ArenaAddonManager.buildSiteSdkAndAddonsScript(
                        scanned,
                        webViewState.connectedEmail
                    )
                    webViewRef?.evaluateJavascript(script, null)
                }
            }
        }
    }

    // Keep screen ON when app is open (if keepScreenAwakeOnOpen is true) OR while generating output
    val shouldKeepScreenOn = webViewState.keepScreenAwakeOnOpen ||
        (webViewState.isGenerating && webViewState.keepScreenAwakeOnGenerating)

    DisposableEffect(shouldKeepScreenOn) {
        if (shouldKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Notification permission state
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        showPermissionBanner = !isGranted
        if (isGranted) {
            Toast.makeText(context, "Notificações ativadas!", Toast.LENGTH_SHORT).show()
        }
    }

    // Device email / account chooser launcher
    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pickedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == Activity.RESULT_OK && !pickedEmail.isNullOrBlank()) {
            val updated = currentSettings.copy(connectedEmail = pickedEmail)
            persistSettings(updated)
            ArenaAccountHelper.injectConnectedEmailIntoWebView(webViewRef, pickedEmail)

            val authUrl = webViewState.pendingAuthUrl
            webViewState.pendingAuthUrl = null
            if (!authUrl.isNullOrBlank()) {
                val hinted = ArenaAccountHelper.appendLoginHintIfGoogleOAuth(authUrl, pickedEmail)
                webViewRef?.loadUrl(hinted)
            }
            Toast.makeText(context, "Conectado com: $pickedEmail", Toast.LENGTH_SHORT).show()
        } else {
            val authUrl = webViewState.pendingAuthUrl
            webViewState.pendingAuthUrl = null
            if (!authUrl.isNullOrBlank()) {
                webViewRef?.loadUrl(authUrl)
            }
        }
    }

    fun launchDeviceEmailPicker(pendingAuthUrl: String? = null) {
        webViewState.pendingAuthUrl = pendingAuthUrl
        try {
            accountChooserLauncher.launch(ArenaAccountHelper.createChooseAccountIntent())
        } catch (_: Exception) {
            try {
                accountChooserLauncher.launch(ArenaAccountHelper.createChooseAnyAccountIntent())
            } catch (_: Exception) {
                Toast.makeText(context, "Nenhuma lista de contas disponível no aparelho", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Import addon (.js / .json) launcher
    val importAddonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val res = ArenaAddonManager.importAddonFromUri(context, uri, pendingImportType, currentSettings)
                withContext(Dispatchers.Main) {
                    res.onSuccess { name ->
                        Toast.makeText(context, "Importado: $name", Toast.LENGTH_SHORT).show()
                        runAddonScan(notifyUser = true)
                    }.onFailure { err ->
                        Toast.makeText(context, "Erro ao importar: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // SAF Folder permission launcher for ArenaAI folder
    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: Exception) {
            }
            val updated = currentSettings.copy(safTreeUri = treeUri.toString())
            currentSettings = updated
            coroutineScope.launch(Dispatchers.IO) {
                ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context, updated)
            }
            Toast.makeText(context, "Pasta ArenaAI criada/vinculada!", Toast.LENGTH_SHORT).show()
            runAddonScan(notifyUser = true)
            pendingPickDirectoryCallback?.invoke(treeUri.toString())
            pendingPickDirectoryCallback = null
        } else {
            pendingPickDirectoryCallback?.invoke("")
            pendingPickDirectoryCallback = null
        }
    }

    // Startup: request notification permission if needed + ensure directories + optional scan
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (currentSettings.autoScanAddonsOnStartup) {
            runAddonScan(notifyUser = true)
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context, currentSettings)
            }
        }
    }

    LaunchedEffect(openExtensionsInitially) {
        if (openExtensionsInitially) {
            showExtensionsDialog = true
            onConsumeOpenExtensions()
        }
    }

    // File chooser launcher for web uploads
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = webViewState.filePathCallback
        if (callback != null) {
            val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val clipData = intent.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    } else {
                        intent.data?.let { uri -> arrayOf(uri) }
                    }
                }
            } else {
                null
            }
            callback.onReceiveValue(results)
            webViewState.filePathCallback = null
        }
    }

    BackHandler(enabled = true) {
        if (webViewState.isFullscreen) {
            persistSettings(currentSettings.copy(fullscreen = false))
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            (context as? ComponentActivity)?.finish()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!webViewState.isFullscreen) {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_arena_ai_logo),
                                    contentDescription = stringResource(R.string.app_name),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        !isNetworkOnline -> Color(0xFFEF4444)
                                                        webViewState.isGenerating -> Color(0xFFF59E0B)
                                                        else -> Color(0xFF10B981)
                                                    }
                                                )
                                        )
                                    }
                                    Text(
                                        text = when {
                                            webViewState.isGenerating -> "Gerando saída • Tela ativa"
                                            webViewState.connectedEmail.isNotBlank() -> webViewState.connectedEmail
                                            else -> "arena.ai/agent"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (webViewState.isGenerating) {
                                            Color(0xFFF59E0B)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            IconButton(
                                onClick = {
                                    webViewState.hasError = false
                                    webViewRef?.reload()
                                },
                                modifier = Modifier.testTag("nav_reload_button")
                            ) {
                                Icon(
                                    imageVector = if (webViewState.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.action_reload),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = webViewState.canGoBack,
                                modifier = Modifier.testTag("nav_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = if (webViewState.canGoBack) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }

                            IconButton(
                                onClick = { webViewRef?.goForward() },
                                enabled = webViewState.canGoForward,
                                modifier = Modifier.testTag("nav_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.action_forward),
                                    tint = if (webViewState.canGoForward) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }

                            IconButton(
                                onClick = { showMenu = !showMenu },
                                modifier = Modifier.testTag("nav_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Mais opções"
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_fullscreen)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Fullscreen, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        persistSettings(currentSettings.copy(fullscreen = true))
                                        Toast.makeText(
                                            context,
                                            "Tela cheia salva em ArenaAI/settings.json",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configurações") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showAppSettingsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.action_extensions))
                                            val compatCount = webViewState.addons.count { it.compatible }
                                            if (compatCount > 0) {
                                                Text(
                                                    text = "$compatCount complemento(s) compatível(is)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Extension, contentDescription = null, tint = ArenaPrimary)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showExtensionsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.action_connect_account))
                                            if (webViewState.connectedEmail.isNotBlank()) {
                                                Text(
                                                    text = webViewState.connectedEmail,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        launchDeviceEmailPicker(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Página Inicial") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Home, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        webViewState.hasError = false
                                        webViewRef?.loadUrl(ARENA_HOME_URL)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        shareUrl(context, webViewState.currentUrl)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_open_browser)) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        openInExternalBrowser(context, webViewState.currentUrl)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Limpar Cache & Dados") },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        webViewRef?.clearCache(true)
                                        webViewRef?.clearFormData()
                                        CookieManager.getInstance().removeAllCookies(null)
                                        Toast.makeText(context, "Cache limpo com sucesso", Toast.LENGTH_SHORT).show()
                                        webViewRef?.loadUrl(ARENA_HOME_URL)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_about)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Info, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showAboutDialog = true
                                    }
                                )
                            }
                        }
                    )

                    if (webViewState.isLoading) {
                        LinearProgressIndicator(
                            progress = { webViewState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = ArenaPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    if (webViewState.isGenerating) {
                        Surface(
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gerando saída • Tela mantida acesa e app ativo em segundo plano",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (!hasNotificationPermission && showPermissionBanner) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ative as notificações para avisos de resposta e complementos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                ) {
                                    Text("Ativar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ArenaWebView(
                state = webViewState,
                onWebViewCreated = {
                    webViewRef = it
                    onWebViewAttached(it)
                },
                onLaunchFileChooser = { intent ->
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        webViewState.filePathCallback?.onReceiveValue(null)
                        webViewState.filePathCallback = null
                        Toast.makeText(context, "Nenhum gerenciador de arquivos encontrado", Toast.LENGTH_SHORT).show()
                    }
                },
                onLoginRequested = { url ->
                    if (webViewState.autoLoginPromptEnabled || url == null) {
                        launchDeviceEmailPicker(url)
                    }
                },
                onCreatePopupWindow = { resultMsg ->
                    popupWebViewMessage = resultMsg
                },
                onRequestPickDirectory = { callback ->
                    pendingPickDirectoryCallback = callback
                    try {
                        safFolderLauncher.launch(null)
                    } catch (_: Exception) {
                        pendingPickDirectoryCallback = null
                        callback("")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = webViewState.hasError || (!isNetworkOnline && webViewState.isLoading),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.fillMaxSize()
            ) {
                NetworkErrorView(
                    errorMessage = webViewState.errorMessage,
                    onRetry = {
                        webViewState.hasError = false
                        webViewRef?.reload()
                    }
                )
            }

            if (webViewState.isFullscreen) {
                Surface(
                    onClick = {
                        persistSettings(currentSettings.copy(fullscreen = false))
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .testTag("exit_fullscreen_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = stringResource(R.string.action_exit_fullscreen),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_exit_fullscreen),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    popupWebViewMessage?.let { msg ->
        OAuthPopupDialog(
            resultMsg = msg,
            connectedEmail = webViewState.connectedEmail,
            onDismiss = { popupWebViewMessage = null }
        )
    }

    if (showAboutDialog) {
        AboutArenaDialog(
            connectedEmail = webViewState.connectedEmail,
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showAppSettingsDialog) {
        AppSettingsDialog(
            settings = currentSettings,
            storageSummary = ArenaSettingsRepository.getDisplayStorageSummary(context, currentSettings),
            onUpdateSettings = { updated ->
                persistSettings(updated)
                if (updated.backgroundServiceEnabled) {
                    NotificationHelper.startBackgroundService(context)
                } else if (!webViewState.isGenerating) {
                    NotificationHelper.stopBackgroundService(context)
                }
            },
            onTestNotification = {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    NotificationHelper.showResponseNotification(
                        context,
                        "Teste: O Arena AI notificará você no telefone assim que uma resposta terminar!"
                    )
                    Toast.makeText(context, "Notificação de teste enviada!", Toast.LENGTH_SHORT).show()
                }
            },
            onAuthorizeSafFolder = {
                try {
                    safFolderLauncher.launch(null)
                } catch (_: Exception) {
                    Toast.makeText(context, "Seletor de pastas indisponível", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenGuide = { showGuideDialog = true },
            onDismiss = { showAppSettingsDialog = false }
        )
    }

    if (showExtensionsDialog) {
        ExtensionSettingsDialog(
            addons = webViewState.addons,
            autoLoadEnabled = webViewState.extensionsAutoLoad,
            isScanning = webViewState.isScanningAddons,
            storageSummary = ArenaSettingsRepository.getDisplayStorageSummary(context, currentSettings),
            onToggleAutoLoad = { enabled ->
                persistSettings(currentSettings.copy(extensionsAutoLoad = enabled))
                if (enabled) {
                    val script = ArenaAddonManager.buildSiteSdkAndAddonsScript(
                        webViewState.addons,
                        webViewState.connectedEmail
                    )
                    webViewRef?.evaluateJavascript(script, null)
                }
            },
            onToggleAddon = { addon, enabled ->
                val newDisabled = currentSettings.disabledAddonIds.toMutableSet()
                if (enabled) newDisabled.remove(addon.id) else newDisabled.add(addon.id)
                val updatedAddons = webViewState.addons.map {
                    if (it.id == addon.id) it.copy(isEnabled = enabled) else it
                }
                webViewState.addons = updatedAddons
                val synced = syncActiveAddonsInSettings(
                    updatedAddons,
                    currentSettings.copy(disabledAddonIds = newDisabled)
                )
                persistSettings(synced)
                Toast.makeText(context, "Salvo em ArenaAI/settings.json", Toast.LENGTH_SHORT).show()
            },
            onDeleteAddon = { addon ->
                coroutineScope.launch(Dispatchers.IO) {
                    ArenaAddonManager.deleteAddon(addon)
                    withContext(Dispatchers.Main) {
                        runAddonScan(notifyUser = false)
                    }
                }
            },
            onRescan = { runAddonScan(notifyUser = true) },
            onImportAddon = { type ->
                pendingImportType = type
                importAddonLauncher.launch(arrayOf("*/*"))
            },
            onAuthorizeSafFolder = {
                try {
                    safFolderLauncher.launch(null)
                } catch (_: Exception) {
                    Toast.makeText(context, "Seletor de pastas indisponível", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenGuide = { showGuideDialog = true },
            onDismiss = { showExtensionsDialog = false }
        )
    }

    if (showGuideDialog) {
        MarkdownGuideDialog(
            markdownText = ArenaSettingsRepository.ADDONS_GUIDE_MARKDOWN,
            onDismiss = { showGuideDialog = false }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArenaWebView(
    state: ArenaWebViewState,
    onWebViewCreated: (WebView) -> Unit,
    onLaunchFileChooser: (Intent) -> Unit,
    onLoginRequested: (String?) -> Unit,
    onCreatePopupWindow: (Message) -> Unit,
    onRequestPickDirectory: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.testTag("arena_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    mediaPlaybackRequiresUserGesture = false

                    userAgentString = userAgentString.replace("; wv", "")
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(
                    ArenaJsInterface(context, state, onLoginRequested),
                    "AndroidBridge"
                )
                addJavascriptInterface(
                    ArenaFileBridge(context, onRequestPickDirectory),
                    "ArenaFS"
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        state.progress = newProgress / 100f
                        state.isLoading = newProgress < 100

                        if (newProgress > 60) {
                            view?.evaluateJavascript(ArenaJsInterface.SCRIPT_INJECTION, null)
                        }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) {
                            state.pageTitle = title
                        }
                    }

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        if (resultMsg != null) {
                            onCreatePopupWindow(resultMsg)
                            return true
                        }
                        return false
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        state.filePathCallback?.onReceiveValue(null)
                        state.filePathCallback = filePathCallback
                        state.fileChooserParams = fileChooserParams

                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }

                        onLaunchFileChooser(intent)
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                                return true
                            } catch (_: Exception) {
                                return true
                            }
                        }

                        if (ArenaAccountHelper.isLoginOrAuthUrl(url)) {
                            if (state.autoLoginPromptEnabled && state.connectedEmail.isBlank() && !state.hasPromptedAccountForCurrentAuth) {
                                state.hasPromptedAccountForCurrentAuth = true
                                onLoginRequested(url)
                                return true
                            } else if (state.connectedEmail.isNotBlank()) {
                                val hinted = ArenaAccountHelper.appendLoginHintIfGoogleOAuth(url, state.connectedEmail)
                                if (hinted != url) {
                                    view?.loadUrl(hinted)
                                    return true
                                }
                            }
                        } else {
                            state.hasPromptedAccountForCurrentAuth = false
                        }

                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        state.isLoading = true
                        state.hasError = false
                        state.errorMessage = null
                        if (url != null) {
                            state.currentUrl = url
                        }
                        state.canGoBack = view?.canGoBack() == true
                        state.canGoForward = view?.canGoForward() == true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        state.isLoading = false
                        if (url != null) {
                            state.currentUrl = url
                        }
                        state.canGoBack = view?.canGoBack() == true
                        state.canGoForward = view?.canGoForward() == true

                        view?.evaluateJavascript(ArenaJsInterface.SCRIPT_INJECTION, null)
                        val addonsToInject = if (state.extensionsAutoLoad) state.addons else emptyList()
                        val sdkScript = ArenaAddonManager.buildSiteSdkAndAddonsScript(
                            addonsToInject,
                            state.connectedEmail
                        )
                        view?.evaluateJavascript(sdkScript, null)

                        if (state.connectedEmail.isNotBlank() && url != null && ArenaAccountHelper.isLoginOrAuthUrl(url)) {
                            ArenaAccountHelper.injectConnectedEmailIntoWebView(view, state.connectedEmail)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            state.hasError = true
                            state.errorMessage = error?.description?.toString()
                        }
                    }
                }

                onWebViewCreated(this)
                loadUrl(ARENA_HOME_URL)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthPopupDialog(
    resultMsg: Message,
    connectedEmail: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (connectedEmail.isNotBlank()) "Login ($connectedEmail)" else "Autenticação",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar login")
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                userAgentString = userAgentString.replace("; wv", "")
                            }
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onCloseWindow(window: WebView?) {
                                    onDismiss()
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (connectedEmail.isNotBlank() && url != null && ArenaAccountHelper.isLoginOrAuthUrl(url)) {
                                        ArenaAccountHelper.injectConnectedEmailIntoWebView(view, connectedEmail)
                                    }
                                }
                            }

                            val transport = resultMsg.obj as? WebView.WebViewTransport
                            transport?.webView = this
                            resultMsg.sendToTarget()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NetworkErrorView(
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.network_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage ?: stringResource(R.string.network_error_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("retry_connection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun shareUrl(context: Context, url: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, url)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Compartilhar Arena AI")
    context.startActivity(shareIntent)
}

private fun openInExternalBrowser(context: Context, url: String) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Não foi possível abrir o navegador", Toast.LENGTH_SHORT).show()
    }
}
