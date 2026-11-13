package com.example

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ArenaWebViewState {
    var currentUrl by mutableStateOf("https://arena.ai/agent")
    var pageTitle by mutableStateOf("Arena AI")
    var isLoading by mutableStateOf(true)
    var progress by mutableFloatStateOf(0f)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var hasError by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isFullscreen by mutableStateOf(false)
    var desktopMode by mutableStateOf(false)
    var textZoom by mutableIntStateOf(100)

    // Generation, notifications, language, screen wake, privacy, and background status
    var isGenerating by mutableStateOf(false)
    var notifyOnFinish by mutableStateOf(true)
    var backgroundServiceEnabled by mutableStateOf(true)
    var keepScreenAwakeOnOpen by mutableStateOf(true)
    var keepScreenAwakeOnGenerating by mutableStateOf(true)
    var extensionsAutoLoad by mutableStateOf(true)
    var autoScanAddonsOnStartup by mutableStateOf(true)
    var autoLoginPromptEnabled by mutableStateOf(true)
    var keepSessionCookies by mutableStateOf(true)
    var language by mutableStateOf(AppLanguage.AUTO.code)
    var isInBackground by mutableStateOf(false)
    var lastResponseSnippet by mutableStateOf<String?>(null)

    // Account and add-ons state
    var connectedEmail by mutableStateOf("")
    var pendingAuthUrl by mutableStateOf<String?>(null)
    var hasPromptedAccountForCurrentAuth by mutableStateOf(false)
    var addons by mutableStateOf<List<ArenaAddon>>(emptyList())
    var isScanningAddons by mutableStateOf(false)

    // File chooser callback holder
    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var fileChooserParams: WebChromeClient.FileChooserParams? = null
}
