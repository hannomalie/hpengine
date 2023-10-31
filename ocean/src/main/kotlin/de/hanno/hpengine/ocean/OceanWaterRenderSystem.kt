package de.hanno.hpengine.ocean

import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import InternalTextureFormat.RGBA32F
import com.artemis.BaseSystem
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.OpenGLContext.Companion.RED_BUFFER
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureDimension2D
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.forIndex
import java.util.*
import kotlin.math.ln

@Single(binds = [OceanWaterRenderSystem::class, RenderSystem::class, BaseSystem::class])
class OceanWaterRenderSystem(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val oceanWaterSystem: OceanWaterSystem,
) : BaseSystem(), RenderSystem {
    private val N = 512

    private val dimension = TextureDimension2D(N, N)
    private val random0 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_0.png", srgba = false, directory = engineDir)
    private val random1 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_1.png", srgba = false, directory = engineDir)
    private val random2 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_2.png", srgba = false, directory = engineDir)
    private val random3 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_3.png", srgba = false, directory = engineDir)
    val waterNormalMap = textureManager.getTexture("assets/textures/water_normal_map.jpg", srgba = false, directory = config.engineDir)

    private fun createRandomTexture(): Texture2D = listOf(ColorAttachmentDefinition("Random",
        RGBA32F
    )).toTextures(graphicsApi, N, N).first().apply {
        fillWithRandomData(graphicsApi)
    }

    private fun Texture2D.fillWithRandomData(graphicsApi: GraphicsApi) = graphicsApi.run {
        val buffer = BufferUtils.createByteBuffer(Float.SIZE_BYTES * dimension.width * dimension.height * 4)
        val floatBuffer = buffer.asFloatBuffer()
        val random = Random()
        for (x in 0 until dimension.width) {
            for (y in 0 until dimension.height) {
                floatBuffer.put(x + y * dimension.width, random.nextGaussian().toFloat())
    //                    buffer.put(x + y * dimension.width, Random.nextFloat())
            }
        }
        floatBuffer.flip()
        texSubImage2D(
            0,
            0,
            0,
            dimension.width,
            dimension.height,
            Format.RED,
            TexelComponentType.Float,
            buffer
        )
    }

    //    TODO: These ones don't do the same thing as code below, find out why
//    val displacementMap = allocateTexture(
//        RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val displacementMap = listOf(ColorAttachmentDefinition("Displacement", RGBA32F)).toTextures(graphicsApi, N, N).first()
    val displacementMapX = listOf(ColorAttachmentDefinition("DisplacementX", RGBA32F)).toTextures(graphicsApi, N, N).first()
    val displacementMapY = listOf(ColorAttachmentDefinition("DisplacementY", RGBA32F)).toTextures(graphicsApi, N, N).first()
    val displacementMapZ = listOf(ColorAttachmentDefinition("DisplacementZ", RGBA32F)).toTextures(graphicsApi, N, N).first()
    val normalMap = listOf(ColorAttachmentDefinition("Normals", RGBA32F)).toTextures(graphicsApi, N, N).first()
    val albedoMap = listOf(ColorAttachmentDefinition("Color", RGBA32F)).toTextures(graphicsApi, N, N).first()
    val roughnessMap = listOf(ColorAttachmentDefinition("Roughness", RGBA32F)).toTextures(graphicsApi, N, N).first()

    val debugMap = listOf(ColorAttachmentDefinition("Color", RGBA32F)).toTextures(graphicsApi, N, N).first().apply {
        graphicsApi.clearTexImage(id, Format.RGBA, 0, TexelComponentType.Float, RED_BUFFER)
    }
    private val pingPongMapX = listOf(ColorAttachmentDefinition("PingPongX", RGBA32F)).toTextures(graphicsApi, N, N).first()
    private val pingPongMapY = listOf(ColorAttachmentDefinition("PingPongY", RGBA32F)).toTextures(graphicsApi, N, N).first()
    private val pingPongMapZ = listOf(ColorAttachmentDefinition("PingPongZ", RGBA32F)).toTextures(graphicsApi, N, N).first()

    private val tildeHktDyMap = listOf(ColorAttachmentDefinition("tildeHktDyMap", RGBA32F)).toTextures(graphicsApi, N, N).first()
    private val tildeHktDxMap = listOf(ColorAttachmentDefinition("tildeHktDxMap", RGBA32F)).toTextures(graphicsApi, N, N).first()
    private val tildeHktDzMap = listOf(ColorAttachmentDefinition("tildeHktDzMap", RGBA32F)).toTextures(graphicsApi, N, N).first()
    data class TildeMapHelper(val tildeMap: Texture2D, val displacementMap: Texture2D, val pingPongMap: Texture2D)
    val tildeMapHelpers = listOf(
        TildeMapHelper(tildeHktDyMap, displacementMapY, pingPongMapY),
    )
    val tildeMapHelpersChoppy = listOf(
        TildeMapHelper(tildeHktDxMap, displacementMapX, pingPongMapX),
        TildeMapHelper(tildeHktDyMap, displacementMapY, pingPongMapY),
        TildeMapHelper(tildeHktDzMap, displacementMapZ, pingPongMapZ),
    )

    private val h0kMap = listOf(ColorAttachmentDefinition("h0kMap", RGBA32F)).toTextures(graphicsApi, N, N).first()
    private val h0MinuskMap = listOf(ColorAttachmentDefinition("h0MinuskMap", RGBA32F)).toTextures(graphicsApi, N, N).first()
    private val log2N = (ln(N.toFloat()) / ln(2.0f)).toInt()
    private val twiddleIndicesMap = listOf(ColorAttachmentDefinition("h0kMap", RGBA32F)).toTextures(graphicsApi, log2N, N).first()
    private val bitReversedIndices = graphicsApi.PersistentShaderStorageBuffer(N * IntStrukt.sizeInBytes).typed(IntStrukt.type).apply {
        initBitReversedIndices(N).forEachIndexed { index, value ->
            typedBuffer.forIndex(index) { it.value = value }
        }
    }

    private val h0kShader = programManager.getComputeProgram(config.GameAsset("shaders/ocean/h0k_compute.glsl").toCodeSource())
    private val hktShader = programManager.getComputeProgram(config.GameAsset("shaders/ocean/hkt_compute.glsl").toCodeSource())
    private val twiddleIndicesShader = programManager.getComputeProgram(config.GameAsset("shaders/ocean/twiddle_indices_compute.glsl").toCodeSource())
    private val butterflyShader = programManager.getComputeProgram(config.GameAsset("shaders/ocean/butterfly_compute.glsl").toCodeSource())
    private val inversionShader = programManager.getComputeProgram(config.GameAsset("shaders/ocean/inversion_compute.glsl").toCodeSource())
    private val mergeDisplacementMapsShader = programManager.getComputeProgram(config.GameAsset("shaders/ocean/merge_displacement_maps_compute.glsl").toCodeSource())

    init {
        graphicsApi.onGpu {
            twiddleIndicesShader.use()
            twiddleIndicesShader.setUniform("N", N)
            bindImageTexture(
                0,
                twiddleIndicesMap,
                0,
                false,
                0,
                Access.ReadOnly,
            )
            twiddleIndicesShader.bindShaderStorageBuffer(1, bitReversedIndices)
            twiddleIndicesShader.dispatchCompute(log2N,N/16,1)
        }
        textureManager.apply {
            registerTextureForDebugOutput("[Ocean Water] Albedo", albedoMap)
            registerTextureForDebugOutput("[Ocean Water] NormalGenerated", normalMap)
            registerTextureForDebugOutput("[Ocean Water] Displacement", displacementMap)
            registerTextureForDebugOutput("[Ocean Water] DisplacementX", displacementMapX)
            registerTextureForDebugOutput("[Ocean Water] DisplacementY", displacementMapY)
            registerTextureForDebugOutput("[Ocean Water] DisplacementZ", displacementMapZ)
            registerTextureForDebugOutput("[Ocean Water] Roughness", roughnessMap)
            registerTextureForDebugOutput("[Ocean Water] Debug", debugMap)
            registerTextureForDebugOutput("[Ocean Water] Random0", random0)
            registerTextureForDebugOutput("[Ocean Water] Random1", random1)
            registerTextureForDebugOutput("[Ocean Water] Random2", random2)
            registerTextureForDebugOutput("[Ocean Water] Random3", random3)
            registerTextureForDebugOutput("[Ocean Water] h0k", h0kMap)
            registerTextureForDebugOutput("[Ocean Water] h0MinusK", h0MinuskMap)
            registerTextureForDebugOutput("[Ocean Water] ~hktdx", tildeHktDxMap)
            registerTextureForDebugOutput("[Ocean Water] ~hktdy", tildeHktDyMap)
            registerTextureForDebugOutput("[Ocean Water] ~hktdz", tildeHktDzMap)
            registerTextureForDebugOutput("[Ocean Water] Twiddle Indices", twiddleIndicesMap)
        }
    }

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        val oceanWaterState = renderState[oceanWaterSystem.state]
        val components = oceanWaterState.oceanWaterComponents
        if(components.isEmpty()) return
        val oceanWaterComponent = components.first()
        if(oceanWaterComponent.windspeed == 0f) { return }

        if(oceanWaterComponent.initRandomNess) {
            random0.fillWithRandomData(graphicsApi)
            random1.fillWithRandomData(graphicsApi)
            random2.fillWithRandomData(graphicsApi)
            random3.fillWithRandomData(graphicsApi)
            oceanWaterComponent.initRandomNess = false // TODO: This doesn't work when this component gets copied properly
        }

        h0kShader.use()
        bindImageTexture(0, h0kMap, 0, false, 0, Access.WriteOnly)
        bindImageTexture(1, h0MinuskMap, 0, false, 0, Access.WriteOnly)
        bindTexture(2, random0)
        bindTexture(3, random1)
        bindTexture(4, random2)
        bindTexture(5, random3)
        h0kShader.setUniform("N", N)
        h0kShader.setUniform("L", oceanWaterComponent.L)
        h0kShader.setUniform("amplitude", oceanWaterComponent.amplitude)
        h0kShader.setUniform("windspeed", oceanWaterComponent.windspeed)
        h0kShader.setUniform("direction", oceanWaterComponent.direction)
        h0kShader.dispatchCompute(N/16,N/16,1)

        hktShader.use()
        hktShader.setUniform("t", oceanWaterState.seconds)
        hktShader.setUniform("N", N)
        hktShader.setUniform("L", oceanWaterComponent.L)
        bindImageTexture(0, tildeHktDyMap, 0, false, 0, Access.WriteOnly)
        bindImageTexture(1, tildeHktDxMap, 0, false, 0, Access.WriteOnly)
        bindImageTexture(2, tildeHktDzMap, 0, false, 0, Access.WriteOnly)
        bindImageTexture(3, h0kMap, 0, false, 0, Access.ReadOnly)
        bindImageTexture(4, h0MinuskMap, 0, false, 0, Access.ReadOnly)
        hktShader.dispatchCompute(N/16,N/16,1)

        for(helper in if(oceanWaterComponent.choppy) tildeMapHelpersChoppy else tildeMapHelpers) {
            var pingpong = 0
            butterflyShader.use()
            val pingPongMap = helper.pingPongMap
            val tildeMap = helper.tildeMap
            val displacementMap = helper.displacementMap
            bindImageTexture(0, twiddleIndicesMap, 0, false, 0, Access.ReadOnly)
            bindImageTexture(1, tildeMap, 0, false, 0, Access.ReadWrite)
            bindImageTexture(2, pingPongMap, 0, false, 0, Access.ReadWrite)

            for (stage in 0 until log2N) {
                butterflyShader.setUniform("pingpong", pingpong)
                butterflyShader.setUniform("direction", 0)
                butterflyShader.setUniform("stage", stage)
                butterflyShader.dispatchCompute(N/16,N/16,1)
                finish()
                pingpong++
                pingpong %= 2
            }

            for (stage in 0 until log2N) {
                butterflyShader.setUniform("pingpong", pingpong)
                butterflyShader.setUniform("direction", 1)
                butterflyShader.setUniform("stage", stage)
                butterflyShader.dispatchCompute(N/16,N/16,1)
                finish()
                pingpong++
                pingpong %= 2
            }

            inversionShader.use()
            bindImageTexture(0, displacementMap.id, 0, false, 0, Access.WriteOnly, displacementMap.internalFormat)
            bindImageTexture(1, tildeMap.id, 0, false, 0, Access.ReadWrite, tildeMap.internalFormat)
            bindImageTexture(2, pingPongMap.id, 0, false, 0, Access.ReadWrite, pingPongMap.internalFormat)
            inversionShader.setUniform("pingpong", pingpong)
            inversionShader.setUniform("N", N)
            inversionShader.dispatchCompute(N/16,N/16,1)
            finish()
        }

        val camera = renderState[primaryCameraStateHolder.camera]

        mergeDisplacementMapsShader.use()
        mergeDisplacementMapsShader.setUniform("diffuseColor", oceanWaterComponent.albedo)
        mergeDisplacementMapsShader.setUniform("N", N)
        mergeDisplacementMapsShader.setUniform("L", oceanWaterComponent.L)
        mergeDisplacementMapsShader.setUniform("choppiness", oceanWaterComponent.choppiness)
        mergeDisplacementMapsShader.setUniform("waveHeight", oceanWaterComponent.waveHeight)
        mergeDisplacementMapsShader.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        bindImageTexture(0, displacementMap.id, 0, false, 0, Access.WriteOnly, displacementMap.internalFormat)
        bindTexture(1, displacementMapX)
        bindTexture(2, displacementMapY)
        bindTexture(3, displacementMapZ)
        bindImageTexture(4, normalMap.id, 0, false, 0, Access.WriteOnly, normalMap.internalFormat)
        bindImageTexture(5, albedoMap.id, 0, false, 0, Access.WriteOnly, albedoMap.internalFormat)
        bindImageTexture(6, roughnessMap.id, 0, false, 0, Access.WriteOnly, roughnessMap.internalFormat)
        bindTexture(7, random3)
        mergeDisplacementMapsShader.dispatchCompute(N/16,N/16,1)
    }

    private fun initBitReversedIndices(N: Int): IntArray {
        val bitReversedIndices = IntArray(N)
        val bits = (ln(N.toFloat()) / ln(2.0)).toInt()
        for (i in 0 until N) {
            var x = Integer.reverse(i)
            x = Integer.rotateLeft(x, bits)
            bitReversedIndices[i] = x
        }
        return bitReversedIndices
    }

    override fun processSystem() { }
}
