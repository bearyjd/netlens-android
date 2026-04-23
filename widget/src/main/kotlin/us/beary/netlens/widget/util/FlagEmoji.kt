package us.beary.netlens.widget.util

fun String.toFlagEmoji(): String {
    if (length != 2) return ""
    val first = Character.codePointAt(uppercase(), 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
