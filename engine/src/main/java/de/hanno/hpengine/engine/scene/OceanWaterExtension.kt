package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.model.texture.UploadState
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.component.get
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

private val defaultTextureFilterConfig = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)

class OceanWaterComponenSystem: SimpleComponentSystem<OceanWaterExtension.OceanWater>(OceanWaterExtension.OceanWater::class.java)
object OceanWaterExtension {
    class OceanWater(override val entity: Entity,
                     var amplitude: Float = 2f,
                     var windspeed: Float = 26f,
                     var timeFactor: Float = 1f,
                     var direction: Vector2f = Vector2f(0.25f, 1.0f),
                     var L: Int = 800) : Component
    class OceanWaterRenderState(var amplitude: Float = 2f,
                                var intensity: Float = 26f,
                                var timeFactor: Float = 1f,
                                var direction: Vector2f = Vector2f(1.0f, 1.0f),
                                var L: Int = 500,
                                var albedo: Vector3f = Vector3f()
    )
}

class OceanWaterRenderSystem(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>
) : RenderSystem {

    private val N = 512

    private val dimension = TextureDimension2D(N, N)
    private val random0 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_0.png", srgba = false, directory = engineDir)
    private val random1 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_1.png", srgba = false, directory = engineDir)
    private val random2 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_2.png", srgba = false, directory = engineDir)
    private val random3 = createRandomTexture()//textureManager.getTexture("assets/textures/noise_256_3.png", srgba = false, directory = engineDir)

    private fun createRandomTexture(): Texture2D = allocateTexture(GL30.GL_RGBA16F, dimension).apply {
        gpuContext.invoke {
            val buffer = BufferUtils.createFloatBuffer(dimension.width * dimension.height * 4)
            for (x in 0 until dimension.width) {
                for (y in 0 until dimension.height) {
                    buffer.put(x + y * dimension.width, Random.nextFloat())
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

    val displacementMap = allocateTexture(
        GL30.GL_RGBA32F, dimension,
        defaultTextureFilterConfig
    )
    val displacementMapX = allocateTexture(GL30.GL_RGBA32F, dimension)
    val displacementMapY = allocateTexture(GL30.GL_RGBA32F, dimension)
    val displacementMapZ = allocateTexture(GL30.GL_RGBA32F, dimension)
    val normalMap = allocateTexture(
        GL30.GL_RGBA32F, dimension,
        defaultTextureFilterConfig
    )
    val albedoMap = allocateTexture(
        GL30.GL_RGBA32F, dimension,
        defaultTextureFilterConfig
    )
    val roughnessMap = allocateTexture(
        GL30.GL_RGBA32F, dimension,
        defaultTextureFilterConfig
    )
    private val pingPongMap = allocateTexture(GL30.GL_RGBA32F, dimension)

    private val tildeHktDyMap = allocateTexture(GL30.GL_RGBA32F, dimension)
    private val tildeHktDxMap = allocateTexture(GL30.GL_RGBA32F, dimension)
    private val tildeHktDzMap = allocateTexture(GL30.GL_RGBA32F, dimension)
    private val tildeMaps = mapOf(
        tildeHktDxMap to displacementMapX,
        tildeHktDyMap to displacementMapY,
        tildeHktDzMap to displacementMapZ
    )

    private val h0kMap = allocateTexture(GL30.GL_RGBA32F, dimension)
    private val h0MinuskMap = allocateTexture(GL30.GL_RGBA32F, dimension)
    private val log2N = (ln(N.toFloat()) / ln(2.0f)).toInt()
    private val twiddleIndicesMap = allocateTexture(GL30.GL_RGBA32F, dimension.copy(width = log2N))
    private val bitReversedIndices = PersistentMappedStructBuffer(N, gpuContext, { IntStruct() }).apply {
        initBitReversedIndices(N).forEachIndexed { index, value ->
            get(index).value = value
        }
    }

    private val h0kShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/h0k_compute.glsl").toCodeSource())
    private val hktShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/hkt_compute.glsl").toCodeSource())
    private val twiddleIndicesShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/twiddle_indices_compute.glsl").toCodeSource())
    private val butterflyShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/butterfly_compute.glsl").toCodeSource())
    private val inversionShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/inversion_compute.glsl").toCodeSource())
    private val mergeDisplacementMapsShader = programManager.getComputeProgram(config.EngineAsset("shaders/ocean/merge_displacement_maps_compute.glsl").toCodeSource())

    val oceanwaterRenderState = renderStateManager.renderState.registerState { OceanWaterExtension.OceanWaterRenderState() }
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
    }

    override fun extract(scene: Scene, renderState: RenderState) {
        val componentSystem = scene.get<OceanWaterComponenSystem>()
        val target = renderState[oceanwaterRenderState]
        componentSystem.components.firstOrNull()?.let {
            target.intensity = it.windspeed
            target.timeFactor = it.timeFactor
            target.amplitude = it.amplitude
            target.direction.set(it.direction)
            target.L = it.L
            target.albedo.set(it.entity.getComponent(ModelComponent::class.java)!!.model.material.materialInfo.diffuse)
        }
    }
    private var seconds = 0.0f
    override fun render(result: DrawResult, renderState: RenderState) {

        val oceanWaterRenderState = renderState[oceanwaterRenderState]
        seconds += oceanWaterRenderState.timeFactor * max(0.01f, renderState.deltaSeconds)

        h0kShader.use()
        GL42.glBindImageTexture(0, h0kMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, h0kMap.internalFormat)
        GL42.glBindImageTexture(1, h0MinuskMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, h0MinuskMap.internalFormat)
        gpuContext.bindTexture(2, random0)
        gpuContext.bindTexture(3, random1)
        gpuContext.bindTexture(4, random2)
        gpuContext.bindTexture(5, random3)
        h0kShader.setUniform("N", N)
        h0kShader.setUniform("L", oceanWaterRenderState.L)
        h0kShader.setUniform("amplitude", oceanWaterRenderState.amplitude)
        h0kShader.setUniform("windspeed", oceanWaterRenderState.intensity)
        h0kShader.setUniform("direction", oceanWaterRenderState.direction)
        h0kShader.dispatchCompute(N/16,N/16,1)

        hktShader.use()
        hktShader.setUniform("t", seconds)
        hktShader.setUniform("N", N)
        hktShader.setUniform("L", oceanWaterRenderState.L)
        GL42.glBindImageTexture(0, tildeHktDyMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, tildeHktDyMap.internalFormat)
        GL42.glBindImageTexture(1, tildeHktDxMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, tildeHktDxMap.internalFormat)
        GL42.glBindImageTexture(2, tildeHktDzMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, tildeHktDzMap.internalFormat)
        GL42.glBindImageTexture(3, h0kMap.id, 0, false, 0, GL15.GL_READ_ONLY, h0kMap.internalFormat)
        GL42.glBindImageTexture(4, h0MinuskMap.id, 0, false, 0, GL15.GL_READ_ONLY, h0MinuskMap.internalFormat)
        hktShader.dispatchCompute(N/16,N/16,1)

        GL42.glBindImageTexture(
            0,
            twiddleIndicesMap.id,
            0,
            false,
            0,
            GL15.GL_READ_ONLY,
            twiddleIndicesMap.internalFormat
        )
        GL42.glBindImageTexture(1, tildeHktDyMap.id, 0, false, 0, GL15.GL_READ_WRITE, tildeHktDyMap.internalFormat)
        GL42.glBindImageTexture(2, pingPongMap.id, 0, false, 0, GL15.GL_READ_WRITE, pingPongMap.internalFormat)
        for((tildeMap, displacementMap) in tildeMaps) {
            var pingpong = 0
            GL42.glBindImageTexture(1, tildeMap.id, 0, false, 0, GL15.GL_READ_WRITE, tildeMap.internalFormat)
            for (direction in arrayOf(0, 1)) {
                butterflyShader.use()
                for (stage in 0 until log2N) {
                    butterflyShader.setUniform("pingpong", pingpong)
                    butterflyShader.setUniform("direction", direction)
                    butterflyShader.setUniform("stage", stage)
                    butterflyShader.dispatchCompute(N/16,N/16,1)
                    GL11.glFinish()
                    pingpong++
                    pingpong %= 2
                }
            }
            inversionShader.use()
            GL42.glBindImageTexture(
                0,
                displacementMap.id,
                0,
                false,
                0,
                GL15.GL_WRITE_ONLY,
                displacementMap.internalFormat
            )
            GL42.glBindImageTexture(1, pingPongMap.id, 0, false, 0, GL15.GL_READ_ONLY, pingPongMap.internalFormat)
            GL42.glBindImageTexture(2, tildeMap.id, 0, false, 0, GL15.GL_READ_ONLY, tildeMap.internalFormat)
            inversionShader.setUniform("pingpong", pingpong)
            inversionShader.setUniform("N", N)
            inversionShader.setUniform("L", oceanWaterRenderState.L)
            inversionShader.dispatchCompute(N/16,N/16,1)
        }

        mergeDisplacementMapsShader.use()
        mergeDisplacementMapsShader.setUniform("diffuseColor", renderState[oceanwaterRenderState].albedo)
        mergeDisplacementMapsShader.setUniform("N", N)
        mergeDisplacementMapsShader.setUniform("L", oceanWaterRenderState.L)
        mergeDisplacementMapsShader.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        GL42.glBindImageTexture(0, displacementMap.id, 0, false, 0, GL15.GL_WRITE_ONLY, displacementMap.internalFormat)
        gpuContext.bindTexture(1, displacementMapX)
        gpuContext.bindTexture(2, displacementMapY)
        gpuContext.bindTexture(3, displacementMapZ)
        GL42.glBindImageTexture(4, normalMap.id, 0, false, 0, GL15.GL_READ_ONLY, normalMap.internalFormat)
        GL42.glBindImageTexture(5, albedoMap.id, 0, false, 0, GL15.GL_READ_ONLY, albedoMap.internalFormat)
        GL42.glBindImageTexture(6, roughnessMap.id, 0, false, 0, GL15.GL_READ_ONLY, roughnessMap.internalFormat)
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
        val (textureId, internalFormat, handle) = de.hanno.hpengine.engine.model.texture.allocateTexture(
            gpuContext,
            Texture2D.TextureUploadInfo.Texture2DUploadInfo(textureDimension2D),
            GlTextureTarget.TEXTURE_2D,
            internalFormat = internalFormat,
            filterConfig = textureFilterConfig,
            wrapMode = wrapMode
        )

        return Texture2D(
            textureDimension2D,
            textureId,
            GlTextureTarget.TEXTURE_2D,
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

class OceanWaterEntitySystem(val oceanWaterRenderSystem: OceanWaterRenderSystem) : SimpleEntitySystem(listOf(OceanWaterExtension.OceanWater::class.java)) {
        override fun onComponentAdded(scene: Scene, component: Component) {
            super.onComponentAdded(scene, component)
            adjustOceanMaterial(scene)
        }

        override fun onEntityAdded(scene: Scene, entities: List<Entity>) {
            super.onEntityAdded(scene, entities)
            adjustOceanMaterial(scene)
        }

        private fun adjustOceanMaterial(scene: Scene) {
            components.firstOrNull()?.let {
                it as OceanWaterExtension.OceanWater
                it.entity.getComponent(ModelComponent::class.java)!!.model.material.materialInfo.apply {
                    lodFactor = 100.0f
                    roughness = 0.0f
                    metallic = 1.0f
                    diffuse.set(0f, 0.1f, 1f)
                    ambient = 0.05f
                    parallaxBias = 0.0f
                    put(SimpleMaterial.MAP.DISPLACEMENT, oceanWaterRenderSystem.displacementMap)
                    put(SimpleMaterial.MAP.NORMAL, oceanWaterRenderSystem.normalMap)
                    put(SimpleMaterial.MAP.DIFFUSE, oceanWaterRenderSystem.albedoMap)
                    put(SimpleMaterial.MAP.ROUGHNESS, oceanWaterRenderSystem.roughnessMap)
                    put(SimpleMaterial.MAP.ENVIRONMENT, scene.get<TextureManager>().cubeMap)
                    put(SimpleMaterial.MAP.DIFFUSE, oceanWaterRenderSystem.displacementMap)
                }
            }
        }
    }