package de.hanno.hpengine.graphics.renderer.deferred

import InternalTextureFormat
import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.World
import com.artemis.annotations.All
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.feature.BindlessTextures
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.addAABBLines
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.rendertarget.toCubeMapArrays
import de.hanno.hpengine.graphics.shader.LinesProgramUniforms
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.math.OmniCamera
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.StringBasedCodeSource
import de.hanno.hpengine.toCount
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.forIndex
import java.nio.FloatBuffer

class ReflectionProbe(val extents: Vector3f = Vector3f(100f)) : com.artemis.Component() {
    val transformComponent = TransformComponent()
    val halfExtents: Vector3f
        get() = Vector3f(extents).mul(0.5f)
}

@All(ReflectionProbe::class)
@Single(binds=[BaseSystem::class, ReflectionProbeManager::class])
class ReflectionProbeManager(val config: Config) : BaseEntitySystem() {
    override fun processSystem() {}
    override fun inserted(entityId: Int) {
        config.debug.reRenderProbes = true
    }
}

@Single
class ReflectionProbesStateHolder(renderStateContext: RenderStateContext) {
    val probesState = renderStateContext.renderState.registerState {
        ReflectionProbesState()
    }
}
class ReflectionProbesState {
    val transforms = mutableListOf<ReflectionProbeState>()
}
class ReflectionProbeState {
    val transform = Transform()
    val halfExtents = Vector3f()
}

class ReflectionProbeRenderState(graphicsApi: GraphicsApi) {
    var reRenderProbesInCycle = 0L
    var probeCount: Int = 0
    val probeMinMaxStructBuffer = graphicsApi.onGpu {
        PersistentShaderStorageBuffer(SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    }
    val probePositions = mutableListOf<Vector3f>()
}

@Single(binds = [ReflectionProbeRenderExtension::class, DeferredRenderExtension::class])
class ReflectionProbeRenderExtension(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val textureManager: TextureManager,
    private val programManager: ProgramManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val reflectionProbesStateHolder: ReflectionProbesStateHolder,
    private val pointLightSystem: PointLightSystem,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
) : DeferredRenderExtension {
    private val fullscreenBuffer = QuadVertexBuffer(graphicsApi)
    override val renderPriority = 3000

    private val reflectionProbeRenderState = renderStateContext.renderState.registerState {
        ReflectionProbeRenderState(graphicsApi)
    }
    private val linesProgram = programManager.run {
        val uniforms = LinesProgramUniforms(graphicsApi)
        getProgram(
            StringBasedCodeSource(
                "mvp_vertex_vec4", """
                //include(globals_structs.glsl)
                
                ${uniforms.shaderDeclarations}

                in vec4 in_Position;

                out vec4 pass_Position;
                out vec4 pass_WorldPosition;

                void main()
                {
                	vec4 vertex = vertices[gl_VertexID];
                	vertex.w = 1;

                	pass_WorldPosition = ${uniforms::modelMatrix.name} * vertex;
                	pass_Position = ${uniforms::projectionMatrix.name} * ${uniforms::viewMatrix.name} * pass_WorldPosition;
                    gl_Position = pass_Position;
                }
            """.trimIndent()
            ),
            StringBasedCodeSource(
                "simple_color_vec3", """
            ${uniforms.shaderDeclarations}

            layout(location=0)out vec4 out_color;

            void main()
            {
                out_color = vec4(${uniforms::color.name},1);
            }
        """.trimIndent()
            ), null, Defines(), uniforms
        )
    }

    val probeResolution = 256
    val maxReflectionProbes = 10
    val cubeMapArray = listOf(
        ColorAttachmentDefinition(
            "ReflectionProbe",
            InternalTextureFormat.RGBA16F,
            TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR)
        )
    ).toCubeMapArrays(
        graphicsApi,
        probeResolution,
        probeResolution,
        maxReflectionProbes
    ).first()

    private var renderedInCycle: Long = -1
    val pointCubeShadowPassProgram: Program<Uniforms> = programManager.getProgram(
        config.EngineAsset("shaders/pointlight_shadow_cubemap_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/reflectionprobe_cube_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/pointlight_shadow_cubemap_geometry.glsl").toCodeSource(),
        Defines(),
        Uniforms.Empty
    )
    val probeRenderers = (0 until cubeMapArray.dimension.depth).map {
        ReflectionProbeRenderer(graphicsApi, config, programManager, graphicsApi.createView(cubeMapArray, it), pointCubeShadowPassProgram, it)
    }

    val evaluateProbeProgram = programManager.getProgram(
        config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/evaluate_reflection_probe_fragment.glsl").toCodeSource(),
        Uniforms.Empty,
        Defines()
    )

    private var renderCounter = 0
    private val probesPerFrame = 1

    override fun extract(renderState: RenderState, world: World) {
        // TODO: Implement extraction here, not sure whether this is currently doing the right thing
        val components = renderState[reflectionProbesStateHolder.probesState].transforms
        val componentCount = components.size.toCount()
        val targetState = renderState[reflectionProbeRenderState]

        targetState.reRenderProbesInCycle = if (config.debug.reRenderProbes) renderState.cycle else 0L
        targetState.probeCount = componentCount.value.toInt()
        val probeMinMaxStructBuffer = targetState.probeMinMaxStructBuffer
        probeMinMaxStructBuffer.ensureCapacityInBytes(componentCount * 2 * SizeInBytes(Vector4fStrukt.sizeInBytes) )
        val probePositions = targetState.probePositions
        probePositions.clear()
        components.forEachIndexed { index, probe ->
            probeMinMaxStructBuffer.typedBuffer.forIndex(2 * index) {
                it.set(
                    Vector3f(probe.transform.position).sub(
                        probe.halfExtents
                    )
                )
            }
            probeMinMaxStructBuffer.typedBuffer.forIndex(2 * index + 1) {
                it.set(
                    Vector3f(probe.transform.position).add(
                        probe.halfExtents
                    )
                )
            }
            probePositions.add(Vector3f(probe.transform.position))
        }

    }

    private val lineVertices = graphicsApi.PersistentShaderStorageBuffer(100.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    override fun renderEditor(renderState: RenderState): Unit = graphicsApi.run {
        val linePoints = (0 until renderState[reflectionProbeRenderState].probeCount).flatMap {
            val minWorld =
                renderState[reflectionProbeRenderState].probeMinMaxStructBuffer.typedBuffer.forIndex(2 * it) {
                    Vector3f(
                        it.x,
                        it.y,
                        it.z
                    )
                }
            val maxWorld =
                renderState[reflectionProbeRenderState].probeMinMaxStructBuffer.typedBuffer.forIndex(2 * it + 1) {
                    Vector3f(
                        it.x,
                        it.y,
                        it.z
                    )
                }
            mutableListOf<Vector3fc>().apply { addAABBLines(minWorld, maxWorld) }
        }

        deferredRenderingBuffer.finalBuffer.use(false)
        blend = false

        val camera = renderState[primaryCameraStateHolder.camera]
        drawLines(
            linesProgram,
            lineVertices,
            linePoints,
            viewMatrix = camera.viewMatrixBuffer,
            projectionMatrix = camera.projectionMatrixBuffer,
            color = Vector3f(1f, 0f, 0f)
        )
    }

    override fun renderFirstPass(
        renderState: RenderState
    ) {
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val entityAdded = entitiesState.entityAddedInCycle > renderedInCycle
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

    override fun renderSecondPassFullScreen(renderState: RenderState): Unit = graphicsApi.run {

        val currentReflectionProbeRenderState = renderState[reflectionProbeRenderState]
        val directionalLightState = renderState[directionalLightStateHolder.lightState]

        val gBuffer = deferredRenderingBuffer
        disable(Capability.DEPTH_TEST)
        evaluateProbeProgram.use()
        gBuffer.reflectionBuffer.use(false)

        bindTexture(0, TextureTarget.TEXTURE_2D, gBuffer.positionMap)
        bindTexture(1, TextureTarget.TEXTURE_2D, gBuffer.normalMap)
        bindTexture(2, TextureTarget.TEXTURE_2D, gBuffer.colorReflectivenessMap)
        bindTexture(3, TextureTarget.TEXTURE_2D, gBuffer.motionMap)

        val directionalShadowMap = directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
        if(directionalShadowMap > -1) {
            bindTexture(6, TextureTarget.TEXTURE_2D, directionalShadowMap)
        }
        bindTexture(7, TextureTarget.TEXTURE_CUBE_MAP_ARRAY, cubeMapArray.id)
        pointLightSystem.shadowMapStrategy.bindTextures()

        val camera = renderState[primaryCameraStateHolder.camera]
        evaluateProbeProgram.setUniform("eyePosition", camera.getPosition())
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixBuffer)
        evaluateProbeProgram.setUniform("time", renderState.time.toInt())

        evaluateProbeProgram.setUniform("probeCount", currentReflectionProbeRenderState.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, currentReflectionProbeRenderState.probeMinMaxStructBuffer)
        fullscreenBuffer.draw(indexBuffer = null)

        gBuffer.use(false)

    }

    private val omniCamera = OmniCamera(Vector3f())
    fun ReflectionProbeRenderer.renderProbes(renderState: RenderState, startIndex: Int, probesPerFrame: Int): Unit = graphicsApi.run {
        val currentReflectionProbeRenderState = renderState[reflectionProbeRenderState]
        if (currentReflectionProbeRenderState.probeCount == 0) return

        val directionalLightState = renderState[directionalLightStateHolder.lightState]
        val pointLightState = renderState[pointLightStateHolder.lightState]
        val entitiesState = renderState[entitiesStateHolder.entitiesState]

        profiled("ReflectionProbes") {

            depthMask = true
            enable(Capability.DEPTH_TEST)
            enable(Capability.CULL_FACE)
            cubeMapRenderTarget.use(true)
            viewPort(0, 0, cubeMap.dimension.width, cubeMap.dimension.height)

            val endIndex = startIndex + probesPerFrame
            val range = startIndex until endIndex
            for (probeIndex in range) {
                clearDepthBuffer()

                // TODO: Re-enable skybox here
//                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, pointLightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", pointLightState.pointLightCount)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
                pointCubeShadowPassProgram.setUniform(
                    "pointLightPositionWorld",
                    currentReflectionProbeRenderState.probePositions[probeIndex]
                )
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
//                pointCubeShadowPassProgram.setUniform("lightIndex", probeIndex)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, directionalLightState)

                if (!isSupported(BindlessTextures)) {
                    bindTexture(
                        8,
                        TextureTarget.TEXTURE_2D,
                        directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                    )
                }
//                bindTexture(8, skyBox.texture)
                omniCamera.updatePosition(currentReflectionProbeRenderState.probePositions[probeIndex])
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    omniCamera.cameras[floatBufferIndex].viewMatrix.get(viewMatrices[floatBufferIndex])
                    omniCamera.cameras[floatBufferIndex].projectionMatrix.get(projectionMatrices[floatBufferIndex])

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
                    for (batch in renderState[defaultBatchesSystem.renderBatchesStatic]) {
                        pointCubeShadowPassProgram.setTextureUniforms(
                            graphicsApi,
                            material = batch.material
                        )
                        entitiesState.geometryBufferStatic.draw(
                            batch
                                .drawElementsIndirectCommand, true, PrimitiveType.Triangles, RenderingMode.Fill
                        )
                    }
                }
                generateMipMaps(cubeMapRenderTarget.textures.first())
            }
        }
    }
}

class ReflectionProbeRenderer(
    private val graphicsApi: GraphicsApi,
    val config: Config,
    val programManager: ProgramManager,
    val cubeMap: CubeMap,
    val pointCubeShadowPassProgram: Program<Uniforms>,
    val indexInCubeMapArray: Int
) {

    var pointLightShadowMapsRenderedInCycle: Long = 0

    val filterConfig = TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR)
    val cubeMapRenderTarget = graphicsApi.RenderTarget(
        frameBuffer = graphicsApi.FrameBuffer(
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    graphicsApi,
                    TextureDescription.CubeMapDescription(
                        TextureDimension(cubeMap.dimension.width, cubeMap.dimension.height),
                        internalFormat = InternalTextureFormat.DEPTH_COMPONENT24,
                        textureFilterConfig = filterConfig,
                        wrapMode = WrapMode.Repeat,
                    )
                )
            )
        ),
        width = cubeMap.dimension.width,
        height = cubeMap.dimension.height,
        textures = listOf(cubeMap),
        name = "ReflectionProbe$indexInCubeMapArray",
        clear = Vector4f(),
    )

}
