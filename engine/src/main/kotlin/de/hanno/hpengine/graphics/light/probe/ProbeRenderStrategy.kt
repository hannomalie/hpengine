package de.hanno.hpengine.graphics.light.probe

import AmbientCubeImpl.Companion.sizeInBytes
import InternalTextureFormat.*
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.rendertarget.toCubeMaps
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLCubeMap
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import de.hanno.hpengine.math.getCubeViewProjectionMatricesForPosition
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

class ProbeRenderStrategy(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    programManager: ProgramManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
) {
    private val redBuffer = BufferUtils.createFloatBuffer(4).apply { put(0, 1f); rewind(); }
    private val blackBuffer = BufferUtils.createFloatBuffer(4).apply { rewind(); }

    private val cubeMapRenderTarget = graphicsApi.RenderTarget(
        frameBuffer = OpenGLFrameBuffer(
            graphicsApi,
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    graphicsApi,
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
        ).toCubeMaps(graphicsApi, resolution, resolution),
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


    val probeGrid = graphicsApi.PersistentShaderStorageBuffer(
        capacityInBytes = resolution * resolution * resolution * AmbientCube.sizeInBytes
    )
    var x = 0
    var y = 0

    var z = 0

    fun renderProbes(renderState: RenderState) = graphicsApi.run {
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
                        graphicsApi,
                        dimension,
                        filterConfig,
                        RGBA8
                    )
                    val distanceCubeMap = OpenGLCubeMap(graphicsApi, dimension, filterConfig, RG16F)
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

