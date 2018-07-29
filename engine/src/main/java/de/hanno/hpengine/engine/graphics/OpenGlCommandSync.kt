package de.hanno.hpengine.engine.graphics

import org.lwjgl.opengl.GL32.*


class OpenGlCommandSync internal constructor() : GpuCommandSync {

    private val gpuCommandSync: Long = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)

    var signaled = false

    fun checkSignal() {
        val result = if (gpuCommandSync > 0) {
            val signaled = glClientWaitSync(gpuCommandSync, GL_SYNC_FLUSH_COMMANDS_BIT, 0)
            signaled == GL_ALREADY_SIGNALED || signaled == GL_CONDITION_SATISFIED
        } else true
        signaled = result
    }

    override fun delete() {
        if (gpuCommandSync > 0) {
            glDeleteSync(gpuCommandSync)
        }
    }
}

fun checkCommandSyncsReturnUnsignaled(commandSyncs: List<OpenGlCommandSync>): List<OpenGlCommandSync> {
    return commandSyncs.filter { !it.signaled }.apply {
        forEach { it.checkSignal() }
    }
}