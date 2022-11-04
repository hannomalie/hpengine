package de.hanno.hpengine.graphics.light.area

import AreaLightStruktImpl.Companion.sizeInBytes
import AreaLightStruktImpl.Companion.type
import EntityStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All

import de.hanno.hpengine.artemis.AreaLightComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.Capability
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.Mat4
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.SSBO
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.safePut
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.model.enlarge
import de.hanno.hpengine.model.texture.OpenGLCubeMap
import de.hanno.hpengine.model.texture.TextureDimension
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTargetImpl
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import struktgen.api.TypedBuffer
import java.nio.FloatBuffer
import java.util.ArrayList

// TODO: Implement autoadd for transform
@All(AreaLightComponent::class, TransformComponent::class)
class AreaLightSystem(
    val gpuContext: GpuContext,
    programManager: ProgramManager,
    config: Config
) : BaseEntitySystem(), RenderSystem, Extractor {
    override lateinit var artemisWorld: World
    private var gpuAreaLightArray = TypedBuffer(BufferUtils.createByteBuffer(AreaLightStrukt.sizeInBytes), AreaLightStrukt.type)


    lateinit var areaLightComponentComponentMapper: ComponentMapper<AreaLightComponent>

    private val mapRenderTarget = CubeMapRenderTarget(
        gpuContext, RenderTargetImpl(
            gpuContext,
            FrameBuffer(
                gpuContext,
                DepthBuffer(
                    OpenGLCubeMap(
                        gpuContext,
                        TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION),
                        TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
                        GL14.GL_DEPTH_COMPONENT24, GL11.GL_REPEAT
                    )
                )
            ),
            AREALIGHT_SHADOWMAP_RESOLUTION,
            AREALIGHT_SHADOWMAP_RESOLUTION,
            listOf(
                ColorAttachmentDefinition(
                    "Shadow",
                    GL30.GL_RGBA32F,
                    TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)
                )
            ).toCubeMaps(gpuContext, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION),
            "AreaLight Shadow"
        )
    )

    private val areaShadowPassProgram = programManager.getProgram(
        config.EngineAsset("shaders/mvp_entitybuffer_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/shadowmap_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        AreaShadowPassUniforms(gpuContext)
    )
    private val areaLightDepthMaps = ArrayList<Int>().apply {
        gpuContext.invoke {
            for (i in 0 until MAX_AREALIGHT_SHADOWMAPS) {
                val renderedTextureTemp = gpuContext.genTextures()
                gpuContext.bindTexture(TextureTarget.TEXTURE_2D, renderedTextureTemp)
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA16,
                    AREALIGHT_SHADOWMAP_RESOLUTION,
                    AREALIGHT_SHADOWMAP_RESOLUTION,
                    0,
                    GL11.GL_RGB,
                    GL11.GL_UNSIGNED_BYTE,
                    null as FloatBuffer?
                )
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                add(renderedTextureTemp)
            }
        }
    }

    fun renderAreaLightShadowMaps(renderState: RenderState) {
        val areaLights = (renderState.componentExtracts[AreaLightComponent::class.java] as List<AreaLightComponent>?)?: return
        if(areaLights.isEmpty()) return

        profiled("Arealight shadowmaps") {
            gpuContext.depthMask = true
            gpuContext.enable(Capability.DEPTH_TEST)
            gpuContext.disable(Capability.CULL_FACE)
            mapRenderTarget.use(gpuContext, true)

            for (i in 0 until Math.min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size)) {

                mapRenderTarget.setTargetTexture(areaLightDepthMaps[i], 0)

                gpuContext.clearDepthAndColorBuffer()

                val light = areaLights[i]

                areaShadowPassProgram.useAndBind { uniforms ->
                    uniforms.entitiesBuffer = renderState.entitiesBuffer
                    // TODO: Move buffer creation somewhere else or eliminate
                    val buffer = BufferUtils.createFloatBuffer(16)
                    light.transform.transform.invert(Matrix4f()).get(buffer)
                    uniforms.viewMatrix.safePut(buffer)
                    light.camera.projectionMatrix.get(buffer)
                    uniforms.projectionMatrix.safePut(buffer)
                }

                for (e in renderState.renderBatchesStatic) {
                    renderState.vertexIndexBufferStatic.indexBuffer.draw(
                        e.drawElementsIndirectCommand,
                        true,
                        PrimitiveType.Triangles,
                        RenderingMode.Faces
                    )
                }
            }
        }
    }

    override fun processSystem() {
        var areaLightCount = 0
        forEachEntity { areaLightCount++ }

//        TODO: Resize with instance count
        this@AreaLightSystem.gpuAreaLightArray = gpuAreaLightArray.enlarge(areaLightCount * AreaLightStrukt.sizeInBytes)
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

    override fun render(result: DrawResult, renderState: RenderState) {
        renderAreaLightShadowMaps(renderState)
    }

    override fun extract(currentWriteState: RenderState) {
//        TODO: Use this stuff instead of uniforms, take a look at DrawUtils.java
//        currentWriteState.entitiesState.jointsBuffer.sizeInBytes = getRequiredAreaLightBufferSize()
//        gpuAreaLightArray.shrink(currentWriteState.entitiesState.jointsBuffer.buffer.capacity())
//        gpuAreaLightArray.copyTo(currentWriteState.entitiesState.jointsBuffer.buffer)

        currentWriteState.lightState.areaLightDepthMaps = areaLightDepthMaps
    }

    companion object {
        @JvmField val MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField val AREALIGHT_SHADOWMAP_RESOLUTION = 512
    }

}

class AreaShadowPassUniforms(gpuContext: GpuContext): Uniforms() {
    var entitiesBuffer by SSBO("Entity", 3, PersistentMappedBuffer(1, gpuContext).typed(EntityStrukt.type))
    val viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
}