package org.zenconverter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.zenconverter.app.R
import org.zenconverter.app.conversion.ContactSheetAlignment
import org.zenconverter.app.conversion.ContactSheetBackground
import org.zenconverter.app.conversion.ContactSheetCellHeightMode
import org.zenconverter.app.conversion.ContactSheetFitMode
import org.zenconverter.app.conversion.ContactSheetGeometry
import org.zenconverter.app.conversion.ContactSheetGrid
import org.zenconverter.app.conversion.ContactSheetWidthMode
import org.zenconverter.app.conversion.FileBasicInfo
import org.zenconverter.app.conversion.VideoContactSheetOptions

@Composable
fun ContactSheetDesignerCard(
    options: VideoContactSheetOptions,
    inputInfo: FileBasicInfo?,
    outputExtension: String,
    onOptionsChange: (VideoContactSheetOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDesigner by remember { mutableStateOf(false) }
    val geometry = ContactSheetGeometry.calculate(
        options,
        inputInfo?.width ?: 16,
        inputInfo?.height ?: 9
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ui_contact_sheet_designer_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.ui_contact_sheet_designer_summary,
                        options.rows,
                        options.columns,
                        geometry.width,
                        geometry.height,
                        geometry.frameCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { showDesigner = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.ui_contact_sheet_design))
            }
        }
        Text(
            text = stringResource(R.string.ui_contact_sheet_designer_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showDesigner) {
        ContactSheetDesignerSheet(
            initialOptions = options,
            inputInfo = inputInfo,
            outputExtension = outputExtension,
            onDismiss = { showDesigner = false },
            onSave = {
                onOptionsChange(it)
                showDesigner = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSheetDesignerSheet(
    initialOptions: VideoContactSheetOptions,
    inputInfo: FileBasicInfo?,
    outputExtension: String,
    onDismiss: () -> Unit,
    onSave: (VideoContactSheetOptions) -> Unit
) {
    var draft by remember(initialOptions) { mutableStateOf(initialOptions) }
    val geometry = ContactSheetGeometry.calculate(
        draft,
        inputInfo?.width ?: 16,
        inputInfo?.height ?: 9
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_contact_sheet_designer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.ui_contact_sheet_designer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(
                    R.string.ui_contact_sheet_designer_summary,
                    draft.rows,
                    draft.columns,
                    geometry.width,
                    geometry.height,
                    geometry.frameCount
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DesignerSectionTitle(stringResource(R.string.ui_contact_sheet_layout_section))
                    PresetRow(draft) { draft = draft.withGrid(it) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_rows),
                            value = draft.rows,
                            range = ContactSheetGeometry.MIN_ROWS..ContactSheetGeometry.MAX_ROWS,
                            onValueChange = { draft = draft.copy(rows = it) },
                            modifier = Modifier.weight(1f)
                        )
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_columns),
                            value = draft.columns,
                            range = ContactSheetGeometry.MIN_COLUMNS..ContactSheetGeometry.MAX_COLUMNS,
                            onValueChange = { draft = draft.copy(columns = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ChoiceRow(
                        label = stringResource(R.string.ui_contact_sheet_width_basis),
                        values = listOf(
                            ContactSheetWidthMode.Canvas to stringResource(R.string.ui_contact_sheet_canvas_width),
                            ContactSheetWidthMode.Cell to stringResource(R.string.ui_contact_sheet_cell_width)
                        ),
                        selected = draft.widthMode,
                        onSelected = { draft = draft.copy(widthMode = it) }
                    )
                    if (draft.widthMode == ContactSheetWidthMode.Canvas) {
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_canvas_width),
                            value = draft.canvasWidthPx,
                            range = ContactSheetGeometry.MIN_CANVAS_WIDTH..ContactSheetGeometry.MAX_CANVAS_WIDTH,
                            onValueChange = { draft = draft.copy(canvasWidthPx = it) }
                        )
                    } else {
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_cell_width),
                            value = draft.cellWidthPx,
                            range = ContactSheetGeometry.MIN_CELL_WIDTH..ContactSheetGeometry.MAX_CELL_WIDTH,
                            onValueChange = { draft = draft.copy(cellWidthPx = it) }
                        )
                    }
                    ChoiceRow(
                        label = stringResource(R.string.ui_contact_sheet_cell_height),
                        values = listOf(
                            ContactSheetCellHeightMode.AspectRatio to stringResource(R.string.ui_contact_sheet_keep_aspect),
                            ContactSheetCellHeightMode.Fixed to stringResource(R.string.ui_contact_sheet_fixed_height)
                        ),
                        selected = draft.cellHeightMode,
                        onSelected = { draft = draft.copy(cellHeightMode = it) }
                    )
                    if (draft.cellHeightMode == ContactSheetCellHeightMode.Fixed) {
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_cell_height_px),
                            value = draft.cellHeightPx,
                            range = ContactSheetGeometry.MIN_CELL_HEIGHT..ContactSheetGeometry.MAX_CELL_HEIGHT,
                            onValueChange = { draft = draft.copy(cellHeightPx = it) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_gap),
                            value = draft.gapPx,
                            range = ContactSheetGeometry.MIN_GAP..ContactSheetGeometry.MAX_GAP,
                            onValueChange = { draft = draft.copy(gapPx = it) },
                            modifier = Modifier.weight(1f)
                        )
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_margin),
                            value = draft.outerMarginPx,
                            range = ContactSheetGeometry.MIN_MARGIN..ContactSheetGeometry.MAX_MARGIN,
                            onValueChange = { draft = draft.copy(outerMarginPx = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ChoiceRow(
                        label = stringResource(R.string.ui_contact_sheet_alignment),
                        values = listOf(
                            ContactSheetAlignment.Start to stringResource(R.string.ui_contact_sheet_align_start),
                            ContactSheetAlignment.Center to stringResource(R.string.ui_contact_sheet_align_center),
                            ContactSheetAlignment.End to stringResource(R.string.ui_contact_sheet_align_end)
                        ),
                        selected = draft.alignment,
                        onSelected = { draft = draft.copy(alignment = it) }
                    )
                }
                item {
                    DesignerSectionTitle(stringResource(R.string.ui_contact_sheet_visual_section))
                    ChoiceRow(
                        label = stringResource(R.string.ui_contact_sheet_fit_mode),
                        values = listOf(
                            ContactSheetFitMode.Crop to stringResource(R.string.ui_contact_sheet_fit_crop),
                            ContactSheetFitMode.Contain to stringResource(R.string.ui_contact_sheet_fit_contain),
                            ContactSheetFitMode.Stretch to stringResource(R.string.ui_contact_sheet_fit_stretch)
                        ),
                        selected = draft.fitMode,
                        onSelected = { draft = draft.copy(fitMode = it) }
                    )
                    ChoiceRow(
                        label = stringResource(R.string.ui_contact_sheet_background),
                        values = listOf(
                            ContactSheetBackground.Dark to stringResource(R.string.ui_contact_sheet_background_dark),
                            ContactSheetBackground.Light to stringResource(R.string.ui_contact_sheet_background_light),
                            ContactSheetBackground.Transparent to stringResource(R.string.ui_contact_sheet_background_transparent)
                        ),
                        selected = draft.background,
                        onSelected = { draft = draft.copy(background = it) }
                    )
                }
                item {
                    DesignerSectionTitle(stringResource(R.string.ui_contact_sheet_info_section))
                    DesignerSwitch(
                        label = stringResource(R.string.ui_contact_sheet_include_header),
                        checked = draft.includeHeader,
                        onCheckedChange = { draft = draft.copy(includeHeader = it) }
                    )
                    if (draft.includeHeader) {
                        DesignerNumberField(
                            label = stringResource(R.string.ui_contact_sheet_header_height),
                            value = draft.headerHeightPx,
                            range = ContactSheetGeometry.MIN_HEADER_HEIGHT..ContactSheetGeometry.MAX_HEADER_HEIGHT,
                            onValueChange = { draft = draft.copy(headerHeightPx = it) }
                        )
                    }
                    DesignerSwitch(
                        label = stringResource(R.string.ui_contact_sheet_include_timestamp),
                        checked = draft.includeTimestamp,
                        onCheckedChange = { draft = draft.copy(includeTimestamp = it) }
                    )
                    DesignerSwitch(
                        label = stringResource(R.string.ui_contact_sheet_include_watermark),
                        checked = draft.includeWatermark,
                        onCheckedChange = { draft = draft.copy(includeWatermark = it) }
                    )
                }
                item {
                    DesignerSectionTitle(stringResource(R.string.ui_contact_sheet_output_section))
                    Text(
                        text = if (outputExtension.endsWith("jpg", ignoreCase = true)) {
                            stringResource(R.string.ui_contact_sheet_jpg_note)
                        } else {
                            stringResource(R.string.ui_contact_sheet_png_note)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (geometry.violations.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_contact_sheet_size_too_large),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_cancel))
                }
                Button(
                    onClick = { onSave(draft) },
                    enabled = geometry.isValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ui_contact_sheet_apply))
                }
            }
        }
    }
}

@Composable
private fun PresetRow(options: VideoContactSheetOptions, onSelected: (ContactSheetGrid) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContactSheetGrid.entries.forEach { grid ->
            val selected = options.rows == grid.rows && options.columns == grid.cols
            OutlinedButton(
                onClick = { onSelected(grid) },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(grid.labelKey, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DesignerSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    values: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEach { (value, text) ->
                val active = value == selected
                Text(
                    text = text,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(100.dp)
                        )
                        .clickable { onSelected(value) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesignerNumberField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.let { onValueChange(it.coerceIn(range.first, range.last)) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        delay(150)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
    )
}

@Composable
private fun DesignerSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary)
        )
    }
}
