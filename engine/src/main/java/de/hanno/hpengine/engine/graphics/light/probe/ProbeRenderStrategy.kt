package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.dimension
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.dimensionHalf
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.extent
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toCubeMaps
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
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
import java.io.File
import java.nio.FloatBuffer


class ProbeRenderStrategy(private val engineContext: EngineContext) {
    val redBuffer = BufferUtils.createFloatBuffer(4).apply { put(0, 1f); rewind(); }
    val blackBuffer = BufferUtils.createFloatBuffer(4).apply { rewind(); }

    private val cubeMapRenderTarget = RenderTarget(
            gpuContext = engineContext.gpuContext,
            frameBuffer = FrameBuffer(
                    gpuContext = engineContext.gpuContext,
                    depthBuffer = DepthBuffer(CubeMap(
                            engineContext.gpuContext,
                            TextureDimension(resolution, resolution),
                            TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
                            GL14.GL_DEPTH_COMPONENT24, GL11.GL_REPEAT)
                    )
            ),
            width = resolution,
            height = resolution,
            textures = listOf(
                    ColorAttachmentDefinition("Diffuse", GL_RGBA16F, TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)),
                    ColorAttachmentDefinition("Radial Distance", GL_RG16F, TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR))
            ).toCubeMaps(engineContext.gpuContext, resolution, resolution),
            name = "Probes"
    )

    private var probeProgram = engineContext.programManager.getProgram(
            FileBasedCodeSource(File("shaders/" + "probe_cubemap_vertex.glsl")),
            FileBasedCodeSource(File("shaders/" + "probe_cube_fragment.glsl")),
            FileBasedCodeSource(File("shaders/" + "probe_cubemap_geometry.glsl")),
            Defines(),
            Uniforms.Empty
    )

    private val colorValueBuffers: Array<out FloatBuffer> = (0..5).map { BufferUtils.createFloatBuffer(4 * 6) }.toTypedArray()
    private val visibilityValueBuffers: Array<out FloatBuffer> = (0..5).map { BufferUtils.createFloatBuffer(resolution * resolution * 4 * 6) }.toTypedArray()

    private val ambientCubeCache = HashMap<Vector3i, AmbientCube>()


    val probeGrid = PersistentMappedBuffer(engineContext.gpuContext, resolution*resolution*resolution*AmbientCube.sizeInBytes)
    var x = 0
    var y = 0

    var z = 0

    fun renderProbes(renderState: RenderState) {
        val gpuContext = engineContext.gpuContext

        profiled("PointLight shadowmaps") {

            gpuContext.depthMask = true
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.CULL_FACE)

            var counter = 0
            while(counter < 1) {

                val cubeMapIndex = getCubeMapIndex(x,y,z)

                gpuContext.enable(GlCap.DEPTH_TEST)
//            gpuContext.cullFace(CullMode.BACK)
//            gpuContext.enable(GlCap.CULL_FACE)
                gpuContext.disable(GlCap.CULL_FACE)
                gpuContext.depthMask = true
                gpuContext.clearColor(0f,0f,0f,0f)
                cubeMapRenderTarget.use(engineContext.gpuContext, true)
                gpuContext.viewPort(0, 0, resolution, resolution)

                val probePosition = Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).sub(Vector3f(dimensionHalf.toFloat())).mul(extent)

                probeProgram.use()
                probeProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                probeProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                probeProgram.setUniform("probePositionWorld", probePosition)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(probePosition)
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    probeProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex]!!)
                    probeProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex]!!)
                    probeProgram.bindShaderStorageBuffer(5, renderState.directionalLightState)
                }

                profiled("Probe entity rendering") {
                    for (e in renderState.renderBatchesStatic) {
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(e, probeProgram)
                    }
                }
                engineContext.textureManager.generateMipMaps(TEXTURE_CUBE_MAP, cubeMapRenderTarget.renderedTexture)

                val ambientCube = ambientCubeCache.computeIfAbsent(Vector3i(x,y,z)) {
                    val dimension = TextureDimension(resolution, resolution)
                    val filterConfig = TextureFilterConfig(MinFilter.LINEAR)
                    val cubeMap = CubeMap.invoke(engineContext.gpuContext, dimension, filterConfig, GL11.GL_RGBA8)
                    val distanceCubeMap = CubeMap(engineContext.gpuContext, dimension, filterConfig, GL_RG16F)
                    AmbientCube(Vector3f(x.toFloat(),y.toFloat(),z.toFloat()), cubeMap, distanceCubeMap, cubeMapIndex)
                }

                glFinish()
                GL43.glCopyImageSubData(cubeMapRenderTarget.renderedTexture, GL13.GL_TEXTURE_CUBE_MAP, mipmapCount-1, 0, 0, 0,
                        ambientCube.cubeMap.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0, 1, 1, 6)
                GL43.glCopyImageSubData(cubeMapRenderTarget.getRenderedTexture(1), GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                        ambientCube.distanceMap.id, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0, resolution, resolution, 6)
//            GL44.glClearTexSubImage(ambientCube.cubeMap.textureId, 0, 0, 0, 0, 1, 1, 6, GL_RGBA, GL11.GL_FLOAT, blackBuffer)

                glFinish()

                counter++
                z++
                if(z == dimension) { z = 0; y++ }
                if(y == dimension) { y = 0; x++ }
                if(x == dimension) { x = 0 }
            }
//        TODO: Fix this with structs
//        probeGrid.put(0, getAmbientCubes())
        }
    }

    fun getAmbientCubes() = ambientCubeCache.values.toList().sorted()

    private fun getCubeMapIndex(x: Int, y: Int, z: Int): Int {
        return (z) * dimension * dimension + (y) * dimension + (x)
    }

    companion object {
        const val dimension = 6
        const val dimensionHalf = dimension/2
        const val extent = 20f

        private const val resolution = 16
        private val mipmapCount = Util.calculateMipMapCount(resolution)
    }
}

class EvaluateProbeRenderExtension(val engineContext: EngineContext): RenderExtension<OpenGl> {

    private val probeRenderStrategy = ProbeRenderStrategy(engineContext)

    val evaluateProbeProgram = engineContext.programManager.getProgram(
            engineContext.config.engineDir.resolve("shaders/passthrough_vertex.glsl").toCodeSource(),
            engineContext.config.engineDir.resolve("shaders/evaluate_probe_fragment.glsl").toCodeSource(),
            Uniforms.Empty
    )

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        probeRenderStrategy.renderProbes(renderState)

    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val deferredRenderingBuffer = engineContext.deferredRenderingBuffer
        deferredRenderingBuffer.lightAccumulationBuffer.use(engineContext.gpuContext, false)

        engineContext.gpuContext.bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.positionMap)
        engineContext.gpuContext.bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
        engineContext.gpuContext.bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        engineContext.gpuContext.bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.motionMap)
        engineContext.gpuContext.bindTexture(7, TEXTURE_2D, deferredRenderingBuffer.visibilityMap)

        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.entity.transform.getTranslation(camTranslation))
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderStrategy.probeGrid)
        evaluateProbeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
        evaluateProbeProgram.setUniform("extent", extent)
        evaluateProbeProgram.setUniform("dimension", dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", dimensionHalf)
        engineContext.gpuContext.fullscreenBuffer.draw()
    }
}
