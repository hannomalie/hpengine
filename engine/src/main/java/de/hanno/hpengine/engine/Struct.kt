package de.hanno.hpengine.engine

import de.hanno.struct.StaticStructObjectArray
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import org.lwjgl.BufferUtils

class SizedArray<T: Struct>(theSize: Int, val factory: (Struct) -> T): Struct(), StructArray<T> {
// TODO: Offer delegation for properties here somehow
    private var _size by 0
    private var dummy1 by 0
    private var dummy2 by 0
    private var dummy3 by 0
    override val size: Int
        get() = _size
//        set(value) {
//            array.size = value
//            _size = value
//        }
    val array: StructArray<T> by StaticStructObjectArray(this, theSize, factory)

    override val buffer = BufferUtils.createByteBuffer(array.sizeInBytes + 4*Integer.BYTES)

    override val indices: IntRange = array.indices
    override operator fun get(index: Int): T = array[index]
    override fun getAtIndex(index: Int): T = array.getAtIndex(index)

    init {
        _size = theSize
    }
}