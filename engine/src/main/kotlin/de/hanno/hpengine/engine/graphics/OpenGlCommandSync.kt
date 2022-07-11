package de.hanno.hpengine.engine.graphics

import org.lwjgl.opengl.GL32.*


class OpenGlCommandSync internal constructor(val onSignaled: (() -> Unit)? = null) : GpuCommandSync {
    private val gpuCommandSync: Long = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)

    var signaled = false

    /**
     * retrieves the signal state from the gpu and returns true if it is signaled now but
     * wasn't before and false otherwise.
     */
    fun checkSignal(): Boolean {
        val signaledBefore = signaled

        val result = if (gpuCommandSync > 0) {
            val signaled = glClientWaitSync(gpuCommandSync, GL_SYNC_FLUSH_COMMANDS_BIT, 0)
            signaled == GL_ALREADY_SIGNALED || signaled == GL_CONDITION_SATISFIED
        } else true
        signaled = result

        return signaled && !signaledBefore
    }

    override fun delete() {
        if (gpuCommandSync > 0) {
            glDeleteSync(gpuCommandSync)
        }
    }
}

/**
 * retrieves signal state from the gpu and returns the sync objects that got signaled just before this check
 */
fun List<OpenGlCommandSync>.check() = mapNotNull { if(it.checkSignal()) it else null }