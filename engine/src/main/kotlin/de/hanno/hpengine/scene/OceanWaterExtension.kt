package de.hanno.hpengine.scene

import IntStruktImpl.Companion.sizeInBytes
import IntStruktImpl.Companion.type
import com.artemis.World
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.artemis.MaterialComponent
import de.hanno.hpengine.artemis.OceanSurfaceComponent
import de.hanno.hpengine.artemis.OceanWaterComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.OpenGLContext.Companion.RED_BUFFER
import de.hanno.hpengine.graphics.RenderStateManager
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.TextureDimension2D
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.model.texture.UploadState
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import kotlin.math.ln
import kotlin.math.max

private val defaultTextureFilterConfig = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)

class OceanWaterRenderSystem(
    val config: Config,
    val gpuContext: GpuContext,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager,
) : RenderSystem {
    override lateinit var artemisWorld: World
    private val N = 512

    private val dimension = TextureDimension2D(N, N)
    private val random0 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_0.png", srgba = false, directory = engineDir)
    private val random1 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_1.png", srgba = false, directory = engineDir)
    private val random2 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_2.png", srgba = false, directory = engineDir)
    private val random3 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_3.png", srgba = false, directory = engineDir)

    private fun createRandomTexture(): Texture2D = listOf(ColorAttachmentDefinition("Random", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first().apply {
        gpuContext.invoke {
            val buffer = BufferUtils.createFloatBuffer(dimension.width * dimension.height * 4)
            val random = java.util.Random()
            for (x in 0 until dimension.width) {
                for (y in 0 until dimension.height) {
                    buffer.put(x + y * dimension.width, random.nextGaussian().toFloat())
//                    buffer.put(x + y * dimension.width, Random.nextFloat())
                }
            }
            buffer.flip()
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                dimension.width,
                dimension.height,
                GL11.GL_RED,
                GL11.GL_FLOAT,
                buffer
            )
        }
    }

    //    TODO: These ones don't do the same thing as code below, find out why
//    val displacementMap = allocateTexture(
//        GL30.GL_RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val displacementMap = listOf(ColorAttachmentDefinition("Displacement", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
//    val displacementMapX = allocateTexture(GL30.GL_RGBA32F, dimension)
//    val displacementMapY = allocateTexture(GL30.GL_RGBA32F, dimension)
//    val displacementMapZ = allocateTexture(GL30.GL_RGBA32F, dimension)
    val displacementMapX = listOf(ColorAttachmentDefinition("DisplacementX", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    val displacementMapY = listOf(ColorAttachmentDefinition("DisplacementY", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    val displacementMapZ = listOf(ColorAttachmentDefinition("DisplacementZ", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
//    val normalMap = allocateTexture(
//        GL30.GL_RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val normalMap = listOf(ColorAttachmentDefinition("Normals", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
//    val albedoMap = allocateTexture(
//        GL30.GL_RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val albedoMap = listOf(ColorAttachmentDefinition("Color", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
//    val roughnessMap = allocateTexture(
//        GL30.GL_RGBA32F, dimension,
//        defaultTextureFilterConfig
//    )
    val roughnessMap = listOf(ColorAttachmentDefinition("Roughness", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()

    val debugMap = listOf(ColorAttachmentDefinition("Color", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first().apply {
        gpuContext { ARBClearTexture.glClearTexImage(id, 0, GL30.GL_RGBA, GL11.GL_FLOAT, RED_BUFFER) }
    }
    private val pingPongMapX = listOf(ColorAttachmentDefinition("PingPongX", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    private val pingPongMapY = listOf(ColorAttachmentDefinition("PingPongY", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    private val pingPongMapZ = listOf(ColorAttachmentDefinition("PingPongZ", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()

    private val tildeHktDyMap = listOf(ColorAttachmentDefinition("tildeHktDyMap", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    private val tildeHktDxMap = listOf(ColorAttachmentDefinition("tildeHktDxMap", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    private val tildeHktDzMap = listOf(ColorAttachmentDefinition("tildeHktDzMap", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    data class TildeMapHelper(val tildeMap: Texture2D, val displacementMap: Texture2D, val pingPongMap: Texture2D)
    val tildeMapHelpers = listOf(
        TildeMapHelper(tildeHktDyMap, displacementMapY, pingPongMapY),
    )
    val tildeMapHelpersChoppy = listOf(
        TildeMapHelper(tildeHktDxMap, displacementMapX, pingPongMapX),
        TildeMapHelper(tildeHktDyMap, displacementMapY, pingPongMapY),
        TildeMapHelper(tildeHktDzMap, displacementMapZ, pingPongMapZ),
    )

    private val h0kMap = listOf(ColorAttachmentDefinition("h0kMap", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    private val h0MinuskMap = listOf(ColorAttachmentDefinition("h0MinuskMap", GL30.GL_RGBA32F)).toTextures(gpuContext, N, N).first()
    private val log2N = (ln(N.toFloat()) / ln(2.0f)).toInt()
    private val twiddleIndicesMap = listOf(ColorAttachmentDefinition("h0kMap", GL30.GL_RGBA32F)).toTextures(gpuContext, log2N, N).first()
    private val bitReversedIndices = PersistentMappedBuffer(N * IntStrukt.sizeInBytes, gpuContext).typed(IntStrukt.type).apply {
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
        gpuContext {
            twiddleIndicesShader.use()
            twiddleIndicesShader.setUniform("N", N)
            GL42.glBindImageTexture(
                0,
                twiddleIndicesMap.id,
                0,
                false,
                0,
                GL15.GL_READ_ONLY,
                twiddleIndicesMap.internalFormat
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
    override fun extract(renderState: RenderState, world: World) {
        val oceanWaterEntities = renderState.componentsForEntities.filter { it.value.any { it is OceanSurfaceComponent } }
        if(oceanWaterEntities.isNotEmpty()) {
            val components = oceanWaterEntities.entries.first().value
            val materialComponent = components.firstIsInstance<MaterialComponent>()
            val oceanSurfaceComponent = components.firstIsInstance<OceanSurfaceComponent>()
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
    override fun render(result: DrawResult, renderState: RenderState) {
        val components = renderState.componentExtracts[OceanWaterComponent::class.java] ?: return
        if(components.isEmpty()) return
        val oceanWaterComponent = components.first() as OceanWaterComponent
        if(oceanWaterComponent.windspeed == 0f) { return }

        seconds += oceanWaterComponent.timeFactor * max(0.001f, renderState.deltaSeconds)

        h0kShader.use()
        GL42.glBindImageTexture(0, h0kMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, h0kMap.internalFormat)
        GL42.glBindImageTexture(1, h0MinuskMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, h0MinuskMap.internalFormat)
        gpuContext.bindTexture(2, random0)
        gpuContext.bindTexture(3, random1)
        gpuContext.bindTexture(4, random2)
        gpuContext.bindTexture(5, random3)
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
        GL42.glBindImageTexture(0, tildeHktDyMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, tildeHktDyMap.internalFormat)
        GL42.glBindImageTexture(1, tildeHktDxMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, tildeHktDxMap.internalFormat)
        GL42.glBindImageTexture(2, tildeHktDzMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, tildeHktDzMap.internalFormat)
        GL42.glBindImageTexture(3, h0kMap.id, 0, false, 0, GL15.GL_READ_ONLY, h0kMap.internalFormat)
        GL42.glBindImageTexture(4, h0MinuskMap.id, 0, false, 0, GL15.GL_READ_ONLY, h0MinuskMap.internalFormat)
        hktShader.dispatchCompute(N/16,N/16,1)

        for(helper in if(oceanWaterComponent.choppy) tildeMapHelpersChoppy else tildeMapHelpers) {
            var pingpong = 0
            butterflyShader.use()
            val pingPongMap = helper.pingPongMap
            val tildeMap = helper.tildeMap
            val displacementMap = helper.displacementMap
            GL42.glBindImageTexture(0, twiddleIndicesMap.id, 0, false, 0, GL15.GL_READ_ONLY, twiddleIndicesMap.internalFormat)
            GL42.glBindImageTexture(1, tildeMap.id, 0, false, 0, GL15.GL_READ_WRITE, tildeMap.internalFormat)
            GL42.glBindImageTexture(2, pingPongMap.id, 0, false, 0, GL15.GL_READ_WRITE, pingPongMap.internalFormat)

            for (stage in 0 until log2N) {
                butterflyShader.setUniform("pingpong", pingpong)
                butterflyShader.setUniform("direction", 0)
                butterflyShader.setUniform("stage", stage)
                butterflyShader.dispatchCompute(N/16,N/16,1)
                GL11.glFinish()
                pingpong++
                pingpong %= 2
            }

            for (stage in 0 until log2N) {
                butterflyShader.setUniform("pingpong", pingpong)
                butterflyShader.setUniform("direction", 1)
                butterflyShader.setUniform("stage", stage)
                butterflyShader.dispatchCompute(N/16,N/16,1)
                GL11.glFinish()
                pingpong++
                pingpong %= 2
            }

            inversionShader.use()
            GL42.glBindImageTexture(0, displacementMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, displacementMap.internalFormat)
            GL42.glBindImageTexture(1, tildeMap.id, 0, false, 0, GL15.GL_READ_WRITE, tildeMap.internalFormat)
            GL42.glBindImageTexture(2, pingPongMap.id, 0, false, 0, GL15.GL_READ_WRITE, pingPongMap.internalFormat)
            inversionShader.setUniform("pingpong", pingpong)
            inversionShader.setUniform("N", N)
            inversionShader.dispatchCompute(N/16,N/16,1)
            GL11.glFinish()
        }

        mergeDisplacementMapsShader.use()
        mergeDisplacementMapsShader.setUniform("diffuseColor", oceanWaterComponent.albedo)
        mergeDisplacementMapsShader.setUniform("N", N)
        mergeDisplacementMapsShader.setUniform("L", oceanWaterComponent.L)
        mergeDisplacementMapsShader.setUniform("choppiness", oceanWaterComponent.choppiness)
        mergeDisplacementMapsShader.setUniform("waveHeight", oceanWaterComponent.waveHeight)
        mergeDisplacementMapsShader.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        GL42.glBindImageTexture(0, displacementMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, displacementMap.internalFormat)
        gpuContext.bindTexture(1, displacementMapX)
        gpuContext.bindTexture(2, displacementMapY)
        gpuContext.bindTexture(3, displacementMapZ)
        GL42.glBindImageTexture(4, normalMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, normalMap.internalFormat)
        GL42.glBindImageTexture(5, albedoMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, albedoMap.internalFormat)
        GL42.glBindImageTexture(6, roughnessMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, roughnessMap.internalFormat)
        gpuContext.bindTexture(7, random3)
        mergeDisplacementMapsShader.dispatchCompute(N/16,N/16,1)
    }

    private fun allocateTexture(internalFormat: Int,
                                textureDimension2D: TextureDimension2D,
                                textureFilterConfig: TextureFilterConfig = TextureFilterConfig(
                                    MinFilter.NEAREST,
                                    MagFilter.NEAREST
                                ),
                                wrapMode: Int = GL14.GL_REPEAT
    ): Texture2D {
        val (textureId, internalFormat, handle) = de.hanno.hpengine.model.texture.allocateTexture(
            gpuContext,
            Texture2D.TextureUploadInfo.Texture2DUploadInfo(textureDimension2D),
            TextureTarget.TEXTURE_2D,
            internalFormat = internalFormat,
            filterConfig = textureFilterConfig,
            wrapMode = wrapMode
        )

        return Texture2D(
            textureDimension2D,
            textureId,
            TextureTarget.TEXTURE_2D,
            internalFormat,
            handle,
            textureFilterConfig,
            wrapMode,
            UploadState.UPLOADED
        )
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
