package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.dimension
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.dimensionHalf
import de.hanno.hpengine.engine.graphics.light.probe.ProbeRenderStrategy.Companion.extent
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayBasedCubeRenderTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.Shader.ShaderSourceFactory.getShaderSource
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL30
import java.io.File
import java.nio.FloatBuffer


class ProbeRenderStrategy(private val engine: Engine) {
//    var pointLightShadowMapsRenderedInCycle: Long = 0
    val cubemapArrayRenderTarget: CubeMapArrayBasedCubeRenderTarget
    private var probeProgram: Program? = null
    var probeDepthMapsArrayCube: Int = 0
    private val resolution = 16
    private val mipmapCount = Util.calculateMipMapCount(resolution)
    val cubeMapArray = CubeMapArray(engine.gpuContext, dimension*dimension*dimension, GL11.GL_LINEAR_MIPMAP_LINEAR, GL30.GL_RGBA16F, resolution)

    init {
        this.probeProgram = engine.programManager.getProgram(getShaderSource(File(Shader.getDirectory() + "probe_cubemap_vertex.glsl")), getShaderSource(File(Shader.getDirectory() + "probe_cubemap_geometry.glsl")), getShaderSource(File(Shader.getDirectory() + "probe_cube_fragment.glsl")), Defines())

        probeDepthMapsArrayCube = cubeMapArray.textureID
        this.cubemapArrayRenderTarget = CubeMapArrayBasedCubeRenderTarget(engine, cubeMapArray)
    }


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
            gpuContext.cullFace(CullMode.BACK)
            gpuContext.enable(GlCap.CULL_FACE)
            gpuContext.depthMask(true)
            gpuContext.clearColor(0f,0f,0f,0f)
            cubemapArrayRenderTarget.use(false)
            gpuContext.clearDepthBuffer()
            gpuContext.clearCubeMapInCubeMapArray(cubemapArrayRenderTarget.cubeMapArray.textureID, GL_RGBA, resolution, resolution, cubeMapIndex)
//                    gpuContext.clearCubeMap(cubemapArrayRenderTarget.depthBufferTexture, GL_DEPTH_COMPONENT24)
            gpuContext.viewPort(0, 0, resolution, resolution)

            val probePosition = Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).sub(Vector3f(dimensionHalf.toFloat())).mul(extent)

            probeProgram!!.use()
            probeProgram!!.bindShaderStorageBuffer(1, renderState.materialBuffer)
            probeProgram!!.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
            probeProgram!!.setUniform("probePositionWorld", probePosition)
            probeProgram!!.setUniform("lightIndex", cubeMapIndex)
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
                probeProgram!!.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex])
                probeProgram!!.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex])
            }

            GPUProfiler.start("Probe entity rendering")
            for (e in renderState.renderBatchesStatic) {
                DrawStrategy.draw(gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, probeProgram, !e.isVisible)
            }
            engine.textureManager.generateMipMapsCubeMap(cubemapArrayRenderTarget.cubeMapViews[cubeMapIndex])
            GPUProfiler.end()

            counter++
            z++
            if(z == dimension) { z = 0; y++ }
            if(y == dimension) { y = 0; x++ }
            if(x == dimension) { x = 0 }
        }

//        for (x in 0 until dimension) {
//            for (y in 0 until dimension) {
//                for (z in 0 until dimension) {
//
//                    gpuContext.enable(GlCap.DEPTH_TEST)
//                    gpuContext.depthMask(true)
//                    gpuContext.clearColor(0f,0f,0f,0f)
//                    cubemapArrayRenderTarget.use(false)
//                    gpuContext.clearDepthBuffer()
//                    gpuContext.clearCubeMapInCubeMapArray(cubemapArrayRenderTarget.cubeMapArray.textureID, GL_RGBA, resolution, resolution, cubeMapIndex)
////                    gpuContext.clearCubeMap(cubemapArrayRenderTarget.depthBufferTexture, GL_DEPTH_COMPONENT24)
//                    gpuContext.viewPort(0, 0, resolution, resolution)
//
//                    val probePosition = Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).sub(Vector3f(dimensionHalf.toFloat())).mul(extent.toFloat())
//
//                    probeProgram!!.use()
//                    probeProgram!!.bindShaderStorageBuffer(1, renderState.materialBuffer)
//                    probeProgram!!.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
//                    probeProgram!!.setUniform("probePositionWorld", probePosition)
//                    probeProgram!!.setUniform("lightIndex", cubeMapIndex)
//                    val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(probePosition)
//                    val viewMatrices = arrayOfNulls<FloatBuffer>(6)
//                    val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
//                    for (floatBufferIndex in 0..5) {
//                        viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
//                        projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
//
//                        viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
//                        viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])
//
//                        viewMatrices[floatBufferIndex]!!.rewind()
//                        projectionMatrices[floatBufferIndex]!!.rewind()
//                        probeProgram!!.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex])
//                        probeProgram!!.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex])
//                    }
//
//                    GPUProfiler.start("Probe entity rendering")
//                    for (e in renderState.renderBatchesStatic) {
//                        DrawStrategy.draw(gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, probeProgram, !e.isVisible)
//                    }
//                    engine.textureManager.generateMipMapsCubeMap(cubemapArrayRenderTarget.cubeMapViews[cubeMapIndex])
//                    GPUProfiler.end()
//
//                    cubeMapIndex++
//                }
//            }
//
//        }
        GPUProfiler.end()
    }
    private fun getCubeMapIndex(x: Int, y: Int, z: Int): Int {
        return (z) * dimension * dimension + (y) * dimension + (x)
    }

    companion object {
        const val dimension = 6
        const val dimensionHalf = dimension/2
        const val extent = 5f
    }
}

class EvaluateProbeRenderExtension(val engine: Engine): RenderExtension {

    val evaluateProbeProgram = engine.programManager.getProgramFromFileNames("passthrough_vertex.glsl", "evaluate_probe_fragment.glsl", Defines())

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        engine.renderer.gBuffer.lightAccumulationBuffer.use(false)

        engine.gpuContext.bindTexture(0, TEXTURE_2D, engine.renderer.gBuffer.positionMap)
        engine.gpuContext.bindTexture(1, TEXTURE_2D, engine.renderer.gBuffer.normalMap)
        engine.gpuContext.bindTexture(2, TEXTURE_2D, engine.renderer.gBuffer.colorReflectivenessMap)
        engine.gpuContext.bindTexture(3, TEXTURE_2D, engine.renderer.gBuffer.motionMap)
        engine.gpuContext.bindTexture(7, TEXTURE_2D, engine.renderer.gBuffer.visibilityMap)
        engine.gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, engine.getScene().probeSystem.strategy.cubeMapArray.textureID)

        evaluateProbeProgram.use()
        val camTranslation = Vector3f()
        evaluateProbeProgram.setUniform("eyePosition", renderState.camera.entity.getTranslation(camTranslation))
        evaluateProbeProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.entity.viewMatrixAsBuffer)
        evaluateProbeProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        evaluateProbeProgram.bindShaderStorageBuffer(0, engine.renderer.gBuffer.storageBuffer)
        evaluateProbeProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
        evaluateProbeProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
        evaluateProbeProgram.setUniform("extent", extent)
        evaluateProbeProgram.setUniform("dimension", dimension)
        evaluateProbeProgram.setUniform("dimensionHalf", dimensionHalf)
        engine.gpuContext.fullscreenBuffer.draw()
    }
}