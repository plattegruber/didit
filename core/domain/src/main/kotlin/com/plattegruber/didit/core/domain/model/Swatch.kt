package com.plattegruber.didit.core.domain.model

/**
 * The colours a choice option can take. [Ink] is the theme's own foreground, so the most common
 * outcome reads as plain ink and the others stand out against it. Concrete colours are the UI's
 * business; the domain only names them.
 *
 * [key] is the persisted identifier. Never change an existing key; add new swatches instead.
 */
enum class Swatch(val key: String) {
    Ink("ink"),
    Blue("blue"),
    Amber("amber"),
    Coral("coral"),
    Lilac("lilac"),
    Sage("sage"),
    ;

    companion object {
        private val byKey = entries.associateBy(Swatch::key)

        /** Resolves a persisted key, falling back to [Ink] so unknown data still renders. */
        fun fromKey(key: String): Swatch = byKey[key] ?: Ink
    }
}
