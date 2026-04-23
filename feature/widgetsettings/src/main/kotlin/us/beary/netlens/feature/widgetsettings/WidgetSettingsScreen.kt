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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import us.beary.netlens.widget.model.WidgetColor
import us.beary.netlens.widget.model.WidgetPage
import us.beary.netlens.widget.model.WidgetPreferences
import us.beary.netlens.widget.model.WidgetSize
import us.beary.netlens.widget.model.WidgetTextSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    viewModel: WidgetSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        ) {
            WidgetPreview(prefs)
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Appearance")
            ColorPickerRow("Background", prefs.backgroundColor, listOf(WidgetColor.BLACK, WidgetColor.WHITE, WidgetColor.DARK_GRAY, WidgetColor.NAVY)) {
                viewModel.setBackgroundColor(it)
            }
            Spacer(modifier = Modifier.height(12.dp))
            SliderRow("Opacity", prefs.backgroundAlpha, 0f, 1f) { viewModel.setBackgroundAlpha(it) }
            Spacer(modifier = Modifier.height(12.dp))
            ColorPickerRow("Accent", prefs.accentColor, WidgetColor.entries.toList()) {
                viewModel.setAccentColor(it)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextSizeSelector(prefs.textSize) { viewModel.setTextSize(it) }
            Spacer(modifier = Modifier.height(12.dp))
            SliderRow("Corner radius", prefs.cornerRadius.toFloat(), 0f, 24f) {
                viewModel.setCornerRadius(it.toInt())
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Layout")
            WidgetSizeToggle(prefs.widgetSize) { viewModel.setWidgetSize(it) }
            Text(
                text = "Resize the widget on your home screen after changing this",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Carousel")
            WidgetPage.entries.forEach { page ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.togglePage(page) }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = prefs.pages.contains(page),
                        onCheckedChange = { viewModel.togglePage(page) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(page.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AutoAdvanceSelector(prefs.autoAdvanceSeconds) { viewModel.setAutoAdvance(it) }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.applyToWidget() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply to Widget")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WidgetPreview(prefs: WidgetPreferences) {
    val bgColor = Color(prefs.backgroundColor.argb).copy(alpha = prefs.backgroundAlpha)
    val textColor = if (prefs.backgroundColor.argb == 0xFFFFFFFF.toLong()) Color.Black else Color.White
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
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🇺🇸", fontSize = (textSizeSp.value + 4).sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("●vpn", color = accentColor, fontSize = (textSizeSp.value - 2).sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("1.2.3.4", color = textColor, fontWeight = FontWeight.Bold, fontSize = textSizeSp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Home-WiFi · 192.168.1.45",
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerRow(
    label: String,
    selected: WidgetColor,
    options: List<WidgetColor>,
    onSelect: (WidgetColor) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { color ->
            FilterChip(
                selected = selected == color,
                onClick = { onSelect(color) },
                label = { Text(color.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")) },
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (max <= 1f) "${(value * 100).toInt()}%" else "${value.toInt()}dp",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSizeSelector(selected: WidgetTextSize, onSelect: (WidgetTextSize) -> Unit) {
    Text("Text size", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetTextSize.entries.forEach { size ->
            FilterChip(
                selected = selected == size,
                onClick = { onSelect(size) },
                label = { Text(size.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSizeToggle(selected: WidgetSize, onSelect: (WidgetSize) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetSize.entries.forEach { size ->
            val label = when (size) {
                WidgetSize.SMALL -> "2×1 Compact"
                WidgetSize.MEDIUM -> "2×2 Full"
            }
            FilterChip(
                selected = selected == size,
                onClick = { onSelect(size) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun AutoAdvanceSelector(selected: Int, onSelect: (Int) -> Unit) {
    Text("Auto-advance", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    val options = listOf(0 to "Off", 5 to "5s", 10 to "10s", 30 to "30s")
    Column {
        options.forEach { (seconds, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(seconds) }
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(selected = selected == seconds, onClick = { onSelect(seconds) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}
