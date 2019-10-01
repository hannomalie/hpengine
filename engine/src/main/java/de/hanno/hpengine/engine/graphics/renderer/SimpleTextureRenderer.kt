package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.model.texture.Texture

class SimpleTextureRenderer(engineContext: EngineContext<OpenGl>,
                            config: Config,
                            programManager: ProgramManager<OpenGl>,
                            var texture: Texture<*>,
                            deferredRenderingBuffer: DeferredRenderingBuffer) : AbstractDeferredRenderer(engineContext, programManager as OpenGlProgramManager, config, deferredRenderingBuffer) {
    override var finalImage = texture.id
}
