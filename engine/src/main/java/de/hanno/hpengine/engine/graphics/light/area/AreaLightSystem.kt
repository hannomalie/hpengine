package de.hanno.hpengine.engine.graphics.light.area

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateConsumer
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import net.engio.mbassy.listener.Handler
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import java.io.File
import java.nio.FloatBuffer
import java.util.*

class AreaLightComponentSystem: SimpleComponentSystem<AreaLight>(theComponentClass = AreaLight::class.java, factory = { TODO("not implemented") })

class AreaLightSystem(engine: Engine, scene: Scene) : SimpleEntitySystem(engine, scene, listOf(AreaLight::class.java)), StateConsumer {
    private val cameraEntity: Entity = Entity("AreaLightComponentSystem")
    private val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    val lightBuffer: PersistentMappedBuffer<AreaLight> = engine.gpuContext.calculate { PersistentMappedBuffer(engine.gpuContext, 1000) }

    private val renderTarget: RenderTarget = RenderTargetBuilder<RenderTargetBuilder<*,*>, RenderTarget>(engine.gpuContext)
            .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
            .setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
            .add(ColorAttachmentDefinition()
                    .setInternalFormat(GL30.GL_RGBA32F)
                    .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
            .build()

    private val areaShadowPassProgram: Program = engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "mvp_entitybuffer_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "shadowmap_fragment.glsl")), Defines())
    private val areaLightDepthMaps = ArrayList<Int>().apply {
        engine.gpuContext.execute {
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

    @Subscribe
    @Handler
    fun handleLightChange(event: LightChangedEvent) {
        bufferLights()
    }

    private fun bufferLights() {
        val areaLights = getComponents(AreaLight::class.java)
        engine.gpuContext.execute {
            if (areaLights.isNotEmpty()) {
                lightBuffer.put(0, areaLights)
            }
        }
    }

    fun renderAreaLightShadowMaps(renderState: RenderState) {
        val areaLights = getComponents(AreaLight::class.java)

        GPUProfiler.start("Arealight shadowmaps")
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
            areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", light.entity.viewMatrixAsBuffer)
            areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", light.camera.projectionMatrixAsBuffer)

            for (e in renderState.renderBatchesStatic) {
                DrawStrategy.draw(engine.gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, areaShadowPassProgram, !e.isVisible)
            }
        }
        GPUProfiler.end()
    }

    fun getDepthMapForAreaLight(light: AreaLight): Int {
        val index = getAreaLights().indexOf(light)
        return if (index >= MAX_AREALIGHT_SHADOWMAPS) {
            -1
        } else areaLightDepthMaps[index]
    }

    fun getCameraForAreaLight(light: AreaLight): Camera {
        return light.camera
    }
    fun getShadowMatrixForAreaLight(light: AreaLight): FloatBuffer {
        return light.camera.viewProjectionMatrixAsBuffer
    }


    fun getAreaLights() = getComponents(AreaLight::class.java)

    override fun update(deltaSeconds: Float) {
    }

    override fun consume(state: RenderState) {
        renderAreaLightShadowMaps(state)
    }

    companion object {
        @JvmField val MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField val AREALIGHT_SHADOWMAP_RESOLUTION = 512
    }
}