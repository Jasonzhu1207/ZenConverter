package org.zenconverter.app.ui.studio

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsGroupHeader
import org.zenconverter.app.ui.settings.SettingsPage
import org.zenconverter.app.ui.settings.SettingsRow

/**
 * Hub for the local-first audio and tools studio. Every entry opens a subpage;
 * the list is grouped so it stays scannable on a phone.
 */
@Composable
internal fun StudioScreen(
    texts: UiText,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPage(texts.studioTitle(), texts.studioBack(), onBack, modifier = modifier) {
        item(key = "studio-audio-header") { SettingsGroupHeader(texts.studioSectionAudio()) }
        item(key = "studio-audio") {
            InsetGroupCard {
                StudioEntry(Icons.Rounded.GraphicEq, texts.studioWaveform(), texts.studioWaveformDesc(), AppScreen.StudioWaveform, onNavigate)
                StudioEntry(Icons.Rounded.Mic, texts.studioRecorder(), texts.studioRecorderDesc(), AppScreen.StudioRecorder, onNavigate)
                StudioEntry(Icons.Rounded.Tune, texts.studioMixer(), texts.studioMixerDesc(), AppScreen.StudioMixer, onNavigate)
                StudioEntry(Icons.Rounded.Equalizer, texts.studioAnalyze(), texts.studioAnalyzeDesc(), AppScreen.StudioAnalyze, onNavigate)
                StudioEntry(Icons.Rounded.AudioFile, texts.studioTagEditor(), texts.studioTagEditorDesc(), AppScreen.StudioTags, onNavigate)
                StudioEntry(Icons.Rounded.NotificationsActive, texts.studioRingtone(), texts.studioRingtoneDesc(), AppScreen.StudioRingtone, onNavigate, showDivider = false)
            }
        }

        item(key = "studio-mastering-header") { SettingsGroupHeader(texts.studioSectionMastering()) }
        item(key = "studio-mastering") {
            InsetGroupCard {
                StudioEntry(Icons.Rounded.AutoAwesome, texts.studioMastering(), texts.studioMasteringDesc(), AppScreen.StudioMastering, onNavigate)
                StudioEntry(Icons.Rounded.Bolt, texts.studioStems(), texts.studioStemsDesc(), AppScreen.StudioMasteringStems, onNavigate)
                StudioEntry(Icons.Rounded.Balance, texts.studioStemBalance(), texts.studioStemBalanceDesc(), AppScreen.StudioMasteringStems, onNavigate)
                StudioEntry(Icons.Rounded.Tune, texts.studioEq(), texts.studioEqDesc(), AppScreen.StudioMasteringEq, onNavigate)
                StudioEntry(Icons.Rounded.GraphicEq, texts.studioSpectrum(), texts.studioSpectrumDesc(), AppScreen.StudioMasteringSpectrum, onNavigate)
                StudioEntry(Icons.Rounded.Equalizer, texts.studioLoudness(), texts.studioLoudnessDesc(), AppScreen.StudioMasteringLoudness, onNavigate)
                StudioEntry(Icons.Rounded.Radio, texts.studioRadioReady(), texts.studioRadioReadyDesc(), AppScreen.StudioMasteringCheck, onNavigate)
                StudioEntry(Icons.Rounded.Speaker, texts.studioLoudnessNorm(), texts.studioLoudnessNormDesc(), AppScreen.StudioMasteringLoudnessNorm, onNavigate)
                StudioEntry(Icons.Rounded.AutoFixHigh, texts.studioExciter(), texts.studioExciterDesc(), AppScreen.StudioMasteringExciter, onNavigate, showDivider = false)
            }
        }

        item(key = "studio-create-header") { SettingsGroupHeader(texts.studioSectionCreative()) }
        item(key = "studio-create") {
            InsetGroupCard {
                StudioEntry(Icons.Rounded.Speaker, texts.studioTts(), texts.studioTtsDesc(), AppScreen.StudioTts, onNavigate)
                StudioEntry(Icons.Rounded.Movie, texts.studioVisualizer(), texts.studioVisualizerDesc(), AppScreen.StudioVisualizer, onNavigate)
                StudioEntry(Icons.Rounded.AutoAwesome, texts.studioRecipes(), texts.studioRecipesDesc(), AppScreen.StudioRecipes, onNavigate)
                StudioEntry(Icons.Rounded.SwapHoriz, texts.abPlayerTitle(), texts.abNoOutput(), AppScreen.StudioAbPlayer, onNavigate, showDivider = false)
            }
        }

        item(key = "studio-smart-header") { SettingsGroupHeader(texts.studioSectionSmart()) }
        item(key = "studio-smart") {
            InsetGroupCard {
                StudioEntry(Icons.Rounded.SettingsVoice, texts.studioTranscribe(), texts.studioTranscribeDesc(), AppScreen.StudioTranscribe, onNavigate)
                StudioEntry(Icons.Rounded.Bolt, texts.studioStems(), texts.studioStemsDesc(), AppScreen.StudioStems, onNavigate)
                StudioEntry(Icons.Rounded.ContentCut, texts.cutTranscriptTitle(), texts.cutTranscriptDesc(), AppScreen.StudioCutTranscript, onNavigate)
                StudioEntry(Icons.AutoMirrored.Rounded.CallSplit, texts.splitSilenceTitle(), texts.splitSilenceDesc(), AppScreen.StudioSplitSilence, onNavigate)
                StudioEntry(Icons.Rounded.History, texts.studioHistory(), texts.studioHistoryDesc(), AppScreen.StudioHistory, onNavigate)
                StudioEntry(Icons.Rounded.Terminal, texts.studioCommandView(), texts.studioCommandViewDesc(), AppScreen.StudioCommand, onNavigate, showDivider = false)
            }
        }

        item(key = "studio-privacy-header") { SettingsGroupHeader(texts.studioSectionPrivacy()) }
        item(key = "studio-privacy") {
            InsetGroupCard {
                StudioEntry(Icons.Rounded.RecordVoiceOver, texts.studioPlugins(), texts.studioPluginsDesc(), AppScreen.StudioPlugins, onNavigate)
                StudioEntry(Icons.Rounded.Wifi, texts.studioWifi(), texts.studioWifiDesc(), AppScreen.StudioWifi, onNavigate)
                StudioEntry(Icons.Rounded.DeleteSweep, texts.studioCleaner(), texts.studioCleanerDesc(), AppScreen.StudioCleaner, onNavigate)
                StudioEntry(Icons.Rounded.FolderZip, texts.archiveTitle(), texts.archiveDesc(), AppScreen.StudioArchive, onNavigate)
                StudioEntry(Icons.AutoMirrored.Rounded.CallSplit, texts.pdfSplitTitle(), texts.pdfSplitDesc(), AppScreen.StudioPdfSplit, onNavigate)
                StudioEntry(Icons.Rounded.PictureAsPdf, texts.pdfCreateTitle(), texts.pdfCreateDesc(), AppScreen.StudioPdfCreate, onNavigate)
                StudioEntry(Icons.Rounded.MenuBook, texts.epubTitle(), texts.epubDesc(), AppScreen.StudioEpub, onNavigate)
                StudioEntry(Icons.Rounded.FolderShared, texts.folderWatchTitle(), texts.folderWatchDesc(), AppScreen.StudioFolderWatch, onNavigate)
                StudioEntry(Icons.Rounded.Compare, texts.privacyScrubTitle(), texts.privacyScrubDesc(), AppScreen.MetadataSecurity, onNavigate)
                StudioEntry(Icons.Rounded.BatterySaver, texts.guardTitle(), texts.guardDesc(), AppScreen.StudioGuard, onNavigate)
                StudioEntry(Icons.Rounded.Accessibility, texts.studioAccessibility(), texts.studioAccessibilityDesc(), AppScreen.StudioAccessibility, onNavigate)
                StudioEntry(Icons.Rounded.BugReport, texts.crashReportingTitle(), texts.crashReportingDesc(), AppScreen.StudioCrashReports, onNavigate, showDivider = false)
            }
        }
    }
}

@Composable
private fun StudioEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    screen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    showDivider: Boolean = true
) {
    SettingsRow(
        badgeIcon = icon,
        title = title,
        subtitle = subtitle,
        showChevron = true,
        showDivider = showDivider,
        onClick = { onNavigate(screen) }
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
