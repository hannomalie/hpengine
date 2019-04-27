package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.model.texture.Texture

class SimpleTextureRenderer(programManager: ProgramManager<OpenGl>,
                            var texture: Texture<*>) : AbstractDeferredRenderer(programManager as OpenGlProgramManager) {
    override var finalImage = texture.textureId
}
