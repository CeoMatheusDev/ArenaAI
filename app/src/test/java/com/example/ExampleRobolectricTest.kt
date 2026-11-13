package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `default apk language is english`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Arena AI", context.getString(R.string.app_name))
        assertEquals("App Settings", context.getString(R.string.settings_title))
        assertEquals("Extension", context.getString(R.string.action_extensions))
    }

    @Test
    fun `settings json persists all app configurations language and active addons`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = ArenaSettings(
            fullscreen = true,
            desktopMode = true,
            textZoom = 120,
            notifyOnFinish = false,
            backgroundServiceEnabled = false,
            keepScreenAwakeOnOpen = true,
            keepScreenAwakeOnGenerating = false,
            extensionsAutoLoad = true,
            autoScanAddonsOnStartup = false,
            autoLoginPromptEnabled = true,
            keepSessionCookies = false,
            language = AppLanguage.SPANISH.code,
            connectedEmail = "test@gmail.com",
            activeExtensions = listOf("ext-1"),
            activePlugins = listOf("plug-1")
        )
        ArenaSettingsRepository.saveSettings(context, saved)

        val root = context.getExternalFilesDir(null)!!
        val settingsFile = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.SETTINGS_FILE_NAME}")
        assertTrue(settingsFile.exists())

        val loaded = ArenaSettingsRepository.loadSettings(context)
        assertTrue(loaded.fullscreen)
        assertTrue(loaded.desktopMode)
        assertEquals(120, loaded.textZoom)
        assertFalse(loaded.notifyOnFinish)
        assertFalse(loaded.backgroundServiceEnabled)
        assertTrue(loaded.keepScreenAwakeOnOpen)
        assertFalse(loaded.keepScreenAwakeOnGenerating)
        assertFalse(loaded.autoScanAddonsOnStartup)
        assertFalse(loaded.keepSessionCookies)
        assertEquals(AppLanguage.SPANISH.code, loaded.language)
        assertEquals("test@gmail.com", loaded.connectedEmail)
        assertEquals(listOf("ext-1"), loaded.activeExtensions)
        assertEquals(listOf("plug-1"), loaded.activePlugins)
    }

    @Test
    fun `resetAllAppData wipes custom ArenaAI files and restores english defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ArenaSettingsRepository.saveSettings(
            context,
            ArenaSettings(
                fullscreen = true,
                desktopMode = true,
                textZoom = 130,
                keepSessionCookies = false,
                language = AppLanguage.PORTUGUESE_BR.code,
                connectedEmail = "user@gmail.com"
            )
        )
        val root = context.getExternalFilesDir(null)!!
        val customAddon = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.PLUGINS_SUBFOLDER}/custom.js")
        customAddon.parentFile?.mkdirs()
        customAddon.writeText("// custom")
        assertTrue(customAddon.exists())

        val reset = ArenaSettingsRepository.resetAllAppData(context)

        assertFalse(customAddon.exists())
        assertFalse(reset.fullscreen)
        assertFalse(reset.desktopMode)
        assertEquals(100, reset.textZoom)
        assertTrue(reset.keepSessionCookies)
        assertEquals(AppLanguage.AUTO.code, reset.language)
        assertEquals("", reset.connectedEmail)

        val guide = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.GUIDE_FILE_NAME}")
        val settingsFile = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.SETTINGS_FILE_NAME}")
        val extDir = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.EXTENSIONS_SUBFOLDER}")
        val pluginDir = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.PLUGINS_SUBFOLDER}")
        assertTrue(guide.exists())
        assertTrue(settingsFile.exists())
        assertTrue(extDir.isDirectory)
        assertTrue(pluginDir.isDirectory)
    }

    @Test
    fun `addon workspace creates ArenaAI extensions plugins documentation md and settings json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context)

        val root = context.getExternalFilesDir(null)!!
        val guide = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.GUIDE_FILE_NAME}")
        val settingsFile = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.SETTINGS_FILE_NAME}")
        val extDir = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.EXTENSIONS_SUBFOLDER}")
        val pluginDir = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.PLUGINS_SUBFOLDER}")

        assertTrue(guide.exists())
        assertTrue(guide.readText().contains("Official Extensions and Plugins Documentation"))
        assertTrue(settingsFile.exists())
        assertTrue(extDir.isDirectory)
        assertTrue(pluginDir.isDirectory)

        // Clean workspace should have 0 addons (settings.json and README.txt must be ignored)
        val initialAddons = ArenaAddonManager.scanAllAddons(context, ArenaSettings())
        assertEquals(0, initialAddons.size)

        // Add a valid JS plugin and verify discovery and compatibility
        val pluginFile = File(pluginDir, "my_plugin.js")
        pluginFile.writeText(
            """
            // ==ArenaAI==
            // @id           my-plugin
            // @name         My Plugin
            // @version      1.0.0
            // @type         plugin
            // @minAppVersion 2.0.0
            // @match        *://arena.ai/*
            // @compatible   true
            // ==/ArenaAI==
            window.ArenaAI.registerPlugin({ id: 'my-plugin' });
            """.trimIndent()
        )

        val afterAdd = ArenaAddonManager.scanAllAddons(context, ArenaSettings())
        assertEquals(1, afterAdd.size)
        assertEquals("My Plugin", afterAdd.first().name)
        assertEquals(AddonType.PLUGIN, afterAdd.first().type)
        assertTrue(afterAdd.first().compatible)
    }

    @Test
    fun `locale helper auto detects supported languages and defaults unknown locales to english`() {
        assertEquals(12, AppLanguage.entries.size)
        assertTrue(AppLanguage.entries.all { it.flag.isNotBlank() })

        assertEquals("en", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale.ENGLISH))
        assertEquals("zh-CN", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale.SIMPLIFIED_CHINESE))
        assertEquals("hi", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("hi")))
        assertEquals("es", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("es", "ES")))
        assertEquals("fr", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale.FRENCH))
        assertEquals("ar", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("ar")))
        assertEquals("bn", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("bn")))
        assertEquals("pt-BR", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("pt", "BR")))
        assertEquals("pt-PT", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("pt", "PT")))
        assertEquals("ru", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("ru")))
        assertEquals("ur", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale("ur")))

        // Unsupported device locale falls back to English
        assertEquals("en", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale.JAPANESE))
        assertEquals("en", ArenaLocaleHelper.resolveEffectiveLanguageCode("auto", Locale.GERMAN))

        // Explicit choice overrides device locale
        assertEquals("pt-BR", ArenaLocaleHelper.resolveEffectiveLanguageCode("pt-BR", Locale.JAPANESE))
    }

    @Test
    fun `notifications are completely suppressed when notifyOnFinish is disabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)

        ArenaSettingsRepository.saveSettings(context, ArenaSettings(notifyOnFinish = false))
        assertFalse(NotificationHelper.isNotificationsAllowed(context))

        NotificationHelper.showResponseNotification(context, "Should not display")
        NotificationHelper.showScanningAddonsNotification(context)
        NotificationHelper.showAddonsFoundNotification(context, 1, 1)

        assertEquals(0, shadowNm.allNotifications.size)
    }

    @Test
    fun `account helper detects auth urls and appends login hint`() {
        assertTrue(ArenaAccountHelper.isLoginOrAuthUrl("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ArenaAccountHelper.isLoginOrAuthUrl("https://arena.ai/login"))
        assertFalse(ArenaAccountHelper.isLoginOrAuthUrl("https://arena.ai/agent"))

        val hinted = ArenaAccountHelper.appendLoginHintIfGoogleOAuth(
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=123",
            "user@gmail.com"
        )
        assertTrue(hinted.contains("login_hint=user%40gmail.com"))
    }

    @Test
    fun `file bridge ArenaFS reads writes lists and blocks sandbox escape`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context)

        val bridge = ArenaFileBridge(context) { callback -> callback("content://sample/tree") }

        assertTrue(bridge.writeFile("plugins/test_fs.txt", "ArenaFS Content"))
        assertTrue(bridge.exists("plugins/test_fs.txt"))
        assertEquals("ArenaFS Content", bridge.readFile("plugins/test_fs.txt"))

        val listingJson = bridge.listFiles("plugins")
        assertTrue(listingJson.contains("\"ok\":true"))
        assertTrue(listingJson.contains("test_fs.txt"))

        // Verify sandbox prevents escaping ArenaAI directory
        assertFalse(bridge.writeFile("../../etc/passwd", "hack"))
        assertEquals(null, bridge.readFile("/etc/hosts"))
        val blockedList = bridge.listFiles("/etc")
        assertTrue(blockedList.contains("\"ok\":false"))

        assertTrue(bridge.deleteFile("plugins/test_fs.txt"))
        assertFalse(bridge.exists("plugins/test_fs.txt"))
    }

    @Test
    fun `sdk injection exposes ArenaAI dom helpers language and exact composer button detection`() {
        val sdk = ArenaAddonManager.buildSiteSdkAndAddonsScript(
            addons = emptyList(),
            connectedEmail = "",
            languageCode = AppLanguage.PORTUGUESE_BR.code
        )
        assertTrue(sdk.contains("language: \"pt-BR\""))
        assertTrue(sdk.contains("arena-ai-language"))
        assertTrue(sdk.contains("dom:"))
        assertTrue(sdk.contains("click: function(el)"))
        assertTrue(sdk.contains("waitFor: function(selector, timeout)"))
        assertTrue(sdk.contains("PointerEvent"))
        assertTrue(sdk.contains("TouchEvent"))
        assertTrue(sdk.contains("MouseEvent"))

        assertTrue(ArenaJsInterface.SCRIPT_INJECTION.contains("DOM_SETTLE_DEBOUNCE_MS = 1000"))
        assertTrue(ArenaJsInterface.SCRIPT_INJECTION.contains("getChatContainer"))
        assertTrue(ArenaJsInterface.SCRIPT_INJECTION.contains("button[data-perf-target=\"send\"]"))
        assertTrue(ArenaJsInterface.SCRIPT_INJECTION.contains("Stop generating"))
        assertTrue(ArenaJsInterface.SCRIPT_INJECTION.contains("Send message"))
        assertTrue(ArenaJsInterface.SCRIPT_INJECTION.contains("sawStopButton"))
    }

    @Test
    fun `default path validation persistence and automatic workspace recreation work properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("/ArenaAI/", ArenaSettings().defaultPath)
        assertTrue(ArenaSettingsRepository.isValidCustomPathInput("/CustomArena/"))
        assertFalse(ArenaSettingsRepository.isValidCustomPathInput("../escape"))
        assertFalse(ArenaSettingsRepository.isValidCustomPathInput("bad:path"))

        val customSettings = ArenaSettings(defaultPath = "/CustomArena/Workspace")
        ArenaSettingsRepository.saveSettings(context, customSettings)
        ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context, customSettings)

        val root = context.getExternalFilesDir(null)!!
        val customBase = File(root, "CustomArena/Workspace")
        val settingsFile = File(customBase, ArenaSettingsRepository.SETTINGS_FILE_NAME)
        val docFile = File(customBase, ArenaSettingsRepository.GUIDE_FILE_NAME)
        val extDir = File(customBase, ArenaSettingsRepository.EXTENSIONS_SUBFOLDER)
        val pluginDir = File(customBase, ArenaSettingsRepository.PLUGINS_SUBFOLDER)

        assertTrue(settingsFile.exists())
        assertTrue(docFile.exists())
        assertTrue(extDir.isDirectory)
        assertTrue(pluginDir.isDirectory)

        // Delete document.md and extension/ and ensure scanAllAddons recreates them
        docFile.delete()
        extDir.deleteRecursively()
        assertFalse(docFile.exists())
        assertFalse(extDir.exists())

        ArenaAddonManager.scanAllAddons(context, customSettings)
        assertTrue(docFile.exists())
        assertTrue(extDir.isDirectory)

        // Restore default path so other tests remain clean
        ArenaSettingsRepository.resetAllAppData(context)
    }

    @Test
    fun `forceReload emits hot reload reset in sdk injection script`() {
        val noReload = ArenaAddonManager.buildSiteSdkAndAddonsScript(
            addons = emptyList(),
            connectedEmail = "",
            languageCode = "en",
            forceReload = false
        )
        val withReload = ArenaAddonManager.buildSiteSdkAndAddonsScript(
            addons = emptyList(),
            connectedEmail = "",
            languageCode = "en",
            forceReload = true
        )

        assertTrue(noReload.contains("_resetForReload"))
        assertFalse(noReload.contains("window.ArenaAI._resetForReload();"))
        assertTrue(withReload.contains("window.ArenaAI._resetForReload();"))
    }
}
