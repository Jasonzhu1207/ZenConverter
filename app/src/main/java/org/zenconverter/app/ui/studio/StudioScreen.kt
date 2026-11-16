package org.zenconverter.app.ui.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.zenconverter.app.studio.HubLayout
import org.zenconverter.app.ui.AppIcon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zenconverter.app.ui.AppScreen
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.GlassCard
import org.zenconverter.app.ui.settings.SettingsGroupHeader
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * Hub for the local-first audio and tools studio. Rendered as a glass tile grid:
 * each section leads with one full-width card, then square tiles fill descending
 * rows (6, 5, 4, 3, 2, 1) so a section never leaves an orphan on its own line.
 * Lyrics & sync uses full-width rows instead, because its entries carry long labels.
 */
@Composable
internal fun StudioScreen(
    texts: UiText,
    onNavigate: (AppScreen) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPage(
        title = texts.studioTitle(),
        backLabel = texts.studioBack(),
        onBack = {},
        modifier = modifier,
        showBack = false,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = texts.openSettings)
            }
        }
    ) {
        studioSections(texts).forEach { section ->
            item(key = "hub-header-${section.title}") { HubSectionHeader(section.title) }
            section.hero?.let { hero ->
                item(key = "hub-hero-${hero.title}") { HubHeroCard(hero, onNavigate) }
            }
            if (section.horizontalRows) {
                items(section.tiles, key = { "hub-row-${it.title}" }) { entry ->
                    HubListRow(entry, onNavigate)
                }
            } else {
                descendingChunks(section.tiles).forEachIndexed { index, chunk ->
                    item(key = "hub-grid-${section.title}-$index") {
                        HubTileGrid(chunk, onNavigate)
                    }
                }
            }
        }
    }
}

private val HubTileGap = 12.dp

/** One hub group: an optional leading full-width card plus the remaining entries. */
private data class HubSection(
    val title: String,
    val hero: HubEntry?,
    val tiles: List<HubEntry>,
    val horizontalRows: Boolean = false
)

private data class HubEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: AppScreen,
    val tag: String? = null
)

/** Splits [entries] into the descending row sizes produced by [HubLayout]. */
private fun descendingChunks(entries: List<HubEntry>): List<List<HubEntry>> {
    var index = 0
    return HubLayout.rowSizes(entries.size).map { size ->
        entries.subList(index, index + size).also { index += size }
    }
}

private fun studioSections(texts: UiText): List<HubSection> = listOf(
    HubSection(
        title = texts.studioSectionAudio(),
        hero = HubEntry(
            texts.studioAudioConverter(), texts.studioAudioConverterDesc(),
            Icons.Rounded.AudioFile, AppScreen.StudioAudioConverter
        ),
        tiles = listOf(
            HubEntry(texts.studioWaveform(), texts.studioWaveformDesc(), Icons.Rounded.GraphicEq, AppScreen.StudioWaveform),
            HubEntry(texts.studioRecorder(), texts.studioRecorderDesc(), Icons.Rounded.Mic, AppScreen.StudioRecorder),
            HubEntry(texts.studioMixer(), texts.studioMixerDesc(), Icons.Rounded.Tune, AppScreen.StudioMixer),
            HubEntry(texts.studioAnalyze(), texts.studioAnalyzeDesc(), Icons.Rounded.Equalizer, AppScreen.StudioAnalyze),
            HubEntry(texts.studioMediaLibrary(), texts.studioMediaLibraryDesc(), Icons.Rounded.FolderOpen, AppScreen.StudioMediaLibrary),
            HubEntry(texts.studioTagEditor(), texts.studioTagEditorDesc(), Icons.Rounded.AudioFile, AppScreen.StudioTags),
            HubEntry(texts.studioRingtone(), texts.studioRingtoneDesc(), Icons.Rounded.NotificationsActive, AppScreen.StudioRingtone)
        )
    ),
    HubSection(
        title = texts.studioSectionCreative(),
        hero = null,
        tiles = listOf(
            HubEntry(texts.studioTts(), texts.studioTtsDesc(), Icons.Rounded.Speaker, AppScreen.StudioTts),
            HubEntry(texts.studioVisualizer(), texts.studioVisualizerDesc(), Icons.Rounded.Movie, AppScreen.StudioVisualizer),
            HubEntry(texts.studioRecipes(), texts.studioRecipesDesc(), Icons.Rounded.AutoAwesome, AppScreen.StudioRecipes, tag = texts.studioTagNew()),
            HubEntry(texts.abPlayerTitle(), texts.abNoOutput(), Icons.Rounded.SwapHoriz, AppScreen.StudioAbPlayer)
        )
    ),
    HubSection(
        title = texts.studioSectionMastering(),
        hero = HubEntry(
            texts.studioMastering(), texts.studioMasteringDesc(),
            Icons.Rounded.Radio, AppScreen.StudioMastering
        ),
        tiles = listOf(
            HubEntry(texts.studioStems(), texts.studioStemsDesc(), Icons.Rounded.Bolt, AppScreen.StudioStems),
            HubEntry(texts.studioStemBalance(), texts.studioStemBalanceDesc(), Icons.Rounded.Balance, AppScreen.StudioMasteringStems),
            HubEntry(texts.studioEq(), texts.studioEqDesc(), Icons.Rounded.Tune, AppScreen.StudioMasteringEq),
            HubEntry(texts.studioSpectrum(), texts.studioSpectrumDesc(), Icons.Rounded.GraphicEq, AppScreen.StudioMasteringSpectrum),
            HubEntry(texts.studioLoudness(), texts.studioLoudnessDesc(), Icons.Rounded.Equalizer, AppScreen.StudioMasteringLoudness),
            HubEntry(texts.studioRadioReady(), texts.studioRadioReadyDesc(), Icons.Rounded.Radio, AppScreen.StudioMasteringCheck),
            HubEntry(texts.studioLoudnessNorm(), texts.studioLoudnessNormDesc(), Icons.Rounded.Speaker, AppScreen.StudioMasteringLoudnessNorm),
            HubEntry(texts.studioExciter(), texts.studioExciterDesc(), Icons.Rounded.AutoFixHigh, AppScreen.StudioMasteringExciter)
        )
    ),
    HubSection(
        title = texts.lyricsSection(),
        hero = HubEntry(
            texts.lyricsEditorTitle(), texts.lyricsEditorDesc(),
            Icons.Rounded.Lyrics, AppScreen.StudioLyricsEditor
        ),
        tiles = listOf(
            HubEntry(texts.lrcImportTitle(), texts.lrcImportDesc(), Icons.Rounded.QueueMusic, AppScreen.StudioLrcImport),
            HubEntry(texts.lrcExportTitle(), texts.lrcExportDesc(), Icons.Rounded.LibraryMusic, AppScreen.StudioLrcExport),
            HubEntry(texts.lyrTransTitle(), texts.lyrTransDesc(), Icons.Rounded.SettingsVoice, AppScreen.StudioLyricTranscribe),
            HubEntry(texts.lyrShiftTitle(), texts.lyrShiftDesc(), Icons.Rounded.AvTimer, AppScreen.StudioLyricShift),
            HubEntry(texts.karaokeTitle(), texts.karaokeDesc(), Icons.Rounded.Subtitles, AppScreen.StudioKaraoke)
        ),
        horizontalRows = true
    ),
    HubSection(
        title = texts.studioSectionSmart(),
        hero = null,
        tiles = listOf(
            HubEntry(texts.studioTranscribe(), texts.studioTranscribeDesc(), Icons.Rounded.SettingsVoice, AppScreen.StudioTranscribe),
            HubEntry(texts.studioStems(), texts.studioStemsDesc(), Icons.Rounded.Bolt, AppScreen.StudioStems),
            HubEntry(texts.cutTranscriptTitle(), texts.cutTranscriptDesc(), Icons.Rounded.ContentCut, AppScreen.StudioCutTranscript),
            HubEntry(texts.splitSilenceTitle(), texts.splitSilenceDesc(), Icons.AutoMirrored.Rounded.CallSplit, AppScreen.StudioSplitSilence),
            HubEntry(texts.burnSubsTitle(), texts.burnSubsDesc(), Icons.Rounded.Subtitles, AppScreen.StudioBurnSubs),
            HubEntry(texts.chapterSplitTitle(), texts.chapterSplitDesc(), Icons.Rounded.Bookmarks, AppScreen.StudioChapterSplit),
            HubEntry(texts.studioHistory(), texts.studioHistoryDesc(), Icons.Rounded.History, AppScreen.StudioHistory),
            HubEntry(texts.studioCommandView(), texts.studioCommandViewDesc(), Icons.Rounded.Terminal, AppScreen.StudioCommand)
        )
    ),
    HubSection(
        title = texts.studioSectionPrivacy(),
        hero = HubEntry(texts.docTitle(), texts.docDesc(), Icons.Rounded.Description, AppScreen.StudioDocumentConverter),
        tiles = listOf(
            HubEntry(texts.studioPlugins(), texts.studioPluginsDesc(), Icons.Rounded.RecordVoiceOver, AppScreen.StudioPlugins),
            HubEntry(texts.studioWifi(), texts.studioWifiDesc(), Icons.Rounded.Wifi, AppScreen.StudioWifi),
            HubEntry(texts.studioCleaner(), texts.studioCleanerDesc(), Icons.Rounded.DeleteSweep, AppScreen.StudioCleaner),
            HubEntry(texts.archiveTitle(), texts.archiveDesc(), Icons.Rounded.FolderZip, AppScreen.StudioArchive, tag = texts.studioTagFixed()),
            HubEntry(texts.pdfSplitTitle(), texts.pdfSplitDesc(), Icons.AutoMirrored.Rounded.CallSplit, AppScreen.StudioPdfSplit),
            HubEntry(texts.pdfCreateTitle(), texts.pdfCreateDesc(), Icons.Rounded.PictureAsPdf, AppScreen.StudioPdfCreate),
            HubEntry(texts.epubTitle(), texts.epubDesc(), Icons.Rounded.MenuBook, AppScreen.StudioEpub),
            HubEntry(texts.folderWatchTitle(), texts.folderWatchDesc(), Icons.Rounded.FolderShared, AppScreen.StudioFolderWatch),
            HubEntry(texts.privacyScrubTitle(), texts.privacyScrubDesc(), Icons.Rounded.Compare, AppScreen.MetadataSecurity),
            HubEntry(texts.guardTitle(), texts.guardDesc(), Icons.Rounded.BatterySaver, AppScreen.StudioGuard),
            HubEntry(texts.studioAccessibility(), texts.studioAccessibilityDesc(), Icons.Rounded.Accessibility, AppScreen.StudioAccessibility),
            HubEntry(texts.crashReportingTitle(), texts.crashReportingDesc(), Icons.Rounded.BugReport, AppScreen.StudioCrashReports)
        )
    )
)

/**
 * Lays a chunk of tiles out in a fixed column count: two on a phone, three once
 * there is room. Trailing gaps are filled with invisible spacers so every square
 * keeps the same size.
 */
@Composable
private fun HubTileGrid(chunk: List<HubEntry>, onNavigate: (AppScreen) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < TabletWidthThreshold) PhoneColumns else TabletColumns
        Column(verticalArrangement = Arrangement.spacedBy(HubTileGap)) {
            chunk.chunked(columns).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HubTileGap)
                ) {
                    rowEntries.forEach { entry ->
                        HubSquareTile(entry, onNavigate, Modifier.weight(1f))
                    }
                    repeat(columns - rowEntries.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val TabletWidthThreshold = 600.dp
private const val PhoneColumns = 2
private const val TabletColumns = 3

@Composable
private fun HubSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** Leading full-width card for a section: icon, stacked title/sub, optional tag. */
@Composable
private fun HubHeroCard(entry: HubEntry, onNavigate: (AppScreen) -> Unit) {
    GlassCard(
        onClick = { onNavigate(entry.screen) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HubTileIcon(entry.icon, 40.dp, 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            entry.tag?.let { HubTag(it) }
        }
    }
}

/** Square tile used to fill the descending 6-to-1 rows. */
@Composable
private fun HubSquareTile(entry: HubEntry, onNavigate: (AppScreen) -> Unit, modifier: Modifier = Modifier) {
    GlassCard(onClick = { onNavigate(entry.screen) }, modifier = modifier.aspectRatio(1f)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubTileIcon(entry.icon, 40.dp, 22.dp)
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            entry.tag?.let { HubTag(it) }
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Full-width row: icon, stacked title/sub, trailing tag. */
@Composable
private fun HubListRow(entry: HubEntry, onNavigate: (AppScreen) -> Unit) {
    GlassCard(
        onClick = { onNavigate(entry.screen) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HubTileIcon(entry.icon, 38.dp, 20.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            entry.tag?.let { HubTag(it) }
        }
    }
}

@Composable
private fun HubTileIcon(icon: ImageVector, size: Dp, iconSize: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, null, MaterialTheme.colorScheme.onPrimary, Modifier.size(iconSize))
    }
}

@Composable
private fun HubTag(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Compact informational row used by studio subpages before a file is chosen. */
@Composable
internal fun StudioNote(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun StudioFieldLabel(text: String) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 2.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A row that toggles a boolean setting, shared by the studio mixer and effects. */
@Composable
internal fun StudioSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A row that opens a single-choice menu. Used where a full settings dropdown is
 * overkill, such as picking the output format for watched-folder conversions.
 */
@Composable
internal fun StudioChoiceRow(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = selected,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

/** A mixer track: the source plus its per-track volume, pan and mute state. */
internal data class MixerTrack(
    val uri: android.net.Uri,
    val volume: Float = 1f,
    val pan: Float = 0f,
    val mute: Boolean = false
)

/** Returns a copy of the list with the track at [index] transformed. */
internal fun List<MixerTrack>.withTrack(
    index: Int,
    transform: (MixerTrack) -> MixerTrack
): List<MixerTrack> {
    if (index !in indices) return this
    return toMutableList().also { it[index] = transform(it[index]) }
}
