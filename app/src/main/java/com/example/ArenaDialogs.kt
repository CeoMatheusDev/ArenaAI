package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutArenaDialog(
    connectedEmail: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_arena_ai_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ARENA_HOME_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                AboutInfoRow(label = stringResource(R.string.about_version), value = BuildConfig.VERSION_NAME)
                AboutInfoRow(label = stringResource(R.string.about_build), value = BuildConfig.VERSION_CODE.toString())
                AboutInfoRow(label = stringResource(R.string.about_year), value = stringResource(R.string.app_release_year))
                AboutInfoRow(
                    label = stringResource(R.string.about_url_updated),
                    value = stringResource(R.string.url_last_updated_date)
                )
                AboutInfoRow(label = stringResource(R.string.about_main_url), value = ARENA_HOME_URL)
                if (connectedEmail.isNotBlank()) {
                    AboutInfoRow(label = stringResource(R.string.about_connected_account), value = connectedEmail)
                }
                AboutInfoRow(label = stringResource(R.string.about_json_config), value = "ArenaAI/settings.json")
                AboutInfoRow(label = stringResource(R.string.about_documentation), value = "ArenaAI/documentation.md")
                AboutInfoRow(label = stringResource(R.string.about_addons), value = "ArenaAI/extensions & plugins")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("about_close_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppSettingsDialog(
    settings: ArenaSettings,
    storageSummary: String,
    onUpdateSettings: (ArenaSettings) -> Unit,
    onTestNotification: () -> Unit,
    onAuthorizeSafFolder: () -> Unit,
    onOpenGuide: () -> Unit,
    onClearCacheData: () -> Unit,
    onResetApp: () -> Unit,
    onDismiss: () -> Unit
) {
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val selectedLanguage = remember(settings.language) {
        AppLanguage.fromCode(settings.language)
    }
    val effectiveAutoLanguage = remember(settings.language) {
        val effectiveCode = ArenaLocaleHelper.resolveEffectiveLanguageCode(settings.language)
        AppLanguage.fromCode(effectiveCode)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Category 1: General & Language
                SettingsCategoryHeader(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_cat_general)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { showLanguagePicker = true }
                        .testTag("settings_language_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = if (selectedLanguage == AppLanguage.AUTO) {
                                    effectiveAutoLanguage.flag
                                } else {
                                    selectedLanguage.flag
                                },
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setting_language_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedLanguage == AppLanguage.AUTO) {
                                    stringResource(
                                        R.string.language_auto_resolved,
                                        effectiveAutoLanguage.nativeName
                                    )
                                } else {
                                    "${selectedLanguage.displayName} (${selectedLanguage.nativeName})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.setting_language_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingToggleRow(
                    title = stringResource(R.string.setting_fullscreen_title),
                    subtitle = stringResource(R.string.setting_fullscreen_subtitle),
                    checked = settings.fullscreen,
                    onCheckedChange = { onUpdateSettings(settings.copy(fullscreen = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_desktop_mode_title),
                    subtitle = stringResource(R.string.setting_desktop_mode_subtitle),
                    checked = settings.desktopMode,
                    onCheckedChange = { onUpdateSettings(settings.copy(desktopMode = it)) }
                )
                TextZoomControlRow(
                    textZoom = settings.textZoom,
                    onDecrease = {
                        val updated = (settings.textZoom - 10).coerceIn(80, 140)
                        onUpdateSettings(settings.copy(textZoom = updated))
                    },
                    onIncrease = {
                        val updated = (settings.textZoom + 10).coerceIn(80, 140)
                        onUpdateSettings(settings.copy(textZoom = updated))
                    }
                )

                // Category 2: Notifications & Background
                SettingsCategoryHeader(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_cat_notifications)
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_notify_title),
                    subtitle = stringResource(R.string.setting_notify_subtitle),
                    checked = settings.notifyOnFinish,
                    onCheckedChange = { onUpdateSettings(settings.copy(notifyOnFinish = it)) }
                )
                FilledTonalButton(
                    onClick = onTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_test_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_test_notification))
                }
                SettingToggleRow(
                    title = stringResource(R.string.setting_background_title),
                    subtitle = stringResource(R.string.setting_background_subtitle),
                    checked = settings.backgroundServiceEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(backgroundServiceEnabled = it)) }
                )

                // Category 3: Display & Power
                SettingsCategoryHeader(
                    icon = Icons.Default.BrightnessHigh,
                    title = stringResource(R.string.settings_cat_display)
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_awake_open_title),
                    subtitle = stringResource(R.string.setting_awake_open_subtitle),
                    checked = settings.keepScreenAwakeOnOpen,
                    onCheckedChange = { onUpdateSettings(settings.copy(keepScreenAwakeOnOpen = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_awake_gen_title),
                    subtitle = stringResource(R.string.setting_awake_gen_subtitle),
                    checked = settings.keepScreenAwakeOnGenerating,
                    onCheckedChange = { onUpdateSettings(settings.copy(keepScreenAwakeOnGenerating = it)) }
                )

                // Category 4: Account & Privacy
                SettingsCategoryHeader(
                    icon = Icons.Default.AccountCircle,
                    title = stringResource(R.string.settings_cat_account)
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_autologin_title),
                    subtitle = stringResource(R.string.setting_autologin_subtitle),
                    checked = settings.autoLoginPromptEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoLoginPromptEnabled = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_keep_cookies_title),
                    subtitle = stringResource(R.string.setting_keep_cookies_subtitle),
                    checked = settings.keepSessionCookies,
                    onCheckedChange = { onUpdateSettings(settings.copy(keepSessionCookies = it)) }
                )

                // Category 5: Add-ons & Storage
                SettingsCategoryHeader(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.settings_cat_addons)
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_autoload_title),
                    subtitle = stringResource(R.string.setting_autoload_subtitle),
                    checked = settings.extensionsAutoLoad,
                    onCheckedChange = { onUpdateSettings(settings.copy(extensionsAutoLoad = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_autoscan_title),
                    subtitle = stringResource(R.string.setting_autoscan_subtitle),
                    checked = settings.autoScanAddonsOnStartup,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoScanAddonsOnStartup = it)) }
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = storageSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onAuthorizeSafFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_arena_folder), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onOpenGuide,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_documentation), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Category 6: Maintenance & Data
                SettingsCategoryHeader(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.settings_cat_maintenance)
                )
                OutlinedButton(
                    onClick = onClearCacheData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_clear_cache_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_clear_cache))
                }
                FilledTonalButton(
                    onClick = { showResetConfirmation = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_app_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_reset_app), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentLanguageCode = settings.language,
            onSelectLanguage = { chosen ->
                showLanguagePicker = false
                onUpdateSettings(settings.copy(language = chosen.code))
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.reset_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.reset_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showResetConfirmation = false
                        onResetApp()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("settings_confirm_reset_button")
                ) {
                    Text(stringResource(R.string.action_confirm_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(
    icon: ImageVector,
    title: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun TextZoomControlRow(
    textZoom: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setting_text_zoom_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.setting_text_zoom_subtitle, textZoom),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDecrease,
                enabled = textZoom > 80,
                modifier = Modifier.testTag("settings_zoom_decrease")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "-"
                )
            }
            Text(
                text = "$textZoom%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = onIncrease,
                enabled = textZoom < 140,
                modifier = Modifier.testTag("settings_zoom_increase")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "+"
                )
            }
        }
    }
}

@Composable
fun LanguagePickerDialog(
    currentLanguageCode: String,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = AppLanguage.fromCode(currentLanguageCode)
    val autoTarget = AppLanguage.fromCode(
        ArenaLocaleHelper.resolveEffectiveLanguageCode(AppLanguage.AUTO.code)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.language_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(AppLanguage.entries, key = { it.code }) { lang ->
                    val isSelected = lang == selected
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectLanguage(lang) }
                            .testTag("language_option_${lang.code}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = lang.flag,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == AppLanguage.AUTO) {
                                        stringResource(R.string.language_auto_label)
                                    } else {
                                        lang.displayName
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (lang == AppLanguage.AUTO) {
                                        "${autoTarget.flag} ${autoTarget.nativeName}"
                                    } else {
                                        lang.nativeName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isSelected) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun ExtensionSettingsDialog(
    addons: List<ArenaAddon>,
    autoLoadEnabled: Boolean,
    isScanning: Boolean,
    storageSummary: String,
    onToggleAutoLoad: (Boolean) -> Unit,
    onToggleAddon: (ArenaAddon, Boolean) -> Unit,
    onDeleteAddon: (ArenaAddon) -> Unit,
    onRescan: () -> Unit,
    onImportAddon: (AddonType) -> Unit,
    onAuthorizeSafFolder: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    var showImportTypeChooser by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.action_extensions_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.ext_dialog_subtitle, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ext_run_on_open_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.ext_run_on_open_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoLoadEnabled,
                            onCheckedChange = onToggleAutoLoad
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilledTonalButton(
                        onClick = onRescan,
                        enabled = !isScanning,
                        modifier = Modifier.testTag("addons_rescan_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isScanning) stringResource(R.string.ext_scanning) else stringResource(R.string.ext_rescan))
                    }
                    FilledTonalButton(
                        onClick = { showImportTypeChooser = true },
                        modifier = Modifier.testTag("addons_import_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ext_import))
                    }
                    OutlinedButton(
                        onClick = onAuthorizeSafFolder,
                        modifier = Modifier.testTag("addons_folder_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_arena_folder))
                    }
                    OutlinedButton(
                        onClick = onOpenGuide,
                        modifier = Modifier.testTag("addons_guide_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_documentation))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (addons.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.ext_empty_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.ext_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = storageSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(addons, key = { it.id }) { addon ->
                            AddonItemCard(
                                addon = addon,
                                onToggle = { enabled -> onToggleAddon(addon, enabled) },
                                onDelete = { onDeleteAddon(addon) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )

    if (showImportTypeChooser) {
        AlertDialog(
            onDismissRequest = { showImportTypeChooser = false },
            title = { Text(stringResource(R.string.ext_import_title)) },
            text = { Text(stringResource(R.string.ext_import_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportTypeChooser = false
                        onImportAddon(AddonType.EXTENSION)
                    }
                ) {
                    Text(stringResource(R.string.ext_folder_extension))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportTypeChooser = false
                        onImportAddon(AddonType.PLUGIN)
                    }
                ) {
                    Text(stringResource(R.string.ext_folder_plugin))
                }
            }
        )
    }
}

@Composable
private fun AddonItemCard(
    addon: ArenaAddon,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = addon.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (addon.type == AddonType.PLUGIN) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = if (addon.type == AddonType.PLUGIN) {
                                    stringResource(R.string.ext_type_plugin)
                                } else {
                                    stringResource(R.string.ext_type_extension)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "v${addon.version} • ${addon.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = addon.isEnabled && addon.compatible,
                    enabled = addon.compatible,
                    onCheckedChange = onToggle
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = addon.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (addon.compatible) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (addon.compatible) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = addon.compatibilityReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (addon.compatible) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MarkdownGuideDialog(
    markdownText: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ArenaAI/${ArenaSettingsRepository.GUIDE_FILE_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = markdownText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
