package us.beary.netlens.widget.util

fun String.toFlagEmoji(): String {
    if (length != 2) return ""
    val upper = uppercase()
    if (upper[0] !in 'A'..'Z' || upper[1] !in 'A'..'Z') return ""
    val first = upper[0].code - 0x41 + 0x1F1E6
    val second = upper[1].code - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
