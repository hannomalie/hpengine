package de.hanno.hpengine.graphics.buffer.vertex


sealed class DataChannelComponent(componentCount : Int, componentByteSize : Int) {
    abstract val name : String
    abstract val type : String
    data class FloatTwo(override val name : String, override val type : String) : DataChannelComponent(2, Float.SIZE_BYTES)
    data class FloatThree(override val name : String, override val type : String) : DataChannelComponent(3, Float.SIZE_BYTES)
    data class FloatFour(override val name : String, override val type : String) : DataChannelComponent(4, Float.SIZE_BYTES)
    data class IntFour(override val name : String, override val type : String) : DataChannelComponent(4, Int.SIZE_BYTES)

    val byteSize = componentCount * componentByteSize
}
