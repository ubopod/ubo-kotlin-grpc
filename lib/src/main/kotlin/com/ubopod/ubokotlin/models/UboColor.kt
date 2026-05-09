package com.ubopod.ubokotlin.models

/**
 * RGBA color representation. Mirrors `Sources/UboSwift/Models/UboColor.swift`.
 *
 * Components are stored as unsigned bytes (0..255) and exposed as [Int] for
 * Java/Kotlin friendliness. SwiftUI/UIColor bridging is replaced on the
 * Android side by Compose `Color` extensions in the consuming app module.
 */
public data class UboColor(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = 255,
) {
    init {
        require(red in 0..255) { "red must be in 0..255 (was $red)" }
        require(green in 0..255) { "green must be in 0..255 (was $green)" }
        require(blue in 0..255) { "blue must be in 0..255 (was $blue)" }
        require(alpha in 0..255) { "alpha must be in 0..255 (was $alpha)" }
    }

    public val hexString: String
        get() = if (alpha == 255) {
            "#%02x%02x%02x".format(red, green, blue)
        } else {
            "#%02x%02x%02x%02x".format(red, green, blue, alpha)
        }

    public companion object {
        public val Black: UboColor = UboColor(0, 0, 0)
        public val White: UboColor = UboColor(255, 255, 255)
        public val Red: UboColor = UboColor(255, 0, 0)
        public val Green: UboColor = UboColor(0, 255, 0)
        public val Blue: UboColor = UboColor(0, 0, 255)
        public val Clear: UboColor = UboColor(0, 0, 0, 0)

        /**
         * Parse a hex string in `#rrggbb`, `rrggbb`, `#rrggbbaa`, or
         * `rrggbbaa` form. Returns `null` for malformed input.
         */
        public fun fromHex(hex: String): UboColor? {
            val trimmed = hex.trim().removePrefix("#")
            if (trimmed.length != 6 && trimmed.length != 8) return null
            val value = trimmed.toLongOrNull(radix = 16) ?: return null
            return if (trimmed.length == 6) {
                UboColor(
                    red = ((value shr 16) and 0xFF).toInt(),
                    green = ((value shr 8) and 0xFF).toInt(),
                    blue = (value and 0xFF).toInt(),
                    alpha = 255,
                )
            } else {
                UboColor(
                    red = ((value shr 24) and 0xFF).toInt(),
                    green = ((value shr 16) and 0xFF).toInt(),
                    blue = ((value shr 8) and 0xFF).toInt(),
                    alpha = (value and 0xFF).toInt(),
                )
            }
        }
    }
}
