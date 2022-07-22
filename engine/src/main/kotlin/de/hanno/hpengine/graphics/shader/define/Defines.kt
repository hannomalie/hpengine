package de.hanno.hpengine.graphics.shader.define

fun Defines() = Defines(emptyList())
fun Defines(vararg defines: Define<Boolean>) = Defines(listOf(*defines))

// TODO: Make it an array instead of List
class Defines(private val _defines: List<Define<*>>): List<Define<*>> by _defines {
    private val definesArray = _defines.toTypedArray()

    override fun equals(other: Any?): Boolean = (other is Defines) && definesArray contentEquals other.definesArray

    override fun hashCode(): Int = definesArray.contentHashCode()
}