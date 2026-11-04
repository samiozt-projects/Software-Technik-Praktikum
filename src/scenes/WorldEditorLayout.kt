package scenes

/** Layout constants for the world editor scene and its helper panels. */
internal object WorldEditorLayout {
    /** Main headline shown above the editor. */
    const val TITLE_TEXT = "Welt-Editor"
    /** Vertical position of the main headline. */
    const val TITLE_Y = 38.0
    /** Vertical position of the world/template subtitle. */
    const val HEADER_Y = 138.0

    /** Left edge of the palette panel. */
    const val PALETTE_X = 62.0
    /** Top edge of the palette panel. */
    const val PALETTE_Y = 195.0
    /** Width of the palette panel. */
    const val PALETTE_W = 345.0
    /** Height of the palette panel. */
    const val PALETTE_H = 515.0

    /** Left edge of the playable field preview. */
    const val FIELD_X = 455.0
    /** Top edge of the playable field preview. */
    const val FIELD_Y = 195.0
    /** Width of the playable field preview. */
    const val FIELD_W = 810.0
    /** Height of the playable field preview. */
    const val FIELD_H = 450.0

    /** Left edge of the properties panel. */
    const val PROPS_X = 1315.0
    /** Top edge of the properties panel. */
    const val PROPS_Y = 195.0
    /** Width of the properties panel. */
    const val PROPS_W = 420.0
    /** Height of the properties panel. */
    const val PROPS_H = 515.0

    /** Left edge of the source-configurations button below the palette. */
    const val CONFIG_BUTTON_X = PALETTE_X
    /** Top edge of the source-configurations button below the palette. */
    const val CONFIG_BUTTON_Y = PALETTE_Y + PALETTE_H + 20.0
    /** Width of the source-configurations button, matching the palette panel. */
    const val CONFIG_BUTTON_W = PALETTE_W
    /** Height of the source-configurations button. */
    const val CONFIG_BUTTON_H = 62.0

    /** Square side length for source and sink markers. */
    const val NODE_SIZE = 30.0
    /** Width of bottom action buttons. */
    const val BUTTON_W = 190.0
    /** Height of bottom action buttons. */
    const val BUTTON_H = 62.0
    /** Vertical position of bottom action buttons. */
    const val BOTTOM_Y = 855.0
    /** Left edge of the cancel button. */
    const val CANCEL_X = 1320.0
    /** Left edge of the save button. */
    const val SAVE_X = 1545.0

    /** Left edge of the editor error box. */
    const val ERROR_X = 470.0
    /** Top edge of the editor error box. */
    const val ERROR_Y = 745.0
    /** Width of the editor error box. */
    const val ERROR_W = 780.0
    /** Height of the editor error box. */
    const val ERROR_H = 48.0
    /** Maximum displayed characters before truncating an editor error. */
    const val ERROR_MAX_CHARS = 88
}
