package com.example

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ArenaOriginGuard {
    const val ALLOWED_ORIGIN = "https://arena.ai"
    const val BRIDGE_NAME = "_arenaNativeBridge"
    const val MAX_MESSAGE_CHARS = 524_288
    const val MAX_PREVIEW_CHARS = 1_000
    const val MAX_NOTIFICATION_TITLE_CHARS = 120
    const val MAX_NOTIFICATION_MESSAGE_CHARS = 1_000
    const val MAX_URL_CHARS = 2_048

    val ALLOWED_OPERATIONS: Set<String> = setOf(
        "generation.started",
        "generation.finished",
        "auth.requestLogin",
        "notification.show",
        "filesystem.getDir",
        "filesystem.getAuthorizedUri",
        "filesystem.list",
        "filesystem.read",
        "filesystem.write",
        "filesystem.exists",
        "filesystem.mkdir",
        "filesystem.delete",
        "filesystem.pickDirectory"
    )

    private val TRUSTED_AUTH_HOSTS: Set<String> = setOf(
        "auth.arena.ai",
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "appleid.apple.com"
    )

    fun isWebMessageBridgeSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        } catch (_: Exception) {
            false
        }
    }

    fun isAuthorizedOrigin(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val port = uri.port
        return scheme == "https" && host == "arena.ai" && (port == -1 || port == 443)
    }

    fun isAuthorizedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            isAuthorizedOrigin(Uri.parse(url))
        } catch (_: Exception) {
            false
        }
    }

    fun isTrustedAuthNavigation(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > MAX_URL_CHARS) return false
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "https") return false
            val host = uri.host?.lowercase() ?: return false
            val path = uri.encodedPath?.lowercase().orEmpty()
            when {
                host == "arena.ai" -> ArenaAccountHelper.isLoginOrAuthUrl(url)
                host in TRUSTED_AUTH_HOSTS -> true
                host == "github.com" -> path.startsWith("/login") || path.startsWith("/sessions")
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}

class ArenaOriginGuardBridge(
    private val context: Context,
    private val state: ArenaWebViewState,
    private val fileBridge: ArenaFileBridge,
    private val onLoginRequested: (String?) -> Unit
) : WebViewCompat.WebMessageListener {

    private val jsInterface = ArenaJsInterface(context, state, onLoginRequested)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightCallIds = ConcurrentHashMap.newKeySet<String>()
    private val isPickingDirectory = AtomicBoolean(false)
    @Volatile
    private var lastLoginPromptAtMs = 0L

    private val callIdRegex = Regex("^[a-zA-Z0-9._:-]{1,128}$")

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        val currentPageUrl = view.url
        handleIncomingMessage(
            sourceOrigin = sourceOrigin,
            isMainFrame = isMainFrame,
            currentPageUrl = currentPageUrl,
            messageType = message.type,
            rawData = message.data
        ) { responseJson ->
            mainHandler.post {
                try {
                    replyProxy.postMessage(responseJson)
                } catch (_: Exception) {
                }
            }
        }
    }

    internal fun handleIncomingMessage(
        sourceOrigin: Uri?,
        isMainFrame: Boolean,
        currentPageUrl: String?,
        messageType: Int,
        rawData: String?,
        reply: (String) -> Unit
    ) {
        if (!ArenaOriginGuard.isAuthorizedOrigin(sourceOrigin) || !isMainFrame) {
            reply(buildErrorJson(null, "UNAUTHORIZED_ORIGIN", "Origin or frame is not authorized."))
            return
        }

        if (!currentPageUrl.isNullOrBlank() && !ArenaOriginGuard.isAuthorizedUrl(currentPageUrl)) {
            reply(buildErrorJson(null, "UNAUTHORIZED_PAGE_URL", "Current WebView URL is not authorized."))
            return
        }

        if (messageType != WebMessageCompat.TYPE_STRING || rawData == null) {
            reply(buildErrorJson(null, "INVALID_MESSAGE_TYPE", "Expected UTF-8 JSON string message."))
            return
        }

        if (rawData.isBlank() || rawData.length > ArenaOriginGuard.MAX_MESSAGE_CHARS) {
            reply(buildErrorJson(null, "INVALID_MESSAGE_SIZE", "Message payload exceeds allowed size limit."))
            return
        }

        val root = try {
            JSONObject(rawData)
        } catch (_: JSONException) {
            reply(buildErrorJson(null, "MALFORMED_JSON", "Malformed JSON bridge request."))
            return
        }

        val callId = root.optString("callId", "").trim()
        if (!callIdRegex.matches(callId)) {
            reply(buildErrorJson(null, "INVALID_CALL_ID", "Missing or invalid callId."))
            return
        }

        val op = root.optString("op", "").trim()
        if (op.isBlank() || op !in ArenaOriginGuard.ALLOWED_OPERATIONS) {
            reply(buildErrorJson(callId, "OPERATION_NOT_ALLOWED", "Operation is not in the allowlist."))
            return
        }

        if (root.has("payload") && !root.isNull("payload") && root.optJSONObject("payload") == null) {
            reply(buildErrorJson(callId, "INVALID_PAYLOAD", "Payload must be a JSON object."))
            return
        }
        val payload = root.optJSONObject("payload") ?: JSONObject()

        if (!inFlightCallIds.add(callId)) {
            reply(buildErrorJson(callId, "DUPLICATE_CALL_ID", "Duplicate in-flight callId rejected."))
            return
        }

        dispatchAuthorizedOperation(callId, op, payload, reply)
    }

    private fun dispatchAuthorizedOperation(
        callId: String,
        op: String,
        payload: JSONObject,
        reply: (String) -> Unit
    ) {
        when (op) {
            "generation.started" -> {
                mainHandler.post {
                    try {
                        jsInterface.onGenerationStarted()
                        reply(buildSuccessJson(callId, JSONObject().put("ok", true)))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "generation.finished" -> {
                val preview = payload.optString("preview", "")
                    .take(ArenaOriginGuard.MAX_PREVIEW_CHARS)
                mainHandler.post {
                    try {
                        jsInterface.onGenerationFinished(preview)
                        reply(buildSuccessJson(callId, JSONObject().put("ok", true)))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "auth.requestLogin" -> {
                val sourceUrl = payload.optString("sourceUrl", "").trim()
                if (sourceUrl.isNotEmpty() && !isValidHttpOrHttpsUrl(sourceUrl)) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Invalid sourceUrl."))
                    return
                }
                mainHandler.post {
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastLoginPromptAtMs < 2_000L) {
                            reply(buildErrorJson(callId, "RATE_LIMITED", "Login prompt already requested recently."))
                            return@post
                        }
                        lastLoginPromptAtMs = now
                        jsInterface.requestLogin(sourceUrl.ifBlank { null })
                        reply(buildSuccessJson(callId, JSONObject().put("ok", true)))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "notification.show" -> {
                val title = payload.optString("title", "Arena AI")
                    .trim()
                    .take(ArenaOriginGuard.MAX_NOTIFICATION_TITLE_CHARS)
                val message = payload.optString("message", "")
                    .trim()
                    .take(ArenaOriginGuard.MAX_NOTIFICATION_MESSAGE_CHARS)
                if (message.isBlank() && title.isBlank()) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Notification message is required."))
                    return
                }
                mainHandler.post {
                    try {
                        jsInterface.showCustomNotification(title.ifBlank { "Arena AI" }, message)
                        reply(
                            buildSuccessJson(
                                callId,
                                JSONObject()
                                    .put("ok", true)
                                    .put("shown", state.notifyOnFinish)
                            )
                        )
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.getDir" -> {
                ioScope.launch {
                    try {
                        val dir = fileBridge.getArenaDir()
                        reply(buildSuccessJson(callId, JSONObject().put("path", dir)))
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to resolve ArenaAI directory."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.getAuthorizedUri" -> {
                ioScope.launch {
                    try {
                        val uri = fileBridge.getAuthorizedDirectoryUri()
                        reply(buildSuccessJson(callId, JSONObject().put("uri", uri)))
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to read authorized URI."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.list" -> {
                val path = payload.optString("path", ".")
                if (path.length > ArenaFileBridge.MAX_PATH_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Path exceeds allowed length."))
                    return
                }
                ioScope.launch {
                    try {
                        val rawJson = fileBridge.listFiles(path)
                        val parsed = JSONObject(rawJson)
                        if (parsed.optBoolean("ok", false)) {
                            reply(buildSuccessJson(callId, parsed))
                        } else {
                            reply(
                                buildErrorJson(
                                    callId,
                                    "FILESYSTEM_ERROR",
                                    parsed.optString("error", "Unable to list directory.")
                                )
                            )
                        }
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to list directory."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.read" -> {
                val path = payload.optString("path", "").trim()
                if (path.isBlank() || path.length > ArenaFileBridge.MAX_PATH_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Valid path is required."))
                    return
                }
                ioScope.launch {
                    try {
                        if (!fileBridge.isPathAllowed(path)) {
                            reply(buildErrorJson(callId, "SANDBOX_VIOLATION", "Path outside allowed sandbox."))
                            return@launch
                        }
                        val exists = fileBridge.exists(path)
                        val content = if (exists) fileBridge.readFile(path) else null
                        reply(
                            buildSuccessJson(
                                callId,
                                JSONObject()
                                    .put("exists", exists)
                                    .put("content", content ?: JSONObject.NULL)
                            )
                        )
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to read file."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.write" -> {
                val path = payload.optString("path", "").trim()
                if (path.isBlank() || path.length > ArenaFileBridge.MAX_PATH_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Valid path is required."))
                    return
                }
                if (!payload.has("content") || payload.isNull("content")) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "File content string is required."))
                    return
                }
                val content = payload.optString("content", "")
                if (content.length > ArenaFileBridge.MAX_WRITE_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "PAYLOAD_TOO_LARGE", "File content exceeds size limit."))
                    return
                }
                ioScope.launch {
                    try {
                        if (!fileBridge.isPathAllowed(path)) {
                            reply(buildErrorJson(callId, "SANDBOX_VIOLATION", "Path outside allowed sandbox."))
                            return@launch
                        }
                        val written = fileBridge.writeFile(path, content)
                        reply(buildSuccessJson(callId, JSONObject().put("written", written)))
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to write file."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.exists" -> {
                val path = payload.optString("path", "").trim()
                if (path.isBlank() || path.length > ArenaFileBridge.MAX_PATH_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Valid path is required."))
                    return
                }
                ioScope.launch {
                    try {
                        if (!fileBridge.isPathAllowed(path)) {
                            reply(buildErrorJson(callId, "SANDBOX_VIOLATION", "Path outside allowed sandbox."))
                            return@launch
                        }
                        val exists = fileBridge.exists(path)
                        reply(buildSuccessJson(callId, JSONObject().put("exists", exists)))
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to check path."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.mkdir" -> {
                val path = payload.optString("path", "").trim()
                if (path.isBlank() || path.length > ArenaFileBridge.MAX_PATH_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Valid path is required."))
                    return
                }
                ioScope.launch {
                    try {
                        if (!fileBridge.isPathAllowed(path)) {
                            reply(buildErrorJson(callId, "SANDBOX_VIOLATION", "Path outside allowed sandbox."))
                            return@launch
                        }
                        val created = fileBridge.mkdir(path)
                        reply(buildSuccessJson(callId, JSONObject().put("created", created)))
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to create directory."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.delete" -> {
                val path = payload.optString("path", "").trim()
                if (path.isBlank() || path.length > ArenaFileBridge.MAX_PATH_CHARS) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "INVALID_ARGUMENT", "Valid path is required."))
                    return
                }
                ioScope.launch {
                    try {
                        if (!fileBridge.isPathAllowed(path)) {
                            reply(buildErrorJson(callId, "SANDBOX_VIOLATION", "Path outside allowed sandbox."))
                            return@launch
                        }
                        val deleted = fileBridge.deleteFile(path)
                        reply(buildSuccessJson(callId, JSONObject().put("deleted", deleted)))
                    } catch (_: Exception) {
                        reply(buildErrorJson(callId, "FILESYSTEM_ERROR", "Unable to delete path."))
                    } finally {
                        inFlightCallIds.remove(callId)
                    }
                }
            }

            "filesystem.pickDirectory" -> {
                if (!isPickingDirectory.compareAndSet(false, true)) {
                    inFlightCallIds.remove(callId)
                    reply(buildErrorJson(callId, "OPERATION_IN_PROGRESS", "Directory picker is already open."))
                    return
                }
                fileBridge.pickDirectoryAsync { uri ->
                    try {
                        reply(buildSuccessJson(callId, JSONObject().put("uri", uri)))
                    } finally {
                        isPickingDirectory.set(false)
                        inFlightCallIds.remove(callId)
                    }
                }
            }
        }
    }

    fun destroy() {
        ioScope.cancel()
        inFlightCallIds.clear()
        isPickingDirectory.set(false)
    }

    private fun isValidHttpOrHttpsUrl(url: String): Boolean {
        if (url.length > ArenaOriginGuard.MAX_URL_CHARS) return false
        return try {
            val scheme = Uri.parse(url).scheme?.lowercase()
            scheme == "https" || scheme == "http"
        } catch (_: Exception) {
            false
        }
    }

    private fun buildSuccessJson(callId: String, data: JSONObject): String {
        return JSONObject().apply {
            put("callId", callId)
            put("ok", true)
            put("data", data)
        }.toString()
    }

    private fun buildErrorJson(callId: String?, code: String, message: String): String {
        return JSONObject().apply {
            put("callId", callId ?: JSONObject.NULL)
            put("ok", false)
            put(
                "error",
                JSONObject().apply {
                    put("code", code)
                    put("message", message)
                }
            )
        }.toString()
    }
}

class ArenaJsInterface(
    private val context: Context,
    private val state: ArenaWebViewState,
    private val onLoginRequested: (String?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onGenerationStarted() {
        mainHandler.post {
            state.isGenerating = true
            if (state.backgroundServiceEnabled && state.notifyOnFinish) {
                NotificationHelper.startBackgroundService(context)
            }
        }
    }

    fun onGenerationFinished(preview: String?) {
        mainHandler.post {
            state.isGenerating = false
            state.lastResponseSnippet = preview

            if (state.notifyOnFinish) {
                NotificationHelper.showResponseNotification(context, preview)
            } else {
                NotificationHelper.cancelAllNotifications(context)
            }
        }
    }

    fun requestLogin(sourceUrl: String?) {
        mainHandler.post {
            onLoginRequested(sourceUrl)
        }
    }

    fun showCustomNotification(title: String, message: String) {
        mainHandler.post {
            if (!state.notifyOnFinish) {
                return@post
            }
            NotificationHelper.showResponseNotification(
                context,
                if (title.isNotBlank()) "$title: $message" else message
            )
        }
    }

    companion object {
        const val SCRIPT_INJECTION = """
(function() {
    if (window._arenaMonitorInstalled) return;
    window._arenaMonitorInstalled = true;

    var isGenerating = false;
    var sawStopButton = false;
    var settleTimer = null;
    var checkInterval = null;
    var lastLoginPromptAt = 0;
    var lastChatMutationAt = Date.now();
    var lastIdleObservedAt = 0;
    var DOM_SETTLE_DEBOUNCE_MS = 1000;
    var IDLE_CONFIRM_MS = 1800;

    function sendNative(op, payload) {
        try {
            if (window.ArenaAI && window.ArenaAI.native && typeof window.ArenaAI.native.invoke === 'function') {
                window.ArenaAI.native.invoke(op, payload || {}).catch(function() {});
                return;
            }
            if (window._arenaNativeBridge && typeof window._arenaNativeBridge.postMessage === 'function') {
                var cid = 'mon_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
                window._arenaNativeBridge.postMessage(JSON.stringify({
                    callId: cid,
                    op: op,
                    payload: payload || {}
                }));
            }
        } catch (e) {}
    }

    function isAddonElement(node) {
        if (!node) return false;
        var el = node.nodeType === 1 ? node : node.parentElement;
        if (!el || !el.closest) return false;
        return !!(el.closest('#autoqueue-panel') || el.closest('[data-arena-addon]') || el.closest('[data-arena-ui]'));
    }

    function getChatContainer() {
        return document.querySelector('main, [role="main"], [class*="chat" i], [class*="conversation" i], [class*="messages" i]') || document.body;
    }

    /**
     * Exact detection for Arena AI composer button:
     * - While generating: button[data-perf-target="send"] has aria-label="Stop generating" and <svg><rect .../></svg> (square stop icon).
     * - When finished: button[data-perf-target="send"] switches back to aria-label="Send message" with the right-arrow <svg> (no <rect>).
     */
    function isStopButtonActive() {
        var composerBtn = document.querySelector('button[data-perf-target="send"]');
        if (composerBtn) {
            var label = (composerBtn.getAttribute('aria-label') || '').toLowerCase();
            if (label.indexOf('stop') !== -1) return true;
            if (composerBtn.querySelector('svg rect')) return true;
            if (label.indexOf('send') !== -1) return false;
        }
        return !!document.querySelector(
            'button[aria-label="Stop generating"], ' +
            'button[aria-label*="Stop generating" i], ' +
            'button[title*="Stop generating" i], ' +
            'button[data-testid*="stop" i]'
        );
    }

    function isSendButtonIdle() {
        var composerBtn = document.querySelector('button[data-perf-target="send"]');
        if (composerBtn) {
            var label = (composerBtn.getAttribute('aria-label') || '').toLowerCase();
            var hasStopSquare = !!composerBtn.querySelector('svg rect');
            return label.indexOf('send') !== -1 && !hasStopSquare;
        }
        var sendBtn = document.querySelector('button[aria-label="Send message"], button[aria-label*="Send message" i]');
        return !!(sendBtn && !sendBtn.querySelector('svg rect'));
    }

    function isStillActive() {
        if (isStopButtonActive()) return true;
        var explicitStreaming = document.querySelector(
            '[data-is-streaming="true"], .result-streaming'
        );
        return !!explicitStreaming;
    }

    function clearSettleTimer() {
        if (settleTimer) {
            clearTimeout(settleTimer);
            settleTimer = null;
        }
    }

    function scheduleSettleCheck() {
        clearSettleTimer();
        var now = Date.now();
        if (!lastIdleObservedAt) {
            lastIdleObservedAt = now;
        }
        var remainingIdle = Math.max(0, IDLE_CONFIRM_MS - (now - lastIdleObservedAt));
        var remainingDomQuiet = Math.max(0, DOM_SETTLE_DEBOUNCE_MS - (now - lastChatMutationAt));
        var waitMs = Math.max(remainingIdle, remainingDomQuiet, 350);

        settleTimer = setTimeout(function() {
            settleTimer = null;
            if (!isGenerating || !sawStopButton) return;

            if (isStillActive()) {
                lastIdleObservedAt = 0;
                return;
            }

            if (!isSendButtonIdle()) {
                lastIdleObservedAt = 0;
                scheduleSettleCheck();
                return;
            }

            var currentNow = Date.now();
            var quietFor = currentNow - lastChatMutationAt;
            var idleFor = currentNow - lastIdleObservedAt;
            if (quietFor < DOM_SETTLE_DEBOUNCE_MS || idleFor < IDLE_CONFIRM_MS) {
                scheduleSettleCheck();
                return;
            }

            isGenerating = false;
            sawStopButton = false;
            lastIdleObservedAt = 0;

            var assistantBlocks = document.querySelectorAll(
                '[data-message-author-role="assistant"], .prose, .markdown, .agent-response, [class*="assistant"], [class*="agent-message"]'
            );
            var latestText = "";
            if (assistantBlocks.length > 0) {
                latestText = assistantBlocks[assistantBlocks.length - 1].innerText || "";
            }

            if (window.ArenaAI && window.ArenaAI._emitResponseComplete) {
                window.ArenaAI._emitResponseComplete(latestText);
            }
            sendNative('generation.finished', { preview: latestText.substring(0, 300) });
        }, waitMs);
    }

    function detectGeneratingState() {
        var stopActive = isStopButtonActive();
        var activeNow = stopActive || isStillActive();

        if (activeNow) {
            if (stopActive) {
                sawStopButton = true;
            }
            lastIdleObservedAt = 0;
            clearSettleTimer();
            if (!isGenerating && stopActive) {
                isGenerating = true;
                sendNative('generation.started', {});
            }
        } else if (isGenerating) {
            if (!sawStopButton) {
                isGenerating = false;
                lastIdleObservedAt = 0;
                clearSettleTimer();
                return;
            }
            if (isSendButtonIdle()) {
                if (!lastIdleObservedAt) {
                    lastIdleObservedAt = Date.now();
                }
                if (!settleTimer) {
                    scheduleSettleCheck();
                }
            } else {
                lastIdleObservedAt = 0;
                clearSettleTimer();
            }
        }
    }

    function isLoginTriggerElement(el) {
        if (!el) return false;
        var target = el.closest('button, a, [role="button"]');
        if (!target) return false;
        var text = (target.innerText || target.getAttribute('aria-label') || '').toLowerCase();
        var href = (target.getAttribute('href') || '').toLowerCase();
        return text.includes('sign in') ||
               text.includes('log in') ||
               text.includes('login') ||
               text.includes('entrar') ||
               text.includes('continue with google') ||
               text.includes('continuar com google') ||
               href.includes('/login') ||
               href.includes('/signin') ||
               href.includes('/auth');
    }

    document.addEventListener('click', function(e) {
        var el = e.target;
        if (isLoginTriggerElement(el)) {
            var now = Date.now();
            if (now - lastLoginPromptAt > 3000) {
                lastLoginPromptAt = now;
                sendNative('auth.requestLogin', { sourceUrl: window.location.href });
            }
        }

        if (el && !isAddonElement(el) && (el.closest('button[data-perf-target="send"]') || el.closest('button[type="submit"]') || el.closest('button[aria-label*="send" i]'))) {
            var textarea = document.querySelector('textarea');
            if (textarea && textarea.value && window.ArenaAI && window.ArenaAI._emitPromptSubmit) {
                window.ArenaAI._emitPromptSubmit(textarea.value);
            }
            setTimeout(detectGeneratingState, 250);
        }
    }, true);

    document.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey && !isAddonElement(e.target)) {
            var textarea = document.querySelector('textarea');
            if (textarea && textarea.value && window.ArenaAI && window.ArenaAI._emitPromptSubmit) {
                window.ArenaAI._emitPromptSubmit(textarea.value);
            }
            setTimeout(detectGeneratingState, 350);
        }
    }, true);

    var observer = new MutationObserver(function(mutations) {
        var hasChatMutation = false;
        for (var i = 0; i < mutations.length; i++) {
            if (!isAddonElement(mutations[i].target)) {
                hasChatMutation = true;
                break;
            }
        }
        if (hasChatMutation) {
            lastChatMutationAt = Date.now();
            if (isGenerating && settleTimer && !isStillActive() && isSendButtonIdle()) {
                scheduleSettleCheck();
            }
            detectGeneratingState();
        }
    });

    function attachObserver() {
        var root = document.body || getChatContainer();
        if (root) {
            observer.observe(root, { childList: true, subtree: true, characterData: true, attributes: true });
        }
    }

    if (document.body) {
        attachObserver();
    } else {
        document.addEventListener('DOMContentLoaded', attachObserver);
    }

    checkInterval = setInterval(detectGeneratingState, 600);
})();
        """
    }
}
