package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Arena AI", appName)
  }

  @Test
  fun `settings json persists all app configurations and active addons`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val saved = ArenaSettings(
      fullscreen = true,
      notifyOnFinish = false,
      backgroundServiceEnabled = false,
      keepScreenAwakeOnOpen = true,
      keepScreenAwakeOnGenerating = false,
      extensionsAutoLoad = true,
      autoScanAddonsOnStartup = false,
      autoLoginPromptEnabled = true,
      connectedEmail = "teste@gmail.com",
      activeExtensions = listOf("ext-1"),
      activePlugins = listOf("plug-1")
    )
    ArenaSettingsRepository.saveSettings(context, saved)

    val root = context.getExternalFilesDir(null)!!
    val settingsFile = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.SETTINGS_FILE_NAME}")
    assertTrue(settingsFile.exists())

    val loaded = ArenaSettingsRepository.loadSettings(context)
    assertTrue(loaded.fullscreen)
    assertFalse(loaded.notifyOnFinish)
    assertFalse(loaded.backgroundServiceEnabled)
    assertTrue(loaded.keepScreenAwakeOnOpen)
    assertFalse(loaded.keepScreenAwakeOnGenerating)
    assertFalse(loaded.autoScanAddonsOnStartup)
    assertEquals("teste@gmail.com", loaded.connectedEmail)
    assertEquals(listOf("ext-1"), loaded.activeExtensions)
    assertEquals(listOf("plug-1"), loaded.activePlugins)
  }

  @Test
  fun `addon workspace creates ArenaAI extencao plugin documento md and settings json`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    ArenaSettingsRepository.ensureAddonWorkspaceInitialized(context)

    val root = context.getExternalFilesDir(null)!!
    val guide = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.GUIDE_FILE_NAME}")
    val settingsFile = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.SETTINGS_FILE_NAME}")
    val extDir = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.EXTENSIONS_SUBFOLDER}")
    val pluginDir = File(root, "${ArenaSettingsRepository.ROOT_FOLDER}/${ArenaSettingsRepository.PLUGINS_SUBFOLDER}")

    assertTrue(guide.exists())
    assertTrue(settingsFile.exists())
    assertTrue(extDir.isDirectory)
    assertTrue(pluginDir.isDirectory)

    // Clean workspace should have 0 addons (settings.json and LEIA_ME.txt must be ignored)
    val initialAddons = ArenaAddonManager.scanAllAddons(context, ArenaSettings())
    assertEquals(0, initialAddons.size)

    // Add a valid JS plugin and verify discovery and compatibility
    val pluginFile = File(pluginDir, "meu_plugin.js")
    pluginFile.writeText(
      """
      // ==ArenaAI==
      // @id           meu-plugin
      // @name         Meu Plugin
      // @version      1.0.0
      // @type         plugin
      // @minAppVersion 2.0.0
      // @match        *://arena.ai/*
      // @compatible   true
      // ==/ArenaAI==
      window.ArenaAI.registerPlugin({ id: 'meu-plugin' });
      """.trimIndent()
    )

    val afterAdd = ArenaAddonManager.scanAllAddons(context, ArenaSettings())
    assertEquals(1, afterAdd.size)
    assertEquals("Meu Plugin", afterAdd.first().name)
    assertEquals(AddonType.PLUGIN, afterAdd.first().type)
    assertTrue(afterAdd.first().compatible)
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

    assertTrue(bridge.writeFile("plugin/teste_fs.txt", "Conteudo ArenaFS"))
    assertTrue(bridge.exists("plugin/teste_fs.txt"))
    assertEquals("Conteudo ArenaFS", bridge.readFile("plugin/teste_fs.txt"))

    val listingJson = bridge.listFiles("plugin")
    assertTrue(listingJson.contains("\"ok\":true"))
    assertTrue(listingJson.contains("teste_fs.txt"))

    // Verify sandbox prevents escaping ArenaAI directory
    assertFalse(bridge.writeFile("../../etc/passwd", "hack"))
    assertEquals(null, bridge.readFile("/etc/hosts"))
    val blockedList = bridge.listFiles("/etc")
    assertTrue(blockedList.contains("\"ok\":false"))

    assertTrue(bridge.deleteFile("plugin/teste_fs.txt"))
    assertFalse(bridge.exists("plugin/teste_fs.txt"))
  }
}
