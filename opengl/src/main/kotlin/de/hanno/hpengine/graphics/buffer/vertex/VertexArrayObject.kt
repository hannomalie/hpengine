package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.graphics.DataChannels
import de.hanno.hpengine.graphics.GraphicsApi
import org.lwjgl.opengl.*

import java.util.*


class VertexArrayObject(
    private val graphicsApi: GraphicsApi,
    private val channels: EnumSet<DataChannels>
) {

    var id = graphicsApi.onGpu { GL30.glGenVertexArrays() }
    init {
        setUpAttributes()
    }

    private fun setUpAttributes() = graphicsApi.onGpu {
        bind()
        var currentOffset = 0
        for (channel in channels) {
            GL20.glEnableVertexAttribArray(channel.location)
            GL20.glVertexAttribPointer(channel.location, channel.size, GL11.GL_FLOAT, false, bytesPerVertex(channels), currentOffset.toLong())

            currentOffset += channel.size * 4
        }
    }

    fun bind() = graphicsApi.onGpu {
        GL30.glBindVertexArray(id)
    }

    fun delete() = GL30.glDeleteVertexArrays(id)

    companion object {
        fun getForChannels(graphicsApi: GraphicsApi, channels: EnumSet<DataChannels>) = VertexArrayObject(graphicsApi, channels)

        private val cache = HashMap<EnumSet<DataChannels>, Int>()

        fun bytesPerVertex(channels: EnumSet<DataChannels>): Int = if (cache.containsKey(channels)) {
            cache[channels]!!
        } else {
            var sum = 0
            for (channel in channels) {
                sum += channel.size
            }
            val bytesPerVertex = sum * 4
            cache[channels] = bytesPerVertex
            bytesPerVertex
        }
    }
}
