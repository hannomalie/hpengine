package de.hanno.hpengine.vertexbuffer

import java.util.*

enum class DataChannels(//TODO Use not only float byte size
    val binding: String, val size: Int, val location: Int, val byteOffset: Int
) {
    POSITION3("in_Position", 3, 0, 0), COLOR("in_Color", 3, 1, POSITION3.siB()), TEXCOORD(
        "in_TextureCoord",
        2,
        2,
        POSITION3.siB() + COLOR.siB()
    ),
    NORMAL("in_Normal", 3, 3, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB()), WEIGHTS(
        "in_Weights",
        4,
        5,
        POSITION3.siB() + COLOR.siB() + TEXCOORD.siB() + NORMAL.siB()
    ),
    JOINT_INDICES(
        "in_JointIndices",
        4,
        6,
        POSITION3.siB() + COLOR.siB() + TEXCOORD.siB() + NORMAL.siB() + WEIGHTS.siB()
    );

    // Size in Bytes per Attribute
    fun siB(): Int {
        return java.lang.Float.BYTES * size
    }

    fun getOffset(): Int {
        return byteOffset / 4
    }

    override fun toString(): String {
        return String.format("%s (%d)", binding, size)
    }

    companion object {
        fun totalElementsPerVertex(channels: EnumSet<DataChannels>): Int {
            var count = 0
            for (channel in channels) {
                count += channel.size
            }
            return count
        }

        fun bytesPerVertex(channels: EnumSet<DataChannels>): Int {
            var sum = 0
            for (channel in channels) {
                sum += channel.size
            }
            return sum * java.lang.Float.BYTES
        }
    }
}