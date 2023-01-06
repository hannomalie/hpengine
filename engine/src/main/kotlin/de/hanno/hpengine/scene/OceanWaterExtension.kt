package de.hanno.hpengine.scene

import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import InternalTextureFormat.RGBA32F
import de.hanno.hpengine.artemis.model.MaterialComponent
import de.hanno.hpengine.artemis.OceanSurfaceComponent
import de.hanno.hpengine.artemis.OceanWaterComponent
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.OpenGLContext.Companion.RED_BUFFER
import de.hanno.hpengine.graphics.state.RenderStateContext
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
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.lwjgl.BufferUtils
import struktgen.api.forIndex
import kotlin.math.ln
import kotlin.math.max

private val defaultTextureFilterConfig = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)

context(GraphicsApi)
class OceanWaterRenderSystem(
    private val config: Config,
    private val renderStateContext: RenderStateContext,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : RenderSystem {
    private val N = 512

    private val dimension = TextureDimension2D(N, N)
    private val random0 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_0.png", srgba = false, directory = engineDir)
    private val random1 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_1.png", srgba = false, directory = engineDir)
    private val random2 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_2.png", srgba = false, directory = engineDir)
    private val random3 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_3.png", srgba = false, directory = engineDir)

    private fun createRandomTexture(): Texture2D = listOf(ColorAttachmentDefinition("Random",
        RGBA32F
    )).toTextures(N, N).first().apply {
        onGpu {
            val buffer = BufferUtils.createByteBuffer(Float.SIZE_BYTES * dimension.width * dimension.height * 4)
            val floatBuffer = buffer.asFloatBuffer()
            val random = java.util.Random()
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
    }

    //    TODO: These ones don't do the same thing as code below, find out why
//    val displacementMap = allocateTexture(
//        RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val displacementMap = listOf(ColorAttachmentDefinition("Displacement", RGBA32F)).toTextures(N, N).first()
//    val displacementMapX = allocateTexture(RGBA32F, dimension)
//    val displacementMapY = allocateTexture(RGBA32F, dimension)
//    val displacementMapZ = allocateTexture(RGBA32F, dimension)
    val displacementMapX = listOf(ColorAttachmentDefinition("DisplacementX", RGBA32F)).toTextures(N, N).first()
    val displacementMapY = listOf(ColorAttachmentDefinition("DisplacementY", RGBA32F)).toTextures(N, N).first()
    val displacementMapZ = listOf(ColorAttachmentDefinition("DisplacementZ", RGBA32F)).toTextures(N, N).first()
//    val normalMap = allocateTexture(
//        RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val normalMap = listOf(ColorAttachmentDefinition("Normals", RGBA32F)).toTextures(N, N).first()
//    val albedoMap = allocateTexture(
//        RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val albedoMap = listOf(ColorAttachmentDefinition("Color", RGBA32F)).toTextures(N, N).first()
//    val roughnessMap = allocateTexture(
//        RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val roughnessMap = listOf(ColorAttachmentDefinition("Roughness", RGBA32F)).toTextures(N, N).first()

    val debugMap = listOf(ColorAttachmentDefinition("Color", RGBA32F)).toTextures(N, N).first().apply {
        clearTexImage(id, Format.RGBA, 0, TexelComponentType.Float, RED_BUFFER)
    }
    private val pingPongMapX = listOf(ColorAttachmentDefinition("PingPongX", RGBA32F)).toTextures(N, N).first()
    private val pingPongMapY = listOf(ColorAttachmentDefinition("PingPongY", RGBA32F)).toTextures(N, N).first()
    private val pingPongMapZ = listOf(ColorAttachmentDefinition("PingPongZ", RGBA32F)).toTextures(N, N).first()

    private val tildeHktDyMap = listOf(ColorAttachmentDefinition("tildeHktDyMap", RGBA32F)).toTextures(N, N).first()
    private val tildeHktDxMap = listOf(ColorAttachmentDefinition("tildeHktDxMap", RGBA32F)).toTextures(N, N).first()
    private val tildeHktDzMap = listOf(ColorAttachmentDefinition("tildeHktDzMap", RGBA32F)).toTextures(N, N).first()
    data class TildeMapHelper(val tildeMap: Texture2D, val displacementMap: Texture2D, val pingPongMap: Texture2D)
    val tildeMapHelpers = listOf(
        TildeMapHelper(tildeHktDyMap, displacementMapY, pingPongMapY),
    )
    val tildeMapHelpersChoppy = listOf(
        TildeMapHelper(tildeHktDxMap, displacementMapX, pingPongMapX),
        TildeMapHelper(tildeHktDyMap, displacementMapY, pingPongMapY),
        TildeMapHelper(tildeHktDzMap, displacementMapZ, pingPongMapZ),
    )

    private val h0kMap = listOf(ColorAttachmentDefinition("h0kMap", RGBA32F)).toTextures(N, N).first()
    private val h0MinuskMap = listOf(ColorAttachmentDefinition("h0MinuskMap", RGBA32F)).toTextures(N, N).first()
    private val log2N = (ln(N.toFloat()) / ln(2.0f)).toInt()
    private val twiddleIndicesMap = listOf(ColorAttachmentDefinition("h0kMap", RGBA32F)).toTextures(log2N, N).first()
    private val bitReversedIndices = PersistentShaderStorageBuffer(N * IntStrukt.sizeInBytes).typed(IntStrukt.type).apply {
        initBitReversedIndices(N).forEachIndexed { index, value ->
            typedBuffer.forIndex(index) { it.value = value }
        }
    }

    private val h0kShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/h0k_compute.glsl").toCodeSource())
    private val hktShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/hkt_compute.glsl").toCodeSource())
    private val twiddleIndicesShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/twiddle_indices_compute.glsl").toCodeSource())
    private val butterflyShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/butterfly_compute.glsl").toCodeSource())
    private val inversionShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/inversion_compute.glsl").toCodeSource())
    private val mergeDisplacementMapsShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/merge_displacement_maps_compute.glsl").toCodeSource())

    init {
        onGpu {
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
        textureManager.registerTextureForDebugOutput("[Ocean Water] Albedo", albedoMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] NormalGenerated", normalMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Displacement", displacementMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] DisplacementX", displacementMapX)
        textureManager.registerTextureForDebugOutput("[Ocean Water] DisplacementY", displacementMapY)
        textureManager.registerTextureForDebugOutput("[Ocean Water] DisplacementZ", displacementMapZ)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Roughness", roughnessMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Debug", debugMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Random0", random0)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Random1", random1)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Random2", random2)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Random3", random3)
        textureManager.registerTextureForDebugOutput("[Ocean Water] h0k", h0kMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] h0MinusK", h0MinuskMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] ~hktdx", tildeHktDxMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] ~hktdy", tildeHktDyMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] ~hktdz", tildeHktDzMap)
        textureManager.registerTextureForDebugOutput("[Ocean Water] Twiddle Indices", twiddleIndicesMap)
    }
    override fun extract(renderState: RenderState) {
        // TODO: Implement extraction here
        val oceanWaterEntities = listOf<Triple<OceanWaterComponent, MaterialComponent, OceanSurfaceComponent>>()
        if(oceanWaterEntities.isNotEmpty()) {
            val components = oceanWaterEntities.first()
            val materialComponent = components.second
            val oceanSurfaceComponent = components.third
            if(!oceanSurfaceComponent.mapsSet) {
                materialComponent.material.let {
                    it.maps.putIfAbsent(Material.MAP.DIFFUSE, albedoMap)
                    it.maps.putIfAbsent(Material.MAP.DISPLACEMENT, displacementMap)
                    it.maps.putIfAbsent(Material.MAP.NORMAL, normalMap)
                }
                oceanSurfaceComponent.mapsSet = true
            }
        }
    }

    private var seconds = 0.0f
    override fun render(renderState: RenderState) {
        val components = listOf<OceanWaterComponent>()
        if(components.isEmpty()) return
        val oceanWaterComponent = components.first()
        if(oceanWaterComponent.windspeed == 0f) { return }

        seconds += oceanWaterComponent.timeFactor * max(0.001f, renderState.deltaSeconds)

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
        hktShader.setUniform("t", seconds)
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
}
