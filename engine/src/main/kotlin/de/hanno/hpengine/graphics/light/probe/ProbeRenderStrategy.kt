package de.hanno.hpengine.graphics.light.probe

import AmbientCubeImpl.Companion.sizeInBytes
import InternalTextureFormat.*
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy.Companion.dimension
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy.Companion.dimensionHalf
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy.Companion.extent
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.renderer.drawstrategy.*
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLCubeMap
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.draw
import de.hanno.hpengine.math.getCubeViewProjectionMatricesForPosition
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

context(GraphicsApi, GPUProfiler)
class ProbeRenderStrategy(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
) {
    private val redBuffer = BufferUtils.createFloatBuffer(4).apply { put(0, 1f); rewind(); }
    private val blackBuffer = BufferUtils.createFloatBuffer(4).apply { rewind(); }

    private val cubeMapRenderTarget = RenderTarget(
        frameBuffer = OpenGLFrameBuffer(
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    TextureDimension(resolution, resolution),
                    TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
                    DEPTH_COMPONENT24,
                    WrapMode.Repeat
                )
            )
        ),
        width = resolution,
        height = resolution,
        textures = listOf(
            ColorAttachmentDefinition(
                "Diffuse",
                RGBA16F,
                TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)
            ),
            ColorAttachmentDefinition(
                "Radial Distance",
                RG16F,
                TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
            )
        ).toCubeMaps(resolution, resolution),
        name = "Probes",
        clear = Vector4f()
    )

    private var probeProgram = programManager.getProgram(
        config.EngineAsset("shaders/probe_cubemap_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/probe_cube_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/probe_cubemap_geometry.glsl").toCodeSource(),
        Defines(),
        Uniforms.Empty
    )

    private val colorValueBuffers: Array<out FloatBuffer> =
        (0..5).map { BufferUtils.createFloatBuffer(4 * 6) }.toTypedArray()
    private val visibilityValueBuffers: Array<out FloatBuffer> =
        (0..5).map { BufferUtils.createFloatBuffer(resolution * resolution * 4 * 6) }.toTypedArray()

    private val ambientCubeCache = HashMap<Vector3i, AmbientCubeData>()


    val probeGrid = PersistentShaderStorageBuffer(
        capacityInBytes = resolution * resolution * resolution * AmbientCube.sizeInBytes
    )
    var x = 0
    var y = 0

    var z = 0

    fun renderProbes(renderState: RenderState) {
        profiled("PointLight shadowmaps") {

            graphicsApi.depthMask = true
            graphicsApi.enable(Capability.DEPTH_TEST)
            graphicsApi.enable(Capability.CULL_FACE)
            val entitiesState = renderState[entitiesStateHolder.entitiesState]

            var counter = 0
            while (counter < 1) {

                val cubeMapIndex = getCubeMapIndex(x, y, z)

                graphicsApi.enable(Capability.DEPTH_TEST)
//            gpuContext.cullFace(CullMode.BACK)
//            gpuContext.enable(GlCap.CULL_FACE)
                graphicsApi.disable(Capability.CULL_FACE)
                graphicsApi.depthMask = true
                graphicsApi.clearColor(0f, 0f, 0f, 0f)
                cubeMapRenderTarget.use(true)
                graphicsApi.viewPort(0, 0, resolution, resolution)

                val probePosition =
                    Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).sub(Vector3f(dimensionHalf.toFloat())).mul(extent)

                probeProgram.use()
                probeProgram.bindShaderStorageBuffer(1, entitiesState.materialBuffer)
                probeProgram.bindShaderStorageBuffer(3, entitiesState.entitiesBuffer)
                probeProgram.setUniform("probePositionWorld", probePosition)
                val viewProjectionMatrices = getCubeViewProjectionMatricesForPosition(probePosition)
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.first[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.second[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    probeProgram.setUniformAsMatrix4(
                        "viewMatrices[$floatBufferIndex]",
                        viewMatrices[floatBufferIndex]!!
                    )
                    probeProgram.setUniformAsMatrix4(
                        "projectionMatrices[$floatBufferIndex]",
                        projectionMatrices[floatBufferIndex]!!
                    )
                    probeProgram.bindShaderStorageBuffer(5, renderState[directionalLightStateHolder.lightState])
                }

                profiled("Probe entity rendering") {
                    for (e in entitiesState.renderBatchesStatic) {
                        entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                            e.drawElementsIndirectCommand,
                            true,
                            PrimitiveType.Triangles,
                            RenderingMode.Fill
                        )
                    }
                }
                generateMipMaps(cubeMapRenderTarget.textures[0])

                val ambientCube = ambientCubeCache.computeIfAbsent(Vector3i(x, y, z)) {
                    val dimension = TextureDimension(resolution, resolution)
                    val filterConfig = TextureFilterConfig(MinFilter.LINEAR)
                    val cubeMap = OpenGLCubeMap.invoke(
                        dimension,
                        filterConfig,
                        RGBA8
                    )
                    val distanceCubeMap = OpenGLCubeMap(dimension, filterConfig, RG16F)
                    AmbientCubeData(
                        Vector3f(x.toFloat(), y.toFloat(), z.toFloat()),
                        cubeMap,
                        distanceCubeMap,
                        cubeMapIndex
                    )
                }

                finish()
                graphicsApi.copyImageSubData(
                    cubeMapRenderTarget.textures[0], mipmapCount - 1, 0, 0, 0, ambientCube.cubeMap,
                    0, 0, 0, 0, 1, 1, 6
                )
                copyImageSubData(
                    cubeMapRenderTarget.textures[1], 0, 0, 0, 0, ambientCube.distanceMap,
                    0, 0, 0, 0, resolution, resolution, 6
                )
                finish()

                counter++
                z++
                if (z == dimension) {
                    z = 0; y++
                }
                if (y == dimension) {
                    y = 0; x++
                }
                if (x == dimension) {
                    x = 0
                }
            }
//        TODO: Fix this with structs
//        probeGrid.put(0, getAmbientCubes())
        }
    }

    private fun getCubeMapIndex(x: Int, y: Int, z: Int): Int {
        return (z) * dimension * dimension + (y) * dimension + (x)
    }

    companion object {
        const val dimension = 6
        const val dimensionHalf = dimension / 2
        const val extent = 20f

        private const val resolution = 16
        private val mipmapCount = calculateMipMapCount(resolution)
    }
}

context(GraphicsApi, GPUProfiler)
class EvaluateProbeRenderExtension(
    private val graphicsApi: GraphicsApi,
    private val programManager: ProgramManager,
    textureManager: OpenGLTextureManager,
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): DeferredRenderExtension {

    private val fullscreenBuffer = QuadVertexBuffer()

    private val probeRenderStrategy = ProbeRenderStrategy(
        config,
        graphicsApi,
        programManager,
        textureManager,
        directionalLightStateHolder,
        entitiesStateHolder,
    )

    val evaluateProbeProgram = programManager.getProgram(
        config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/evaluate_probe_fragment.glsl").toCodeSource(),
        Uniforms.Empty,
 Defines()
    )

    override fun renderFirstPass(
        renderState: RenderState
    ) {
        probeRenderStrategy.renderProbes(renderState)

    }

    override fun renderSecondPassFullScreen(renderState: RenderState) {

        val deferredRenderingBuffer = deferredRenderingBuffer
        deferredRenderingBuffer.lightAccumulationBuffer.use(false)

        graphicsApi.bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.positionMap)
        graphicsApi.bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
        graphicsApi.bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        graphicsApi.bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.motionMap)
        graphicsApi.bindTexture(7, TEXTURE_2D, deferredRenderingBuffer.visibilityMap)

        val camera = renderState[primaryCameraStateHolder.camera]
        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform(
            "eyePosition",
            camera.transform.getTranslation(camTranslation)
        )
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderStrategy.probeGrid)
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniform("extent", extent)
        evaluateProbeProgram.setUniform("dimension", dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", dimensionHalf)
        fullscreenBuffer.draw()
    }
}
