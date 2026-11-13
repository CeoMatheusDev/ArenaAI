package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ArenaFileBridge(
    private val context: Context,
    private val onRequestPickDirectory: ((String?) -> Unit) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getArenaDir(): String {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val arenaDir = File(base, ArenaSettingsRepository.ROOT_FOLDER)
        if (!arenaDir.exists()) arenaDir.mkdirs()
        return arenaDir.absolutePath
    }

    @JavascriptInterface
    fun getAuthorizedDirectoryUri(): String {
        return ArenaSettingsRepository.loadSettings(context).safTreeUri
    }

    @JavascriptInterface
    fun listFiles(path: String?): String {
        val result = JSONObject()
        return try {
            val raw = path?.trim().orEmpty()
            if (raw.startsWith("content://")) {
                return listSafDirectory(raw)
            }

            val dir = resolveSandboxedFile(raw.ifBlank { "." }, mustExist = true)
                ?: return errorJson("Caminho inválido ou fora do diretório permitido.")

            if (!dir.exists() || !dir.isDirectory) {
                return errorJson("Diretório não encontrado: ${dir.name}")
            }

            val entries = JSONArray()
            val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            for (file in files) {
                entries.put(
                    JSONObject().apply {
                        put("name", file.name)
                        put("path", file.absolutePath)
                        put("isDirectory", file.isDirectory)
                        put("size", if (file.isFile) file.length() else 0L)
                        put("lastModified", file.lastModified())
                    }
                )
            }

            result.put("ok", true)
            result.put("path", dir.absolutePath)
            result.put("entries", entries)
            result.toString()
        } catch (e: Exception) {
            errorJson(e.message ?: "Erro ao listar arquivos.")
        }
    }

    @JavascriptInterface
    fun readFile(path: String?): String? {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return null

        if (raw.startsWith("content://")) {
            return try {
                val uri = Uri.parse(raw)
                if (!isSafUriAuthorized(uri)) return null
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                null
            }
        }

        val file = resolveSandboxedFile(raw, mustExist = true) ?: return null
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @JavascriptInterface
    fun writeFile(path: String?, content: String?): Boolean {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank() || content == null) return false

        if (raw.startsWith("content://")) {
            return try {
                val uri = Uri.parse(raw)
                if (!isSafUriAuthorized(uri)) return false
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        val file = resolveSandboxedFile(raw, mustExist = false) ?: return false
        return try {
            file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            file.writeText(content, Charsets.UTF_8)

            // Mirror writes inside ArenaAI subfolders to public Documents/ArenaAI/ & SAF if applicable
            mirrorPublicIfInsideArenaAI(file, content)
            true
        } catch (_: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun exists(path: String?): Boolean {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return false
        if (raw.startsWith("content://")) {
            return try {
                val uri = Uri.parse(raw)
                if (!isSafUriAuthorized(uri)) return false
                context.contentResolver.openInputStream(uri)?.close()
                true
            } catch (_: Exception) {
                false
            }
        }
        val file = resolveSandboxedFile(raw, mustExist = true) ?: return false
        return file.exists()
    }

    @JavascriptInterface
    fun mkdir(path: String?): Boolean {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return false
        val dir = resolveSandboxedFile(raw, mustExist = false) ?: return false
        return try {
            dir.exists() || dir.mkdirs()
        } catch (_: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun deleteFile(path: String?): Boolean {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return false
        val file = resolveSandboxedFile(raw, mustExist = true) ?: return false
        // Prevent deleting the root ArenaAI folder itself
        if (file.canonicalPath == File(getArenaDir()).canonicalPath) return false
        return try {
            file.exists() && file.delete()
        } catch (_: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun pickDirectory(): String {
        val latch = CountDownLatch(1)
        var pickedUri: String? = null

        mainHandler.post {
            onRequestPickDirectory { uri ->
                pickedUri = uri
                latch.countDown()
            }
        }

        return try {
            latch.await(120, TimeUnit.SECONDS)
            pickedUri ?: ""
        } catch (_: InterruptedException) {
            ""
        }
    }

    private fun resolveSandboxedFile(rawPath: String, mustExist: Boolean): File? {
        if (rawPath.contains("\u0000")) return null

        val arenaRoot = File(getArenaDir())
        val allowedRoots = buildAllowedRoots(arenaRoot)

        val candidate = when {
            rawPath == "." || rawPath == "/" || rawPath.equals("ArenaAI", ignoreCase = true) || rawPath.equals("/ArenaAI", ignoreCase = true) -> {
                arenaRoot
            }
            rawPath.startsWith("ArenaAI/", ignoreCase = true) || rawPath.startsWith("/ArenaAI/", ignoreCase = true) -> {
                val sub = rawPath.removePrefix("/").substringAfter("/")
                File(arenaRoot, sub)
            }
            !rawPath.startsWith("/") -> {
                File(arenaRoot, rawPath)
            }
            else -> {
                File(rawPath)
            }
        }

        return try {
            val canonicalCandidate = candidate.canonicalFile
            val isInsideSandbox = allowedRoots.any { root ->
                val canonicalRoot = root.canonicalFile
                canonicalCandidate.path == canonicalRoot.path ||
                    canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)
            }
            if (!isInsideSandbox) return null
            if (mustExist && !canonicalCandidate.exists()) {
                // Also try public Documents/ArenaAI fallback for relative paths if not in app external dir
                if (!rawPath.startsWith("/")) {
                    @Suppress("DEPRECATION")
                    val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val publicCandidate = File(File(docsDir, ArenaSettingsRepository.ROOT_FOLDER), rawPath.removePrefix("ArenaAI/")).canonicalFile
                    if (publicCandidate.exists()) return publicCandidate
                }
            }
            canonicalCandidate
        } catch (_: Exception) {
            null
        }
    }

    private fun buildAllowedRoots(arenaRoot: File): List<File> {
        val roots = mutableListOf(arenaRoot)
        context.getExternalFilesDir(null)?.let { roots.add(it) }
        try {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) roots.add(File(docsDir, ArenaSettingsRepository.ROOT_FOLDER))
        } catch (_: Exception) {
        }
        try {
            @Suppress("DEPRECATION")
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null) roots.add(File(extRoot, ArenaSettingsRepository.ROOT_FOLDER))
        } catch (_: Exception) {
        }
        return roots
    }

    private fun mirrorPublicIfInsideArenaAI(file: File, content: String) {
        try {
            val arenaRoot = File(getArenaDir()).canonicalFile
            val canonicalFile = file.canonicalFile
            if (!canonicalFile.path.startsWith(arenaRoot.path + File.separator)) return
            val relative = canonicalFile.path.removePrefix(arenaRoot.path + File.separator)
            val parentRel = relative.substringBeforeLast(File.separator, "")
            val subPath = if (parentRel.isBlank()) {
                ArenaSettingsRepository.ROOT_FOLDER
            } else {
                "${ArenaSettingsRepository.ROOT_FOLDER}/$parentRel"
            }
            ArenaSettingsRepository.writePublicDocumentText(
                context = context,
                subPath = subPath,
                fileName = canonicalFile.name,
                mimeType = "application/octet-stream",
                content = content
            )
            val settings = ArenaSettingsRepository.loadSettings(context)
            if (settings.safTreeUri.isNotBlank() && parentRel.isNotBlank()) {
                ArenaSettingsRepository.writeSafAddonFile(
                    context = context,
                    safTreeUriStr = settings.safTreeUri,
                    subfolder = parentRel,
                    fileName = canonicalFile.name,
                    mimeType = "application/octet-stream",
                    content = content
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun isSafUriAuthorized(uri: Uri): Boolean {
        val savedSaf = ArenaSettingsRepository.loadSettings(context).safTreeUri
        if (savedSaf.isBlank()) return false
        val treeAuth = Uri.parse(savedSaf).authority
        return uri.authority == treeAuth
    }

    private fun listSafDirectory(uriString: String): String {
        return try {
            val treeUri = Uri.parse(uriString)
            if (!isSafUriAuthorized(treeUri)) {
                return errorJson("URI SAF não autorizada.")
            }
            val docId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            val entries = JSONArray()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIdx)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val mime = cursor.getString(mimeIdx) ?: ""
                    entries.put(
                        JSONObject().apply {
                            put("name", cursor.getString(nameIdx) ?: "")
                            put("path", childUri.toString())
                            put("isDirectory", mime == DocumentsContract.Document.MIME_TYPE_DIR)
                            put("size", cursor.getLong(sizeIdx))
                            put("lastModified", cursor.getLong(modIdx))
                        }
                    )
                }
            }
            JSONObject().apply {
                put("ok", true)
                put("path", uriString)
                put("entries", entries)
            }.toString()
        } catch (e: Exception) {
            errorJson(e.message ?: "Erro ao listar diretório SAF.")
        }
    }

    private fun errorJson(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
            put("entries", JSONArray())
        }.toString()
    }
}
