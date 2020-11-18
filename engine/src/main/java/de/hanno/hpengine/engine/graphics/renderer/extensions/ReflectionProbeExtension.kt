package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toCubeMapArrays
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toCubeMaps
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.scene.Extension
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import java.nio.FloatBuffer

class ReflectionProbeExtension(val engineContext: EngineContext): Extension {
    override val deferredRendererExtension = ReflectionProbeRenderExtension(engineContext)
}
class ReflectionProbeRenderExtension(val engineContext: EngineContext) : RenderExtension<OpenGl> {

    private var renderedInCycle: Long = -1
    val probeRenderer = ReflectionProbeRenderer(engineContext)
    val evaluateProbeProgram = engineContext.programManager.getProgram(
            engineContext.config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
            engineContext.config.engineDir.resolve("shaders/evaluate_reflection_probe_fragment.glsl").toCodeSource(),
            Uniforms.Empty
    )

    private var renderCounter = 0
    private val probesPerFrame = 1.apply {
        require(probeRenderer.probeCount % this == 0) { "probecount has to be devidable by probesperframe" }
    }
    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        val entityAdded = renderState.entitiesState.entityAddedInCycle > renderedInCycle
        val needsRerender = engineContext.config.debug.reRenderProbes || entityAdded
        if (needsRerender) {
            renderCounter = 0
            engineContext.config.debug.reRenderProbes = false
            renderedInCycle = renderState.cycle
        }
        if(renderCounter <= probeRenderer.probeCount-probesPerFrame) {
            probeRenderer.renderProbes(renderState, renderCounter, probesPerFrame)
            renderCounter+=probesPerFrame
        }
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val gBuffer = engineContext.deferredRenderingBuffer
        val gpuContext = engineContext.gpuContext
        gpuContext.disable(GlCap.DEPTH_TEST)
        evaluateProbeProgram.use()
        gBuffer.reflectionBuffer.use(gpuContext, false)

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, gBuffer.positionMap)
        gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.normalMap)
        gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, gBuffer.colorReflectivenessMap)
        gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, gBuffer.motionMap)
        gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
        gpuContext.bindTexture(7, GlTextureTarget.TEXTURE_CUBE_MAP, probeRenderer.cubeMapRenderTarget.textures.first().id)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()

        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.getPosition())
        evaluateProbeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.setUniform("time", renderState.time.toInt())
        evaluateProbeProgram.setUniform("probeDimensions", probeRenderer.probeDimensions[0])

        evaluateProbeProgram.setUniform("probeCount", probeRenderer.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderer.probePositionsStructBuffer)
        gpuContext.fullscreenBuffer.draw()

        gBuffer.use(gpuContext, false)

    }
}

class ReflectionProbeRenderer(private val engineContext: EngineContext) {
    val probeCount = 1
    val probeResolution = 256
    val probePositions = mutableListOf(Vector3f(0f,0f,0f))
    val probeDimensions = mutableListOf(Vector3f(50f,50f,50f))
    val probePositionsStructBuffer = engineContext.gpuContext.window.invoke {
        PersistentMappedStructBuffer(probeCount, engineContext.gpuContext, { HpVector4f() })
    }

    init {
        engineContext.gpuContext.window.invoke {
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        }
    }

    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram = engineContext.programManager.getProgram(
            engineContext.config.EngineAsset("shaders/pointlight_shadow_cubemap_vertex.glsl").toCodeSource(),
            engineContext.config.EngineAsset("shaders/reflectionprobe_cube_fragment.glsl").toCodeSource(),
            engineContext.config.EngineAsset("shaders/pointlight_shadow_cubemap_geometry.glsl").toCodeSource(),
            Defines(),
            Uniforms.Empty
    )

    val cubeMapRenderTarget = RenderTarget(
            gpuContext = engineContext.gpuContext,
            frameBuffer = FrameBuffer(
                    gpuContext = engineContext.gpuContext,
                    depthBuffer = DepthBuffer(CubeMap(
                            engineContext.gpuContext,
                            TextureDimension(probeResolution, probeResolution),
                            TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                            GL14.GL_DEPTH_COMPONENT24, GL11.GL_REPEAT)
                    )
            ),
            width = probeResolution,
            height = probeResolution,
            textures = listOf(
                ColorAttachmentDefinition("ReflectionProbe", GL30.GL_RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))
            ).toCubeMaps(engineContext.gpuContext, probeResolution, probeResolution),
            name = "ReflectionProbe"
    )

    fun renderProbes(renderState: RenderState, probeStartIndex: Int, probesPerFrame: Int) {
        val gpuContext = engineContext.gpuContext

        profiled("ReflectionProbes") {

            gpuContext.depthMask = true
            gpuContext.disable(GlCap.DEPTH_TEST)
            gpuContext.disable(GlCap.CULL_FACE)
            cubeMapRenderTarget.use(engineContext.gpuContext, true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, probeResolution, probeResolution)

            for (probeIndex in probeStartIndex until (probeStartIndex + probesPerFrame)) {
                gpuContext.clearDepthBuffer()

                val skyBox = engineContext.textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", probePositions[probeIndex])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform("lightIndex", probeIndex)
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions[probeIndex])
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, renderState.directionalLightState)

                if(!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
                }
                engineContext.gpuContext.bindTexture(8, skyBox)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(probePositions[probeIndex])
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex]!!)
                    pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex]!!)
                }

                profiled("ReflectionProbe entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(batch.materialInfo.maps)
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(batch,
                                pointCubeShadowPassProgram)
                    }
                }
                val cubeMapArray = cubeMapRenderTarget.textures.first()
                engineContext.textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMapArray.id)
            }
        }
    }
}
