package com.ubopod.ubokotlin.models

/**
 * Data for a single menu item carried inside [ViewData] payloads.
 *
 * Mirrors the `MenuItemData` struct in `Sources/UboSwift/Models/ViewData.swift`.
 * Named `MenuItemData` to avoid clashing with `android.view.MenuItem` in the
 * consuming Android module.
 */
public data class MenuItemData(
    val key: String = "",
    val label: String = "",
    val icon: String = "",
    val color: String = "#ffffff",
    val backgroundColor: String? = null,
    val isShort: Boolean = false,
    val actionId: String? = null,
) {
    public val uboColor: UboColor? get() = UboColor.fromHex(color)
    public val uboBackgroundColor: UboColor? get() = backgroundColor?.let { UboColor.fromHex(it) }
}
