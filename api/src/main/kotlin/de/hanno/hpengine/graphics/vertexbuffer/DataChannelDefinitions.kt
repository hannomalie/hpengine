package de.hanno.hpengine.graphics.vertexbuffer

import java.lang.Float

sealed class DataChannelComponent(componentCount : Int, byteSizePerComponent : Int) {
    abstract val name : String
    abstract val type : String
    data class FloatTwo(override val name : String, override val type : String) : DataChannelComponent(2, Float.BYTES)
    data class FloatThree(override val name : String, override val type : String) : DataChannelComponent(3, Float.BYTES)
    data class FloatFour(override val name : String, override val type : String) : DataChannelComponent(4, Float.BYTES)
    data class IntFour(override val name : String, override val type : String) : DataChannelComponent(4, Integer.BYTES)

    val byteSize = componentCount * byteSizePerComponent
}
