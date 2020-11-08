package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext

interface OpenGl: BackendType {
    val gpuContext: OpenGLContext
}

class OpenGlBackend(override val eventBus: EventBus,
                    override val gpuContext: GpuContext<OpenGl>,
                    override val programManager: ProgramManager<OpenGl>,
                    override val textureManager: TextureManager,
                    override val input: Input,
                    override val addResourceContext: AddResourceContext) : Backend<OpenGl> {

    companion object {
        operator fun invoke(window: Window<OpenGl>, config: Config, singleThreadContext: AddResourceContext): OpenGlBackend {
            val eventBus = MBassadorEventBus()
            val gpuContext = OpenGLContext.invoke(window)
            val programManager = OpenGlProgramManager(gpuContext, eventBus, config)
            val textureManager = TextureManager(config, programManager, gpuContext, singleThreadContext)
            val input = Input(eventBus, gpuContext)

            return OpenGlBackend(eventBus, gpuContext, programManager, textureManager, input, singleThreadContext)
        }
    }
}

