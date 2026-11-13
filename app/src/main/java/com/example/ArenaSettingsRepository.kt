package com.example

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ArenaSettings(
    val fullscreen: Boolean = false,
    val desktopMode: Boolean = false,
    val textZoom: Int = 100,
    val notifyOnFinish: Boolean = true,
    val backgroundServiceEnabled: Boolean = true,
    val keepScreenAwakeOnOpen: Boolean = true,
    val keepScreenAwakeOnGenerating: Boolean = true,
    val extensionsAutoLoad: Boolean = true,
    val autoScanAddonsOnStartup: Boolean = true,
    val autoLoginPromptEnabled: Boolean = true,
    val keepSessionCookies: Boolean = true,
    val language: String = AppLanguage.AUTO.code,
    val connectedEmail: String = "",
    val safTreeUri: String = "",
    val activeExtensions: List<String> = emptyList(),
    val activePlugins: List<String> = emptyList(),
    val disabledAddonIds: Set<String> = emptySet()
)

object ArenaSettingsRepository {
    const val ROOT_FOLDER = "ArenaAI"
    const val LEGACY_SETTINGS_FOLDER = "ArenaIA"
    const val SETTINGS_FILE_NAME = "settings.json"
    const val EXTENSIONS_SUBFOLDER = "extension"
    const val LEGACY_EXTENSIONS_SUBFOLDER = "extensions"
    const val LEGACY_EXTENSIONS_ALT_SUBFOLDER = "extencao"
    const val LEGACY_EXTENSIONS_ACCENT_SUBFOLDER = "extensão"
    const val PLUGINS_SUBFOLDER = "plugin"
    const val LEGACY_PLUGINS_SUBFOLDER = "plugins"
    const val GUIDE_FILE_NAME = "document.md"
    private const val FOLDER_PLACEHOLDER_FILE = "README.txt"

    fun loadSettings(context: Context): ArenaSettings {
        val safUriStr = readInternalSafUriCache(context)
        val jsonText = readFromSafRoot(context, safUriStr, SETTINGS_FILE_NAME)
            ?: readFromRootExternal(ROOT_FOLDER, SETTINGS_FILE_NAME)
            ?: readPublicDocumentText(context, ROOT_FOLDER, SETTINGS_FILE_NAME)
            ?: readAppExternalFile(context, ROOT_FOLDER, SETTINGS_FILE_NAME)
            ?: readPublicDocumentText(context, LEGACY_SETTINGS_FOLDER, "Settings.json")
            ?: readAppExternalFile(context, LEGACY_SETTINGS_FOLDER, "Settings.json")

        if (jsonText.isNullOrBlank()) {
            val defaults = ArenaSettings(safTreeUri = safUriStr)
            saveSettings(context, defaults)
            return defaults
        }

        return try {
            val obj = JSONObject(jsonText)
            val disabledIds = parseStringSet(obj.optJSONArray("disabledAddonIds"))
            val activeExt = parseStringList(obj.optJSONArray("activeExtensions"))
            val activePlug = parseStringList(obj.optJSONArray("activePlugins"))
            val loadedSaf = obj.optString("safTreeUri", safUriStr).ifBlank { safUriStr }
            val loadedLang = AppLanguage.fromCode(obj.optString("language", AppLanguage.AUTO.code)).code
            val loadedNotify = obj.optBoolean("notifyOnFinish", true)

            ArenaLocaleHelper.persistLanguageCode(context, loadedLang)
            ArenaLocaleHelper.persistNotifyPreference(context, loadedNotify)

            ArenaSettings(
                fullscreen = obj.optBoolean("fullscreen", false),
                desktopMode = obj.optBoolean("desktopMode", false),
                textZoom = obj.optInt("textZoom", 100).coerceIn(75, 150),
                notifyOnFinish = loadedNotify,
                backgroundServiceEnabled = obj.optBoolean("backgroundServiceEnabled", true),
                keepScreenAwakeOnOpen = obj.optBoolean("keepScreenAwakeOnOpen", true),
                keepScreenAwakeOnGenerating = obj.optBoolean("keepScreenAwakeOnGenerating", true),
                extensionsAutoLoad = obj.optBoolean("extensionsAutoLoad", true),
                autoScanAddonsOnStartup = obj.optBoolean("autoScanAddonsOnStartup", true),
                autoLoginPromptEnabled = obj.optBoolean("autoLoginPromptEnabled", true),
                keepSessionCookies = obj.optBoolean("keepSessionCookies", true),
                language = loadedLang,
                connectedEmail = obj.optString("connectedEmail", ""),
                safTreeUri = loadedSaf,
                activeExtensions = activeExt,
                activePlugins = activePlug,
                disabledAddonIds = disabledIds
            )
        } catch (_: Exception) {
            ArenaSettings(safTreeUri = safUriStr)
        }
    }

    fun saveSettings(context: Context, settings: ArenaSettings) {
        if (settings.safTreeUri.isNotBlank()) {
            saveInternalSafUriCache(context, settings.safTreeUri)
        }
        ArenaLocaleHelper.persistLanguageCode(context, settings.language)
        ArenaLocaleHelper.persistNotifyPreference(context, settings.notifyOnFinish)

        val json = JSONObject().apply {
            put("appName", "Arena AI")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("buildNumber", BuildConfig.VERSION_CODE)
            put("targetUrl", ARENA_HOME_URL)
            put("fullscreen", settings.fullscreen)
            put("desktopMode", settings.desktopMode)
            put("textZoom", settings.textZoom.coerceIn(75, 150))
            put("notifyOnFinish", settings.notifyOnFinish)
            put("backgroundServiceEnabled", settings.backgroundServiceEnabled)
            put("keepScreenAwakeOnOpen", settings.keepScreenAwakeOnOpen)
            put("keepScreenAwakeOnGenerating", settings.keepScreenAwakeOnGenerating)
            put("extensionsAutoLoad", settings.extensionsAutoLoad)
            put("autoScanAddonsOnStartup", settings.autoScanAddonsOnStartup)
            put("autoLoginPromptEnabled", settings.autoLoginPromptEnabled)
            put("keepSessionCookies", settings.keepSessionCookies)
            put("language", settings.language)
            put("connectedEmail", settings.connectedEmail)
            put("safTreeUri", settings.safTreeUri)
            put("activeExtensions", JSONArray(settings.activeExtensions))
            put("activePlugins", JSONArray(settings.activePlugins))
            put("disabledAddonIds", JSONArray(settings.disabledAddonIds.toList()))
            put("updatedAt", System.currentTimeMillis())
        }
        val content = json.toString(2)

        // 1. Always write to app-external ArenaAI/settings.json
        writeAppExternalFile(context, ROOT_FOLDER, SETTINGS_FILE_NAME, content)

        // 2. Try writing directly to /storage/emulated/0/ArenaAI/settings.json if permitted
        writeToRootExternal(ROOT_FOLDER, SETTINGS_FILE_NAME, content)

        // 3. Write to Public Documents/ArenaAI/settings.json via MediaStore / File
        writePublicDocumentText(
            context = context,
            subPath = ROOT_FOLDER,
            fileName = SETTINGS_FILE_NAME,
            mimeType = "application/octet-stream",
            content = content
        )

        // 4. Write to user-linked SAF folder if authorized
        if (settings.safTreeUri.isNotBlank()) {
            writeToSafRoot(context, settings.safTreeUri, SETTINGS_FILE_NAME, "application/json", content)
        }
    }

    fun ensureAddonWorkspaceInitialized(context: Context, settings: ArenaSettings = loadSettings(context)) {
        var missingSettingsFile = false

        // 1. App-external ArenaAI/{extensions, plugins, documentation.md, settings.json}
        val appExternalRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val arenaDir = File(appExternalRoot, ROOT_FOLDER)
        val extDir = File(arenaDir, EXTENSIONS_SUBFOLDER)
        val pluginDir = File(arenaDir, PLUGINS_SUBFOLDER)
        if (!extDir.exists()) extDir.mkdirs()
        if (!pluginDir.exists()) pluginDir.mkdirs()

        val appGuideFile = File(arenaDir, GUIDE_FILE_NAME)
        if (!appGuideFile.exists() || appGuideFile.length() == 0L) {
            writeAppExternalFile(context, ROOT_FOLDER, GUIDE_FILE_NAME, ADDONS_GUIDE_MARKDOWN)
        }
        val appSettingsFile = File(arenaDir, SETTINGS_FILE_NAME)
        if (!appSettingsFile.exists() || appSettingsFile.length() == 0L) {
            missingSettingsFile = true
        }

        // 2. Attempt direct /storage/emulated/0/ArenaAI/ creation (Android <= 10 or permissive devices)
        try {
            @Suppress("DEPRECATION")
            val rootStorage = Environment.getExternalStorageDirectory()
            if (rootStorage != null && rootStorage.canWrite()) {
                val rootArena = File(rootStorage, ROOT_FOLDER)
                val rootExt = File(rootArena, EXTENSIONS_SUBFOLDER)
                val rootPlug = File(rootArena, PLUGINS_SUBFOLDER)
                if (!rootExt.exists()) rootExt.mkdirs()
                if (!rootPlug.exists()) rootPlug.mkdirs()
                val rootGuide = File(rootArena, GUIDE_FILE_NAME)
                if (!rootGuide.exists() || rootGuide.length() == 0L) {
                    writeToRootExternal(ROOT_FOLDER, GUIDE_FILE_NAME, ADDONS_GUIDE_MARKDOWN)
                }
                val rootSettings = File(rootArena, SETTINGS_FILE_NAME)
                if (!rootSettings.exists() || rootSettings.length() == 0L) {
                    missingSettingsFile = true
                }
            }
        } catch (_: Exception) {
        }

        // 3. Public Documents/ArenaAI/ via MediaStore (works on Android 10-16 without extra permissions)
        val publicGuideExists = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findPublicMediaStoreUri(context, ROOT_FOLDER, GUIDE_FILE_NAME) != null
        } else {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            File(File(docsDir, ROOT_FOLDER), GUIDE_FILE_NAME).exists()
        }
        if (!publicGuideExists) {
            writePublicDocumentText(
                context = context,
                subPath = ROOT_FOLDER,
                fileName = GUIDE_FILE_NAME,
                mimeType = "application/octet-stream",
                content = ADDONS_GUIDE_MARKDOWN
            )
        }
        ensurePublicSubfolderExists(context, "$ROOT_FOLDER/$EXTENSIONS_SUBFOLDER")
        ensurePublicSubfolderExists(context, "$ROOT_FOLDER/$PLUGINS_SUBFOLDER")

        val publicSettingsExists = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findPublicMediaStoreUri(context, ROOT_FOLDER, SETTINGS_FILE_NAME) != null
        } else {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            File(File(docsDir, ROOT_FOLDER), SETTINGS_FILE_NAME).exists()
        }
        if (!publicSettingsExists || missingSettingsFile) {
            saveSettings(context, settings)
        }

        // 4. If user authorized a SAF tree (e.g. Internal Storage/ArenaAI), create missing files/folders there too
        if (settings.safTreeUri.isNotBlank()) {
            ensureSafWorkspace(context, settings.safTreeUri)
            if (readFromSafRoot(context, settings.safTreeUri, SETTINGS_FILE_NAME).isNullOrBlank()) {
                saveSettings(context, settings)
            }
        }
    }

    fun resetAllAppData(context: Context): ArenaSettings {
        val currentSafUri = readInternalSafUriCache(context)
        if (currentSafUri.isNotBlank()) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(Uri.parse(currentSafUri), flags)
            } catch (_: Exception) {
            }
        }

        // Clear all internal SharedPreferences
        try {
            val prefs = context.getSharedPreferences("arena_internal_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
        } catch (_: Exception) {
        }

        // Delete app-external, internal, and external ArenaAI / legacy ArenaIA directories
        val folderNames = listOf(ROOT_FOLDER, LEGACY_SETTINGS_FOLDER)
        val baseCandidates = buildList {
            context.getExternalFilesDir(null)?.let { add(it) }
            add(context.filesDir)
            try {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()?.takeIf { it.canWrite() }?.let { add(it) }
            } catch (_: Exception) {
            }
            try {
                @Suppress("DEPRECATION")
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { add(it) }
            } catch (_: Exception) {
            }
        }

        for (base in baseCandidates) {
            for (folder in folderNames) {
                try {
                    val target = File(base, folder)
                    if (target.exists()) {
                        target.deleteRecursively()
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Delete MediaStore entries owned by this app inside Documents/ArenaAI and Documents/ArenaIA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$ROOT_FOLDER%", "%$LEGACY_SETTINGS_FOLDER%")
                context.contentResolver.delete(collection, selection, selectionArgs)
            } catch (_: Exception) {
            }
        }

        // Clear app cache files
        try {
            context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }

        // Recreate clean factory-default English ArenaAI directory structure
        val defaults = ArenaSettings()
        ensureAddonWorkspaceInitialized(context, defaults)
        saveSettings(context, defaults)
        return defaults
    }

    private fun ensurePublicSubfolderExists(context: Context, subPath: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val existing = findPublicMediaStoreUri(context, subPath, FOLDER_PLACEHOLDER_FILE)
            if (existing == null) {
                writePublicDocumentText(
                    context = context,
                    subPath = subPath,
                    fileName = FOLDER_PLACEHOLDER_FILE,
                    mimeType = "text/plain",
                    content = "Official Arena AI add-ons folder.\nPlace your .js or manifest.json files in this directory.\n"
                )
            }
        }
        try {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            File(docsDir, subPath).mkdirs()
        } catch (_: Exception) {
        }
    }

    private fun ensureSafWorkspace(context: Context, safTreeUriStr: String) {
        try {
            val treeUri = Uri.parse(safTreeUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val targetParentId = ensureSafSubdir(context, treeUri, rootDocId, ROOT_FOLDER) ?: rootDocId
            ensureSafSubdir(context, treeUri, targetParentId, EXTENSIONS_SUBFOLDER)
            ensureSafSubdir(context, treeUri, targetParentId, PLUGINS_SUBFOLDER)
            if (findSafChildDocId(context, treeUri, targetParentId, GUIDE_FILE_NAME) == null) {
                writeSafDocument(context, treeUri, targetParentId, GUIDE_FILE_NAME, "application/octet-stream", ADDONS_GUIDE_MARKDOWN)
            }
        } catch (_: Exception) {
        }
    }

    private fun readFromSafRoot(context: Context, safTreeUriStr: String, fileName: String): String? {
        if (safTreeUriStr.isBlank()) return null
        return try {
            val treeUri = Uri.parse(safTreeUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val arenaDocId = findSafChildDocId(context, treeUri, rootDocId, ROOT_FOLDER) ?: rootDocId
            val fileDocId = findSafChildDocId(context, treeUri, arenaDocId, fileName) ?: return null
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
            context.contentResolver.openInputStream(docUri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToSafRoot(
        context: Context,
        safTreeUriStr: String,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        try {
            val treeUri = Uri.parse(safTreeUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val arenaDocId = ensureSafSubdir(context, treeUri, rootDocId, ROOT_FOLDER) ?: rootDocId
            writeSafDocument(context, treeUri, arenaDocId, fileName, mimeType, content)
        } catch (_: Exception) {
        }
    }

    fun writeSafAddonFile(
        context: Context,
        safTreeUriStr: String,
        subfolder: String,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        if (safTreeUriStr.isBlank()) return
        try {
            val treeUri = Uri.parse(safTreeUriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val arenaDocId = ensureSafSubdir(context, treeUri, rootDocId, ROOT_FOLDER) ?: rootDocId
            val subDocId = ensureSafSubdir(context, treeUri, arenaDocId, subfolder) ?: return
            writeSafDocument(context, treeUri, subDocId, fileName, mimeType, content)
        } catch (_: Exception) {
        }
    }

    private fun findSafChildDocId(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        targetName: String
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                if (name.equals(targetName, ignoreCase = true)) {
                    return cursor.getString(idCol)
                }
            }
        }
        return null
    }

    private fun ensureSafSubdir(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        dirName: String
    ): String? {
        // Avoid nesting ArenaAI/ArenaAI if the user already picked the ArenaAI folder directly
        if (dirName.equals(ROOT_FOLDER, ignoreCase = true) && parentDocId.endsWith(ROOT_FOLDER, ignoreCase = true)) {
            return parentDocId
        }
        findSafChildDocId(context, treeUri, parentDocId, dirName)?.let { return it }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val created = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName
        )
        return created?.let { DocumentsContract.getDocumentId(it) }
    }

    private fun writeSafDocument(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        val resolver = context.contentResolver
        val existingId = findSafChildDocId(context, treeUri, parentDocId, fileName)
        val docUri = if (existingId != null) {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
        } else {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            DocumentsContract.createDocument(resolver, parentUri, mimeType, fileName)
        } ?: return

        resolver.openOutputStream(docUri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readFromRootExternal(folder: String, fileName: String): String? {
        return try {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory() ?: return null
            val file = File(File(root, folder), fileName)
            if (file.exists() && file.canRead()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToRootExternal(folder: String, fileName: String, content: String) {
        try {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory() ?: return
            if (!root.canWrite()) return
            val dir = File(root, folder)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeText(content)
        } catch (_: Exception) {
        }
    }

    private fun readAppExternalFile(context: Context, folder: String, fileName: String): String? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(File(baseDir, folder), fileName)
            if (file.exists() && file.canRead()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeAppExternalFile(
        context: Context,
        folder: String,
        fileName: String,
        content: String
    ) {
        try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(baseDir, folder)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeText(content)
        } catch (_: Exception) {
        }
    }

    private fun readPublicDocumentText(
        context: Context,
        subPath: String,
        fileName: String
    ): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findPublicMediaStoreUri(context, subPath, fileName)
            if (uri != null) {
                try {
                    return context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                }
            }
        }
        return try {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(File(docsDir, subPath), fileName)
            if (file.exists() && file.canRead()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun writePublicDocumentText(
        context: Context,
        subPath: String,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val existingUri = findPublicMediaStoreUri(context, subPath, fileName)
                if (existingUri != null) {
                    try {
                        resolver.openOutputStream(existingUri, "wt")?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                        return
                    } catch (_: SecurityException) {
                        // Orphaned row from previous install; delete if possible or insert fresh
                        try {
                            resolver.delete(existingUri, null, null)
                        } catch (_: Exception) {
                        }
                    }
                }

                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$subPath"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    val doneValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, doneValues, null, null)
                    return
                }
            } catch (_: Exception) {
            }
        }

        // Direct File fallback
        try {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(docsDir, subPath)
            if (!targetDir.exists()) targetDir.mkdirs()
            File(targetDir, fileName).writeText(content)
        } catch (_: Exception) {
        }
    }

    private fun findPublicMediaStoreUri(
        context: Context,
        subPath: String,
        fileName: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativePathWithSlash = "${Environment.DIRECTORY_DOCUMENTS}/$subPath/"
            val relativePathNoSlash = "${Environment.DIRECTORY_DOCUMENTS}/$subPath"
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND (${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)"
            val selectionArgs = arrayOf(fileName, relativePathWithSlash, relativePathNoSlash)
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readInternalSafUriCache(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences("arena_internal_prefs", Context.MODE_PRIVATE)
            prefs.getString("saf_tree_uri", "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun saveInternalSafUriCache(context: Context, uri: String) {
        try {
            val prefs = context.getSharedPreferences("arena_internal_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("saf_tree_uri", uri).apply()
        } catch (_: Exception) {
        }
    }

    private fun parseStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    val ADDONS_GUIDE_MARKDOWN = """
# Official Extensions and Plugins Documentation — Arena AI

Welcome to the developer guide for **Arena AI Extensions & Plugins** (`https://arena.ai/agent`).
This document (`ArenaAI/document.md`) provides an introduction, directory layout, required parameters, and the bridge API connecting the web app, your add-on, and the Android client.

---

## 1. Directory Structure (`ArenaAI/`)

The application automatically creates and monitors the following files and folders on device storage (`Documents/ArenaAI/` and any linked SAF directory):

```text
ArenaAI/
├── settings.json      # All app settings: fullscreen, keep awake, notifications, language, background mode, and active extensions/plugins
├── documentation.md   # This manual with parameters and developer instructions
├── extensions/        # Place your Extensions here (.js file or subfolder with manifest.json)
└── plugins/           # Place your Plugins here (.js file or subfolder with manifest.json)
```

> **Tip:** To link a custom `ArenaAI` folder in internal storage, open **Menu (⋮) → Settings → ArenaAI Folder** (or **Extension → ArenaAI Folder**) and select or create the directory.

---

## 2. Automatic Add-on Discovery

Whenever **Arena AI** launches (when notifications and startup scan are enabled):
1. Silently displays **"Scanning for add-ons…"** while reading `ArenaAI/extension/` and `ArenaAI/plugin/` in the background (legacy `extencao/` and `plugin/` folders are also supported).
2. Validates the manifest/header and compatibility with the app version (`2.0.0`) and `arena.ai/agent`.
3. If compatible add-ons are found, posts a notification letting you know compatible add-ons are ready.
4. If the folders are empty, the scanning notification closes automatically.
5. Active IDs are saved automatically in `ArenaAI/settings.json` under `activeExtensions` and `activePlugins`.

---

## 3. Accepted Formats and Parameters

### Option A — `manifest.json` + `.js` Script
```json
{
  "id": "my-neon-theme",
  "name": "Neon Theme",
  "version": "1.0.0",
  "type": "extension",
  "description": "Customizes colors and adds quick action buttons in Arena AI.",
  "author": "Your Name",
  "minAppVersion": "2.0.0",
  "compatible": true,
  "matchUrls": ["*://arena.ai/*"],
  "permissions": ["ui", "storage", "notifications", "account"],
  "entryPoint": "index.js",
  "styles": "style.css"
}
```

#### `manifest.json` Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `id` | `string` | Yes | Unique identifier for the add-on. |
| `name` | `string` | Yes | Display name shown in the app and `settings.json`. |
| `version` | `string` | Yes | Semantic version (e.g. `1.0.0`). |
| `type` | `string` | Yes | `"extension"` (`extensions/` folder) or `"plugin"` (`plugins/` folder). |
| `description` | `string` | No | Description displayed in the Extension Settings screen. |
| `author` | `string` | No | Author name. |
| `minAppVersion` | `string` | No | Minimum Arena AI app version (current: `2.0.0`). |
| `compatible` | `boolean` | No | Set to `true` to enable loading. |
| `matchUrls` | `string[]` | No | Compatible URL patterns (default: `["*://arena.ai/*"]`). |
| `permissions` | `string[]` | No | `"ui"`, `"storage"`, `"notifications"`, `"account"`. |
| `entryPoint` | `string` | No | Main `.js` file (or provide inline `"script"`). |
| `styles` | `string` | No | Optional `.css` file (or provide inline `"css"`). |

---

### Option B — Single `.js` File (With Metadata Header)
Save a `.js` file directly inside `ArenaAI/extensions/` or `ArenaAI/plugins/`:

```javascript
// ==ArenaAI==
// @id            prompt-shortcut
// @name          Prompt Shortcut
// @version       1.0.0
// @type          plugin
// @description   Adds a floating button for quick prompts in Arena AI.
// @author        Arena AI Community
// @minAppVersion 2.0.0
// @match         *://arena.ai/*
// @compatible    true
// ==/ArenaAI==

(function(ArenaAI) {
    ArenaAI.registerPlugin({
        id: 'prompt-shortcut',
        onReady: function() {
            ArenaAI.ui.injectCSS('.arena-btn { position: fixed; bottom: 88px; right: 16px; z-index: 9999; padding: 10px 14px; border-radius: 999px; background: #38BDF8; color: #031E2F; font-weight: bold; border: none; }');
            var btn = document.createElement('button');
            btn.className = 'arena-btn';
            btn.innerText = 'Quick Prompt';
            btn.onclick = function() {
                var ta = document.querySelector('textarea');
                if (ta) {
                    ta.value = 'Explain step by step clearly: ';
                    ta.dispatchEvent(new Event('input', { bubbles: true }));
                    ta.focus();
                }
            };
            document.body.appendChild(btn);
        }
    });
})(window.ArenaAI);
```

---

## 4. Global API `window.ArenaAI` (App <-> Web)

- `window.ArenaAI.version` -> App version (`"2.0.0"`).
- `window.ArenaAI.platform` -> `"android-webview"`.
- `window.ArenaAI.language` -> Active resolved app language code (e.g. `"en"`, `"pt-BR"`, `"es"`).
- `window.ArenaAI.connectedEmail` -> Device email account connected to the app.
- `window.ArenaAI.requestLogin()` -> Opens the native device email picker.
- `window.ArenaAI.notify(title, message)` -> Sends a native phone notification (if notifications are enabled).
- `window.ArenaAI.ui.injectCSS(css)` -> Injects custom CSS into the page.
- `window.ArenaAI.storage.get(key)` / `set(key, value)` -> Persists local add-on data.
- `window.ArenaAI.onResponseComplete(fn)` -> Fires only after generation finishes (`Stop generating` -> `Send message`) and the DOM settles.
- `window.ArenaAI.onPromptSubmit(fn)` -> Fires when a prompt is submitted.
- `window.ArenaAI.dom.click(el)` -> Dispatches full event sequence (`PointerEvent`, `TouchEvent`, `MouseEvent`, `.focus()`, `.click()`) compatible with React/Tailwind/Radix inside WebView.
- `window.ArenaAI.dom.waitFor(selector, timeout)` -> Returns a `Promise` resolving when the element appears in the DOM (default: `10000ms`).
- `window.ArenaAI.dom.cleanText(el)` -> Extracts clean text ignoring `<svg>`, `<style>`, and `<script>`.
- `window.ArenaAI.registerExtension(cfg)` / `registerPlugin(cfg)` -> Registers the add-on for the session.
- `window.ArenaAI.fs` -> Direct reference to the native `window.ArenaFS` bridge.
- **WebView Debugging**: `WebView.setWebContentsDebuggingEnabled(true)` is enabled; connect via USB and open `chrome://inspect` on desktop.

---

## 5. Native File Bridge (`window.ArenaFS`)

The app exposes `window.ArenaFS` (`addJavascriptInterface(..., "ArenaFS")`) in WebView so scripts can safely read and write files inside the `ArenaAI/` sandbox and SAF-authorized folders:

- `ArenaFS.getArenaDir()` -> Returns the root `ArenaAI` directory path.
- `ArenaFS.listFiles(path)` -> Returns JSON `{ ok, path, entries: [{ name, path, isDirectory, size, lastModified }] }`. Use `"."`, `"extensions"`, `"plugins"`, or a `content://` URI.
- `ArenaFS.readFile(path)` -> Reads and returns UTF-8 file contents (or `null` if missing).
- `ArenaFS.writeFile(path, content)` -> Writes `content` to the file (`true`/`false`).
- `ArenaFS.exists(path)` / `ArenaFS.mkdir(path)` / `ArenaFS.deleteFile(path)` -> Check existence, create directories, and safely delete files.
- `ArenaFS.pickDirectory()` -> Opens the Android folder picker (SAF) and returns the authorized URI (`content://...`).

```javascript
// Example using ArenaFS inside an extension or plugin
const list = JSON.parse(window.ArenaFS.listFiles("extensions"));
window.ArenaFS.writeFile("plugins/notes.txt", "Saved by plugin!");
const config = window.ArenaFS.readFile("settings.json");
```

---

## 6. `ArenaAI/settings.json` Schema

All preferences configured in the app are automatically saved to `ArenaAI/settings.json`:
- `fullscreen`: Fullscreen mode enabled/disabled
- `desktopMode`: Desktop User-Agent mode for `arena.ai/agent`
- `textZoom`: WebView page text scale percentage (`75` to `150`, default `100`)
- `notifyOnFinish`: Enable or completely disable all app notifications
- `backgroundServiceEnabled`: Continuous background execution
- `keepScreenAwakeOnOpen`: Keep screen on while app is open
- `keepScreenAwakeOnGenerating`: Keep screen on while the model is generating
- `extensionsAutoLoad`: Automatically inject active extensions/plugins
- `autoScanAddonsOnStartup`: Scan `ArenaAI` add-on folders on startup
- `autoLoginPromptEnabled`: Show device email picker on login prompts
- `keepSessionCookies`: Persist session cookies across restarts
- `language`: Language code (`"auto"`, `"en"`, `"zh-CN"`, `"hi"`, `"es"`, `"fr"`, `"ar"`, `"bn"`, `"pt-BR"`, `"pt-PT"`, `"ru"`, `"ur"`)
- `connectedEmail`: Device email connected to the app and site
- `activeExtensions` / `activePlugins`: Lists of active extension and plugin IDs
- `disabledAddonIds`: IDs disabled by the user
""".trimIndent()
}
