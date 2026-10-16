package net.wingress.mobivious.data

import org.json.JSONObject

/** Device/profile styling, deliberately independent of synced language priorities. */
data class CaptionAppearance(
    val size: Int = 100,
    val textColor: String = "system", val textOpacity: Int = 100,
    val backgroundColor: String = "#000000", val backgroundOpacity: Int = 0,
    val windowColor: String = "#000000", val windowOpacity: Int = 0,
    val font: String = "system", val edge: String = "system",
) {
    fun json() = JSONObject().put("size", size).put("textColor", textColor).put("textOpacity", textOpacity)
        .put("backgroundColor", backgroundColor).put("backgroundOpacity", backgroundOpacity)
        .put("windowColor", windowColor).put("windowOpacity", windowOpacity).put("font", font).put("edge", edge)
    fun normalized() = parse(json())

    companion object {
        const val SAVE_KEY = "profile:captionAppearance"
        const val PAGE = "Caption appearance"
        val sizes = listOf(50, 75, 100, 125, 150, 175, 200, 300, 400)
        val opacities = listOf(0, 50, 100)
        val colors = listOf("#FFFFFF" to "White", "#000000" to "Black", "#FF0000" to "Red", "#00FF00" to "Green",
            "#0000FF" to "Blue", "#FFFF00" to "Yellow", "#FF00FF" to "Magenta", "#00FFFF" to "Cyan")
        val fonts = listOf("system" to "Device default", "proportionalSansSerif" to "Proportional sans serif",
            "monospaceSansSerif" to "Monospaced sans serif", "proportionalSerif" to "Proportional serif",
            "monospaceSerif" to "Monospaced serif", "casual" to "Casual", "script" to "Script", "small-caps" to "Small capitals")
        val edges = listOf("system" to "Device default", "none" to "None", "uniform" to "Outline",
            "dropshadow" to "Shadow", "raised" to "Raised", "depressed" to "Depressed")
        fun parse(raw: String?): CaptionAppearance = runCatching { parse(JSONObject(raw ?: "{}")) }.getOrDefault(CaptionAppearance())
        fun parse(j: JSONObject): CaptionAppearance {
            val d = CaptionAppearance()
            fun number(key: String, choices: List<Int>, fallback: Int): Int =
                (j.opt(key) as? Number)?.takeIf { it is Int || it is Long }?.toLong()?.let { n -> choices.firstOrNull { it.toLong() == n } } ?: fallback
            fun choice(key: String, choices: List<Pair<String, String>>, fallback: String) =
                (j.opt(key) as? String)?.takeIf { v -> choices.any { it.first == v } } ?: fallback
            return CaptionAppearance(number("size", sizes, d.size),
                choice("textColor", listOf("system" to "Device default") + colors, d.textColor), number("textOpacity", opacities, d.textOpacity),
                choice("backgroundColor", colors, d.backgroundColor), number("backgroundOpacity", opacities, d.backgroundOpacity),
                choice("windowColor", colors, d.windowColor), number("windowOpacity", opacities, d.windowOpacity),
                choice("font", fonts, d.font), choice("edge", edges, d.edge))
        }
    }
}
