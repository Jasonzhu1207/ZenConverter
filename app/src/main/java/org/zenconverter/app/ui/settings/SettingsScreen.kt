package org.zenconverter.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.zenconverter.app.BuildConfig
import org.zenconverter.app.R
import org.zenconverter.app.i18n.AppLanguages
import org.zenconverter.app.i18n.LanguageOption
import org.zenconverter.app.ui.*

@Composable
internal fun SettingsScreen(
    texts: UiText,
    selectedAccent: AccentColorOption,
    selectedThemeMode: ThemeModeOption,
    isOledDark: Boolean,
    selectedLanguage: LanguageOption,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    onAccentSelected: (AccentColorOption) -> Unit,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onOledDarkChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickOutputDirectory: () -> Unit,
    onNavigateToMetadataSecurity: () -> Unit,
    onNavigateToOfflineEngines: () -> Unit,
    onShowHelp: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    onShowSupport: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean,
    updateState: UpdateStateHolder
) {
    val context = LocalContext.current
    val installedVersion = remember { installedAppVersion(context) }
    var showOutputDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }

    SettingsPage(texts.settingsTitle, texts.settingsBack, onBack) {
        item(key = "general-title") { SettingsGroupHeader(texts.settingsGroupGeneral) }
        item(key = "general") {
            InsetGroupCard {
                SettingsRow(
                    badgeIcon = Icons.Rounded.FolderOpen, title = texts.output,
                    subtitle = if (outputLocationMode == OutputLocationMode.Default) texts.defaultOutputLocation
                        else outputDirectory?.label?.ifBlank { null } ?: texts.chooseFolderBeforeConversion,
                    showChevron = true, onClick = { showOutputDialog = true }
                )
                SettingsRow(
                    badgeIcon = Icons.Rounded.Language, title = texts.settingsLanguage,
                    trailingText = texts.languageLabel(selectedLanguage), showChevron = true,
                    showDivider = false, onClick = { showLanguageDialog = true }
                )
            }
        }
        item(key = "appearance-title") { SettingsGroupHeader(texts.settingsGroupAppearance) }
        item(key = "appearance") {
            InsetGroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsChoiceGroup(
                        options = ThemeModeOption.entries, selectedOption = selectedThemeMode,
                        onOptionSelected = onThemeModeSelected, labelProvider = texts::themeModeLabel
                    )
                    if (selectedThemeMode != ThemeModeOption.Light) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(value = isOledDark, role = Role.Switch, onValueChange = onOledDarkChange),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(texts.usePureBlackTheme, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = isOledDark, onCheckedChange = null)
                        }
                    }
                    Text(texts.accentColor, style = MaterialTheme.typography.titleSmall)
                    AccentColorPickerRow(selectedAccent, onAccentSelected, texts, isDark)
                }
            }
        }
        item(key = "tools-title") { SettingsGroupHeader(texts.settingsGroupPrivacyEngines) }
        item(key = "tools") {
            InsetGroupCard {
                SettingsRow(
                    badgeIcon = Icons.Rounded.Security, title = texts.metadataSecurityTitle,
                    subtitle = texts.settingsMetadataSecurityDesc, showChevron = true,
                    onClick = onNavigateToMetadataSecurity
                )
                SettingsRow(
                    badgeIcon = Icons.Rounded.AutoAwesome, title = texts.settingsOfflineEngines,
                    subtitle = texts.settingsOfflineEnginesDesc, showChevron = true,
                    showDivider = false, onClick = onNavigateToOfflineEngines
                )
            }
        }
        item(key = "about-title") { SettingsGroupHeader(texts.settingsGroupAboutSupport) }
        item(key = "about") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.zenconverter), contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text("${texts.appVersion} ${installedVersion.versionName}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(texts.appLicense, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsRow(badgeIcon = Icons.AutoMirrored.Rounded.MenuBook, title = texts.helpGuide.help,
                    showChevron = true, onClick = onShowHelp)
                SettingsRow(badgeIcon = Icons.Rounded.PrivacyTip, title = texts.privacyPolicy.title,
                    showChevron = true, onClick = onShowPrivacyPolicy)
                SettingsRow(badgeIcon = Icons.Rounded.Code, title = texts.githubRepository,
                    showChevron = true, onClick = { openExternalLink(context, ZENCONVERTER_REPOSITORY_URL, texts.linkUnavailable) })
                SettingsRow(badgeIcon = Icons.Rounded.Favorite, title = texts.supportDevelopment,
                    showChevron = true, showDivider = false, onClick = onShowSupport)
            }
        }
        if (BuildConfig.ENABLE_GITHUB_UPDATES) {
            item(key = "updates") {
                InsetGroupCard {
                    SettingsRow(badgeIcon = Icons.Rounded.SystemUpdate, title = texts.settingsCheckUpdates,
                        subtitle = if (updateState.checking) texts.checkingUpdates else null,
                        showChevron = !showUpdates, showDivider = showUpdates || updateState.busy,
                        onClick = { showUpdates = !showUpdates })
                    AnimatedVisibility(visible = showUpdates || updateState.busy) {
                        UpdateSection(texts, updateState)
                    }
                }
            }
        }
    }

    // ── Output Location Selection Modal ─────────────────────────────
    if (showOutputDialog) {
        ZenPromptFrame(onDismissRequest = { showOutputDialog = false }) {
            Text(
                text = texts.output,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Default location
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (outputLocationMode == OutputLocationMode.Default) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .selectable(selected = outputLocationMode == OutputLocationMode.Default, role = Role.RadioButton, onClick = {
                            onOutputLocationModeChange(OutputLocationMode.Default)
                            showOutputDialog = false
                        })
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = texts.defaultOutputLocation,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (outputLocationMode == OutputLocationMode.Default) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = texts.defaultOutputNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (outputLocationMode == OutputLocationMode.Default) {
                        AppIcon(
                            icon = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Custom location
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (outputLocationMode == OutputLocationMode.Custom) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .selectable(selected = outputLocationMode == OutputLocationMode.Custom, role = Role.RadioButton, onClick = {
                            onOutputLocationModeChange(OutputLocationMode.Custom)
                            if (outputDirectory == null) {
                                onPickOutputDirectory()
                            }
                            showOutputDialog = false
                        })
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = texts.customOutputLocation,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (outputLocationMode == OutputLocationMode.Custom) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = outputDirectory?.label?.ifBlank { null } ?: texts.chooseFolderBeforeConversion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (outputLocationMode == OutputLocationMode.Custom) {
                        AppIcon(
                            icon = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (outputLocationMode == OutputLocationMode.Custom) {
                    OutlinedButton(
                        onClick = {
                            showOutputDialog = false
                            onPickOutputDirectory()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(texts.chooseFolderBeforeConversion)
                    }
                }
            }
        }
    }

    // ── Language Selection Modal ────────────────────────────────────
    if (showLanguageDialog) {
        ZenPromptFrame(onDismissRequest = { showLanguageDialog = false }) {
            Text(
                text = texts.settingsLanguage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguages.options(context).forEach { option ->
                    val isSelected = option == selectedLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = {
                                onLanguageSelected(option)
                                showLanguageDialog = false
                            })
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = texts.languageLabel(option),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            AppIcon(
                                icon = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

