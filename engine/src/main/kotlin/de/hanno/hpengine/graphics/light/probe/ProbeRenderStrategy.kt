package de.hanno.hpengine.graphics.light.probe

import AmbientCubeImpl.Companion.sizeInBytes

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy.Companion.dimension
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy.Companion.dimensionHalf
import de.hanno.hpengine.graphics.light.probe.ProbeRenderStrategy.Companion.extent
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.Capability
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_CUBE_MAP
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.*
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTargetImpl
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLCubeMap
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.calculateMipMapCount
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.math.getCubeViewProjectionMatricesForPosition
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30.GL_RG16F
import org.lwjgl.opengl.GL30.GL_RGBA16F
import org.lwjgl.opengl.GL30.glFinish
import org.lwjgl.opengl.GL43
import java.nio.FloatBuffer

context(GpuContext)
class ProbeRenderStrategy(
    val config: Config,
    val gpuContext: GpuContext,
    programManager: ProgramManager,
    val textureManager: OpenGLTextureManager
) {
    val redBuffer = BufferUtils.createFloatBuffer(4).apply { put(0, 1f); rewind(); }
    val blackBuffer = BufferUtils.createFloatBuffer(4).apply { rewind(); }

    private val cubeMapRenderTarget = RenderTargetImpl(
        frameBuffer = OpenGLFrameBuffer(
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    TextureDimension(resolution, resolution),
                    TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
                    GL14.GL_DEPTH_COMPONENT24,
                    GL11.GL_REPEAT
                )
            )
        ),
        width = resolution,
        height = resolution,
        textures = listOf(
            ColorAttachmentDefinition(
                "Diffuse",
                GL_RGBA16F,
                TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)
            ),
            ColorAttachmentDefinition(
                "Radial Distance",
                GL_RG16F,
                TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
            )
        ).toCubeMaps(resolution, resolution),
        name = "Probes"
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


    val probeGrid = PersistentMappedBuffer(
        GL43.GL_SHADER_STORAGE_BUFFER, capacityInBytes = resolution * resolution * resolution * AmbientCube.sizeInBytes
    )
    var x = 0
    var y = 0

    var z = 0

    fun renderProbes(renderState: RenderState) {
        val gpuContext = gpuContext

        profiled("PointLight shadowmaps") {

            gpuContext.depthMask = true
            gpuContext.enable(Capability.DEPTH_TEST)
            gpuContext.enable(Capability.CULL_FACE)

            var counter = 0
            while (counter < 1) {

                val cubeMapIndex = getCubeMapIndex(x, y, z)

                gpuContext.enable(Capability.DEPTH_TEST)
//            gpuContext.cullFace(CullMode.BACK)
//            gpuContext.enable(GlCap.CULL_FACE)
                gpuContext.disable(Capability.CULL_FACE)
                gpuContext.depthMask = true
                gpuContext.clearColor(0f, 0f, 0f, 0f)
                cubeMapRenderTarget.use(true)
                gpuContext.viewPort(0, 0, resolution, resolution)

                val probePosition =
                    Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).sub(Vector3f(dimensionHalf.toFloat())).mul(extent)

                probeProgram.use()
                probeProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                probeProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
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
                    probeProgram.bindShaderStorageBuffer(5, renderState.directionalLightState)
                }

                profiled("Probe entity rendering") {
                    for (e in renderState.renderBatchesStatic) {
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(
                            e.drawElementsIndirectCommand,
                            true,
                            PrimitiveType.Triangles,
                            RenderingMode.Faces
                        )
                    }
                }
                textureManager.generateMipMaps(
                    TEXTURE_CUBE_MAP,
                    cubeMapRenderTarget.renderedTexture
                )

                val ambientCube = ambientCubeCache.computeIfAbsent(Vector3i(x, y, z)) {
                    val dimension = TextureDimension(resolution, resolution)
                    val filterConfig = TextureFilterConfig(MinFilter.LINEAR)
                    val cubeMap = OpenGLCubeMap.invoke(
                        dimension,
                        filterConfig,
                        GL11.GL_RGBA8
                    )
                    val distanceCubeMap = OpenGLCubeMap(dimension, filterConfig, GL_RG16F)
                    AmbientCubeData(
                        Vector3f(x.toFloat(), y.toFloat(), z.toFloat()),
                        cubeMap,
                        distanceCubeMap,
                        cubeMapIndex
                    )
                }

                glFinish()
                GL43.glCopyImageSubData(
                    cubeMapRenderTarget.renderedTexture, GL13.GL_TEXTURE_CUBE_MAP, mipmapCount - 1, 0, 0, 0,
                    ambientCube.cubeMap.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0, 1, 1, 6
                )
                GL43.glCopyImageSubData(
                    cubeMapRenderTarget.getRenderedTexture(1), GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                    ambientCube.distanceMap.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0, resolution, resolution, 6
                )
//            GL44.glClearTexSubImage(ambientCube.cubeMap.textureId, 0, 0, 0, 0, 1, 1, 6, GL_RGBA, GL11.GL_FLOAT, blackBuffer)

                glFinish()

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

context(GpuContext)
class EvaluateProbeRenderExtension(
    val gpuContext: GpuContext,
    val programManager: ProgramManager,
    textureManager: OpenGLTextureManager,
    val config: Config,
    val deferredRenderingBuffer: DeferredRenderingBuffer
): DeferredRenderExtension {

    private val probeRenderStrategy = ProbeRenderStrategy(
        config,
        gpuContext,
        programManager,
        textureManager
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

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val deferredRenderingBuffer = deferredRenderingBuffer
        deferredRenderingBuffer.lightAccumulationBuffer.use(false)

        gpuContext.bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.positionMap)
        gpuContext.bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
        gpuContext.bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        gpuContext.bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.motionMap)
        gpuContext.bindTexture(7, TEXTURE_2D, deferredRenderingBuffer.visibilityMap)

        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform(
            "eyePosition",
            renderState.camera.transform.getTranslation(camTranslation)
        )
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderStrategy.probeGrid)
        evaluateProbeProgram.setUniform("screenWidth", config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", config.height.toFloat())
        evaluateProbeProgram.setUniform("extent", extent)
        evaluateProbeProgram.setUniform("dimension", dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", dimensionHalf)
        gpuContext.fullscreenBuffer.draw()
    }
}
