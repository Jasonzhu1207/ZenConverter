package org.zenconverter.app.ui.settings

import android.os.Build
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
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
import org.zenconverter.app.ui.theme.ZenDimensions
import org.zenconverter.app.ui.theme.bounceClick

/** Shared window insets, reading width, app bar, and scroll behavior for every subpage. */
@Composable
internal fun SettingsPage(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /** Widen the content column on large screens; off by default. */
    allowWideContent: Boolean = false,
    content: LazyListScope.() -> Unit
) {
    GlassBackdrop(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val margin = if (maxWidth >= 600.dp) 24.dp else 16.dp
            val contentWidth = if (allowWideContent && maxWidth >= 840.dp) 1100.dp else 680.dp
            Column(
                Modifier.align(Alignment.TopCenter)
                    .padding(horizontal = margin)
                    .widthIn(max = contentWidth).fillMaxWidth().fillMaxHeight()
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

/**
 * The card surface every subpage draws on: a translucent "glass" panel with a
 * hairline border and a soft top sheen, matching the hub tiles. The translucency
 * is what lets the page backdrop read through, so keep cards on top of the
 * [GlassBackdrop] rather than a flat fill.
 */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassCardShape,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = glassSurfaceAlpha()))
            .border(ZenDimensions.BorderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick, scaleDown = 0.97f) else Modifier)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = GlassSheenAlpha),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

internal val GlassCardShape = RoundedCornerShape(18.dp)

/** Opacity of the top sheen that fakes the preview's blurred highlight. */
private const val GlassSheenAlpha = 0.55f

/**
 * Card fill opacity, matched to the reference design per mode: lighter in light,
 * a touch more open in dark, and denser on OLED black so the card stays readable
 * there without a glow behind it.
 */
@Composable
private fun glassSurfaceAlpha(): Float {
    val background = MaterialTheme.colorScheme.background.luminance()
    return when {
        background > 0.5f -> 0.62f
        background < 0.001f -> 0.72f
        else -> 0.55f
    }
}

/**
 * Paints the soft radial glows the preview uses behind the glass cards. Without
 * a non-flat backdrop the translucent cards would read as plain surfaces, so
 * every page that draws glass lays this down first.
 */
@Composable
internal fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                fun glow(x: Float, y: Float, radiusFraction: Float, color: Color) {
                    val center = Offset(size.width * x, size.height * y)
                    val radius = size.width * radiusFraction
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
                glow(0.12f, -0.08f, 0.85f, secondary.copy(alpha = 0.30f))
                glow(0.92f, 0.04f, 0.75f, primary.copy(alpha = 0.22f))
                glow(0.50f, 1.08f, 0.70f, primary.copy(alpha = 0.16f))
            }
    ) {
        content()
    }
}

/** Solid card fill, for the few places that must sit flat on top of glass. */
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
    GlassCard(modifier = modifier.fillMaxWidth()) {
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(badgeIcon, null, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            HorizontalDivider(Modifier.padding(start = 48.dp), color = settingsDividerColor())
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
            with(density) { measurer.measure(labelProvider(option), style).size.width.toDp() } + 20.dp
        } ?: 0.dp
        if (density.fontScale <= 1.3f && requiredWidth * options.size <= maxWidth) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selectedOption == option,
                        onClick = { onOptionSelected(option) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) { Text(labelProvider(option)) }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .selectable(selectedOption == option, enabled = enabled, role = Role.RadioButton,
                                onClick = { onOptionSelected(option) })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == option, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(10.dp))
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableAccents.forEach { option ->
                val selected = option == selectedAccent
                val (color, foreground) = if (option == AccentColorOption.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    scheme.primary to scheme.onPrimary
                } else option.color(isDark) to option.contentColor(isDark)
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .selectable(selected, role = Role.RadioButton, onClick = { onAccentSelected(option) })
                        .semantics { contentDescription = texts.accentLabel(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(32.dp)
                            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected || option == AccentColorOption.Dynamic) {
                                Icon(if (selected) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
                                    null, tint = foreground, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
