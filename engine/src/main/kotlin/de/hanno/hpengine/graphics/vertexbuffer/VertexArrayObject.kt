package de.hanno.hpengine.graphics.vertexbuffer

import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.*

import java.util.*

class VertexArrayObject private constructor(private val gpuContext: GpuContext, channels: EnumSet<DataChannels>) {

    private val channels: EnumSet<DataChannels> = channels.clone()
    var id = gpuContext.invoke { GL30.glGenVertexArrays() }
    init {
        setUpAttributes()
    }

    fun bind() = gpuContext.invoke {
        GL30.glBindVertexArray(id)
    }

    private fun setUpAttributes() {
        gpuContext.invoke {
            bind()
            var currentOffset = 0
            for (channel in channels) {
                GL20.glEnableVertexAttribArray(channel.location)
                GL20.glVertexAttribPointer(channel.location, channel.size, GL11.GL_FLOAT, false, bytesPerVertex(channels), currentOffset.toLong())

                currentOffset += channel.size * 4
            }
        }
    }

    fun delete() {
        GL30.glDeleteVertexArrays(id)
    }

    companion object {


        fun getForChannels(gpuContext: GpuContext, channels: EnumSet<DataChannels>): VertexArrayObject {
            return VertexArrayObject(gpuContext, channels)
        }

        private val cache = HashMap<EnumSet<DataChannels>, Int>()

        fun bytesPerVertex(channels: EnumSet<DataChannels>): Int {
            if (cache.containsKey(channels)) {
                return cache[channels]!!
            } else {
                var sum = 0
                for (channel in channels) {
                    sum += channel.size
                }
                val bytesPerVertex = sum * 4
                cache[channels] = bytesPerVertex
                return bytesPerVertex
            }
        }
    }
}
