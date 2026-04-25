package us.beary.netlens.feature.widgetsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import us.beary.netlens.widget.model.WidgetColor
import us.beary.netlens.widget.model.WidgetPage
import us.beary.netlens.widget.model.WidgetPreferences
import us.beary.netlens.widget.model.WidgetSize
import us.beary.netlens.widget.model.WidgetTextSize

private val AUTO_ADVANCE_OPTIONS = listOf(
    0 to R.string.widget_settings_auto_off,
    5 to R.string.widget_settings_auto_5s,
    10 to R.string.widget_settings_auto_10s,
    30 to R.string.widget_settings_auto_30s,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    viewModel: WidgetSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.widget_settings_cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            WidgetPreview(prefs)

            AppearanceSection(
                prefs = prefs,
                onBackgroundColorChanged = viewModel::setBackgroundColor,
                onBackgroundAlphaChanged = viewModel::setBackgroundAlpha,
                onAccentColorChanged = viewModel::setAccentColor,
                onTextSizeChanged = viewModel::setTextSize,
                onCornerRadiusChanged = viewModel::setCornerRadius,
            )

            LayoutSection(
                selectedSize = prefs.widgetSize,
                onSizeChanged = viewModel::setWidgetSize,
            )

            CarouselSection(
                pages = prefs.pages,
                autoAdvanceSeconds = prefs.autoAdvanceSeconds,
                onTogglePage = viewModel::togglePage,
                onAutoAdvanceChanged = viewModel::setAutoAdvance,
            )

            Button(
                onClick = { viewModel.applyToWidget() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.widget_settings_button_apply))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WidgetPreview(prefs: WidgetPreferences) {
    val bgColor = Color(prefs.backgroundColor.argb).copy(alpha = prefs.backgroundAlpha)
    val textColor = if (prefs.backgroundColor == WidgetColor.WHITE) Color.Black else Color.White
    val accentColor = Color(prefs.accentColor.argb)
    val textSizeSp = prefs.textSize.sp.sp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(prefs.cornerRadius.dp))
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(prefs.cornerRadius.dp))
            .padding(12.dp),
    ) {
        if (prefs.pages.isEmpty()) {
            Text(
                stringResource(R.string.widget_settings_preview_no_connection),
                color = textColor.copy(alpha = 0.6f),
                fontSize = textSizeSp,
            )
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🇺🇸", fontSize = (textSizeSp.value + 4).sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "●${stringResource(R.string.widget_settings_preview_vpn)}",
                        color = accentColor,
                        fontSize = (textSizeSp.value - 2).sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.widget_settings_preview_ip),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = textSizeSp,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    stringResource(R.string.widget_settings_preview_ssid),
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = (textSizeSp.value - 2).sp,
                )
                if (prefs.pages.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        prefs.pages.forEachIndexed { i, _ ->
                            Text(
                                if (i == 0) "●" else "○",
                                color = if (i == 0) accentColor else textColor.copy(alpha = 0.3f),
                                fontSize = 8.sp,
                            )
                            if (i < prefs.pages.lastIndex) Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    prefs: WidgetPreferences,
    onBackgroundColorChanged: (WidgetColor) -> Unit,
    onBackgroundAlphaChanged: (Float) -> Unit,
    onAccentColorChanged: (WidgetColor) -> Unit,
    onTextSizeChanged: (WidgetTextSize) -> Unit,
    onCornerRadiusChanged: (Int) -> Unit,
) {
    SettingsCard(titleRes = R.string.widget_settings_section_appearance) {
        ColorPickerRow(
            labelRes = R.string.widget_settings_label_background,
            selected = prefs.backgroundColor,
            options = listOf(WidgetColor.BLACK, WidgetColor.WHITE, WidgetColor.DARK_GRAY, WidgetColor.NAVY),
            onSelect = onBackgroundColorChanged,
        )

        SliderRow(
            labelRes = R.string.widget_settings_label_opacity,
            value = prefs.backgroundAlpha,
            min = 0f,
            max = 1f,
            onChange = onBackgroundAlphaChanged,
        )

        ColorPickerRow(
            labelRes = R.string.widget_settings_label_accent,
            selected = prefs.accentColor,
            options = WidgetColor.entries.toList(),
            onSelect = onAccentColorChanged,
        )

        TextSizeSelector(selected = prefs.textSize, onSelect = onTextSizeChanged)

        SliderRow(
            labelRes = R.string.widget_settings_label_corner_radius,
            value = prefs.cornerRadius.toFloat(),
            min = 0f,
            max = 24f,
            onChange = { onCornerRadiusChanged(it.toInt()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutSection(
    selectedSize: WidgetSize,
    onSizeChanged: (WidgetSize) -> Unit,
) {
    SettingsCard(titleRes = R.string.widget_settings_section_layout) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WidgetSize.entries.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = selectedSize == size,
                    onClick = { onSizeChanged(size) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = WidgetSize.entries.size,
                    ),
                ) {
                    Text(
                        when (size) {
                            WidgetSize.SMALL -> stringResource(R.string.widget_settings_size_small)
                            WidgetSize.MEDIUM -> stringResource(R.string.widget_settings_size_medium)
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.widget_settings_size_resize_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CarouselSection(
    pages: List<WidgetPage>,
    autoAdvanceSeconds: Int,
    onTogglePage: (WidgetPage) -> Unit,
    onAutoAdvanceChanged: (Int) -> Unit,
) {
    SettingsCard(titleRes = R.string.widget_settings_section_carousel) {
        WidgetPage.entries.forEach { page ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTogglePage(page) }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = pages.contains(page),
                    onCheckedChange = { onTogglePage(page) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (page) {
                        WidgetPage.CONNECTION -> stringResource(R.string.widget_settings_page_connection)
                        WidgetPage.NETWORK -> stringResource(R.string.widget_settings_page_network)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        AutoAdvanceSelector(
            selected = autoAdvanceSeconds,
            onSelect = onAutoAdvanceChanged,
        )
    }
}

@Composable
private fun SettingsCard(
    @StringRes titleRes: Int,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerRow(
    @StringRes labelRes: Int,
    selected: WidgetColor,
    options: List<WidgetColor>,
    onSelect: (WidgetColor) -> Unit,
) {
    Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { color ->
            FilterChip(
                selected = selected == color,
                onClick = { onSelect(color) },
                label = {
                    Text(
                        when (color) {
                            WidgetColor.BLACK -> stringResource(R.string.widget_settings_color_black)
                            WidgetColor.WHITE -> stringResource(R.string.widget_settings_color_white)
                            WidgetColor.DARK_GRAY -> stringResource(R.string.widget_settings_color_dark_gray)
                            WidgetColor.NAVY -> stringResource(R.string.widget_settings_color_navy)
                            WidgetColor.GREEN -> stringResource(R.string.widget_settings_color_green)
                        },
                    )
                },
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SliderRow(
    @StringRes labelRes: Int,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(min = 80.dp, max = 120.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (max <= 1f) {
                stringResource(R.string.widget_settings_format_percent, (value * 100).toInt())
            } else {
                stringResource(R.string.widget_settings_format_dp, value.toInt())
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSizeSelector(
    selected: WidgetTextSize,
    onSelect: (WidgetTextSize) -> Unit,
) {
    Text(
        stringResource(R.string.widget_settings_label_text_size),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        WidgetTextSize.entries.forEachIndexed { index, size ->
            SegmentedButton(
                selected = selected == size,
                onClick = { onSelect(size) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = WidgetTextSize.entries.size,
                ),
            ) {
                Text(
                    when (size) {
                        WidgetTextSize.SMALL -> stringResource(R.string.widget_settings_text_small)
                        WidgetTextSize.MEDIUM -> stringResource(R.string.widget_settings_text_medium)
                        WidgetTextSize.LARGE -> stringResource(R.string.widget_settings_text_large)
                    },
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoAdvanceSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(
        stringResource(R.string.widget_settings_label_auto_advance),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AUTO_ADVANCE_OPTIONS.forEachIndexed { index, (seconds, labelRes) ->
            SegmentedButton(
                selected = selected == seconds,
                onClick = { onSelect(seconds) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = AUTO_ADVANCE_OPTIONS.size,
                ),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}
