package de.hanno.hpengine.engine.graphics.light.area

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.scene.SimpleScene
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.struct.StructArray
import de.hanno.struct.enlarge
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import java.io.File
import java.nio.FloatBuffer
import java.util.ArrayList
import java.util.concurrent.Callable

class AreaLightComponentSystem: SimpleComponentSystem<AreaLight>(componentClass = AreaLight::class.java, factory = { TODO("not implemented") })

class AreaLightSystem(engine: Engine<*>, simpleScene: SimpleScene) : SimpleEntitySystem(engine, simpleScene, listOf(AreaLight::class.java)), RenderSystem {
    private val cameraEntity: Entity = Entity("AreaLightComponentSystem")
    private val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)
    private var gpuAreaLightArray = StructArray(size = 20) { AreaLightStruct() }

    val lightBuffer: PersistentMappedBuffer = engine.gpuContext.calculate(Callable{ PersistentMappedBuffer(engine.gpuContext, 1000) })

    private val renderTarget: RenderTarget = RenderTargetBuilder<RenderTargetBuilder<*,*>, RenderTarget>(engine.gpuContext)
            .setName("AreaLight Shadow")
            .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
            .setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
            .add(ColorAttachmentDefinition("Shadow")
                    .setInternalFormat(GL30.GL_RGBA32F)
                    .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
            .build()

    private val areaShadowPassProgram: Program = engine.programManager.getProgram(getShaderSource(File(Shader.directory + "mvp_entitybuffer_vertex.glsl")), getShaderSource(File(Shader.directory + "shadowmap_fragment.glsl")))
    private val areaLightDepthMaps = ArrayList<Int>().apply {
        engine.gpuContext.execute{
            for (i in 0 until MAX_AREALIGHT_SHADOWMAPS) {
                val renderedTextureTemp = engine.gpuContext.genTextures()
                engine.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp)
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, null as FloatBuffer?)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                add(renderedTextureTemp)
            }
        }
    }

    fun renderAreaLightShadowMaps(renderState: RenderState) {
        val areaLights = getComponents(AreaLight::class.java)

        profiled("Arealight shadowmaps") {
            engine.gpuContext.depthMask(true)
            engine.gpuContext.enable(GlCap.DEPTH_TEST)
            engine.gpuContext.disable(GlCap.CULL_FACE)
            renderTarget.use(true)

            for (i in 0 until Math.min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size)) {

                renderTarget.setTargetTexture(areaLightDepthMaps[i], 0)

                engine.gpuContext.clearDepthAndColorBuffer()

                val light = areaLights[i]

                areaShadowPassProgram.use()
                areaShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", light.camera.viewMatrixAsBuffer)
                areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", light.camera.projectionMatrixAsBuffer)

                for (e in renderState.renderBatchesStatic) {
                    DrawUtils.draw(engine.gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, areaShadowPassProgram, !e.isVisible, true)
                }
            }
        }
    }

    fun getDepthMapForAreaLight(light: AreaLight): Int {
        return getDepthMapForAreaLight(getAreaLights(), areaLightDepthMaps, light)
    }

    fun getCameraForAreaLight(light: AreaLight): Camera {
        return light.camera
    }
    fun getShadowMatrixForAreaLight(light: AreaLight): FloatBuffer {
        return light.camera.viewProjectionMatrixAsBuffer
    }


    fun getAreaLights() = getComponents(AreaLight::class.java)

    override fun update(deltaSeconds: Float) {
//        TODO: Resize with instance count
        gpuAreaLightArray = gpuAreaLightArray.enlarge(getRequiredAreaLightBufferSize() * AreaLight.getBytesPerInstance())
        gpuAreaLightArray.buffer.rewind()

        for((index, areaLight) in getComponents(AreaLight::class.java).withIndex()) {
            val target = gpuAreaLightArray.getAtIndex(index)
            target.trafo.set(areaLight.entity)
            target.color.set(areaLight.color)
            target.dummy0 = -1
            target.widthHeightRange.x = areaLight.width
            target.widthHeightRange.y = areaLight.height
            target.widthHeightRange.z = areaLight.range
            target.dummy1 = -1
        }
        gpuAreaLightArray
    }
    private fun getRequiredAreaLightBufferSize() =
            getComponents(AreaLight::class.java).sumBy { it.entity.instanceCount }


    override fun render(result: DrawResult, state: RenderState) {
        renderAreaLightShadowMaps(state)
    }

    override fun extract(renderState: RenderState) {
//        TODO: Use this stuff instead of uniforms, take a look at DrawUtils.java
//        currentWriteState.entitiesState.jointsBuffer.sizeInBytes = getRequiredAreaLightBufferSize()
//        gpuAreaLightArray.shrink(currentWriteState.entitiesState.jointsBuffer.buffer.capacity())
//        gpuAreaLightArray.copyTo(currentWriteState.entitiesState.jointsBuffer.buffer)

        renderState.lightState.areaLights = getAreaLights()
        renderState.lightState.areaLightDepthMaps = areaLightDepthMaps
    }

    companion object {
        @JvmField val MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField val AREALIGHT_SHADOWMAP_RESOLUTION = 512

        fun getDepthMapForAreaLight(areaLights: List<AreaLight>, depthMaps: List<Int>, light: AreaLight): Int {
            val index = areaLights.indexOf(light)
            return if (index >= MAX_AREALIGHT_SHADOWMAPS) {
                -1
            } else depthMaps[index]
        }
    }
}