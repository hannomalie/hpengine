package de.hanno.hpengine.engine.graphics.light

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.event.PointLightMovedEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import net.engio.mbassy.listener.Handler
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import java.io.File
import java.nio.FloatBuffer

class PointLightSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, listOf(PointLight::class.java)) {
    var pointLightMovedInCycle: Long = 0
    private var pointlightShadowMapsRenderedInCycle: Long = 0

    var cubemapArrayRenderTarget: CubeMapArrayRenderTarget? = null
        private set
    private val renderTarget = RenderTargetBuilder(engine.gpuContext)
            .setWidth(LightManager.AREALIGHT_SHADOWMAP_RESOLUTION)
            .setHeight(LightManager.AREALIGHT_SHADOWMAP_RESOLUTION)
            .add(ColorAttachmentDefinition()
                    .setInternalFormat(GL30.GL_RGBA32F)
                    .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
            .build()

    val cameraEntity = Entity()
    val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    private var pointShadowPassProgram: Program? = null
    private var pointCubeShadowPassProgram: Program? = null

    var pointLightDepthMapsArrayCube: Int = 0
        private set
    var pointLightDepthMapsArrayFront: Int = 0
        private set
    var pointLightDepthMapsArrayBack: Int = 0
        private set

    private val pointLightsForwardMaxCount = 20
    private var pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
    var pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
        private set
    var pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount)
        private set

    val lightBuffer: GPUBuffer<PointLight> = engine.gpuContext.calculate { PersistentMappedBuffer<PointLight>(engine.gpuContext, 1000) }

    init {

        if (Config.getInstance().isUseDpsm) {
            // TODO: Use wrapper
            this.pointShadowPassProgram = engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_fragment.glsl")), Defines())

            pointLightDepthMapsArrayFront = GL11.glGenTextures()
            engine.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
            GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.MAX_POINTLIGHT_SHADOWMAPS)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

            pointLightDepthMapsArrayBack = GL11.glGenTextures()
            engine.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
            GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.MAX_POINTLIGHT_SHADOWMAPS)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        } else {
            this.pointCubeShadowPassProgram = engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cubemap_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cubemap_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cube_fragment.glsl")), Defines())

            val cubeMapArray = CubeMapArray(engine.gpuContext, LightManager.MAX_POINTLIGHT_SHADOWMAPS, GL11.GL_LINEAR, GL30.GL_RGBA16F, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION)
            pointLightDepthMapsArrayCube = cubeMapArray.textureID
            this.cubemapArrayRenderTarget = CubeMapArrayRenderTarget(
                    engine.gpuContext, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.MAX_POINTLIGHT_SHADOWMAPS, cubeMapArray)
        }
    }

    private fun bufferLights() {
        val pointLights = getComponents(PointLight::class.java)
        engine.gpuContext.execute {
            if (pointLights.isNotEmpty()) {
                lightBuffer.put(*Util.toArray<PointLight>(pointLights, PointLight::class.java))
            }
        }
    }

    override fun update(deltaSeconds: Float) {
        val pointLights = getComponents(PointLight::class.java)

        for (i in 0 until pointLights.size) {
            val pointLight = pointLights[i]
            if (!pointLight.entity.hasMoved()) {
                continue
            }
            pointLightMovedInCycle = engine.renderManager.drawCycle.get()
            engine.eventBus.post(PointLightMovedEvent())
            pointLight.entity.isHasMoved = false
        }

        val pointLightsIterator = pointLights.iterator()
        while (pointLightsIterator.hasNext()) {
            pointLightsIterator.next().update(deltaSeconds)
        }
    }

    fun getPointLightPositions(): FloatBuffer {
        updatePointLightArrays()
        return pointLightPositions
    }

    private fun updatePointLightArrays() {
        val positions = FloatArray(pointLightsForwardMaxCount * 3)
        val colors = FloatArray(pointLightsForwardMaxCount * 3)
        val radiuses = FloatArray(pointLightsForwardMaxCount)

        val pointLights = getComponents(PointLight::class.java)
        for (i in 0 until Math.min(pointLightsForwardMaxCount, pointLights.size)) {
            val light = pointLights[i]
            positions[3 * i] = light.entity.position.x
            positions[3 * i + 1] = light.entity.position.y
            positions[3 * i + 2] = light.entity.position.z

            colors[3 * i] = light.color.x
            colors[3 * i + 1] = light.color.y
            colors[3 * i + 2] = light.color.z

            radiuses[i] = light.radius
        }

        pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
        pointLightPositions.put(positions)
        pointLightPositions.rewind()
        pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
        pointLightColors.put(colors)
        pointLightColors.rewind()
        pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount)
        pointLightRadiuses.put(radiuses)
        pointLightRadiuses.rewind()
    }

    fun renderPointLightShadowMaps(renderState: RenderState) {
        val pointLights = getComponents(PointLight::class.java)
        val gpuContext = engine.gpuContext
        val needToRedraw = pointlightShadowMapsRenderedInCycle < renderState.entitiesState.entityMovedInCycle || pointlightShadowMapsRenderedInCycle < renderState.pointlightMovedInCycle
        if (!needToRedraw) {
            return
        }

        GPUProfiler.start("PointLight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.enable(GlCap.CULL_FACE)
        cubemapArrayRenderTarget!!.use(false)
        gpuContext.clearDepthAndColorBuffer()
        gpuContext.viewPort(0, 0, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION, LightManager.AREALIGHT_SHADOWMAP_RESOLUTION)

        for (i in 0 until Math.min(LightManager.MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {

            val light = pointLights[i]
            pointCubeShadowPassProgram!!.use()
            pointCubeShadowPassProgram!!.bindShaderStorageBuffer(1, renderState.materialBuffer)
            pointCubeShadowPassProgram!!.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
            pointCubeShadowPassProgram!!.setUniform("pointLightPositionWorld", light.entity.position)
            pointCubeShadowPassProgram!!.setUniform("pointLightRadius", light.radius)
            pointCubeShadowPassProgram!!.setUniform("lightIndex", i)
            val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(light.entity.position)
            val viewMatrices = arrayOfNulls<FloatBuffer>(6)
            val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
            for (floatBufferIndex in 0..5) {
                viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                viewMatrices[floatBufferIndex]!!.rewind()
                projectionMatrices[floatBufferIndex]!!.rewind()
                pointCubeShadowPassProgram!!.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex])
                pointCubeShadowPassProgram!!.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex])
                //				floatBuffers[floatBufferIndex] = null;
            }
            //			pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());
            //			pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getViewMatrixAsBuffer());
            //			pointCubeShadowPassProgram.setUniformAsMatrix4("viewProjectionMatrix", renderState.getCamera().getViewProjectionMatrixAsBuffer());

            GPUProfiler.start("PointLight shadowmap entity rendering")
            for (e in renderState.renderBatchesStatic) {
                DrawStrategy.draw(gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, pointCubeShadowPassProgram, !e.isVisible)
            }
            GPUProfiler.end()
        }
        GPUProfiler.end()
        pointlightShadowMapsRenderedInCycle = renderState.cycle
    }

    fun renderPointLightShadowMaps_dpsm(renderState: RenderState, entities: List<Entity>) {
        val gpuContext = engine.gpuContext

        GPUProfiler.start("PointLight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.disable(GlCap.CULL_FACE)
        renderTarget.use(false)

        val pointLights = getComponents(PointLight::class.java)

        pointShadowPassProgram!!.use()
        for (i in 0 until Math.min(LightManager.MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {
            renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i)

            gpuContext.clearDepthAndColorBuffer()
            val light = pointLights[i]
            pointShadowPassProgram!!.setUniform("pointLightPositionWorld", light.entity.position)
            pointShadowPassProgram!!.setUniform("pointLightRadius", light.radius)
            pointShadowPassProgram!!.setUniform("isBack", false)

            for (e in entities) {
                e.getComponentOption(ModelComponent::class.java, ModelComponent.COMPONENT_KEY).ifPresent { modelComponent ->
                    pointShadowPassProgram!!.setUniformAsMatrix4("modelMatrix", e.transformationBuffer)
                    modelComponent.getMaterial(engine.getScene().materialManager).setTexturesActive(pointShadowPassProgram)
                    pointShadowPassProgram!!.setUniform("hasDiffuseMap", modelComponent.getMaterial(engine.getScene().materialManager).hasDiffuseMap())
                    pointShadowPassProgram!!.setUniform("color", modelComponent.getMaterial(engine.getScene().materialManager).diffuse)

                    val batch = RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)!!.entityBufferIndex, e.isVisible, e.isSelected, Config.getInstance().isDrawLines, cameraEntity.position, true, e.instanceCount, true, e.update, e.minMaxWorld.min, e.minMaxWorld.max, e.centerWorld, e.boundingSphereRadius, modelComponent.indexCount, modelComponent.indexOffset, modelComponent.baseVertex, false, e.instanceMinMaxWorlds)
                    DrawStrategy.draw(gpuContext, renderState, batch)
                }
            }

            pointShadowPassProgram!!.setUniform("isBack", true)
            renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayBack, i)
            gpuContext.clearDepthAndColorBuffer()
            for (e in entities) {
                e.getComponentOption(ModelComponent::class.java, ModelComponent.COMPONENT_KEY).ifPresent { modelComponent ->
                    pointShadowPassProgram!!.setUniformAsMatrix4("modelMatrix", e.transformationBuffer)
                    modelComponent.getMaterial(engine.getScene().materialManager).setTexturesActive(pointShadowPassProgram)
                    pointShadowPassProgram!!.setUniform("hasDiffuseMap", modelComponent.getMaterial(engine.getScene().materialManager).hasDiffuseMap())
                    pointShadowPassProgram!!.setUniform("color", modelComponent.getMaterial(engine.getScene().materialManager).diffuse)

                    val batch = RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)!!.entityBufferIndex, e.isVisible, e.isSelected, Config.getInstance().isDrawLines, cameraEntity.position, true, e.instanceCount, true, e.update, e.minMaxWorld.min, e.minMaxWorld.max, e.centerWorld, e.boundingSphereRadius, modelComponent.indexCount, modelComponent.indexOffset, modelComponent.baseVertex, false, e.instanceMinMaxWorlds)
                    DrawStrategy.draw(gpuContext, renderState, batch)
                }
            }
        }
        GPUProfiler.end()
    }

    @Subscribe
    @Handler
    fun bufferLights(event: LightChangedEvent) {
        bufferLights()
    }

    @Subscribe
    @Handler
    fun bufferLights(event: PointLightMovedEvent) {
        bufferLights()
    }

    @Subscribe
    @Handler
    fun bufferLights(event: SceneInitEvent) {
        bufferLights()
    }

    fun getPointLights(): List<PointLight> = getComponents(PointLight::class.java)
}