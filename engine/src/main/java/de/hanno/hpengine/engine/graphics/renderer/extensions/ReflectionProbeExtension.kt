package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.addAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
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
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.model.texture.createView
import de.hanno.hpengine.engine.scene.Extension
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import java.nio.FloatBuffer

class ReflectionProbe(val extents: Vector3f = Vector3f(100f), override val entity: Entity): Component {
    val halfExtents: Vector3f
        get() = Vector3f(extents).mul(0.5f)
}
class ReflectionProbeExtension(val engineContext: EngineContext): Extension {
    override val manager = object: Manager {
        override fun afterSetScene(lastScene: Scene, currentScene: Scene) {
            engineContext.config.debug.reRenderProbes = true
        }
        override fun onEntityAdded(entities: List<Entity>) {
            engineContext.config.debug.reRenderProbes = true
        }

        override fun onComponentAdded(component: Component) {
            engineContext.config.debug.reRenderProbes = true
        }
    }
    override val componentSystem = SimpleComponentSystem(ReflectionProbe::class.java)
    override val deferredRendererExtension = ReflectionProbeRenderExtension(engineContext, componentSystem)
}
class ReflectionProbeRenderState(val engineContext: EngineContext) {
    var reRenderProbesInCycle = 0L
    var probeCount: Int = 0
    val probeMinMaxStructBuffer = engineContext.gpuContext.window.invoke {
        PersistentMappedStructBuffer(1, engineContext.gpuContext, { HpVector4f() })
    }
    val probePositions = mutableListOf<Vector3f>()
}

class ReflectionProbeRenderExtension(val engineContext: EngineContext,
                                     val componentSystem: SimpleComponentSystem<ReflectionProbe>) : RenderExtension<OpenGl> {

    val reflectionProbeRenderState = engineContext.renderStateManager.renderState.registerState {
        ReflectionProbeRenderState(engineContext)
    }

    val probeResolution = 256
    val maxReflectionProbes = 10
    val cubeMapArray = listOf(ColorAttachmentDefinition("ReflectionProbe", GL30.GL_RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))).toCubeMapArrays(
            engineContext.gpuContext,
            probeResolution,
            probeResolution,
            maxReflectionProbes
    ).first()

    private var renderedInCycle: Long = -1
    val probeRenderers = (0 until cubeMapArray.dimension.depth).map {
        ReflectionProbeRenderer(engineContext, cubeMapArray.createView(engineContext.gpuContext, it), it)
    }

    val evaluateProbeProgram = engineContext.programManager.getProgram(
            engineContext.config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
            engineContext.config.engineDir.resolve("shaders/evaluate_reflection_probe_fragment.glsl").toCodeSource(),
            Uniforms.Empty
    )

    private var renderCounter = 0
    private val probesPerFrame = 1

    override fun extract(scene: Scene, renderState: RenderState) {
        val componentCount = componentSystem.getComponents().size
        val targetState = renderState[reflectionProbeRenderState]

        targetState.reRenderProbesInCycle = if(engineContext.config.debug.reRenderProbes) renderState.cycle else 0L
        targetState.probeCount = componentCount
        val probeMinMaxStructBuffer = targetState.probeMinMaxStructBuffer
        probeMinMaxStructBuffer.resize(componentCount*2)
        val probePositions = targetState.probePositions
        probePositions.clear()
        componentSystem.getComponents().forEachIndexed { index, probe ->
            probeMinMaxStructBuffer[2*index].set(Vector3f(probe.entity.transform.position).sub(probe.halfExtents))
            probeMinMaxStructBuffer[2*index+1].set(Vector3f(probe.entity.transform.position).add(probe.halfExtents))
            probePositions.add(Vector3f(probe.entity.transform.position))
        }

    }

    private val lineVertices = PersistentMappedStructBuffer(100, engineContext.gpuContext, { HpVector4f() })
    override fun renderEditor(renderState: RenderState, result: DrawResult) {

        if(engineContext.config.debug.isEditorOverlay) {
            val linePoints = (0 until renderState[reflectionProbeRenderState].probeCount).flatMap {
                val min = renderState[reflectionProbeRenderState].probeMinMaxStructBuffer[2*it]
                val minWorld = Vector3f(min.x, min.y, min.z)
                val max = renderState[reflectionProbeRenderState].probeMinMaxStructBuffer[2*it+1]
                val maxWorld = Vector3f(max.x, max.y, max.z)
                mutableListOf<Vector3fc>().apply { addAABBLines(minWorld, maxWorld) }
            }

            engineContext.deferredRenderingBuffer.finalBuffer.use(engineContext.gpuContext, false)
            engineContext.gpuContext.blend = false
            engineContext.drawLines(lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
        }

    }

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        val entityAdded = renderState.entitiesState.entityAddedInCycle > renderedInCycle
        val reRender = renderState[reflectionProbeRenderState].reRenderProbesInCycle > renderedInCycle
        val needsRerender = reRender || entityAdded
        if (needsRerender) {
            renderCounter = 0
            engineContext.config.debug.reRenderProbes = false
            renderedInCycle = renderState.cycle
        }
        if(renderCounter < componentSystem.getComponents().size) {
            probeRenderers[renderCounter].renderProbes(renderState, renderCounter, 1)
            renderCounter += 1
        }
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val currentReflectionProbeRenderState = renderState[reflectionProbeRenderState]

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
        gpuContext.bindTexture(7, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, cubeMapArray.id)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()

        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.getPosition())
        evaluateProbeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.setUniform("time", renderState.time.toInt())

        evaluateProbeProgram.setUniform("probeCount", currentReflectionProbeRenderState.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, currentReflectionProbeRenderState.probeMinMaxStructBuffer)
        gpuContext.fullscreenBuffer.draw()

        gBuffer.use(gpuContext, false)

    }

    fun ReflectionProbeRenderer.renderProbes(renderState: RenderState, startIndex: Int, probesPerFrame: Int) {
        val gpuContext = engineContext.gpuContext
        val currentReflectionProbeRenderState = renderState[reflectionProbeRenderState]
        if(currentReflectionProbeRenderState.probeCount == 0) return

        profiled("ReflectionProbes") {

            gpuContext.depthMask = true
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.CULL_FACE)
            cubeMapRenderTarget.use(engineContext.gpuContext, true)
            gpuContext.viewPort(0, 0, cubeMap.dimension.width, cubeMap.dimension.height)

            val endIndex = startIndex + probesPerFrame
            val range = startIndex until endIndex
            for (probeIndex in range) {
                gpuContext.clearDepthBuffer()

                val skyBox = engineContext.textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", currentReflectionProbeRenderState.probePositions[probeIndex])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
//                pointCubeShadowPassProgram.setUniform("lightIndex", probeIndex)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, renderState.directionalLightState)

                if(!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
                }
                engineContext.gpuContext.bindTexture(8, skyBox)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(currentReflectionProbeRenderState.probePositions[probeIndex])
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

class ReflectionProbeRenderer(private val engineContext: EngineContext, val cubeMap: CubeMap, val indexInCubeMapArray: Int) {

    init {
        engineContext.gpuContext.window.invoke {
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        }
    }

    var pointLightShadowMapsRenderedInCycle: Long = 0
    var pointCubeShadowPassProgram = engineContext.programManager.getProgram(
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
                            TextureDimension(cubeMap.dimension.width, cubeMap.dimension.height),
                            TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                            GL14.GL_DEPTH_COMPONENT24, GL11.GL_REPEAT)
                    )
            ),
            width = cubeMap.dimension.width,
            height = cubeMap.dimension.height,
            textures = listOf(cubeMap),
            name = "ReflectionProbe$indexInCubeMapArray"
    )

}
