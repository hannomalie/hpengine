package de.hanno.hpengine.graphics

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class OpenGlCommandSyncTest {
    @Test
    fun `onSignal is executed on update step`() {
        val openGLContext = createOpenGLContext()
        val signaled = AtomicBoolean(false)

        val commandSync = openGLContext.second.CommandSync { signaled.getAndSet(true) }

        commandSync.isSignaled shouldBe false
        openGLContext.second.checkCommandSyncs()
        commandSync.isSignaled shouldBe true
    }
}