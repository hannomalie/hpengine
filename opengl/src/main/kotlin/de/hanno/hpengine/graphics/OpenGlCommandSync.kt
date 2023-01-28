package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.sync.GpuCommandSync
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL32.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.IntBuffer


class OpenGlCommandSync internal constructor(val onSignaled: (() -> Unit)? = null) : GpuCommandSync {
    private val gpuCommandSync: Long = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0).apply {
        require(this > 0) { "Error creating sync with glFenceSync, value is $this" }
    }

    var signaled = false
        private set

    /**
     * retrieves the signal state from the gpu and returns true if it is signaled now but
     * wasn't before and false otherwise.
     */
    override fun await() {
        val syncResult = glClientWaitSync(gpuCommandSync, GL_SYNC_FLUSH_COMMANDS_BIT, 0)
        val result =  syncResult == GL_ALREADY_SIGNALED || syncResult == GL_CONDITION_SATISFIED
        signaled = result
    }

    private val intBuffer = BufferUtils.createIntBuffer(1)
    override fun update() {
        signaled = glGetSynci(gpuCommandSync, GL_SYNC_STATUS, intBuffer) == GL_SIGNALED
    }

    override val isSignaled: Boolean get() = signaled

    override fun delete() {
        if (gpuCommandSync > 0) {
            glDeleteSync(gpuCommandSync)
        }
    }
}

fun List<OpenGlCommandSync>.check() = forEach { it.update() }