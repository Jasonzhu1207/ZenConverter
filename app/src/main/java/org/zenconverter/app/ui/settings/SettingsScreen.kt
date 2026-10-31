package org.zenconverter.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zenconverter.app.BuildConfig
import org.zenconverter.app.R
import org.zenconverter.app.i18n.AppLanguages
import org.zenconverter.app.i18n.LanguageOption
import org.zenconverter.app.settings.ActionBarPosition
import org.zenconverter.app.ui.*
import org.zenconverter.app.ui.theme.ZenDimensions

@Composable
internal fun SettingsScreen(
    texts: UiText,
    selectedAccent: AccentColorOption,
    selectedThemeMode: ThemeModeOption,
    isOledDark: Boolean,
    selectedLanguage: LanguageOption,
    selectedActionBarPosition: ActionBarPosition,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    onAccentSelected: (AccentColorOption) -> Unit,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onOledDarkChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit,
    onActionBarPositionSelected: (ActionBarPosition) -> Unit,
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
    var showActionBarDialog by rememberSaveable { mutableStateOf(false) }
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
                    badgeIcon = Icons.Rounded.ViewAgenda, title = texts.settingsActionBarPosition,
                    trailingText = texts.actionBarPositionLabel(selectedActionBarPosition), showChevron = true,
                    onClick = { showActionBarDialog = true }
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.zenconverter), contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        ZenPromptFrame(
            onDismissRequest = { showOutputDialog = false },
            maxWidth = 380.dp
        ) {
            SettingsModalHeader(
                title = texts.output,
                closeLabel = texts.optionValue("Close"),
                onClose = { showOutputDialog = false }
            )
            InsetGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    SettingsModalOptionRow(
                        title = texts.defaultOutputLocation,
                        subtitle = texts.defaultOutputNote,
                        leadingIcon = Icons.Rounded.Folder,
                        selected = outputLocationMode == OutputLocationMode.Default,
                        showDivider = true,
                        onClick = {
                            onOutputLocationModeChange(OutputLocationMode.Default)
                            showOutputDialog = false
                        }
                    )
                    SettingsModalOptionRow(
                        title = texts.customOutputLocation,
                        subtitle = outputDirectory?.label?.ifBlank { null } ?: texts.chooseFolderBeforeConversion,
                        leadingIcon = Icons.Rounded.FolderOpen,
                        selected = outputLocationMode == OutputLocationMode.Custom,
                        showDivider = false,
                        onClick = {
                            onOutputLocationModeChange(OutputLocationMode.Custom)
                            if (outputDirectory == null) {
                                onPickOutputDirectory()
                            }
                            showOutputDialog = false
                        }
                    )
                }
            }
            if (outputLocationMode == OutputLocationMode.Custom) {
                OutlinedButton(
                    onClick = {
                        showOutputDialog = false
                        onPickOutputDirectory()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 38.dp),
                    shape = RoundedCornerShape(ZenDimensions.RadiusSmall),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    AppIcon(
                        icon = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = texts.chooseDirectory,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    // ── Language Selection Modal ────────────────────────────────────
    if (showLanguageDialog) {
        val languageOptions = AppLanguages.options(context)
        ZenPromptFrame(
            onDismissRequest = { showLanguageDialog = false },
            maxWidth = 380.dp
        ) {
            SettingsModalHeader(
                title = texts.settingsLanguage,
                closeLabel = texts.optionValue("Close"),
                onClose = { showLanguageDialog = false }
            )
            InsetGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    languageOptions.forEachIndexed { index, option ->
                        SettingsModalOptionRow(
                            title = texts.languageLabel(option),
                            selected = option == selectedLanguage,
                            showDivider = index < languageOptions.lastIndex,
                            onClick = {
                                onLanguageSelected(option)
                                showLanguageDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Action Bar Position Selection Modal ─────────────────────────
    if (showActionBarDialog) {
        val positions = listOf(ActionBarPosition.Top, ActionBarPosition.Bottom)
        ZenPromptFrame(
            onDismissRequest = { showActionBarDialog = false },
            maxWidth = 380.dp
        ) {
            SettingsModalHeader(
                title = texts.settingsActionBarPosition,
                subtitle = texts.settingsActionBarPositionDesc,
                closeLabel = texts.optionValue("Close"),
                onClose = { showActionBarDialog = false }
            )
            InsetGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                ) {
                    positions.forEachIndexed { index, position ->
                        SettingsModalOptionRow(
                            title = texts.actionBarPositionLabel(position),
                            leadingIcon = if (position == ActionBarPosition.Bottom) {
                                Icons.Rounded.VerticalAlignBottom
                            } else {
                                Icons.Rounded.VerticalAlignTop
                            },
                            selected = position == selectedActionBarPosition,
                            showDivider = index < positions.lastIndex,
                            onClick = {
                                onActionBarPositionSelected(position)
                                showActionBarDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsModalHeader(
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                icon = Icons.Rounded.Close,
                contentDescription = closeLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SettingsModalOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    else Color.Transparent
                )
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = if (subtitle != null) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                AppIcon(
                    icon = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                AppIcon(
                    icon = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(18.dp))
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = if (leadingIcon != null) 45.dp else 14.dp,
                    end = 14.dp
                ),
                thickness = 0.5.dp,
                color = settingsDividerColor()
            )
        }
    }
}

