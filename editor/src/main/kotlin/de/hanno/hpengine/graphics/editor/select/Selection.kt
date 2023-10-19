package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag

interface Selection {
    fun openNextNode(currentSelection: Selection?) = false
    object None: Selection
}
data class DefaultSelection(val component: Component): Selection

interface EntitySelection: Selection {
    val entity: Int
    val components: Bag<Component>
}

data class NameSelection(override val entity: Int, override val components: Bag<Component>, val name: String): EntitySelection {
    override fun toString(): String = name
}

