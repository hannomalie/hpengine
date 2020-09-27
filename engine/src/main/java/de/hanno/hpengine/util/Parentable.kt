package de.hanno.hpengine.util

interface Parentable<T : Parentable<T>> {
    val children: List<T>

    fun addChild(child: T)
    fun removeChild(child: T)
    fun hasChildInHierarchy(child: T): Boolean = children.contains(child) || children.any { it.hasChildInHierarchy(child) }

    var parent: T?
    val hasParent: Boolean
        get() = parent != null

    fun hasChildren(): Boolean = children.isNotEmpty()
    fun removeParent() {
        parent = null
    }
}