package de.hanno.hpengine.graphics.renderer.extensions

import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.World
import com.artemis.annotations.All
import de.hanno.hpengine.Transform
import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder

import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.addAABBLines
import de.hanno.hpengine.graphics.renderer.constants.*
import de.hanno.hpengine.graphics.renderer.drawLines
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.drawstrategy.*
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.shader.LinesProgramUniforms
import de.hanno.hpengine.graphics.state.PointLightStateHolder
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.math.getCubeViewProjectionMatricesForPosition
import de.hanno.hpengine.ressources.StringBasedCodeSource
import de.hanno.hpengine.stopwatch.GPUProfiler
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import struktgen.api.forIndex
import java.nio.FloatBuffer

class ReflectionProbe(val extents: Vector3f = Vector3f(100f)) : com.artemis.Component() {
    val transformComponent = TransformComponent()
    val halfExtents: Vector3f
        get() = Vector3f(extents).mul(0.5f)
}

@All(ReflectionProbe::class)
class ReflectionProbeManager(val config: Config) : BaseEntitySystem() {
    override fun processSystem() {}
    override fun inserted(entityId: Int) {
        config.debug.reRenderProbes = true
    }
}

context(GraphicsApi, RenderStateContext)
class ReflectionProbesStateHolder {
    val probesState = renderState.registerState {
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

context(GraphicsApi)
class ReflectionProbeRenderState {
    var reRenderProbesInCycle = 0L
    var probeCount: Int = 0
    val probeMinMaxStructBuffer = onGpu {
        PersistentShaderStorageBuffer(Vector4fStrukt.sizeInBytes).typed(Vector4fStrukt.type)
    }
    val probePositions = mutableListOf<Vector3f>()
}

context(GraphicsApi, RenderStateContext, GPUProfiler)
class ReflectionProbeRenderExtension(
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val textureManager: TextureManager,
    private val programManager: ProgramManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val reflectionProbesStateHolder: ReflectionProbesStateHolder,
) : DeferredRenderExtension {
    private val fullscreenBuffer = QuadVertexBuffer()
    override val renderPriority = 3000

    private val reflectionProbeRenderState = renderState.registerState {
        ReflectionProbeRenderState()
    }
    private val linesProgram = programManager.run {
        val uniforms = LinesProgramUniforms()
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
        probeResolution,
        probeResolution,
        maxReflectionProbes
    ).first()

    private var renderedInCycle: Long = -1
    val probeRenderers = (0 until cubeMapArray.dimension.depth).map {
        ReflectionProbeRenderer(config, programManager, createView(cubeMapArray, it), it)
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
        val componentCount = components.size
        val targetState = renderState[reflectionProbeRenderState]

        targetState.reRenderProbesInCycle = if (config.debug.reRenderProbes) renderState.cycle else 0L
        targetState.probeCount = componentCount
        val probeMinMaxStructBuffer = targetState.probeMinMaxStructBuffer
        probeMinMaxStructBuffer.ensureCapacityInBytes(Vector4fStrukt.sizeInBytes * componentCount * 2)
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

    private val lineVertices = PersistentShaderStorageBuffer(100 * Vector4fStrukt.sizeInBytes).typed(Vector4fStrukt.type)
    override fun renderEditor(renderState: RenderState) {

        if (config.debug.isEditorOverlay) {
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
                programManager,
                linesProgram,
                lineVertices,
                linePoints,
                viewMatrix = camera.viewMatrixAsBuffer,
                projectionMatrix = camera.projectionMatrixAsBuffer,
                color = Vector3f(1f, 0f, 0f)
            )
        }

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

    override fun renderSecondPassFullScreen(renderState: RenderState) {

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
        bindTexture(
            6,
            TextureTarget.TEXTURE_2D,
            directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId })
        bindTexture(7, TextureTarget.TEXTURE_CUBE_MAP_ARRAY, cubeMapArray.id)
        renderState[pointLightStateHolder.lightState].pointLightShadowMapStrategy.bindTextures()

        val camera = renderState[primaryCameraStateHolder.camera]
        evaluateProbeProgram.setUniform("eyePosition", camera.getPosition())
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.setUniform("time", renderState.time.toInt())

        evaluateProbeProgram.setUniform("probeCount", currentReflectionProbeRenderState.probeCount)
        evaluateProbeProgram.bindShaderStorageBuffer(4, currentReflectionProbeRenderState.probeMinMaxStructBuffer)
        fullscreenBuffer.draw()

        gBuffer.use(false)

    }

    fun ReflectionProbeRenderer.renderProbes(renderState: RenderState, startIndex: Int, probesPerFrame: Int) {
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

                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, pointLightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", pointLightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, entitiesState.entitiesBuffer)
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
                bindTexture(8, skyBox)
                val viewProjectionMatrices =
                    getCubeViewProjectionMatricesForPosition(currentReflectionProbeRenderState.probePositions[probeIndex])
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
                    for (batch in entitiesState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(batch.material.maps)
                        entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
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

context(GraphicsApi)
class ReflectionProbeRenderer(
    val config: Config,
    val programManager: ProgramManager,
    val cubeMap: CubeMap,
    val indexInCubeMapArray: Int
) {

    var pointLightShadowMapsRenderedInCycle: Long = 0
    var pointCubeShadowPassProgram = programManager.getProgram(
        config.EngineAsset("shaders/pointlight_shadow_cubemap_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/reflectionprobe_cube_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/pointlight_shadow_cubemap_geometry.glsl").toCodeSource(),
        Defines(),
        Uniforms.Empty
    )

    val cubeMapRenderTarget = RenderTarget(
        frameBuffer = FrameBuffer(
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    TextureDimension(cubeMap.dimension.width, cubeMap.dimension.height),
                    TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                    InternalTextureFormat.DEPTH_COMPONENT24,
                    WrapMode.Repeat
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
