package de.hanno.hpengine.graphics.renderer.deferred.extensions

import InternalTextureFormat
import InternalTextureFormat.RGBA16F
import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.feature.BindlessTextures
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.DepthBuffer
import de.hanno.hpengine.graphics.rendertarget.toCubeMaps
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLCubeMap
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.math.OmniCamera
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.spatial.WorldAABBStateHolder
import de.hanno.hpengine.toCount
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import struktgen.api.forIndex
import java.nio.FloatBuffer

class ProbeRenderer(
    private val graphicsApi: GraphicsApi,
    config: Config,
    programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val worldAABBStateHolder: WorldAABBStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
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
    val probeCount = probesPerDimension.x.toCount() * probesPerDimension.y * probesPerDimension.z
    val probeResolution = 16
    val probePositions = mutableListOf<Vector3f>()
    val probePositionsStructBuffer = graphicsApi.onGpu {
        PersistentShaderStorageBuffer(probeCount * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    }
    val probeAmbientCubeValues = graphicsApi.onGpu {
        PersistentShaderStorageBuffer(probeCount * 6 * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    }
    val probeAmbientCubeValuesOld = graphicsApi.onGpu {
        PersistentShaderStorageBuffer(probeCount * 6 * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    }

    init {
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

    val cubeMapRenderTarget = graphicsApi.RenderTarget(
        frameBuffer = graphicsApi.FrameBuffer(
            depthBuffer = DepthBuffer(
                OpenGLCubeMap(
                    graphicsApi,
                    TextureDimension(probeResolution, probeResolution),
                    TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR),
                    InternalTextureFormat.DEPTH_COMPONENT24,
                    WrapMode.Repeat
                )
            )
        ),
        width = probeResolution,
        height = probeResolution,
        textures = listOf(
            ColorAttachmentDefinition("Probes", RGBA16F, TextureFilterConfig(MinFilter.LINEAR_MIPMAP_LINEAR))
        ).toCubeMaps(graphicsApi, probeResolution, probeResolution),
        name = "Probes",
        clear = Vector4f(),
    )

    private val omniCamera = OmniCamera(Vector3f())
    fun renderProbes(renderState: RenderState, probeStartIndex: Int, probesPerFrame: Int) = graphicsApi.run {
        val worldAABBState = renderState[worldAABBStateHolder.worldAABBState]
        if (sceneMin != worldAABBState.min || sceneMax != worldAABBState.max) {
            sceneMin.set(worldAABBState.min)
            sceneMax.set(worldAABBState.max)
            initProbePositions()
        }
        probeAmbientCubeValues.buffer.copyTo(probeAmbientCubeValuesOld.buffer)

        profiled("Probes") {

            graphicsApi.depthMask = true
            graphicsApi.disable(Capability.DEPTH_TEST)
            graphicsApi.disable(Capability.CULL_FACE)
            cubeMapRenderTarget.use(true)
//            gpuContext.clearDepthAndColorBuffer()
            graphicsApi.viewPort(0, 0, probeResolution, probeResolution)
            val entitiesState = renderState[entitiesStateHolder.entitiesState]

            for (probeIndex in probeStartIndex until (probeStartIndex + probesPerFrame)) {
                graphicsApi.clearDepthBuffer()

                val skyBox = textureManager.cubeMap

                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
                pointCubeShadowPassProgram.bindShaderStorageBuffer(2,
                    renderState[pointLightStateHolder.lightState].pointLightBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightCount",
                    renderState[pointLightStateHolder.lightState].pointLightCount)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
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

                if (!graphicsApi.isSupported(BindlessTextures)) {
                    graphicsApi.bindTexture(
                        8,
                        TextureTarget.TEXTURE_2D,
                        directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                    )
                }
                graphicsApi.bindTexture(8, skyBox)

                omniCamera.updatePosition(probePositions[probeIndex])
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    omniCamera.cameras[floatBufferIndex].viewMatrix.get(viewMatrices[floatBufferIndex])
                    omniCamera.cameras[floatBufferIndex].projectionMatrix.get(projectionMatrices[floatBufferIndex])

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
                    for (batch in renderState[defaultBatchesSystem.renderBatchesStatic]) {
                        pointCubeShadowPassProgram.setTextureUniforms(graphicsApi, batch.material.maps)
                        entitiesState.geometryBufferStatic.draw(
                            batch.drawElementsIndirectCommand, true, PrimitiveType.Triangles, RenderingMode.Fill
                        )
                    }
                }
                val cubeMap = cubeMapRenderTarget.textures.first() as OpenGLCubeMap
                graphicsApi.generateMipMaps(cubeMap)
                val floatArrayOf = getTextureSubImage(cubeMap)
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
