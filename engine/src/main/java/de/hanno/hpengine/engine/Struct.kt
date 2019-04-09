package de.hanno.hpengine.engine

import de.hanno.struct.Struct
import de.hanno.struct.StructObjectArray
import org.lwjgl.BufferUtils

class SizedArray<T: Struct>(theSize: Int, val factory: (Struct) -> T): Struct() {
// TODO: Offer delegation for properties here somehow
    private var _size by 0
    private var dummy1 by 0
    private var dummy2 by 0
    private var dummy3 by 0
    val size: Int
        get() = _size
    val array: StructObjectArray<T> by StructObjectArray(theSize, factory)

    override val ownBuffer = BufferUtils.createByteBuffer(array.sizeInBytes + 4*Integer.BYTES)

    val indices: IntRange = array.indices
    operator fun get(index: Int): T = array[index]
    fun getAtIndex(index: Int): T = array.getAtIndex(index)

    init {
        _size = theSize
    }
}