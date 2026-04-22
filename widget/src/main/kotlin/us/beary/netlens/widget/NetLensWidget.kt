package us.beary.netlens.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class NetLensWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
private fun WidgetContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(GlanceTheme.colors.widgetBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NetLens",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        )
        Text(
            text = "Loading...",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 18.sp,
            ),
        )
    }
}
