package io.github.azeezhamzat.ytdlmanager.core

import java.util.Locale

object Formatters {
    /** Decimal units like Android's own file sizes: "45.2 MB". */
    fun bytes(count: Long): String {
        if (count < 1000) return "$count B"
        val units = listOf("kB", "MB", "GB", "TB")
        var value = count.toDouble()
        var unit = -1
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, pattern, value, units[unit])
    }

    /** "4:05" or "1:02:03". */
    fun duration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun speed(bytesPerSecond: Double): String = bytes(bytesPerSecond.toLong()) + "/s"
}
