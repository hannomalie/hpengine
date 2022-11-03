package de.hanno.hpengine.backend

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.bus.EventBus
import de.hanno.hpengine.bus.MBassadorEventBus
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.Window
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.scene.AddResourceContext

class OpenGlBackend(override val eventBus: EventBus,
                    override val gpuContext: GpuContext,
                    override val programManager: ProgramManager,
                    override val textureManager: TextureManager,
                    override val input: Input,
                    override val addResourceContext: AddResourceContext
) : Backend {

    companion object {
        operator fun invoke(window: Window, config: Config, singleThreadContext: AddResourceContext): OpenGlBackend {
            val eventBus = MBassadorEventBus()
            val gpuContext = OpenGLContext.invoke(window)
            val programManager = OpenGlProgramManager(gpuContext, eventBus, config)
            val textureManager = TextureManager(config, programManager, gpuContext)
            val input = Input(gpuContext)

            return OpenGlBackend(eventBus, gpuContext, programManager, textureManager, input, singleThreadContext)
        }
    }
}

