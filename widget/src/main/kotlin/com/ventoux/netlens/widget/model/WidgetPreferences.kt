package com.ventoux.netlens.widget.model

data class WidgetPreferences(
    val backgroundAlpha: Float = 0.6f,
    val backgroundColor: WidgetColor = WidgetColor.BLACK,
    val accentColor: WidgetColor = WidgetColor.GREEN,
    val textSize: WidgetTextSize = WidgetTextSize.MEDIUM,
    val cornerRadius: Int = 16,
    val widgetSize: WidgetSize = WidgetSize.SMALL,
    val pages: List<WidgetPage> = listOf(WidgetPage.CONNECTION, WidgetPage.NETWORK),
    val autoAdvanceSeconds: Int = 0,
)

enum class WidgetColor(val argb: Long) {
    BLACK(0xFF000000),
    WHITE(0xFFFFFFFF),
    DARK_GRAY(0xFF303030),
    NAVY(0xFF1A237E),
    GREEN(0xFF4CAF50),
}

enum class WidgetTextSize(val sp: Int) {
    SMALL(11),
    MEDIUM(13),
    LARGE(15),
}

enum class WidgetSize {
    SMALL,
    MEDIUM,
}

enum class WidgetPage {
    CONNECTION,
    NETWORK,
}
