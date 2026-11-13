package com.example

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AddonType {
    EXTENSION,
    PLUGIN
}

data class ArenaAddon(
    val id: String,
    val name: String,
    val version: String,
    val type: AddonType,
    val description: String,
    val author: String,
    val minAppVersion: String,
    val compatible: Boolean,
    val compatibilityReason: String,
    val matchUrls: List<String>,
    val scriptContent: String,
    val cssContent: String,
    val sourcePath: String,
    val isEnabled: Boolean
)

object ArenaAddonManager {

    private const val APP_VERSION = BuildConfig.VERSION_NAME

    fun scanAllAddons(context: Context, settings: ArenaSettings): List<ArenaAddon> {
        val found = linkedMapOf<String, ArenaAddon>()

        // 1. Scan App External Mirror: <externalFilesDir>/ArenaAI/{extensions, plugins}
        val appExternalRoot = context.getExternalFilesDir(null)
        if (appExternalRoot != null) {
            val arenaRoot = File(appExternalRoot, ArenaSettingsRepository.ROOT_FOLDER)
            scanArenaRootFileDir(arenaRoot, settings, found)
        }

        // 2. Scan /storage/emulated/0/ArenaAI/{extensions, plugins} if readable
        try {
            @Suppress("DEPRECATION")
            val rootStorage = Environment.getExternalStorageDirectory()
            if (rootStorage != null) {
                scanArenaRootFileDir(File(rootStorage, ArenaSettingsRepository.ROOT_FOLDER), settings, found)
            }
        } catch (_: Exception) {
        }

        // 3. Scan Public Documents/ArenaAI/{extensions, plugins} directly if accessible
        try {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            scanArenaRootFileDir(File(docsDir, ArenaSettingsRepository.ROOT_FOLDER), settings, found)
        } catch (_: Exception) {
        }

        // 4. Scan MediaStore entries inside Documents/ArenaAI/{extensions, plugins} (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanMediaStoreAddons(context, settings, found)
        }

        // 5. Scan user-authorized SAF tree URI if configured
        if (settings.safTreeUri.isNotBlank()) {
            try {
                scanSafTree(context, Uri.parse(settings.safTreeUri), settings, found)
            } catch (_: Exception) {
            }
        }

        return found.values.toList().sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
    }

    private fun scanArenaRootFileDir(
        arenaRoot: File,
        settings: ArenaSettings,
        found: MutableMap<String, ArenaAddon>
    ) {
        scanLocalDirectory(File(arenaRoot, ArenaSettingsRepository.EXTENSIONS_SUBFOLDER), AddonType.EXTENSION, settings, found)
        scanLocalDirectory(File(arenaRoot, "extension"), AddonType.EXTENSION, settings, found)
        scanLocalDirectory(File(arenaRoot, ArenaSettingsRepository.LEGACY_EXTENSIONS_SUBFOLDER), AddonType.EXTENSION, settings, found)
        scanLocalDirectory(File(arenaRoot, ArenaSettingsRepository.LEGACY_EXTENSIONS_ALT_SUBFOLDER), AddonType.EXTENSION, settings, found)
        scanLocalDirectory(File(arenaRoot, ArenaSettingsRepository.PLUGINS_SUBFOLDER), AddonType.PLUGIN, settings, found)
        scanLocalDirectory(File(arenaRoot, ArenaSettingsRepository.LEGACY_PLUGINS_SUBFOLDER), AddonType.PLUGIN, settings, found)
    }

    private fun scanLocalDirectory(
        dir: File,
        defaultType: AddonType,
        settings: ArenaSettings,
        result: MutableMap<String, ArenaAddon>
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        val children = dir.listFiles() ?: return

        for (child in children) {
            if (child.isDirectory) {
                val manifest = File(child, "manifest.json")
                if (manifest.exists() && manifest.isFile) {
                    parseManifestJson(
                        jsonText = manifest.readText(),
                        defaultType = defaultType,
                        sourcePath = manifest.absolutePath,
                        siblingTextProvider = { fileName ->
                            val target = File(child, fileName)
                            if (target.exists() && target.isFile) target.readText() else null
                        },
                        settings = settings
                    )?.let { result[it.id] = it }
                }
            } else if (child.isFile) {
                val lower = child.name.lowercase()
                if (lower == ArenaSettingsRepository.SETTINGS_FILE_NAME) continue
                when {
                    lower.endsWith(".json") -> {
                        parseManifestJson(
                            jsonText = child.readText(),
                            defaultType = defaultType,
                            sourcePath = child.absolutePath,
                            siblingTextProvider = { fileName ->
                                val target = File(dir, fileName)
                                if (target.exists() && target.isFile) target.readText() else null
                            },
                            settings = settings
                        )?.let { result[it.id] = it }
                    }
                    lower.endsWith(".js") || lower.endsWith(".mjs") -> {
                        parseStandaloneJs(
                            fileName = child.name,
                            jsText = child.readText(),
                            defaultType = defaultType,
                            sourcePath = child.absolutePath,
                            settings = settings
                        ).let { result[it.id] = it }
                    }
                }
            }
        }
    }

    private fun scanMediaStoreAddons(
        context: Context,
        settings: ArenaSettings,
        result: MutableMap<String, ArenaAddon>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%${ArenaSettingsRepository.ROOT_FOLDER}/%")

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val fileName = cursor.getString(nameCol) ?: continue
                    val relPath = cursor.getString(pathCol) ?: ""
                    val lower = fileName.lowercase()
                    if (lower == ArenaSettingsRepository.SETTINGS_FILE_NAME) continue
                    if (!lower.endsWith(".js") && !lower.endsWith(".mjs") && !lower.endsWith(".json")) continue

                    // Only scan files inside extension or plugin subfolders
                    val inExtFolder = relPath.contains("extension", ignoreCase = true) ||
                        relPath.contains("extencao", ignoreCase = true) ||
                        relPath.contains("extensão", ignoreCase = true)
                    val inPluginFolder = relPath.contains("plugin", ignoreCase = true)
                    if (!inExtFolder && !inPluginFolder) continue

                    val type = if (inPluginFolder) AddonType.PLUGIN else AddonType.EXTENSION

                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: continue

                    val addon = if (lower.endsWith(".json")) {
                        parseManifestJson(text, type, "$relPath$fileName", { null }, settings)
                    } else {
                        parseStandaloneJs(fileName, text, type, "$relPath$fileName", settings)
                    }
                    if (addon != null && !result.containsKey(addon.id)) {
                        result[addon.id] = addon
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun scanSafTree(
        context: Context,
        treeUri: Uri,
        settings: ArenaSettings,
        result: MutableMap<String, ArenaAddon>
    ) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        scanSafDocumentFolder(
            context = context,
            treeUri = treeUri,
            parentDocId = rootDocId,
            inheritedType = null,
            settings = settings,
            result = result,
            depth = 0
        )
    }

    private fun scanSafDocumentFolder(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        inheritedType: AddonType?,
        settings: ArenaSettings,
        result: MutableMap<String, ArenaAddon>,
        depth: Int
    ) {
        if (depth > 3) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val entries = mutableListOf<Triple<String, String, String>>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                entries.add(
                    Triple(
                        cursor.getString(idIdx),
                        cursor.getString(nameIdx) ?: "",
                        cursor.getString(mimeIdx) ?: ""
                    )
                )
            }
        }

        val fileMap = entries.associate { it.second to it.first }

        for ((docId, name, mime) in entries) {
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                val childType = when {
                    name.contains("plugin", ignoreCase = true) -> AddonType.PLUGIN
                    name.contains("exten", ignoreCase = true) -> AddonType.EXTENSION
                    else -> inheritedType
                }
                scanSafDocumentFolder(context, treeUri, docId, childType, settings, result, depth + 1)
            } else if (inheritedType != null) {
                val lower = name.lowercase()
                if (lower == ArenaSettingsRepository.SETTINGS_FILE_NAME) continue
                if (lower.endsWith(".json")) {
                    val text = readSafDocumentText(context, treeUri, docId) ?: continue
                    parseManifestJson(
                        jsonText = text,
                        defaultType = inheritedType,
                        sourcePath = "SAF:/$name",
                        siblingTextProvider = { siblingName ->
                            fileMap[siblingName]?.let { sibDocId -> readSafDocumentText(context, treeUri, sibDocId) }
                        },
                        settings = settings
                    )?.let { result[it.id] = it }
                } else if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
                    val text = readSafDocumentText(context, treeUri, docId) ?: continue
                    val addon = parseStandaloneJs(name, text, inheritedType, "SAF:/$name", settings)
                    if (!result.containsKey(addon.id)) {
                        result[addon.id] = addon
                    }
                }
            }
        }
    }

    private fun readSafDocumentText(context: Context, treeUri: Uri, docId: String): String? {
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.openInputStream(docUri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseManifestJson(
        jsonText: String,
        defaultType: AddonType,
        sourcePath: String,
        siblingTextProvider: (String) -> String?,
        settings: ArenaSettings
    ): ArenaAddon? {
        return try {
            val obj = JSONObject(jsonText)
            val id = obj.optString("id").ifBlank { sourcePath.hashCode().toString() }
            val name = obj.optString("name").ifBlank { id }
            val version = obj.optString("version", "1.0.0")
            val typeStr = obj.optString("type", "").lowercase()
            val type = when {
                typeStr == "plugin" -> AddonType.PLUGIN
                typeStr == "extension" || typeStr == "extensao" || typeStr == "extensão" -> AddonType.EXTENSION
                else -> defaultType
            }
            val description = obj.optString("description", "Custom add-on for Arena AI.")
            val author = obj.optString("author", "Developer")
            val minAppVersion = obj.optString("minAppVersion", "1.0.0")
            val declaredCompatible = obj.optBoolean("compatible", true)

            val matchUrls = mutableListOf<String>()
            val matchArr: JSONArray? = obj.optJSONArray("matchUrls")
            if (matchArr != null) {
                for (i in 0 until matchArr.length()) {
                    val pattern = matchArr.optString(i)
                    if (pattern.isNotBlank()) matchUrls.add(pattern)
                }
            }
            if (matchUrls.isEmpty()) matchUrls.add("*://arena.ai/*")

            val entryPoint = obj.optString("entryPoint", "")
            val inlineScript = obj.optString("script", "")
            val scriptContent = when {
                inlineScript.isNotBlank() -> inlineScript
                entryPoint.isNotBlank() -> siblingTextProvider(entryPoint) ?: ""
                else -> siblingTextProvider("index.js") ?: ""
            }

            val stylesPath = obj.optString("styles", "")
            val inlineCss = obj.optString("css", "")
            val cssContent = when {
                inlineCss.isNotBlank() -> inlineCss
                stylesPath.isNotBlank() -> siblingTextProvider(stylesPath) ?: ""
                else -> ""
            }

            val (isCompat, reason) = evaluateCompatibility(declaredCompatible, minAppVersion, matchUrls, scriptContent, cssContent)

            ArenaAddon(
                id = id,
                name = name,
                version = version,
                type = type,
                description = description,
                author = author,
                minAppVersion = minAppVersion,
                compatible = isCompat,
                compatibilityReason = reason,
                matchUrls = matchUrls,
                scriptContent = scriptContent,
                cssContent = cssContent,
                sourcePath = sourcePath,
                isEnabled = !settings.disabledAddonIds.contains(id)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStandaloneJs(
        fileName: String,
        jsText: String,
        defaultType: AddonType,
        sourcePath: String,
        settings: ArenaSettings
    ): ArenaAddon {
        fun extractTag(tag: String): String? {
            val regex = Regex("""//\s*@$tag\s+(.+)""")
            return regex.find(jsText)?.groupValues?.getOrNull(1)?.trim()
        }

        val id = extractTag("id") ?: fileName.substringBeforeLast(".")
        val name = extractTag("name") ?: fileName.substringBeforeLast(".")
        val version = extractTag("version") ?: "1.0.0"
        val typeTag = extractTag("type")?.lowercase()
        val type = when {
            typeTag == "plugin" -> AddonType.PLUGIN
            typeTag == "extension" || typeTag == "extensao" || typeTag == "extensão" -> AddonType.EXTENSION
            else -> defaultType
        }
        val description = extractTag("description") ?: "JavaScript add-on loaded from ArenaAI directory."
        val author = extractTag("author") ?: "Local"
        val minAppVersion = extractTag("minAppVersion") ?: "1.0.0"
        val compatibleFlag = extractTag("compatible")?.lowercase() != "false"
        val match = extractTag("match")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf("*://arena.ai/*")

        val (isCompat, reason) = evaluateCompatibility(compatibleFlag, minAppVersion, match, jsText, "")

        return ArenaAddon(
            id = id,
            name = name,
            version = version,
            type = type,
            description = description,
            author = author,
            minAppVersion = minAppVersion,
            compatible = isCompat,
            compatibilityReason = reason,
            matchUrls = match,
            scriptContent = jsText,
            cssContent = "",
            sourcePath = sourcePath,
            isEnabled = !settings.disabledAddonIds.contains(id)
        )
    }

    private fun evaluateCompatibility(
        declaredCompatible: Boolean,
        minAppVersion: String,
        matchUrls: List<String>,
        scriptContent: String,
        cssContent: String
    ): Pair<Boolean, String> {
        if (!declaredCompatible) {
            return false to "Marked as incompatible in manifest."
        }
        if (compareVersions(APP_VERSION, minAppVersion) < 0) {
            return false to "Requires Arena AI v$minAppVersion or higher (current: v$APP_VERSION)."
        }
        if (scriptContent.isBlank() && cssContent.isBlank()) {
            return false to "No script (.js) or stylesheet (.css) found."
        }
        val urlMatches = matchUrls.any { pattern ->
            pattern == "*" || pattern == "*://*/*" || pattern.contains("arena.ai", ignoreCase = true)
        }
        if (!urlMatches) {
            return false to "Target URL is incompatible with arena.ai."
        }
        return true to "Compatible with Arena AI v$APP_VERSION"
    }

    private fun compareVersions(current: String, required: String): Int {
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val rParts = required.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(cParts.size, rParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val r = rParts.getOrElse(i) { 0 }
            if (c != r) return c.compareTo(r)
        }
        return 0
    }

    fun importAddonFromUri(
        context: Context,
        uri: Uri,
        type: AddonType,
        settings: ArenaSettings
    ): Result<String> {
        return try {
            val fileName = queryDisplayName(context, uri) ?: "addon_${System.currentTimeMillis()}.js"
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return Result.failure(IllegalStateException("Could not read the selected file."))

            val subfolder = if (type == AddonType.PLUGIN) {
                ArenaSettingsRepository.PLUGINS_SUBFOLDER
            } else {
                ArenaSettingsRepository.EXTENSIONS_SUBFOLDER
            }

            // Save to app external ArenaAI subfolder
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val targetDir = File(baseDir, "${ArenaSettingsRepository.ROOT_FOLDER}/$subfolder")
            if (!targetDir.exists()) targetDir.mkdirs()
            File(targetDir, fileName).writeText(content)

            // Mirror to public Documents/ArenaAI/<subfolder>
            ArenaSettingsRepository.writePublicDocumentText(
                context = context,
                subPath = "${ArenaSettingsRepository.ROOT_FOLDER}/$subfolder",
                fileName = fileName,
                mimeType = "application/octet-stream",
                content = content
            )

            // Mirror to SAF folder if linked
            if (settings.safTreeUri.isNotBlank()) {
                ArenaSettingsRepository.writeSafAddonFile(
                    context = context,
                    safTreeUriStr = settings.safTreeUri,
                    subfolder = subfolder,
                    fileName = fileName,
                    mimeType = "application/octet-stream",
                    content = content
                )
            }

            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteAddon(addon: ArenaAddon): Boolean {
        return try {
            val file = File(addon.sourcePath)
            if (file.exists() && file.isFile) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildSiteSdkAndAddonsScript(
        addons: List<ArenaAddon>,
        connectedEmail: String,
        languageCode: String = ArenaLocaleHelper.resolveEffectiveLanguageCode(AppLanguage.AUTO.code)
    ): String {
        val activeAddons = addons.filter { it.compatible && it.isEnabled }
        val safeEmail = JSONObject.quote(connectedEmail)
        val resolvedLang = ArenaLocaleHelper.resolveEffectiveLanguageCode(languageCode)
        val safeLang = JSONObject.quote(resolvedLang)

        val sdkHeader = """
(function() {
    if (!window.ArenaAI) {
        var _responseCallbacks = [];
        var _promptCallbacks = [];
        var _accountCallbacks = [];
        var _registeredExtensions = {};
        var _registeredPlugins = {};

        window.ArenaAI = {
            version: ${JSONObject.quote(APP_VERSION)},
            platform: 'android-webview',
            language: $safeLang,
            connectedEmail: $safeEmail,
            extensions: _registeredExtensions,
            plugins: _registeredPlugins,
            requestLogin: function() {
                if (window.AndroidBridge && window.AndroidBridge.requestLogin) {
                    window.AndroidBridge.requestLogin(window.location.href);
                }
            },
            notify: function(title, message) {
                if (window.AndroidBridge && window.AndroidBridge.showCustomNotification) {
                    window.AndroidBridge.showCustomNotification(String(title || 'Arena AI'), String(message || ''));
                }
            },
            storage: {
                get: function(key) {
                    try { return localStorage.getItem('arena_addon_' + key); } catch (e) { return null; }
                },
                set: function(key, val) {
                    try { localStorage.setItem('arena_addon_' + key, String(val)); } catch (e) {}
                }
            },
            ui: {
                injectCSS: function(cssText) {
                    try {
                        var style = document.createElement('style');
                        style.setAttribute('data-arena-addon', 'true');
                        style.textContent = cssText;
                        (document.head || document.documentElement).appendChild(style);
                    } catch (e) {}
                }
            },
            dom: {
                cleanText: function(el) {
                    if (!el) return '';
                    try {
                        var clone = el.cloneNode(true);
                        var ignored = clone.querySelectorAll('svg, style, script');
                        for (var i = 0; i < ignored.length; i++) {
                            if (ignored[i].parentNode) ignored[i].parentNode.removeChild(ignored[i]);
                        }
                        return (clone.textContent || clone.innerText || '').replace(/[\s\u00A0]+/g, ' ').trim();
                    } catch (e) {
                        return (el.innerText || el.textContent || '').replace(/[\s\u00A0]+/g, ' ').trim();
                    }
                },
                click: function(el) {
                    if (!el) return false;
                    try {
                        if (typeof el.scrollIntoView === 'function') {
                            el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
                        }
                    } catch (e) {}
                    try {
                        if (typeof el.focus === 'function') el.focus({ preventScroll: true });
                    } catch (e) {
                        try { el.focus(); } catch (e2) {}
                    }

                    var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : { left: 0, top: 0, width: 0, height: 0 };
                    var cx = Math.round(rect.left + (rect.width / 2));
                    var cy = Math.round(rect.top + (rect.height / 2));

                    var commonOpts = {
                        bubbles: true,
                        cancelable: true,
                        composed: true,
                        view: window,
                        clientX: cx,
                        clientY: cy,
                        screenX: cx,
                        screenY: cy,
                        button: 0,
                        buttons: 1
                    };

                    function dispatchPointer(type, buttonsVal) {
                        if (typeof PointerEvent === 'function') {
                            var opts = Object.assign({}, commonOpts, {
                                buttons: buttonsVal,
                                pointerId: 1,
                                width: 1,
                                height: 1,
                                pressure: buttonsVal ? 0.5 : 0,
                                pointerType: 'touch',
                                isPrimary: true
                            });
                            el.dispatchEvent(new PointerEvent(type, opts));
                        }
                    }

                    function dispatchTouch(type) {
                        try {
                            if (typeof Touch === 'function' && typeof TouchEvent === 'function') {
                                var touch = new Touch({
                                    identifier: Date.now() % 100000,
                                    target: el,
                                    clientX: cx,
                                    clientY: cy,
                                    pageX: cx + window.scrollX,
                                    pageY: cy + window.scrollY,
                                    screenX: cx,
                                    screenY: cy,
                                    radiusX: 2.5,
                                    radiusY: 2.5,
                                    force: type === 'touchstart' ? 1 : 0
                                });
                                var activeTouches = type === 'touchstart' ? [touch] : [];
                                el.dispatchEvent(new TouchEvent(type, {
                                    bubbles: true,
                                    cancelable: true,
                                    composed: true,
                                    view: window,
                                    touches: activeTouches,
                                    targetTouches: activeTouches,
                                    changedTouches: [touch]
                                }));
                                return;
                            }
                        } catch (e) {}
                        try {
                            el.dispatchEvent(new Event(type, { bubbles: true, cancelable: true }));
                        } catch (e2) {}
                    }

                    try { dispatchPointer('pointerover', 0); } catch (e) {}
                    try { dispatchPointer('pointerenter', 0); } catch (e) {}
                    try { dispatchPointer('pointerdown', 1); } catch (e) {}
                    try { dispatchTouch('touchstart'); } catch (e) {}
                    try { el.dispatchEvent(new MouseEvent('mouseover', commonOpts)); } catch (e) {}
                    try { el.dispatchEvent(new MouseEvent('mousedown', commonOpts)); } catch (e) {}
                    try { dispatchPointer('pointerup', 0); } catch (e) {}
                    try { dispatchTouch('touchend'); } catch (e) {}
                    try { el.dispatchEvent(new MouseEvent('mouseup', Object.assign({}, commonOpts, { buttons: 0 }))); } catch (e) {}
                    try { el.dispatchEvent(new MouseEvent('click', Object.assign({}, commonOpts, { buttons: 0 }))); } catch (e) {}
                    try { if (typeof el.click === 'function') el.click(); } catch (e) {}
                    try { dispatchPointer('pointercancel', 0); } catch (e) {}
                    return true;
                },
                waitFor: function(selector, timeout) {
                    var maxWait = typeof timeout === 'number' && timeout > 0 ? timeout : 10000;
                    return new Promise(function(resolve, reject) {
                        function check() {
                            try {
                                var found = typeof selector === 'function' ? selector() : document.querySelector(selector);
                                if (found) return found;
                            } catch (e) {}
                            return null;
                        }

                        var immediate = check();
                        if (immediate) {
                            resolve(immediate);
                            return;
                        }

                        var done = false;
                        var obs = null;
                        var poll = null;
                        var timer = null;

                        function cleanup() {
                            done = true;
                            if (obs) { try { obs.disconnect(); } catch (e) {} }
                            if (poll) clearInterval(poll);
                            if (timer) clearTimeout(timer);
                        }

                        function tryResolve() {
                            if (done) return;
                            var el = check();
                            if (el) {
                                cleanup();
                                resolve(el);
                            }
                        }

                        var root = document.body || document.documentElement;
                        if (root && typeof MutationObserver === 'function') {
                            obs = new MutationObserver(tryResolve);
                            obs.observe(root, { childList: true, subtree: true, attributes: true });
                        }
                        poll = setInterval(tryResolve, 150);
                        timer = setTimeout(function() {
                            if (done) return;
                            cleanup();
                            reject(new Error('ArenaAI.dom.waitFor timeout: ' + selector));
                        }, maxWait);
                    });
                }
            },
            registerExtension: function(cfg) {
                if (cfg && cfg.id) {
                    _registeredExtensions[cfg.id] = cfg;
                    if (typeof cfg.onReady === 'function') {
                        try { cfg.onReady(window.ArenaAI); } catch (e) { console.error(e); }
                    }
                }
            },
            registerPlugin: function(cfg) {
                if (cfg && cfg.id) {
                    _registeredPlugins[cfg.id] = cfg;
                    if (typeof cfg.onReady === 'function') {
                        try { cfg.onReady(window.ArenaAI); } catch (e) { console.error(e); }
                    }
                }
            },
            onResponseComplete: function(cb) {
                if (typeof cb === 'function') _responseCallbacks.push(cb);
            },
            onPromptSubmit: function(cb) {
                if (typeof cb === 'function') _promptCallbacks.push(cb);
            },
            onAccountConnected: function(cb) {
                if (typeof cb === 'function') _accountCallbacks.push(cb);
            },
            _emitResponseComplete: function(preview) {
                _responseCallbacks.forEach(function(cb) { try { cb(preview); } catch (e) {} });
            },
            _emitPromptSubmit: function(text) {
                _promptCallbacks.forEach(function(cb) { try { cb(text); } catch (e) {} });
            },
            _emitAccountConnected: function(email) {
                window.ArenaAI.connectedEmail = email;
                _accountCallbacks.forEach(function(cb) { try { cb(email); } catch (e) {} });
                window.dispatchEvent(new CustomEvent('arena-ai-login', { detail: { email: email } }));
            },
            fs: window.ArenaFS || null
        };
    } else {
        window.ArenaAI.connectedEmail = $safeEmail;
        if (window.ArenaAI.language !== $safeLang) {
            window.ArenaAI.language = $safeLang;
            window.dispatchEvent(new CustomEvent('arena-ai-language', { detail: { language: $safeLang } }));
        }
        window.ArenaAI.fs = window.ArenaFS || window.ArenaAI.fs || null;
    }
})();
        """.trimIndent()

        if (activeAddons.isEmpty()) return sdkHeader

        val sb = StringBuilder(sdkHeader)
        sb.append("\n")
        for (addon in activeAddons) {
            val safeId = JSONObject.quote(addon.id)
            sb.append("\n(function() {\n")
            sb.append("  if (window._arenaLoadedAddons && window._arenaLoadedAddons[$safeId]) return;\n")
            sb.append("  window._arenaLoadedAddons = window._arenaLoadedAddons || {};\n")
            sb.append("  window._arenaLoadedAddons[$safeId] = true;\n")
            if (addon.cssContent.isNotBlank()) {
                val safeCss = JSONObject.quote(addon.cssContent)
                sb.append("  if (window.ArenaAI && window.ArenaAI.ui) { window.ArenaAI.ui.injectCSS($safeCss); }\n")
            }
            if (addon.scriptContent.isNotBlank()) {
                sb.append("  try {\n")
                sb.append(addon.scriptContent)
                sb.append("\n  } catch (err) { console.error('ArenaAI Addon Error [' + $safeId + ']:', err); }\n")
            }
            sb.append("})();\n")
        }
        return sb.toString()
    }
}
