package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.OpenGl
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
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeRenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.util.Util
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_RG16F
import org.lwjgl.opengl.GL30.GL_RGBA16F
import org.lwjgl.opengl.GL30.glFinish
import org.lwjgl.opengl.GL43
import java.io.File
import java.nio.FloatBuffer


class ProbeRenderStrategy(private val engine: ManagerContext<OpenGl>) {
    val redBuffer = BufferUtils.createFloatBuffer(4).apply { put(0, 1f); rewind(); }
    val blackBuffer = BufferUtils.createFloatBuffer(4).apply { rewind(); }

    val cubeMapRenderTarget = CubeMapRenderTarget(engine, CubeRenderTargetBuilder(engine)
            .setWidth(resolution)
            .setHeight(resolution)
            .add(ColorAttachmentDefinition("Diffuse")
                    .setInternalFormat(GL_RGBA16F)
                    .setTextureFilter(TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)))
            .add(ColorAttachmentDefinition("Radial Distance")
                    .setInternalFormat(GL_RG16F)
                    .setTextureFilter(TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR))))

    private var probeProgram: Program = engine.programManager.getProgram(getShaderSource(File(Shader.directory + "probe_cubemap_vertex.glsl")), getShaderSource(File(Shader.directory + "probe_cube_fragment.glsl")), getShaderSource(File(Shader.directory + "probe_cubemap_geometry.glsl")))

    private val colorValueBuffers: Array<out FloatBuffer> = (0..5).map { BufferUtils.createFloatBuffer(4 * 6) }.toTypedArray()
    private val visibilityValueBuffers: Array<out FloatBuffer> = (0..5).map { BufferUtils.createFloatBuffer(resolution * resolution * 4 * 6) }.toTypedArray()

    private val ambientCubeCache = HashMap<Vector3i, AmbientCube>()


    val probeGrid = PersistentMappedBuffer(engine.gpuContext, resolution*resolution*resolution*AmbientCube.sizeInBytes)
    var x = 0
    var y = 0

    var z = 0

    fun renderProbes(renderState: RenderState) {
        val gpuContext = engine.gpuContext

        profiled("PointLight shadowmaps") {

            gpuContext.depthMask(true)
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.CULL_FACE)

            var counter = 0
            while(counter < 1) {

                val cubeMapIndex = getCubeMapIndex(x,y,z)

                gpuContext.enable(GlCap.DEPTH_TEST)
//            gpuContext.cullFace(CullMode.BACK)
//            gpuContext.enable(GlCap.CULL_FACE)
                gpuContext.disable(GlCap.CULL_FACE)
                gpuContext.depthMask(true)
                gpuContext.clearColor(0f,0f,0f,0f)
                cubeMapRenderTarget.use(engine.gpuContext as GpuContext<OpenGl>, true) // TODO: Remove cast
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
                    probeProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex])
                    probeProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex])
                    probeProgram.bindShaderStorageBuffer(5, renderState.directionalLightState)
                }

                profiled("Probe entity rendering") {
                    for (e in renderState.renderBatchesStatic) {
                        draw(renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, probeProgram, !e.isVisible, true)
                    }
                }
                engine.textureManager.generateMipMaps(TEXTURE_CUBE_MAP, cubeMapRenderTarget.renderedTexture)

                val ambientCube = ambientCubeCache.computeIfAbsent(Vector3i(x,y,z)) {
                    val dimension = TextureDimension(resolution, resolution)
                    val filterConfig = TextureFilterConfig(MinFilter.LINEAR)
                    val cubeMap = CubeMap.invoke(engine.gpuContext, dimension, filterConfig, GL11.GL_RGBA8)
                    val distanceCubeMap = CubeMap(engine.gpuContext, dimension, filterConfig, GL30.GL_RG16F)
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

class EvaluateProbeRenderExtension(val engine: ManagerContext<OpenGl>): RenderExtension<OpenGl> {

    private val probeRenderStrategy = ProbeRenderStrategy(engine)

    val evaluateProbeProgram = engine.programManager.getProgramFromFileNames("passthrough_vertex.glsl", "evaluate_probe_fragment.glsl", Defines())

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        probeRenderStrategy.renderProbes(renderState)

    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        val deferredRenderingBuffer = engine.deferredRenderingBuffer
        deferredRenderingBuffer.lightAccumulationBuffer.use(engine.gpuContext, false)

        engine.gpuContext.bindTexture(0, TEXTURE_2D, deferredRenderingBuffer.positionMap)
        engine.gpuContext.bindTexture(1, TEXTURE_2D, deferredRenderingBuffer.normalMap)
        engine.gpuContext.bindTexture(2, TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        engine.gpuContext.bindTexture(3, TEXTURE_2D, deferredRenderingBuffer.motionMap)
        engine.gpuContext.bindTexture(7, TEXTURE_2D, deferredRenderingBuffer.visibilityMap)

        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.entity.getTranslation(camTranslation))
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderStrategy.probeGrid)
        evaluateProbeProgram.setUniform("screenWidth", engine.config.width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", engine.config.height.toFloat())
        evaluateProbeProgram.setUniform("extent", extent)
        evaluateProbeProgram.setUniform("dimension", dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", dimensionHalf)
        engine.gpuContext.fullscreenBuffer.draw()
    }
}
