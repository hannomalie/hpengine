package de.hanno.hpengine.graphics.renderer.extensions

import com.artemis.BaseEntitySystem
import com.artemis.World
import com.artemis.annotations.All
import de.hanno.hpengine.backend.Backend
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.addAABBLines
import de.hanno.hpengine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.drawstrategy.*
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.texture.CubeMap
import de.hanno.hpengine.model.texture.TextureDimension
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.model.texture.createView
import de.hanno.hpengine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import java.nio.FloatBuffer

class ReflectionProbe(val extents: Vector3f = Vector3f(100f)) : com.artemis.Component() {
    val transformComponent = TransformComponent()
    val halfExtents: Vector3f
        get() = Vector3f(extents).mul(0.5f)
}

@All(ReflectionProbe::class)
class ReflectionProbeManager(val config: Config) : BaseEntitySystem() {
    override fun processSystem() { }
    override fun inserted(entityId: Int) {
        config.debug.reRenderProbes = true
    }
}

class ReflectionProbeRenderState(val gpuContext: GpuContext<OpenGl>, val renderStateManager: RenderStateManager) {
    var reRenderProbesInCycle = 0L
    var probeCount: Int = 0
    val probeMinMaxStructBuffer = gpuContext.window.invoke {
        PersistentMappedStructBuffer(1, gpuContext, { de.hanno.hpengine.scene.HpVector4f() })
    }
    val probePositions = mutableListOf<Vector3f>()
}

class ReflectionProbeRenderExtension(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val textureManager: TextureManager,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>
) : DeferredRenderExtension<OpenGl> {

    val reflectionProbeRenderState = renderStateManager.renderState.registerState {
        ReflectionProbeRenderState(gpuContext, renderStateManager)
    }

    val probeResolution = 256
    val maxReflectionProbes = 10
    val cubeMapArray = listOf(
        ColorAttachmentDefinition(
            "ReflectionProbe",
            GL30.GL_RGBA16F,
            TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR)
        )
    ).toCubeMapArrays(
        gpuContext,
        probeResolution,
        probeResolution,
        maxReflectionProbes
    ).first()

    private var renderedInCycle: Long = -1
    val probeRenderers = (0 until cubeMapArray.dimension.depth).map {
        ReflectionProbeRenderer(config, gpuContext, programManager, cubeMapArray.createView(gpuContext, it), it)
    }

    val evaluateProbeProgram = programManager.getProgram(
        config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/evaluate_reflection_probe_fragment.glsl").toCodeSource(),
        Uniforms.Empty
    )

    private var renderCounter = 0
    private val probesPerFrame = 1

    override fun extract(renderState: RenderState, world: World) {
        val components = (renderState.componentExtracts[ReflectionProbe::class.java] as List<ReflectionProbe>?) ?: return
        val componentCount = components.size
        val targetState = renderState[reflectionProbeRenderState]

        targetState.reRenderProbesInCycle = if (config.debug.reRenderProbes) renderState.cycle else 0L
        targetState.probeCount = componentCount
        val probeMinMaxStructBuffer = targetState.probeMinMaxStructBuffer
        probeMinMaxStructBuffer.resize(componentCount * 2)
        val probePositions = targetState.probePositions
        probePositions.clear()
        components.forEachIndexed { index, probe ->
            probeMinMaxStructBuffer[2 * index].set(Vector3f(probe.transformComponent.transform.position).sub(probe.halfExtents))
            probeMinMaxStructBuffer[2 * index + 1].set(Vector3f(probe.transformComponent.transform.position).add(probe.halfExtents))
            probePositions.add(Vector3f(probe.transformComponent.transform.position))
        }

    }

    private val lineVertices = PersistentMappedStructBuffer(100, gpuContext, { de.hanno.hpengine.scene.HpVector4f() })
    override fun renderEditor(renderState: RenderState, result: DrawResult) {

        if (config.debug.isEditorOverlay) {
            val linePoints = (0 until renderState[reflectionProbeRenderState].probeCount).flatMap {
                val min = renderState[reflectionProbeRenderState].probeMinMaxStructBuffer[2 * it]
                val minWorld = Vector3f(min.x, min.y, min.z)
                val max = renderState[reflectionProbeRenderState].probeMinMaxStructBuffer[2 * it + 1]
                val maxWorld = Vector3f(max.x, max.y, max.z)
                mutableListOf<Vector3fc>().apply { addAABBLines(minWorld, maxWorld) }
            }

            deferredRenderingBuffer.finalBuffer.use(gpuContext, false)
            gpuContext.blend = false
            drawLines(renderStateManager, programManager, lineVertices, linePoints, color = Vector3f(1f, 0f, 0f))
        }

    }

    override fun renderFirstPass(
        backend: Backend<OpenGl>,
        gpuContext: GpuContext<OpenGl>,
        firstPassResult: FirstPassResult,
        renderState: RenderState
    ) {
        val entityAdded = renderState.entitiesState.entityAddedInCycle > renderedInCycle
        val reRender = renderState[reflectionProbeRenderState].reRenderProbesInCycle > renderedInCycle
        val needsRerender = reRender || entityAdded
        if (needsRerender) {
            renderCounter = 0
            config.debug.reRenderProbes = false
            renderedInCycle = renderState.cycle
        }
        if (renderCounter < renderState[reflectionProbeRenderState].probeCount) {
            probeRenderers[renderCounter].renderProbes(renderState, renderCounter, 1)
            renderCounter += 1
        }
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val currentReflectionProbeRenderState = renderState[reflectionProbeRenderState]

        val gBuffer = deferredRenderingBuffer
        val gpuContext = gpuContext
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
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.setUniform("time", renderState.time.toInt())

        evaluateProbeProgram.setUniform("probeCount", currentReflectionProbeRenderState.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, currentReflectionProbeRenderState.probeMinMaxStructBuffer)
        gpuContext.fullscreenBuffer.draw()

        gBuffer.use(gpuContext, false)

    }

    fun ReflectionProbeRenderer.renderProbes(renderState: RenderState, startIndex: Int, probesPerFrame: Int) {
        val gpuContext = gpuContext
        val currentReflectionProbeRenderState = renderState[reflectionProbeRenderState]
        if (currentReflectionProbeRenderState.probeCount == 0) return

        profiled("ReflectionProbes") {

            gpuContext.depthMask = true
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.CULL_FACE)
            cubeMapRenderTarget.use(gpuContext, true)
            gpuContext.viewPort(0, 0, cubeMap.dimension.width, cubeMap.dimension.height)

            val endIndex = startIndex + probesPerFrame
            val range = startIndex until endIndex
            for (probeIndex in range) {
                gpuContext.clearDepthBuffer()

                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform(
                    "pointLightPositionWorld",
                    currentReflectionProbeRenderState.probePositions[probeIndex]
                )
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
//                pointCubeShadowPassProgram.setUniform("lightIndex", probeIndex)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, renderState.directionalLightState)

                if (!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(
                        8,
                        GlTextureTarget.TEXTURE_2D,
                        renderState.directionalLightState[0].shadowMapId
                    )
                }
                gpuContext.bindTexture(8, skyBox)
                val viewProjectionMatrices =
                    Util.getCubeViewProjectionMatricesForPosition(currentReflectionProbeRenderState.probePositions[probeIndex])
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.first[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.second[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    pointCubeShadowPassProgram.setUniformAsMatrix4(
                        "viewMatrices[$floatBufferIndex]",
                        viewMatrices[floatBufferIndex]!!
                    )
                    pointCubeShadowPassProgram.setUniformAsMatrix4(
                        "projectionMatrices[$floatBufferIndex]",
                        projectionMatrices[floatBufferIndex]!!
                    )
                }

                profiled("ReflectionProbe entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(batch.material.maps)
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(
                            batch,
                            pointCubeShadowPassProgram
                        )
                    }
                }
                val cubeMapArray = cubeMapRenderTarget.textures.first()
                textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMapArray.id)
            }
        }
    }
}

class ReflectionProbeRenderer(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val programManager: ProgramManager<OpenGl>,
    val cubeMap: CubeMap,
    val indexInCubeMapArray: Int
) {

    init {
        gpuContext.window.invoke {
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        }
    }

    var pointLightShadowMapsRenderedInCycle: Long = 0
    var pointCubeShadowPassProgram = programManager.getProgram(
        config.EngineAsset("shaders/pointlight_shadow_cubemap_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/reflectionprobe_cube_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/pointlight_shadow_cubemap_geometry.glsl").toCodeSource(),
        Defines(),
        Uniforms.Empty
    )

    val cubeMapRenderTarget = RenderTarget(
        gpuContext = gpuContext,
        frameBuffer = FrameBuffer(
            gpuContext = gpuContext,
            depthBuffer = DepthBuffer(
                CubeMap(
                    gpuContext,
                    TextureDimension(cubeMap.dimension.width, cubeMap.dimension.height),
                    TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                    GL14.GL_DEPTH_COMPONENT24, GL11.GL_REPEAT
                )
            )
        ),
        width = cubeMap.dimension.width,
        height = cubeMap.dimension.height,
        textures = listOf(cubeMap),
        name = "ReflectionProbe$indexInCubeMapArray"
    )

}
