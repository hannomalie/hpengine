package de.hanno.hpengine.engine.model

import com.google.common.collect.ImmutableSet

sealed class DataChannelComponent(componentCount : Int, byteSizePerComponent : Int) {
    abstract val name : String
    abstract val type : String
    data class FloatTwo(override val name : String, override val type : String) :DataChannelComponent(2, 4)
    data class FloatThree(override val name : String, override val type : String) :DataChannelComponent(3, 4)

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
