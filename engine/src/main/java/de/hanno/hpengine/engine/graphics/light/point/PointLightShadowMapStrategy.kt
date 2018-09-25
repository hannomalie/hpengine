package de.hanno.hpengine.engine.graphics.light.point

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem.Companion.AREALIGHT_SHADOWMAP_RESOLUTION
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D_ARRAY
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig.MinFilter.LINEAR
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import java.io.File
import java.nio.FloatBuffer

interface PointLightShadowMapStrategy {
    fun renderPointLightShadowMaps(renderState: RenderState)
    fun bindTextures()
}

class CubeShadowMapStrategy(private val engine: Engine, private val pointLightSystem: PointLightSystem): PointLightShadowMapStrategy {
    var pointLightShadowMapsRenderedInCycle: Long = 0
    var cubemapArrayRenderTarget: CubeMapArrayRenderTarget? = null
    private var pointCubeShadowPassProgram: Program? = null
    var pointLightDepthMapsArrayCube: Int = 0
    init {
        this.pointCubeShadowPassProgram = engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cubemap_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cubemap_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cube_fragment.glsl")), Defines())

        val cubeMapArray = CubeMapArray(engine.gpuContext, MAX_POINTLIGHT_SHADOWMAPS, LINEAR, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION)
        pointLightDepthMapsArrayCube = cubeMapArray.textureID
        this.cubemapArrayRenderTarget = CubeMapArrayRenderTarget(
                engine.gpuContext, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS, cubeMapArray)
    }

    override fun bindTextures() {
        engine.gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, pointLightDepthMapsArrayCube)
    }

    override fun renderPointLightShadowMaps(renderState: RenderState) {
        val pointLights = pointLightSystem.getComponents(PointLight::class.java)
        val gpuContext = engine.gpuContext
        val needToRedraw = pointLightShadowMapsRenderedInCycle < renderState.entitiesState.entityMovedInCycle || pointLightShadowMapsRenderedInCycle < renderState.pointLightMovedInCycle
        if (!needToRedraw) {
            return
        }

        GPUProfiler.start("PointLight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.enable(GlCap.CULL_FACE)
        cubemapArrayRenderTarget!!.use(false)
        gpuContext.clearDepthAndColorBuffer()
        gpuContext.viewPort(0, 0, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)

        for (i in 0 until Math.min(MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {

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
            }

            GPUProfiler.start("PointLight shadowmap entity rendering")
            for (e in renderState.renderBatchesStatic) {
                DrawStrategy.draw(gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, pointCubeShadowPassProgram, !e.isVisible, true)
            }
            GPUProfiler.end()
        }
        GPUProfiler.end()
        pointLightShadowMapsRenderedInCycle = renderState.cycle
    }
}

class DualParaboloidShadowMapStrategy(private val engine: Engine, private val pointLightSystem: PointLightSystem, val cameraEntity: Entity): PointLightShadowMapStrategy {
    private var pointShadowPassProgram: Program? = null

    var pointLightDepthMapsArrayFront: Int = 0
    var pointLightDepthMapsArrayBack: Int = 0

    private val renderTarget = RenderTargetBuilder<RenderTargetBuilder<*,*>, RenderTarget>(engine.gpuContext)
            .setName("PointLight Shadow")
            .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
            .setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
            .add(ColorAttachmentDefinition("Shadow")
                    .setInternalFormat(GL30.GL_RGBA32F)
                    .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
            .build()

    init {
        this.pointShadowPassProgram = engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_fragment.glsl")), Defines())

        pointLightDepthMapsArrayFront = GL11.glGenTextures()
        engine.gpuContext.bindTexture(TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
        GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

        pointLightDepthMapsArrayBack = GL11.glGenTextures()
        engine.gpuContext.bindTexture(TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
        GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
    }

    override fun bindTextures() {
        engine.gpuContext.bindTexture(6, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
        engine.gpuContext.bindTexture(7, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
    }

    private val modelMatrixBuffer = BufferUtils.createFloatBuffer(16)
    override fun renderPointLightShadowMaps(renderState: RenderState) {
        val entities = engine.getScene().entityManager.getEntities()
        val gpuContext = engine.gpuContext

        GPUProfiler.start("PointLight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.disable(GlCap.CULL_FACE)
        renderTarget.use(false)

        val pointLights = pointLightSystem.getPointLights()

        pointShadowPassProgram!!.use()
        for (i in 0 until Math.min(MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {
            renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i)

            gpuContext.clearDepthAndColorBuffer()
            val light = pointLights[i]
            pointShadowPassProgram!!.setUniform("pointLightPositionWorld", light.entity.position)
            pointShadowPassProgram!!.setUniform("pointLightRadius", light.radius)
            pointShadowPassProgram!!.setUniform("isBack", false)

            for (e in entities) {
                e.getComponentOption(ModelComponent::class.java).ifPresent { modelComponent ->
                    pointShadowPassProgram!!.setUniformAsMatrix4("modelMatrix", e.transformation.get(modelMatrixBuffer))
                    pointShadowPassProgram!!.setUniform("hasDiffuseMap", modelComponent.getMaterial(engine.getScene().materialManager).materialInfo.getHasDiffuseMap())
                    pointShadowPassProgram!!.setUniform("color", modelComponent.getMaterial(engine.getScene().materialManager).materialInfo.diffuse)

                    val batch = RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent::class.java)!!.entityBufferIndex, e.isVisible, e.isSelected, Config.getInstance().isDrawLines, cameraEntity.position, true, e.instanceCount, true, e.update, e.minMaxWorld.min, e.minMaxWorld.max, e.centerWorld, e.boundingSphereRadius, modelComponent.indexCount, modelComponent.indexOffset, modelComponent.baseVertex, false, e.instanceMinMaxWorlds)
                    DrawStrategy.draw(gpuContext, renderState, batch)
                }
            }

            pointShadowPassProgram!!.setUniform("isBack", true)
            renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayBack, i)
            gpuContext.clearDepthAndColorBuffer()
            for (e in entities) {
                e.getComponentOption(ModelComponent::class.java).ifPresent { modelComponent ->
                    pointShadowPassProgram!!.setUniformAsMatrix4("modelMatrix", e.transformation.get(modelMatrixBuffer))
                    pointShadowPassProgram!!.setUniform("hasDiffuseMap", modelComponent.getMaterial(engine.getScene().materialManager).materialInfo.getHasDiffuseMap())
                    pointShadowPassProgram!!.setUniform("color", modelComponent.getMaterial(engine.getScene().materialManager).materialInfo.diffuse)

                    val batch = RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent::class.java)!!.entityBufferIndex, e.isVisible, e.isSelected, Config.getInstance().isDrawLines, cameraEntity.position, true, e.instanceCount, true, e.update, e.minMaxWorld.min, e.minMaxWorld.max, e.centerWorld, e.boundingSphereRadius, modelComponent.indexCount, modelComponent.indexOffset, modelComponent.baseVertex, false, e.instanceMinMaxWorlds)
                    DrawStrategy.draw(gpuContext, renderState, batch)
                }
            }
        }
        GPUProfiler.end()
    }
}