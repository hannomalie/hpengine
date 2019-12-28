package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.engine.scene.AABB
import de.hanno.hpengine.util.Util
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.io.File
import java.nio.FloatBuffer

class EnvironmentProbeExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {

    val probeRenderer = ProbeRenderer(engineContext)

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        probeRenderer.renderProbes(renderState)
    }
}

class ProbeRenderer(private val engine: EngineContext<OpenGl>) {
    val sceneBounds = AABB(Vector3f(0f, 0f, 0f), 50f)
    val probesPerDimension = 4
    val probesPerDimensionHalf = probesPerDimension / 2
    val probeDimensions = sceneBounds.sizeX / probesPerDimension
    val probeCount = probesPerDimension*probesPerDimension*probesPerDimension
    val probeResolution = 16
    val probePositions = mutableListOf<Vector3f>()
    init {
        for(x in -probesPerDimensionHalf .. probesPerDimensionHalf) {
            for(y in -probesPerDimensionHalf .. probesPerDimensionHalf) {
                for(z in -probesPerDimensionHalf .. probesPerDimensionHalf) {
                    if(x == 0 || y == 0 || z == 0) continue
                    probePositions.add(Vector3f(x*probeDimensions, y*probeDimensions, z*probeDimensions))
                }
            }
        }
    }


    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram: Program = engine.programManager.getProgram(
            getShaderSource(File(Shader.directory + "pointlight_shadow_cubemap_vertex.glsl")),
            getShaderSource(File(Shader.directory + "environmentprobe_cube_fragment.glsl")),
            getShaderSource(File(Shader.directory + "pointlight_shadow_cubemap_geometry.glsl")))
    val cubeMapArray = CubeMapArray(
            gpuContext = engine.gpuContext,
            dimension = TextureDimension(probeResolution, probeResolution, probeCount),
            filterConfig = TextureFilterConfig(MinFilter.NEAREST),
            internalFormat = GL30.GL_RGBA16F,
            wrapMode = GL11.GL_REPEAT
    )
    val probesArrayCube = cubeMapArray.id
    var cubemapArrayRenderTarget = CubeMapArrayRenderTarget(
            engine.gpuContext,
            cubeMapArray.dimension.width,
            cubeMapArray.dimension.height,
            Vector4f(0f, 0f, 0f, 0f),
            "Probes",
            cubeMapArray
    ).apply {
        engine.gpuContext.register(this)
    }

     fun bindTextures() {
        engine.gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, probesArrayCube)
    }

     fun renderProbes(renderState: RenderState) {
        val gpuContext = engine.gpuContext
//        val needToRedraw = pointLightShadowMapsRenderedInCycle < renderState.entitiesState.entityMovedInCycle || pointLightShadowMapsRenderedInCycle < renderState.pointLightMovedInCycle
//        if (!needToRedraw) {
//            return
//        }

        profiled("Probes") {

            gpuContext.depthMask(true)
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.CULL_FACE)
            cubemapArrayRenderTarget.use(engine.gpuContext, true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, AreaLightSystem.AREALIGHT_SHADOWMAP_RESOLUTION, AreaLightSystem.AREALIGHT_SHADOWMAP_RESOLUTION)

            for (i in 0 until probeCount) {

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", probePositions[i])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform("lightIndex", i)
                engine.gpuContext.bindTexture(8, engine.materialManager.skyboxMaterial.materialInfo.maps[SimpleMaterial.MAP.ENVIRONMENT] ?: engine.textureManager.cubeMap)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(probePositions[i])
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex])
                    pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex])
                }

                profiled("PointLight shadowmap entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(engine.gpuContext, batch.materialInfo.maps)
                        draw(renderState.vertexIndexBufferStatic.vertexBuffer,
                                renderState.vertexIndexBufferStatic.indexBuffer,
                                batch, pointCubeShadowPassProgram, !batch.isVisible, false)
                    }
                }
            }
        }
//        pointLightShadowMapsRenderedInCycle = renderState.cycle
    }
}