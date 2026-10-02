package com.dojolog.data

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers the colour slot each martial art was given (see
 * [com.dojolog.domain.Stats.reconcileSlots]), so deleting or renaming old sessions never
 * repaints the other arts.
 */
class DisciplineSlotStore(context: Context) {
    private val prefs = context.getSharedPreferences("discipline_colors", Context.MODE_PRIVATE)

    fun read(): Map<String, Int> =
        prefs.all.mapNotNull { (key, slot) -> (slot as? Int)?.let { key to it } }.toMap()

    fun write(slots: Map<String, Int>) = prefs.edit {
        clear()
        slots.forEach { (key, slot) -> putInt(key, slot) }
    }
}
