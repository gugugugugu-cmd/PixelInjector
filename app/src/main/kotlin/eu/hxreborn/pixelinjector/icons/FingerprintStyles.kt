package eu.hxreborn.pixelinjector.icons

object FingerprintStyles {
    const val STOCK = ""
    const val NONE = "none"
    const val CUSTOM = "custom"
    const val IMAGE_FILE = "fingerprint_icon.png"

    val presets: List<String> =
        listOf(
            "spiral",
            "rings",
            "swirl",
            "ridges",
            "pins",
            "maze",
            "lines",
            "bars",
            "arcs",
            "graphite",
            "bold",
            "magenta",
            "neon",
            "gradient",
            "halo",
            "loop",
            "frame",
            "disc",
            "rainbow",
            "mint",
            "ocean",
            "target",
            "ring",
            "glow",
            "triangle",
            "diamond",
            "claws",
            "sunset",
            "dragon",
            "yield",
            "orbit",
            "eclipse",
            "splash",
            "aurora",
        )

    fun isCustom(style: String): Boolean = style.startsWith(CUSTOM)

    fun customStyle(stamp: Long): String = "$CUSTOM:$stamp"

    fun assetPath(style: String): String = "fingerprint/$style.webp"
}
