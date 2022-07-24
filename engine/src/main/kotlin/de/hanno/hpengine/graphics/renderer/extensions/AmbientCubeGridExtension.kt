package de.hanno.hpengine.graphics.renderer.extensions

import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.texture.CubeMap
import de.hanno.hpengine.model.texture.TextureDimension
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.model.texture.mipmapCount
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.toCubeMaps
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.buffers.copyTo
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GL45
import java.nio.FloatBuffer

class ProbeRenderer(
    val gpuContext: GpuContext<OpenGl>,
    config: Config,
    programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager
) {
    val sceneMin = Vector3f(-100f, -100f, -100f)
    val sceneMax = Vector3f(100f, 100f, 100f)
    val probesPerDimension = Vector3i(10, 6, 10)
    val probesPerDimensionFloat =
        Vector3f(probesPerDimension.x.toFloat(), probesPerDimension.y.toFloat(), probesPerDimension.z.toFloat())
    val probesPerDimensionHalfFloat = Vector3f(probesPerDimensionFloat).div(2f)
    val probesPerDimensionHalf = Vector3i(
        probesPerDimensionHalfFloat.x.toInt(),
        probesPerDimensionHalfFloat.y.toInt(),
        probesPerDimensionHalfFloat.z.toInt()
    )
    val probeDimensions: Vector3f
        get() = Vector3f(sceneMax).sub(sceneMin).div(probesPerDimensionFloat)
    val probeDimensionsHalf: Vector3f
        get() = probeDimensions.mul(0.5f)
    val probeCount = probesPerDimension.x * probesPerDimension.y * probesPerDimension.z
    val probeResolution = 16
    val probePositions = mutableListOf<Vector3f>()
    val probePositionsStructBuffer = gpuContext.window.invoke {
        PersistentMappedBuffer(probeCount * Vector4fStrukt.sizeInBytes, gpuContext).typed(Vector4fStrukt.type)
    }
    val probeAmbientCubeValues = gpuContext.window.invoke {
        PersistentMappedBuffer(probeCount * 6 * Vector4fStrukt.sizeInBytes, gpuContext).typed(Vector4fStrukt.type)
    }
    val probeAmbientCubeValuesOld = gpuContext.window.invoke {
        PersistentMappedBuffer(probeCount * 6 * Vector4fStrukt.sizeInBytes, gpuContext).typed(Vector4fStrukt.type)
    }

    init {
        gpuContext.window.invoke {
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
        }
        initProbePositions()
    }

    private fun initProbePositions() {
        val sceneCenter = Vector3f(sceneMin).add(Vector3f(sceneMax).sub(sceneMin).mul(0.5f))
        val offset = probeDimensionsHalf.add(sceneCenter)
        probePositions.clear()
        for (x in -probesPerDimensionHalf.x until probesPerDimensionHalf.x) {
            for (y in -probesPerDimensionHalf.y until probesPerDimensionHalf.y) {
                for (z in -probesPerDimensionHalf.z until probesPerDimensionHalf.z) {
                    val resultingPosition =
                        Vector3f(x * probeDimensions.x, y * probeDimensions.y, z * probeDimensions.z)
                    probePositions.add(resultingPosition.add(offset))
                }
            }
        }
        probePositions.mapIndexed { i, position ->
            probePositionsStructBuffer.typedBuffer.forIndex(i) { it.set(position) }
        }
    }


    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram = programManager.getProgram(
        config.EngineAsset("shaders/pointlight_shadow_cubemap_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/environmentprobe_cube_fragment.glsl").toCodeSource(),
        config.EngineAsset("shaders/pointlight_shadow_cubemap_geometry.glsl").toCodeSource(),
        Defines(),
        Uniforms.Empty
    )

    val cubeMapRenderTarget = de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget(
        gpuContext = gpuContext,
        frameBuffer = FrameBuffer(
            gpuContext = gpuContext,
            depthBuffer = DepthBuffer(
                CubeMap(
                    gpuContext,
                    TextureDimension(probeResolution, probeResolution),
                    TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                    GL14.GL_DEPTH_COMPONENT24, GL_REPEAT
                )
            )
        ),
        width = probeResolution,
        height = probeResolution,
        textures = listOf(
            ColorAttachmentDefinition("Probes", GL30.GL_RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))
        ).toCubeMaps(gpuContext, probeResolution, probeResolution),
        name = "Probes"
    )

    fun renderProbes(renderState: RenderState, probeStartIndex: Int, probesPerFrame: Int) {
        if (sceneMin != renderState.sceneMin || sceneMax != renderState.sceneMax) {
            sceneMin.set(renderState.sceneMin)
            sceneMax.set(renderState.sceneMax)
            initProbePositions()
        }
        probeAmbientCubeValues.buffer.copyTo(probeAmbientCubeValuesOld.buffer)

        val gpuContext = gpuContext

        profiled("Probes") {

            gpuContext.depthMask = true
            gpuContext.disable(GlCap.DEPTH_TEST)
            gpuContext.disable(GlCap.CULL_FACE)
            cubeMapRenderTarget.use(gpuContext, true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, probeResolution, probeResolution)

            for (probeIndex in probeStartIndex until (probeStartIndex + probesPerFrame)) {
                gpuContext.clearDepthBuffer()

                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", probePositions[probeIndex])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform(
                    "lightIndex",
                    0
                ) // We don't use layered rendering with cubmap arrays anymore
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(5, probeAmbientCubeValuesOld)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, renderState.directionalLightState)
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.setUniform("sceneMin", sceneMin)
                pointCubeShadowPassProgram.setUniform("probesPerDimension", probesPerDimensionFloat)

                if (!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(
                        8,
                        GlTextureTarget.TEXTURE_2D,
                        renderState.directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                    )
                }
                gpuContext.bindTexture(8, skyBox)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(probePositions[probeIndex])
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

                profiled("Probe entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(batch.material.maps)
                        renderState.vertexIndexBufferStatic.indexBuffer.draw(
                            batch,
                            pointCubeShadowPassProgram
                        )
                    }
                }
                val cubeMap = cubeMapRenderTarget.textures.first() as CubeMap
                textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMap.id)
                val floatArrayOf = (0 until 6 * 4).map { 0f }.toFloatArray()
                GL45.glGetTextureSubImage(
                    cubeMap.id, cubeMap.mipmapCount - 1,
                    0, 0, 0, 1, 1, 6, GL_RGBA, GL_FLOAT, floatArrayOf
                )
                val ambientCubeValues = floatArrayOf.toList().windowed(4, 4) {
                    Vector3f(it[0], it[1], it[2])
                }
                val baseProbeIndex = 6 * probeIndex
                for (faceIndex in 0 until 6) {
                    probeAmbientCubeValues.typedBuffer.forIndex(baseProbeIndex + faceIndex) { it.set(ambientCubeValues[faceIndex]) }
                }
            }
        }
    }
}
