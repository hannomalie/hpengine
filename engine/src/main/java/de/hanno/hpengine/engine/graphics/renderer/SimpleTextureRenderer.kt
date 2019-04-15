package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.log.ConsoleLogger
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector3f
import java.io.File
import java.util.function.Consumer

class SimpleTextureRenderer(programManager: ProgramManager<OpenGl>,
                            var texture: Texture<*>) : AbstractRenderer(programManager as OpenGlProgramManager) {
    override var finalImage = texture.textureId
}
