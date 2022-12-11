package de.hanno.hpengine.graphics.renderer.extensions

import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import de.hanno.hpengine.artemis.EntitiesStateHolder

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.Capability
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
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
import de.hanno.hpengine.graphics.texture.OpenGLCubeMap
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.mipmapCount
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.toCubeMaps
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.state.PointLightStateHolder
import de.hanno.hpengine.math.getCubeViewProjectionMatricesForPosition
import de.hanno.hpengine.scene.WorldAABBStateHolder
import de.hanno.hpengine.stopwatch.GPUProfiler
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GL45
import java.nio.FloatBuffer

context(GpuContext, GPUProfiler)
class ProbeRenderer(
    private val gpuContext: GpuContext,
    config: Config,
    programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val worldAABBStateHolder: WorldAABBStateHolder,
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
        PersistentMappedBuffer(probeCount * Vector4fStrukt.sizeInBytes).typed(Vector4fStrukt.type)
    }
    val probeAmbientCubeValues = gpuContext.window.invoke {
        PersistentMappedBuffer(probeCount * 6 * Vector4fStrukt.sizeInBytes).typed(Vector4fStrukt.type)
    }
    val probeAmbientCubeValuesOld = gpuContext.window.invoke {
        PersistentMappedBuffer(probeCount * 6 * Vector4fStrukt.sizeInBytes).typed(Vector4fStrukt.type)
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
        frameBuffer = OpenGLFrameBuffer(
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    TextureDimension(probeResolution, probeResolution),
                    TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                    GL14.GL_DEPTH_COMPONENT24,
                    GL_REPEAT
                )
            )
        ),
        width = probeResolution,
        height = probeResolution,
        textures = listOf(
            ColorAttachmentDefinition("Probes", GL30.GL_RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))
        ).toCubeMaps(probeResolution, probeResolution),
        name = "Probes"
    )

    fun renderProbes(renderState: RenderState, probeStartIndex: Int, probesPerFrame: Int) {
        val worldAABBState = renderState[worldAABBStateHolder.worldAABBState]
        if (sceneMin != worldAABBState.sceneMin || sceneMax != worldAABBState.sceneMax) {
            sceneMin.set(worldAABBState.sceneMin)
            sceneMax.set(worldAABBState.sceneMax)
            initProbePositions()
        }
        probeAmbientCubeValues.buffer.copyTo(probeAmbientCubeValuesOld.buffer)

        val gpuContext = gpuContext

        profiled("Probes") {

            gpuContext.depthMask = true
            gpuContext.disable(Capability.DEPTH_TEST)
            gpuContext.disable(Capability.CULL_FACE)
            cubeMapRenderTarget.use(true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, probeResolution, probeResolution)
            val entitiesState = renderState[entitiesStateHolder.entitiesState]

            for (probeIndex in probeStartIndex until (probeStartIndex + probesPerFrame)) {
                gpuContext.clearDepthBuffer()

                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2,
                    renderState[pointLightStateHolder.lightState].pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount",
                    renderState[pointLightStateHolder.lightState].pointLights.size)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, entitiesState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", probePositions[probeIndex])
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform(
                    "lightIndex",
                    0
                ) // We don't use layered rendering with cubmap arrays anymore
                val directionalLightState = renderState[directionalLightStateHolder.lightState]
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(5, probeAmbientCubeValuesOld)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(6, directionalLightState)
                pointCubeShadowPassProgram.setUniform("probeDimensions", probeDimensions)
                pointCubeShadowPassProgram.setUniform("sceneMin", sceneMin)
                pointCubeShadowPassProgram.setUniform("probesPerDimension", probesPerDimensionFloat)

                if (!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(
                        8,
                        TextureTarget.TEXTURE_2D,
                        directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                    )
                }
                gpuContext.bindTexture(8, skyBox)
                val viewProjectionMatrices = getCubeViewProjectionMatricesForPosition(probePositions[probeIndex])
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
                    for (batch in entitiesState.renderBatchesStatic) {
                        pointCubeShadowPassProgram.setTextureUniforms(batch.material.maps)
                        entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                            batch.drawElementsIndirectCommand, true, PrimitiveType.Triangles, RenderingMode.Faces
                        )
                    }
                }
                val cubeMap = cubeMapRenderTarget.textures.first() as OpenGLCubeMap
                textureManager.generateMipMaps(TextureTarget.TEXTURE_CUBE_MAP, cubeMap.id)
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
