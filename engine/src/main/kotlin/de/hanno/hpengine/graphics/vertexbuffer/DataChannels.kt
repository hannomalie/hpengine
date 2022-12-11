package de.hanno.hpengine.graphics.vertexbuffer

import java.util.*

enum class DataChannels(//TODO Use not only float byte size
    val binding: String, val size: Int, val location: Int, val byteOffset: Int
) {
    POSITION3("in_Position", 3, 0, 0), COLOR("in_Color", 3, 1, POSITION3.sizeInBytes()), TEXCOORD(
        "in_TextureCoord",
        2,
        2,
        POSITION3.sizeInBytes() + COLOR.sizeInBytes()
    ),
    NORMAL("in_Normal", 3, 3, POSITION3.sizeInBytes() + COLOR.sizeInBytes() + TEXCOORD.sizeInBytes()), WEIGHTS(
        "in_Weights",
        4,
        5,
        POSITION3.sizeInBytes() + COLOR.sizeInBytes() + TEXCOORD.sizeInBytes() + NORMAL.sizeInBytes()
    ),
    JOINT_INDICES(
        "in_JointIndices",
        4,
        6,
        POSITION3.sizeInBytes() + COLOR.sizeInBytes() + TEXCOORD.sizeInBytes() + NORMAL.sizeInBytes() + WEIGHTS.sizeInBytes()
    );

    // Size in Bytes per Attribute
    fun sizeInBytes(): Int = java.lang.Float.BYTES * size

    fun getOffset(): Int = byteOffset / 4

    override fun toString(): String = String.format("%s (%d)", binding, size)

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