package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.light.point.CubeShadowMapStrategy
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.DirectionalLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ForwardRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.PointLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.SkyBoxRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.StateRef
import de.hanno.hpengine.engine.model.QuadVertexBuffer
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.texture.createView
import org.lwjgl.opengl.GL11
import java.io.File
import java.util.ArrayList
import javax.vecmath.Vector2f

class ExtensibleDeferredRenderer(val engineContext: EngineContext<OpenGl>): RenderSystem, EngineContext<OpenGl> by engineContext {
    val drawlinesExtension = DrawLinesExtension(engineContext, programManager)
    val combinePassExtension = CombinePassRenderExtension(engineContext)
    val simpleColorProgramStatic = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl")
    val simpleColorProgramAnimated = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("ANIMATED", true)))

    val textureRenderer = SimpleTextureRenderer(engineContext, deferredRenderingBuffer.colorReflectivenessTexture)

    private val sixDebugBuffers: ArrayList<VertexBuffer> = gpuContext.setupBuffers()
    private val debugFrameProgram = programManager.getProgram(
            getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")),
            getShaderSource(File(Shader.directory + "debugframe_fragment.glsl")))

    val pipeline: StateRef<SimplePipeline> = engineContext.renderStateManager.renderState.registerState {
        object: SimplePipeline(engineContext) {
            override fun beforeDraw(renderState: RenderState, program: Program) {

                deferredRenderingBuffer.use(gpuContext, false)
                super.beforeDraw(renderState, program)

                gpuContext.enable(GlCap.CULL_FACE)
                gpuContext.depthMask(true)
                gpuContext.enable(GlCap.DEPTH_TEST)
                gpuContext.depthFunc(GlDepthFunc.LESS)
                gpuContext.disable(GlCap.BLEND)
            }
        }
    }

    val extensions: List<RenderExtension<OpenGl>> = listOf(
        SkyBoxRenderExtension(engineContext),
        ForwardRenderExtension(engineContext),
        DirectionalLightSecondPassExtension(engineContext),
        PointLightSecondPassExtension(engineContext)
    )

    override fun render(result: DrawResult, state: RenderState) {
        gpuContext.depthMask(true)
        deferredRenderingBuffer.use(gpuContext, true)

        if(engineContext.config.debug.isDrawBoundingVolumes) {
            drawlinesExtension.renderFirstPass(engineContext, gpuContext, result.firstPassResult, state)
        } else {
            state[pipeline].draw(state, simpleColorProgramStatic, simpleColorProgramAnimated, result.firstPassResult)
            for (extension in extensions) {
                extension.renderFirstPass(backend, gpuContext, result.firstPassResult, state)
            }
            deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, true)
            for (extension in extensions) {
                extension.renderSecondPassHalfScreen(state, result.secondPassResult)
                extension.renderSecondPassFullScreen(state, result.secondPassResult)
            }
            deferredRenderingBuffer.lightAccumulationBuffer.unuse(gpuContext)
            combinePassExtension.renderCombinePass(state)
        }

        val finalImage = if(engineContext.config.debug.isUseDirectTextureOutput) {
            engineContext.config.debug.directTextureOutputTextureIndex
        } else if(engineContext.config.debug.isDrawBoundingVolumes) {
            deferredRenderingBuffer.colorReflectivenessMap
        } else {
            deferredRenderingBuffer.finalMap
        }

        textureRenderer.drawToQuad(engineContext.window.frontBuffer, finalImage)


//        val cubeMapIndex = 0
//        (0..5).map { faceIndex ->
//            val cubeShadowMapStrategy = state.lightState.pointLightShadowMapStrategy as? CubeShadowMapStrategy
//            cubeShadowMapStrategy?.let { cubeShadowMapStrategy ->
//                val id = textureManager.cubeMap.createView(gpuContext, faceIndex).id//cubeShadowMapStrategy.cubeMapArray.createView(gpuContext, cubeMapIndex, faceIndex).id
//                textureRenderer.drawToQuad(texture = id, program = debugFrameProgram, buffer = sixDebugBuffers[faceIndex])
//                GL11.glDeleteTextures(id);
//            }
//        }
    }

    private fun GpuContext<OpenGl>.setupBuffers(): ArrayList<VertexBuffer> {
        return calculate {
            val sixDebugBuffers = object : ArrayList<VertexBuffer>() {
                init {
                    val height = -2f / 3f
                    val width = 2f
                    val widthDiv = width / 6f
                    for (i in 0..5) {
                        val quadVertexBuffer = QuadVertexBuffer(backend.gpuContext, Vector2f(-1f + i * widthDiv, -1f), Vector2f(-1 + (i + 1) * widthDiv, height))
                        add(quadVertexBuffer)
                        quadVertexBuffer.upload()
                    }
                }
            }

            gpuContext.getExceptionOnError("setupBuffers")
            sixDebugBuffers
        }
    }
}

private operator fun <T> RenderState.get(stateRef: StateRef<T>): T = getState(stateRef)
