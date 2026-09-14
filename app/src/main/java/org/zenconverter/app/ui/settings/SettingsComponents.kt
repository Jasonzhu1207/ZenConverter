package org.zenconverter.app.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.zenconverter.app.ui.AccentColorOption
import org.zenconverter.app.ui.AppIcon
import org.zenconverter.app.ui.UiText

/** Shared window insets, reading width, app bar, and scroll behavior for every subpage. */
@Composable
internal fun SettingsPage(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val margin = if (maxWidth >= 600.dp) 24.dp else 16.dp
            Column(
                Modifier.align(Alignment.TopCenter)
                    .padding(horizontal = margin)
                    .widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight()
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
                    }
                    Text(
                        title, modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun settingsCardColor(): Color = MaterialTheme.colorScheme.surface

@Composable
internal fun settingsHairlineColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
internal fun settingsDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
internal fun SettingsGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp)
            .semantics { heading() }
    )
}

@Composable
internal fun InsetGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = settingsCardColor(),
        border = BorderStroke(1.dp, settingsHairlineColor())
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun SettingsRow(
    badgeIcon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(badgeIcon, null, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                (subtitle ?: trailingText)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailingContent?.invoke()
            if (showChevron) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(start = 52.dp), color = settingsDividerColor())
        }
    }
}

/** Actions wrap on phones and at large font scales; the experimental layout stays internal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveActions(content: @Composable RowScope.() -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (maxWidth < 400.dp || fontScale > 1.3f) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

/** Measure localized labels at the actual font scale before choosing the horizontal layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingsChoiceGroup(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val requiredWidth = options.maxOfOrNull { option ->
            with(density) { measurer.measure(labelProvider(option), style).size.width.toDp() } + 64.dp
        } ?: 0.dp
        if (density.fontScale <= 1.3f && requiredWidth * options.size <= maxWidth) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selectedOption == option,
                        onClick = { onOptionSelected(option) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(labelProvider(option)) }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selectedOption == option, enabled = enabled, role = Role.RadioButton,
                                onClick = { onOptionSelected(option) })
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == option, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(12.dp))
                        Text(labelProvider(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccentColorPickerRow(
    selectedAccent: AccentColorOption,
    onAccentSelected: (AccentColorOption) -> Unit,
    texts: UiText,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val availableAccents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AccentColorOption.entries
    } else {
        AccentColorOption.entries.filter { it != AccentColorOption.Dynamic }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(texts.accentLabel(selectedAccent), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableAccents.forEach { option ->
                val selected = option == selectedAccent
                val (color, foreground) = if (option == AccentColorOption.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    scheme.primary to scheme.onPrimary
                } else option.color(isDark) to option.contentColor(isDark)
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .selectable(selected, role = Role.RadioButton, onClick = { onAccentSelected(option) })
                        .semantics { contentDescription = texts.accentLabel(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(38.dp)
                            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(30.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected || option == AccentColorOption.Dynamic) {
                                Icon(if (selected) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
                                    null, tint = foreground, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
