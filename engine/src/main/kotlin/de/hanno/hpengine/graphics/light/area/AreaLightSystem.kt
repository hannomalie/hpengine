package de.hanno.hpengine.graphics.light.area

import AreaLightStruktImpl.Companion.sizeInBytes
import AreaLightStruktImpl.Companion.type
import EntityStruktImpl.Companion.type
import InternalTextureFormat.RGBA32F
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All

import de.hanno.hpengine.artemis.AreaLightComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.shader.Mat4
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.SSBO
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.buffers.safePut
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.Transform
import de.hanno.hpengine.artemis.model.EntitiesStateHolder
import de.hanno.hpengine.buffers.enlarge
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.state.PointLightStateHolder
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.get
import java.util.ArrayList
import kotlin.math.min

// TODO: Implement autoadd for transform
context(GraphicsApi)
@All(AreaLightComponent::class, TransformComponent::class)
class AreaLightSystem(
    private val graphicsApi: GraphicsApi,
    programManager: ProgramManager,
    config: Config,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val areaLightStateHolder: AreaLightStateHolder,
    private val gpuProfiler: GPUProfiler,
) : BaseEntitySystem(), RenderSystem, Extractor {
    private var gpuAreaLightArray = TypedBuffer(
        BufferUtils.createByteBuffer(AreaLightStrukt.sizeInBytes),
        AreaLightStrukt.type,
    )

    lateinit var areaLightComponentComponentMapper: ComponentMapper<AreaLightComponent>

    private val mapRenderTarget = CubeMapRenderTarget(
        RenderTarget(
            FrameBuffer(
                DepthBuffer(
                    CubeMap(
                        TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION),
                        InternalTextureFormat.DEPTH_COMPONENT24,
                        TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
                        WrapMode.Repeat
                    )
                )
            ),
            AREALIGHT_SHADOWMAP_RESOLUTION,
            AREALIGHT_SHADOWMAP_RESOLUTION,
            listOf(
                ColorAttachmentDefinition(
                    "Shadow",
                    RGBA32F,
                    TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)
                )
            ).toCubeMaps(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION),
            "AreaLight Shadow",
            Vector4f(),
        )
    )

    private val areaShadowPassProgram = programManager.getProgram(
        config.EngineAsset("shaders/mvp_entitybuffer_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/shadowmap_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        AreaShadowPassUniforms()
    )
    private val areaLightDepthMaps = ArrayList<Int>().apply {
        graphicsApi.onGpu {
            for (i in 0 until MAX_AREALIGHT_SHADOWMAPS) {
                val internalFormat = InternalTextureFormat.RGBA16F // TODO: Use F for float or not?
                val filterConfig = TextureFilterConfig(MinFilter.LINEAR, MagFilter.LINEAR)
                val wrapMode = WrapMode.ClampToEdge
                val dimension = TextureDimension2D(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)

                val allocation = allocateTexture(
                    UploadInfo.Texture2DUploadInfo(
                        dimension = dimension,
                        internalFormat = internalFormat,
                        textureFilterConfig = filterConfig,
                    ),
                    TextureTarget.TEXTURE_2D,
                    wrapMode,
                )
                graphicsApi.Texture2D(
                    dimension,
                    TextureTarget.TEXTURE_2D,
                    internalFormat,
                    filterConfig,
                    wrapMode,
                    UploadState.Uploaded
                )
                add(allocation.textureId)
            }
        }
    }

    fun renderAreaLightShadowMaps(renderState: RenderState) = gpuProfiler.run {
        val lightState = renderState[areaLightStateHolder.lightState]
        val areaLights = lightState.lights
        if (areaLights.isEmpty()) return

        profiled("Arealight shadowmaps") {
            graphicsApi.depthMask = true
            graphicsApi.enable(Capability.DEPTH_TEST)
            graphicsApi.disable(Capability.CULL_FACE)
            mapRenderTarget.use(true)
            val entitiesState = renderState[entitiesStateHolder.entitiesState]

            for (i in 0 until min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size)) {

                mapRenderTarget.setTargetTexture(areaLightDepthMaps[i], 0)

                graphicsApi.clearDepthAndColorBuffer()

                val light = areaLights[i]

                areaShadowPassProgram.useAndBind { uniforms ->
                    uniforms.entitiesBuffer = entitiesState.entitiesBuffer
                    // TODO: Move buffer creation somewhere else or eliminate
                    val buffer = BufferUtils.createFloatBuffer(16)
                    light.transform.invert(Matrix4f()).get(buffer)
                    uniforms.viewMatrix.safePut(buffer)
                    light.projectionMatrix.get(buffer)
                    uniforms.projectionMatrix.safePut(buffer)
                }

                for (e in entitiesState.renderBatchesStatic) {
                    entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                        e.drawElementsIndirectCommand,
                        true,
                        PrimitiveType.Triangles,
                        RenderingMode.Fill
                    )
                }
            }
        }
    }

    override fun processSystem() {
        var areaLightCount = 0
        forEachEntity { areaLightCount++ }

//        TODO: Resize with instance count
        this@AreaLightSystem.gpuAreaLightArray = gpuAreaLightArray.enlarge(areaLightCount)
        val byteBuffer = gpuAreaLightArray.byteBuffer
        byteBuffer.rewind()
        byteBuffer.run {
            var index = 0
            forEachEntity { entityId ->
                val areaLight = areaLightComponentComponentMapper[entityId]

                gpuAreaLightArray[index].run {
                    trafo.set(areaLight.transform.transform)
                    color.set(areaLight.color)
                    dummy0.value = -1
                    widthHeightRange.x = areaLight.width
                    widthHeightRange.y = areaLight.height
                    widthHeightRange.z = areaLight.range
                    dummy1.value = -1
                }
                index++
            }
        }

        gpuAreaLightArray
    }

    override fun render(renderState: RenderState) {
        renderAreaLightShadowMaps(renderState)
    }

    override fun extract(currentWriteState: RenderState) {
//        TODO: Use this stuff instead of uniforms, take a look at DrawUtils.java
//        currentWriteState.entitiesState.jointsBuffer.sizeInBytes = getRequiredAreaLightBufferSize()
//        gpuAreaLightArray.shrink(currentWriteState.entitiesState.jointsBuffer.buffer.capacity())
//        gpuAreaLightArray.copyTo(currentWriteState.entitiesState.jointsBuffer.buffer)

        currentWriteState[areaLightStateHolder.lightState].areaLightDepthMaps = areaLightDepthMaps
    }

    companion object {
        @JvmField
        val MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField
        val AREALIGHT_SHADOWMAP_RESOLUTION = 512
    }

}

context(GraphicsApi)
class AreaShadowPassUniforms : Uniforms() {
    var entitiesBuffer by SSBO("Entity", 3, PersistentShaderStorageBuffer(1).typed(EntityStrukt.type))
    val viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
}

context(GraphicsApi, RenderStateContext)
class AreaLightStateHolder {
    val lightState = renderState.registerState {
        AreaLightsState()
    }
}
class AreaLightsState {
    val lights: MutableList<AreaLightState> = mutableListOf()
    var areaLightDepthMaps: List<Int> = listOf()
}
class AreaLightState {
    val transform: Transform = Transform()
    val projectionMatrix: Matrix4f = Matrix4f()
}