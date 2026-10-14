package net.wingress.mobivious.data

/** Device-wide palette IDs. Stored independently of account/profile JSON. */
enum class AccentPreset(val id: String, val label: String, val light: List<Long>, val dark: List<Long>) {
    BLUE("blue", "Blue", listOf(0xFF125BB5, 0xFFFFFFFF, 0xFFD6E4FF, 0xFF001B3D), listOf(0xFFA8C7FA, 0xFF062E60, 0xFF153F6B, 0xFFD6E4FF)),
    TEAL("teal", "Teal", listOf(0xFF006B60, 0xFFFFFFFF, 0xFF9FF2DF, 0xFF00201A), listOf(0xFF7AD7C6, 0xFF00382F, 0xFF005047, 0xFF9FF2DF)),
    GREEN("green", "Green", listOf(0xFF356A20, 0xFFFFFFFF, 0xFFBDECA2, 0xFF062100), listOf(0xFFA2D884, 0xFF123800, 0xFF215112, 0xFFBDECA2)),
    VIOLET("violet", "Violet", listOf(0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D), listOf(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF)),
    AMBER("amber", "Amber", listOf(0xFF7A5900, 0xFFFFFFFF, 0xFFFFDF92, 0xFF261900), listOf(0xFFF3C34D, 0xFF402D00, 0xFF5C4300, 0xFFFFDF92)),
    ROSE("rose", "Rose", listOf(0xFFA33358, 0xFFFFFFFF, 0xFFFFD9E3, 0xFF3F0017), listOf(0xFFFFB1C8, 0xFF630E2F, 0xFF812044, 0xFFFFD9E3));
    companion object { fun saved(id: String?) = entries.firstOrNull { it.id == id } ?: BLUE }
}
