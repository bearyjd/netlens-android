package com.ventoux.netlens.feature.widgetsettings

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
import com.ventoux.netlens.widget.model.WidgetColor
import com.ventoux.netlens.widget.model.WidgetPage
import com.ventoux.netlens.widget.model.WidgetPreferences
import com.ventoux.netlens.widget.model.WidgetSize
import com.ventoux.netlens.widget.model.WidgetTextSize

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

            val showCarousel = prefs.widgetSize == WidgetSize.SMALL || prefs.widgetSize == WidgetSize.MEDIUM
            if (showCarousel) {
                CarouselSection(
                    pages = prefs.pages,
                    autoAdvanceSeconds = prefs.autoAdvanceSeconds,
                    onTogglePage = viewModel::togglePage,
                    onAutoAdvanceChanged = viewModel::setAutoAdvance,
                )
            }

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
    val textSizeSp = if (prefs.widgetSize == WidgetSize.BANNER) {
        WidgetTextSize.SMALL.sp.sp
    } else {
        prefs.textSize.sp.sp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(prefs.cornerRadius.dp))
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(prefs.cornerRadius.dp))
            .padding(12.dp),
    ) {
        when (prefs.widgetSize) {
            WidgetSize.SMALL, WidgetSize.MEDIUM -> DefaultPreviewContent(prefs, textColor, accentColor, textSizeSp)
            WidgetSize.WIDE -> WidePreviewContent(textColor, accentColor, textSizeSp)
            WidgetSize.BANNER -> BannerPreviewContent(textColor, accentColor, textSizeSp)
        }
    }
}

@Composable
private fun DefaultPreviewContent(
    prefs: WidgetPreferences,
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
) {
    if (prefs.pages.isEmpty()) {
        Text(
            stringResource(R.string.widget_settings_preview_no_connection),
            color = textColor.copy(alpha = 0.6f),
            fontSize = textSizeSp,
        )
    } else {
        Column {
            ConnectionPreviewRow(textColor, accentColor, textSizeSp)
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

@Composable
private fun WidePreviewContent(
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ConnectionPreviewRow(textColor, accentColor, textSizeSp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                stringResource(R.string.widget_settings_preview_ssid),
                color = textColor.copy(alpha = 0.7f),
                fontSize = (textSizeSp.value - 2).sp,
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(textColor.copy(alpha = 0.2f)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                stringResource(R.string.widget_settings_preview_gateway_full),
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = textSizeSp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                stringResource(R.string.widget_settings_preview_dns),
                color = textColor.copy(alpha = 0.7f),
                fontSize = (textSizeSp.value - 2).sp,
            )
        }
    }
}

@Composable
private fun BannerPreviewContent(
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🇺🇸", fontSize = (textSizeSp.value + 4).sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "●${stringResource(R.string.widget_settings_preview_vpn)}",
            color = accentColor,
            fontSize = (textSizeSp.value - 2).sp,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            stringResource(R.string.widget_settings_preview_ip),
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = textSizeSp,
        )
        Text(" · ", color = textColor.copy(alpha = 0.4f), fontSize = textSizeSp)
        Text(
            stringResource(R.string.widget_settings_preview_ssid_name),
            color = textColor.copy(alpha = 0.7f),
            fontSize = textSizeSp,
        )
        Text(" · ", color = textColor.copy(alpha = 0.4f), fontSize = textSizeSp)
        Text(
            stringResource(R.string.widget_settings_preview_gateway),
            color = textColor.copy(alpha = 0.7f),
            fontSize = textSizeSp,
        )
    }
}

@Composable
private fun ConnectionPreviewRow(
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
) {
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

        if (prefs.widgetSize != WidgetSize.BANNER) {
            TextSizeSelector(selected = prefs.textSize, onSelect = onTextSizeChanged)
        }

        SliderRow(
            labelRes = R.string.widget_settings_label_corner_radius,
            value = prefs.cornerRadius.toFloat(),
            min = 0f,
            max = 24f,
            onChange = { onCornerRadiusChanged(it.toInt()) },
        )
    }
}

@Composable
private fun LayoutSection(
    selectedSize: WidgetSize,
    onSizeChanged: (WidgetSize) -> Unit,
) {
    SettingsCard(titleRes = R.string.widget_settings_section_layout) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SizeCard(
                    labelRes = R.string.widget_settings_size_small,
                    subtitleRes = R.string.widget_settings_size_small_subtitle,
                    aspectRatio = 2f,
                    selected = selectedSize == WidgetSize.SMALL,
                    onSelect = { onSizeChanged(WidgetSize.SMALL) },
                    modifier = Modifier.weight(1f),
                )
                SizeCard(
                    labelRes = R.string.widget_settings_size_medium,
                    subtitleRes = R.string.widget_settings_size_medium_subtitle,
                    aspectRatio = 1f,
                    selected = selectedSize == WidgetSize.MEDIUM,
                    onSelect = { onSizeChanged(WidgetSize.MEDIUM) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SizeCard(
                    labelRes = R.string.widget_settings_size_wide,
                    subtitleRes = R.string.widget_settings_size_wide_subtitle,
                    aspectRatio = 2f,
                    selected = selectedSize == WidgetSize.WIDE,
                    onSelect = { onSizeChanged(WidgetSize.WIDE) },
                    modifier = Modifier.weight(1f),
                )
                SizeCard(
                    labelRes = R.string.widget_settings_size_banner,
                    subtitleRes = R.string.widget_settings_size_banner_subtitle,
                    aspectRatio = 5f,
                    selected = selectedSize == WidgetSize.BANNER,
                    onSelect = { onSizeChanged(WidgetSize.BANNER) },
                    modifier = Modifier.weight(1f),
                )
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
private fun SizeCard(
    @StringRes labelRes: Int,
    @StringRes subtitleRes: Int,
    aspectRatio: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onSelect() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(maxOf(40f / aspectRatio, 16f).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(subtitleRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
