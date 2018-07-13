package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.dimension
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.dimensionHalf
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.extent
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeRenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.Shader.ShaderSourceFactory.getShaderSource
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.model.texture.DynamicCubeMap
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43
import java.io.File
import java.nio.FloatBuffer


class ProbeRenderStrategy(private val engine: Engine) {
    val redBuffer = BufferUtils.createFloatBuffer(4).apply { put(0, 1f); rewind(); }
    val blackBuffer = BufferUtils.createFloatBuffer(4).apply { rewind(); }

    val cubeMapRenderTarget = CubeRenderTarget(engine, CubeRenderTargetBuilder(engine)
            .setWidth(resolution)
            .setHeight(resolution)
            .add(ColorAttachmentDefinition("Diffuse")
                    .setInternalFormat(GL_RGBA16F)
                    .setTextureFilter(GL_LINEAR_MIPMAP_LINEAR))
            .add(ColorAttachmentDefinition("Radial Distance")
                    .setInternalFormat(GL_RG16F)
                    .setTextureFilter(GL_LINEAR)))

    private var probeProgram: Program = engine.programManager.getProgram(getShaderSource(File(Shader.getDirectory() + "probe_cubemap_vertex.glsl")), getShaderSource(File(Shader.getDirectory() + "probe_cubemap_geometry.glsl")), getShaderSource(File(Shader.getDirectory() + "probe_cube_fragment.glsl")), Defines())

    private val colorValueBuffers: Array<out FloatBuffer> = (0..5).map { BufferUtils.createFloatBuffer(4 * 6) }.toTypedArray()
    private val visibilityValueBuffers: Array<out FloatBuffer> = (0..5).map { BufferUtils.createFloatBuffer(resolution * resolution * 4 * 6) }.toTypedArray()

    private val ambientCubeCache = HashMap<Vector3i, AmbientCube>()


    val probeGrid = PersistentMappedBuffer<AmbientCube>(engine.gpuContext, resolution*resolution*resolution*AmbientCube.sizeInBytes)
    var x = 0
    var y = 0

    var z = 0

    fun renderProbes(renderState: RenderState) {
        val gpuContext = engine.gpuContext

        GPUProfiler.start("PointLight shadowmaps")
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
            cubeMapRenderTarget.use(true)
            gpuContext.viewPort(0, 0, resolution, resolution)

            val probePosition = Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).sub(Vector3f(dimensionHalf.toFloat())).mul(extent)

            probeProgram.use()
            probeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
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
                probeProgram.setUniform("directionalLightDirection", renderState.directionalLightState.directionalLightDirection)
                probeProgram.setUniform("directionalLightColor", renderState.directionalLightState.directionalLightColor)
            }

            GPUProfiler.start("Probe entity rendering")
            for (e in renderState.renderBatchesStatic) {
                DrawStrategy.draw(gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, probeProgram, !e.isVisible)
            }
            GPUProfiler.end()
            engine.textureManager.generateMipMapsCubeMap(cubeMapRenderTarget.renderedTexture)

            val ambientCube = ambientCubeCache.computeIfAbsent(Vector3i(x,y,z), {
                val cubeMap: CubeMap = DynamicCubeMap(engine, 1, GL30.GL_RGBA16F, GL11.GL_FLOAT, GL_LINEAR, GL_RGBA, colorValueBuffers)
                val distanceCubeMap = DynamicCubeMap(engine, resolution, GL30.GL_RG16F, GL11.GL_FLOAT, GL_LINEAR, GL_RG, visibilityValueBuffers)
                AmbientCube(Vector3f(x.toFloat(),y.toFloat(),z.toFloat()), cubeMap, distanceCubeMap, cubeMapIndex)
            })

            glFinish()
//            floatBuffer.rewind()
//            floatBuffer.put(0, 0f)
//            floatBuffer.put(1, 0f)
//            floatBuffer.put(2, 0f)
//            floatBuffer.put(3, 0f)
//            glActiveTexture(0)
//            gpuContext.bindTexture(0, TEXTURE_CUBE_MAP, ambientCube.cubeMap.textureId)
//            GL45.glGetTextureSubImage(cubeMapRenderTarget.renderedTexture, 4, 0, 0, 0, 1, 1, 6, GL_RGBA, GL11.GL_FLOAT, floatBuffer)
//            Util.printFloatBuffer(floatBuffer)

            glFinish()
            GL43.glCopyImageSubData(cubeMapRenderTarget.renderedTexture, GL13.GL_TEXTURE_CUBE_MAP, mipmapCount-1, 0, 0, 0,
                    ambientCube.cubeMap.textureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0, 1, 1, 6)
            GL43.glCopyImageSubData(cubeMapRenderTarget.getRenderedTexture(1), GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                    ambientCube.distanceMap.textureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0, resolution, resolution, 6)
//            GL44.glClearTexSubImage(ambientCube.cubeMap.textureId, 0, 0, 0, 0, 1, 1, 6, GL_RGBA, GL11.GL_FLOAT, blackBuffer)

            glFinish()

            counter++
            z++
            if(z == dimension) { z = 0; y++ }
            if(y == dimension) { y = 0; x++ }
            if(x == dimension) { x = 0 }
        }
        probeGrid.put(0, getAmbientCubes())

        GPUProfiler.end()
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

class EvaluateProbeRenderExtension(val engine: Engine): RenderExtension {

    private val probeRenderStrategy = ProbeRenderStrategy(engine)

    val evaluateProbeProgram = engine.programManager.getProgramFromFileNames("passthrough_vertex.glsl", "evaluate_probe_fragment.glsl", Defines())

    override fun renderFirstPass(engine: Engine, gpuContext: GpuContext, firstPassResult: FirstPassResult, renderState: RenderState) {
        probeRenderStrategy.renderProbes(renderState)

    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        engine.renderer.gBuffer.lightAccumulationBuffer.use(false)

        engine.gpuContext.bindTexture(0, TEXTURE_2D, engine.renderer.gBuffer.positionMap)
        engine.gpuContext.bindTexture(1, TEXTURE_2D, engine.renderer.gBuffer.normalMap)
        engine.gpuContext.bindTexture(2, TEXTURE_2D, engine.renderer.gBuffer.colorReflectivenessMap)
        engine.gpuContext.bindTexture(3, TEXTURE_2D, engine.renderer.gBuffer.motionMap)
        engine.gpuContext.bindTexture(7, TEXTURE_2D, engine.renderer.gBuffer.visibilityMap)

        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.entity.getTranslation(camTranslation))
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.entity.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, engine.renderer.gBuffer.storageBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(4, probeRenderStrategy.probeGrid)
        evaluateProbeProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
        evaluateProbeProgram.setUniform("extent", extent)
        evaluateProbeProgram.setUniform("dimension", dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", dimensionHalf)
        engine.gpuContext.fullscreenBuffer.draw()
    }
}
