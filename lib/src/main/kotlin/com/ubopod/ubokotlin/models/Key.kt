package com.ubopod.ubokotlin.models

/**
 * Physical button keys on the Ubo device.
 *
 * Mirrors `Sources/UboSwift/Models/Key.swift`. The proto enum values are
 * stable; do not renumber.
 */
public enum class Key(public val rawValue: String, public val protoValue: Int) {
    BACK("Back", 1),
    HOME("Home", 2),
    UP("Up", 3),
    DOWN("Down", 4),
    L1("L1", 5),
    L2("L2", 6),
    L3("L3", 7);

    public companion object {
        public fun fromProto(protoValue: Int): Key? =
            entries.firstOrNull { it.protoValue == protoValue }

        public fun fromRawValue(rawValue: String): Key? =
            entries.firstOrNull { it.rawValue == rawValue }
    }
}
