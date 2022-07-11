package de.hanno.hpengine.engine.vertexbuffer

import com.google.common.collect.ImmutableSet

sealed class DataChannelComponent(componentCount : Int, byteSizePerComponent : Int) {
    abstract val name : String
    abstract val type : String
    data class FloatTwo(override val name : String, override val type : String) : DataChannelComponent(2, java.lang.Float.BYTES)
    data class FloatThree(override val name : String, override val type : String) : DataChannelComponent(3, java.lang.Float.BYTES)
    data class FloatFour(override val name : String, override val type : String) : DataChannelComponent(4, java.lang.Float.BYTES)
    data class IntFour(override val name : String, override val type : String) : DataChannelComponent(4, java.lang.Integer.BYTES)

    val byteSize = componentCount * byteSizePerComponent
}

interface DataChannelProvider {
    val channels: ImmutableSet<DataChannelComponent>
    val name : String

    fun structCode() : String {
        val buffer = StringBuffer("struct $name {\n")
        for(channel in channels) {
            buffer.append("${channel.type} ${channel.name};\n")
        }
        buffer.append("}")
        return buffer.toString()
    }
}
