package de.hanno.hpengine.util

interface Parentable<T : Parentable<T>> {
    val children: MutableList<T>

    fun addChild(child: T): T {
        if (!children.contains(child)) {
            children.add(child)
        }
        return child
    }

    fun removeParent() {
        parent?.children?.remove(this)
        parent = null
    }

    var parent: T?
    val hasParent: Boolean
        get() = parent != null

    fun hasChildren(): Boolean = children.isNotEmpty()
}