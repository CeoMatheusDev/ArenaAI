package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                        text = "https://arena.ai/agent",
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
                    text = "Cliente Android dedicado ao Arena AI com suporte a login por conta do aparelho, notificações em segundo plano, tela sempre acesa e sistema nativo de Extensões & Plugins.",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                AboutInfoRow(label = "Versão", value = BuildConfig.VERSION_NAME)
                AboutInfoRow(label = "Build", value = BuildConfig.VERSION_CODE.toString())
                AboutInfoRow(label = "Ano", value = stringResource(R.string.app_release_year))
                AboutInfoRow(
                    label = "Atualização da URL",
                    value = stringResource(R.string.url_last_updated_date)
                )
                AboutInfoRow(label = "URL Principal", value = ARENA_HOME_URL)
                if (connectedEmail.isNotBlank()) {
                    AboutInfoRow(label = "Conta Conectada", value = connectedEmail)
                }
                AboutInfoRow(label = "Configuração JSON", value = "ArenaAI/settings.json")
                AboutInfoRow(label = "Documentação", value = "ArenaAI/documento.md")
                AboutInfoRow(label = "Complementos", value = "ArenaAI/extencao & plugin")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("about_close_button")
            ) {
                Text("Fechar")
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
    onAuthorizeSafFolder: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit
) {
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
                        text = "Configurações do App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Salvo em ArenaAI/settings.json",
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SettingToggleRow(
                    title = "Tela Cheia",
                    subtitle = "Ocultar a barra superior para imersão total",
                    checked = settings.fullscreen,
                    onCheckedChange = { onUpdateSettings(settings.copy(fullscreen = it)) }
                )
                SettingToggleRow(
                    title = "Notificar ao finalizar resposta",
                    subtitle = "Som e vibração quando a IA terminar",
                    checked = settings.notifyOnFinish,
                    onCheckedChange = { onUpdateSettings(settings.copy(notifyOnFinish = it)) }
                )
                SettingToggleRow(
                    title = "Execução em Segundo Plano",
                    subtitle = "Mantém o app ativo se minimizado ao gerar saída",
                    checked = settings.backgroundServiceEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(backgroundServiceEnabled = it)) }
                )
                SettingToggleRow(
                    title = "Manter tela ligada (app aberto)",
                    subtitle = "Impede o celular de apagar a tela com o app aberto",
                    checked = settings.keepScreenAwakeOnOpen,
                    onCheckedChange = { onUpdateSettings(settings.copy(keepScreenAwakeOnOpen = it)) }
                )
                SettingToggleRow(
                    title = "Manter tela ligada ao gerar",
                    subtitle = "Garante tela acesa enquanto o modelo gera resposta",
                    checked = settings.keepScreenAwakeOnGenerating,
                    onCheckedChange = { onUpdateSettings(settings.copy(keepScreenAwakeOnGenerating = it)) }
                )
                SettingToggleRow(
                    title = "Extensões e Plugins automáticos",
                    subtitle = "Conecta complementos compatíveis ao abrir o site",
                    checked = settings.extensionsAutoLoad,
                    onCheckedChange = { onUpdateSettings(settings.copy(extensionsAutoLoad = it)) }
                )
                SettingToggleRow(
                    title = "Buscar complementos ao iniciar",
                    subtitle = "Lê ArenaAI/extencao e plugin automaticamente",
                    checked = settings.autoScanAddonsOnStartup,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoScanAddonsOnStartup = it)) }
                )
                SettingToggleRow(
                    title = "Login com contas do celular",
                    subtitle = "Abre lista de e-mails ao detectar chamada de login",
                    checked = settings.autoLoginPromptEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoLoginPromptEnabled = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = storageSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                        Text("Pasta ArenaAI", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onOpenGuide,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("documento.md", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
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
                        text = "Compatível com Arena AI v${BuildConfig.VERSION_NAME} e arena.ai/agent",
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
                                text = "Executar ao abrir o site",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Injeta extensões e plugins ativos no arena.ai",
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
                        Text(if (isScanning) "Lendo…" else "Reescanear")
                    }
                    FilledTonalButton(
                        onClick = { showImportTypeChooser = true },
                        modifier = Modifier.testTag("addons_import_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importar")
                    }
                    OutlinedButton(
                        onClick = onAuthorizeSafFolder,
                        modifier = Modifier.testTag("addons_folder_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pasta ArenaAI")
                    }
                    OutlinedButton(
                        onClick = onOpenGuide,
                        modifier = Modifier.testTag("addons_guide_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("documento.md")
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
                                text = "Nenhum complemento encontrado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Coloque arquivos .js ou manifest.json nas subpastas extencao/ ou plugin/ dentro de ArenaAI, ou toque em Importar.",
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
                Text("Concluir")
            }
        }
    )

    if (showImportTypeChooser) {
        AlertDialog(
            onDismissRequest = { showImportTypeChooser = false },
            title = { Text("Importar Complemento") },
            text = {
                Text("Escolha em qual subpasta de ArenaAI deseja salvar e conectar o arquivo (.js ou .json):")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportTypeChooser = false
                        onImportAddon(AddonType.EXTENSION)
                    }
                ) {
                    Text("Extensão (extencao/)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportTypeChooser = false
                        onImportAddon(AddonType.PLUGIN)
                    }
                ) {
                    Text("Plugin (plugin/)")
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
                                text = if (addon.type == AddonType.PLUGIN) "Plugin" else "Extensão",
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
                        contentDescription = "Remover",
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
                Text("Fechar")
            }
        }
    )
}
