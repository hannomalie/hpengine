package de.hanno.hpengine

interface Parentable<T : Parentable<T>> {
    val children: List<T>
    val hasChildren: Boolean get() = children.isNotEmpty()
    fun addChild(child: T)
    fun removeChild(child: T)
    fun hasChildInHierarchy(child: T): Boolean = children.contains(child) || children.any { it.hasChildInHierarchy(child) }

    var parent: T?
    val hasParent: Boolean get() = parent != null
}