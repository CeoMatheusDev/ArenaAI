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
    val notifyOnFinish: Boolean = true,
    val backgroundServiceEnabled: Boolean = true,
    val keepScreenAwakeOnOpen: Boolean = true,
    val keepScreenAwakeOnGenerating: Boolean = true,
    val extensionsAutoLoad: Boolean = true,
    val autoScanAddonsOnStartup: Boolean = true,
    val autoLoginPromptEnabled: Boolean = true,
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
    const val EXTENSIONS_SUBFOLDER = "extencao"
    const val EXTENSIONS_ALT_SUBFOLDER = "extensão"
    const val PLUGINS_SUBFOLDER = "plugin"
    const val GUIDE_FILE_NAME = "documento.md"
    private const val FOLDER_PLACEHOLDER_FILE = "LEIA_ME.txt"

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

            ArenaSettings(
                fullscreen = obj.optBoolean("fullscreen", false),
                notifyOnFinish = obj.optBoolean("notifyOnFinish", true),
                backgroundServiceEnabled = obj.optBoolean("backgroundServiceEnabled", true),
                keepScreenAwakeOnOpen = obj.optBoolean("keepScreenAwakeOnOpen", true),
                keepScreenAwakeOnGenerating = obj.optBoolean("keepScreenAwakeOnGenerating", true),
                extensionsAutoLoad = obj.optBoolean("extensionsAutoLoad", true),
                autoScanAddonsOnStartup = obj.optBoolean("autoScanAddonsOnStartup", true),
                autoLoginPromptEnabled = obj.optBoolean("autoLoginPromptEnabled", true),
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

        val json = JSONObject().apply {
            put("appName", "Arena AI")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("buildNumber", BuildConfig.VERSION_CODE)
            put("targetUrl", ARENA_HOME_URL)
            put("fullscreen", settings.fullscreen)
            put("notifyOnFinish", settings.notifyOnFinish)
            put("backgroundServiceEnabled", settings.backgroundServiceEnabled)
            put("keepScreenAwakeOnOpen", settings.keepScreenAwakeOnOpen)
            put("keepScreenAwakeOnGenerating", settings.keepScreenAwakeOnGenerating)
            put("extensionsAutoLoad", settings.extensionsAutoLoad)
            put("autoScanAddonsOnStartup", settings.autoScanAddonsOnStartup)
            put("autoLoginPromptEnabled", settings.autoLoginPromptEnabled)
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

        // 2. Try writing directly to /storage/emulated/0/ArenaAI/settings.json if device allows
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
        // 1. App-external ArenaAI/{extencao, plugin, documento.md, settings.json}
        val appExternalRoot = context.getExternalFilesDir(null)
        if (appExternalRoot != null) {
            val arenaDir = File(appExternalRoot, ROOT_FOLDER)
            File(arenaDir, EXTENSIONS_SUBFOLDER).mkdirs()
            File(arenaDir, PLUGINS_SUBFOLDER).mkdirs()
            writeAppExternalFile(context, ROOT_FOLDER, GUIDE_FILE_NAME, ADDONS_GUIDE_MARKDOWN)
        }

        // 2. Attempt direct /storage/emulated/0/ArenaAI/ creation (works on Android <= 10 or permissive devices)
        try {
            @Suppress("DEPRECATION")
            val rootStorage = Environment.getExternalStorageDirectory()
            if (rootStorage != null && rootStorage.canWrite()) {
                val rootArena = File(rootStorage, ROOT_FOLDER)
                File(rootArena, EXTENSIONS_SUBFOLDER).mkdirs()
                File(rootArena, PLUGINS_SUBFOLDER).mkdirs()
                writeToRootExternal(ROOT_FOLDER, GUIDE_FILE_NAME, ADDONS_GUIDE_MARKDOWN)
            }
        } catch (_: Exception) {
        }

        // 3. Public Documents/ArenaAI/ via MediaStore (works on Android 10-16 without permissions)
        writePublicDocumentText(
            context = context,
            subPath = ROOT_FOLDER,
            fileName = GUIDE_FILE_NAME,
            mimeType = "application/octet-stream",
            content = ADDONS_GUIDE_MARKDOWN
        )
        ensurePublicSubfolderExists(context, "$ROOT_FOLDER/$EXTENSIONS_SUBFOLDER")
        ensurePublicSubfolderExists(context, "$ROOT_FOLDER/$PLUGINS_SUBFOLDER")

        // Ensure settings.json also exists in Documents/ArenaAI/settings.json
        saveSettings(context, settings)

        // 4. If user authorized SAF tree (e.g. Internal Storage/ArenaAI), create full structure there too
        if (settings.safTreeUri.isNotBlank()) {
            ensureSafWorkspace(context, settings.safTreeUri)
        }
    }

    fun getDisplayStorageSummary(context: Context, settings: ArenaSettings): String {
        val appPath = File(context.getExternalFilesDir(null), ROOT_FOLDER).absolutePath
        return buildString {
            append("• Pasta pública (Documentos):\n  Documents/$ROOT_FOLDER/\n")
            append("  ├── $SETTINGS_FILE_NAME\n")
            append("  ├── $GUIDE_FILE_NAME\n")
            append("  ├── $EXTENSIONS_SUBFOLDER/\n")
            append("  └── $PLUGINS_SUBFOLDER/\n")
            if (settings.safTreeUri.isNotBlank()) {
                append("\n• Pasta vinculada (SAF): Ativa\n")
            }
            append("\n• Espelho local do app:\n  $appPath")
        }
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
                    content = "Pasta oficial de complementos do Arena AI.\nColoque seus arquivos .js ou manifest.json nesta pasta.\n"
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
            writeSafDocument(context, treeUri, targetParentId, GUIDE_FILE_NAME, "application/octet-stream", ADDONS_GUIDE_MARKDOWN)
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
# Documentação Oficial de Extensões e Plugins — Arena AI

Bem-vindo ao guia de desenvolvimento de **Extensões e Plugins para o Arena AI** (`https://arena.ai/agent`).
Este documento (`ArenaAI/documento.md`) contém a introdução, estrutura de diretórios, parâmetros obrigatórios e a API de conexão entre o site, o complemento e o aplicativo Android.

---

## 1. Estrutura de Diretórios (`ArenaAI/`)

O aplicativo cria e monitora automaticamente os seguintes arquivos e pastas no armazenamento do aparelho (`Documents/ArenaAI/` e pasta vinculada):

```text
ArenaAI/
├── settings.json     # Todas as configurações do app, tela cheia, tela ligada, notificações, 2º plano e extensões/plugins ativos
├── documento.md      # Este manual com parâmetros e instruções
├── extencao/         # Coloque aqui suas Extensões (.js ou pasta com manifest.json)
└── plugin/           # Coloque aqui seus Plugins (.js ou pasta com manifest.json)
```

> **Dica:** Para vincular diretamente a pasta `ArenaAI` na raiz do armazenamento interno (se desejar), abra **Menu (⋮) → Configuração de Extensão → Pasta ArenaAI** e selecione/crie a pasta.

---

## 2. Como o App Faz a Leitura Automática

Sempre que o **Arena AI** é aberto:
1. Exibe a notificação **"Procurando complementos…"** enquanto lê `ArenaAI/extencao/` e `ArenaAI/plugin/` em segundo plano.
2. Valida o manifesto/cabeçalho e a compatibilidade com a versão do app (`2.0.0`) e com o site `arena.ai/agent`.
3. Se houver complementos compatíveis, dispara uma notificação informando que há complementos disponíveis e compatíveis.
4. Se as pastas estiverem vazias, a notificação de busca é fechada automaticamente.
5. Os IDs ativos são gravados automaticamente em `ArenaAI/settings.json` nos campos `activeExtensions` e `activePlugins`.

---

## 3. Parâmetros e Formatos Aceitos

### Opção A — Manifesto `manifest.json` + Script `.js`
```json
{
  "id": "meu-tema-neon",
  "name": "Tema Neon",
  "version": "1.0.0",
  "type": "extension",
  "description": "Personaliza cores e adiciona botões rápidos no Arena AI.",
  "author": "Seu Nome",
  "minAppVersion": "2.0.0",
  "compatible": true,
  "matchUrls": ["*://arena.ai/*"],
  "permissions": ["ui", "storage", "notifications", "account"],
  "entryPoint": "index.js",
  "styles": "style.css"
}
```

#### Parâmetros do `manifest.json`
| Parâmetro | Tipo | Obrigatório | Descrição |
| :--- | :--- | :--- | :--- |
| `id` | `string` | Sim | Identificador único do complemento. |
| `name` | `string` | Sim | Nome exibido no aplicativo e em `settings.json`. |
| `version` | `string` | Sim | Versão semântica (ex: `1.0.0`). |
| `type` | `string` | Sim | `"extension"` (pasta `extencao/`) ou `"plugin"` (pasta `plugin/`). |
| `description` | `string` | Não | Descrição exibida na tela de Configuração de Extensão. |
| `author` | `string` | Não | Nome do autor. |
| `minAppVersion` | `string` | Não | Versão mínima do app Arena AI (atual: `2.0.0`). |
| `compatible` | `boolean` | Não | `true` para habilitar carregamento. |
| `matchUrls` | `string[]` | Não | Padrões de URL compatíveis (padrão: `["*://arena.ai/*"]`). |
| `permissions` | `string[]` | Não | `"ui"`, `"storage"`, `"notifications"`, `"account"`. |
| `entryPoint` | `string` | Não | Arquivo `.js` principal (ou use `"script"` inline). |
| `styles` | `string` | Não | Arquivo `.css` opcional (ou use `"css"` inline). |

---

### Opção B — Arquivo Único `.js` (Com Cabeçalho)
Salve um arquivo `.js` diretamente em `ArenaAI/extencao/` ou `ArenaAI/plugin/`:

```javascript
// ==ArenaAI==
// @id            atalho-prompt
// @name          Atalho de Prompt
// @version       1.0.0
// @type          plugin
// @description   Insere um botão flutuante para prompts rápidos no Arena AI.
// @author        Comunidade Arena AI
// @minAppVersion 2.0.0
// @match         *://arena.ai/*
// @compatible    true
// ==/ArenaAI==

(function(ArenaAI) {
    ArenaAI.registerPlugin({
        id: 'atalho-prompt',
        onReady: function() {
            ArenaAI.ui.injectCSS('.arena-btn { position: fixed; bottom: 88px; right: 16px; z-index: 9999; padding: 10px 14px; border-radius: 999px; background: #38BDF8; color: #031E2F; font-weight: bold; border: none; }');
            var btn = document.createElement('button');
            btn.className = 'arena-btn';
            btn.innerText = 'Prompt Rápido';
            btn.onclick = function() {
                var ta = document.querySelector('textarea');
                if (ta) {
                    ta.value = 'Explique passo a passo de forma clara: ';
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

## 4. API Global `window.ArenaAI` (App ↔ Site)

- `window.ArenaAI.version` → Versão do app (`"2.0.0"`).
- `window.ArenaAI.platform` → `"android-webview"`.
- `window.ArenaAI.connectedEmail` → E-mail da conta do aparelho conectada no app.
- `window.ArenaAI.requestLogin()` → Abre a lista de e-mails do celular.
- `window.ArenaAI.notify(titulo, mensagem)` → Envia uma notificação nativa no telefone.
- `window.ArenaAI.ui.injectCSS(css)` → Injeta CSS personalizado no site.
- `window.ArenaAI.storage.get(chave)` / `set(chave, valor)` → Armazena dados locais do complemento.
- `window.ArenaAI.onResponseComplete(fn)` → Dispara quando o modelo termina de responder.
- `window.ArenaAI.onPromptSubmit(fn)` → Dispara quando um prompt é enviado.
- `window.ArenaAI.registerExtension(cfg)` / `registerPlugin(cfg)` → Registra o complemento na sessão.
- `window.ArenaAI.fs` → Referência direta à ponte nativa `window.ArenaFS`.

---

## 5. Ponte Nativa de Arquivos (`window.ArenaFS`)

O aplicativo expõe nativamente no WebView o objeto `window.ArenaFS` (`addJavascriptInterface(..., "ArenaFS")`) para que o site e extensões/plugins leiam e gravem arquivos com segurança dentro do sandbox `ArenaAI/` e em pastas autorizadas via SAF:

- `ArenaFS.getArenaDir()` → Retorna o caminho raiz da pasta `ArenaAI`.
- `ArenaFS.listFiles(path)` → Retorna JSON `{ ok, path, entries: [{ name, path, isDirectory, size, lastModified }] }`. Use `"."`, `"extencao"`, `"plugin"` ou URI `content://`.
- `ArenaFS.readFile(path)` → Lê e retorna o conteúdo UTF-8 do arquivo (ou `null` se não existir).
- `ArenaFS.writeFile(path, content)` → Grava `content` no arquivo (retorna `true`/`false`).
- `ArenaFS.exists(path)` / `ArenaFS.mkdir(path)` / `ArenaFS.deleteFile(path)` → Verificação, criação de subpastas e remoção segura.
- `ArenaFS.pickDirectory()` → Abre o seletor de pastas do Android (SAF) e retorna a URI autorizada (`content://...`).

```javascript
// Exemplo usando ArenaFS em uma extensão/plugin
const lista = JSON.parse(window.ArenaFS.listFiles("extencao"));
window.ArenaFS.writeFile("plugin/notas.txt", "Salvo pelo plugin!");
const conteudo = window.ArenaFS.readFile("settings.json");
```

---

## 6. Estrutura do `ArenaAI/settings.json`

Todas as configurações ajustadas no aplicativo são salvas automaticamente em `ArenaAI/settings.json`:
- `fullscreen`: Tela cheia ativada/desativada
- `notifyOnFinish`: Notificação ao finalizar resposta
- `backgroundServiceEnabled`: Funcionamento contínuo em segundo plano
- `keepScreenAwakeOnOpen`: Manter tela ligada quando o app está aberto
- `keepScreenAwakeOnGenerating`: Manter tela ligada enquanto o modelo gera saída
- `extensionsAutoLoad`: Executar extensões/plugins automaticamente
- `autoScanAddonsOnStartup`: Ler pasta `ArenaAI` ao abrir o app
- `autoLoginPromptEnabled`: Abrir lista de e-mails do celular em chamadas de login
- `connectedEmail`: E-mail conectado no app e site
- `activeExtensions` / `activePlugins`: Lista de extensões e plugins ativos
- `disabledAddonIds`: IDs desativados pelo usuário
""".trimIndent()
}
