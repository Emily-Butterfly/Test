package com.dojolog.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Remembers the colour slot each martial art was given (see
 * [com.dojolog.domain.Stats.reconcileSlots]), so deleting or renaming old sessions never
 * repaints the other arts. Slots that came with an imported backup are kept apart as
 * preferences until their arts show up.
 */
class DisciplineSlotStore(context: Context) {
    private val slots = context.getSharedPreferences("discipline_colors", Context.MODE_PRIVATE)
    private val preferred = context.getSharedPreferences("discipline_colors_imported", Context.MODE_PRIVATE)

    fun read(): Map<String, Int> = slots.readSlots()

    fun write(map: Map<String, Int>) = slots.writeSlots(map)

    fun readPreferred(): Map<String, Int> = preferred.readSlots()

    fun writePreferred(map: Map<String, Int>) = preferred.writeSlots(map)

    private fun SharedPreferences.readSlots(): Map<String, Int> =
        all.mapNotNull { (key, slot) -> (slot as? Int)?.let { key to it } }.toMap()

    private fun SharedPreferences.writeSlots(map: Map<String, Int>) = edit {
        clear()
        map.forEach { (key, slot) -> putInt(key, slot) }
    }
}
