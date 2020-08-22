package de.hanno.hpengine.engine.graphics.light.point

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.index
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem.Companion.AREALIGHT_SHADOWMAP_RESOLUTION
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D_ARRAY
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.engine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.Texture2D.TextureUploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.engine.model.texture.TextureDimension
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min

interface PointLightShadowMapStrategy {
    fun renderPointLightShadowMaps(renderState: RenderState)
    fun bindTextures()
}

class CubeShadowMapStrategy(private val engine: EngineContext, private val pointLightSystem: PointLightSystem): PointLightShadowMapStrategy {
    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram: Program = engine.programManager.getProgram(
            FileBasedCodeSource(File(Shader.directory + "pointlight_shadow_cubemap_vertex.glsl")),
            FileBasedCodeSource(File(Shader.directory + "pointlight_shadow_cube_fragment.glsl")),
            FileBasedCodeSource(File(Shader.directory + "pointlight_shadow_cubemap_geometry.glsl")))
    val cubeMapArray = CubeMapArray(
            engine.gpuContext,
            TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS),
            TextureFilterConfig(MinFilter.NEAREST),
            GL30.GL_RGBA16F,
            GL_REPEAT
    )
    val pointLightDepthMapsArrayCube = cubeMapArray.id
    var cubemapArrayRenderTarget: CubeMapArrayRenderTarget = CubeMapArrayRenderTarget(
            engine.gpuContext,
            cubeMapArray.dimension.width,
            cubeMapArray.dimension.height,
            "CubeMapArrayRenderTarget",
            Vector4f(0f, 0f, 0f, 0f),
            cubeMapArray
    )
    // TODO: Remove CubeMapArrayRenderTarget to new api

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

        profiled("PointLight shadowmaps") {

            gpuContext.depthMask = true
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.CULL_FACE)
            cubemapArrayRenderTarget.use(engine.gpuContext, true)
//            gpuContext.clearDepthAndColorBuffer()
            gpuContext.viewPort(0, 0, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)

            for (i in 0 until min(MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {

                val light = pointLights[i]
                pointCubeShadowPassProgram.use()
                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", light.entity.transform.position)
                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointCubeShadowPassProgram.setUniform("lightIndex", i)
                val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(light.entity.transform.position)
                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                for (floatBufferIndex in 0..5) {
                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                    viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
                    viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])

                    viewMatrices[floatBufferIndex]!!.rewind()
                    projectionMatrices[floatBufferIndex]!!.rewind()
                    pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex]!!)
                    pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex]!!)
                }

                profiled("PointLight shadowmap entity rendering") {
                    for (batch in renderState.renderBatchesStatic) {
                        draw(renderState.vertexIndexBufferStatic.vertexBuffer,
                                renderState.vertexIndexBufferStatic.indexBuffer,
                                batch, pointCubeShadowPassProgram, false, false)
                    }
                }
            }
        }
        pointLightShadowMapsRenderedInCycle = renderState.cycle
    }
}

class DualParaboloidShadowMapStrategy(private val engine: EngineContext,
                                      private val pointLightSystem: PointLightSystem,
                                      val cameraEntity: Entity,
                                      val entityManager: EntityManager,
                                      val modelComponentSystem: ModelComponentSystem): PointLightShadowMapStrategy {
    private var pointShadowPassProgram: Program = engine.programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "pointlight_shadow_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "pointlight_shadow_fragment.glsl")))

    var pointLightDepthMapsArrayFront: Int = 0
    var pointLightDepthMapsArrayBack: Int = 0

    private val renderTarget = RenderTarget(engine.gpuContext,
            frameBuffer = FrameBuffer(engine.gpuContext, DepthBuffer(engine.gpuContext, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)),
            width = AREALIGHT_SHADOWMAP_RESOLUTION,
            height = AREALIGHT_SHADOWMAP_RESOLUTION,
            textures = listOf(Texture2D.invoke(
                gpuContext = engine.gpuContext,
                info = Texture2DUploadInfo(TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)),
                textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR),
                internalFormat = GL30.GL_RGBA32F
                )
            ),
            name = "PointLight Shadow")

    init {
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
        val entities = entityManager.getEntities()
        val modelComponentSystem = modelComponentSystem
        val gpuContext = engine.gpuContext

        profiled("PointLight shadowmaps") {

            gpuContext.depthMask = true
            gpuContext.enable(GlCap.DEPTH_TEST)
            gpuContext.disable(GlCap.CULL_FACE)
            renderTarget.use(engine.gpuContext, false)

            val pointLights = pointLightSystem.getPointLights()

            pointShadowPassProgram.use()
            for (i in 0 until Math.min(MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {
                renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i)

                gpuContext.clearDepthAndColorBuffer()
                val light = pointLights[i]
                pointShadowPassProgram.setUniform("pointLightPositionWorld", light.entity.transform.position)
                pointShadowPassProgram.setUniform("pointLightRadius", light.radius)
                pointShadowPassProgram.setUniform("isBack", false)

                for (e in entities) {
                    e.getComponentOption(ModelComponent::class.java).ifPresent { modelComponent ->

                        val allocation = modelComponentSystem.allocations[modelComponent]!!
                        pointShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.transform.transformation.get(modelMatrixBuffer))
                        pointShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.material.materialInfo.getHasDiffuseMap())
                        pointShadowPassProgram.setUniform("color", modelComponent.material.materialInfo.diffuse)

                        val command = DrawElementsIndirectCommand().apply {
                            primCount = e.instanceCount
                            count = allocation.forMeshes[0].indexOffset
                            firstIndex = allocation.indexOffset
                            baseVertex = allocation.vertexOffset
                        }
                        val batch = RenderBatch(entityBufferIndex = modelComponentSystem.entityIndices[modelComponent]!!,
                                isDrawLines = engine.config.debug.isDrawLines, cameraWorldPosition = cameraEntity.transform.position,
                                isVisibleForCamera = true, update = e.updateType, entityMinWorld = Vector3f(e.boundingVolume.min), entityMaxWorld = Vector3f(e.boundingVolume.max), centerWorld = e.centerWorld,
                                boundingSphereRadius = e.boundingSphereRadius,
                                animated = false, materialInfo = modelComponent.material.materialInfo,
                                entityIndex = e.index, meshIndex = 0, drawElementsIndirectCommand = command)

                        draw(renderState.vertexIndexBufferStatic, batch, pointShadowPassProgram, true)
                    }
                }

                pointShadowPassProgram.setUniform("isBack", true)
                renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayBack, i)
                gpuContext.clearDepthAndColorBuffer()
                for (e in entities) {
                    e.getComponentOption(ModelComponent::class.java).ifPresent { modelComponent ->
                        val allocation = modelComponentSystem.allocations[modelComponent]!!

                        pointShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.transform.transformation.get(modelMatrixBuffer))
                        pointShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.material.materialInfo.getHasDiffuseMap())
                        pointShadowPassProgram.setUniform("color", modelComponent.material.materialInfo.diffuse)

                        val command = DrawElementsIndirectCommand().apply {
                            primCount = e.instanceCount
                            count = allocation.forMeshes[0].indexOffset // what?
                            firstIndex = allocation.indexOffset
                            baseVertex = allocation.vertexOffset
                        }
                        val batch = RenderBatch(entityBufferIndex = modelComponentSystem.entityIndices[modelComponent]!!,
                                isDrawLines = engine.config.debug.isDrawLines, cameraWorldPosition = cameraEntity.transform.position, isVisibleForCamera = true,
                                update = e.updateType, entityMinWorld = Vector3f(e.boundingVolume.min), entityMaxWorld = Vector3f(e.boundingVolume.max), centerWorld = e.centerWorld, boundingSphereRadius = e.boundingSphereRadius,
                                animated = false, materialInfo = modelComponent.material.materialInfo,
                                entityIndex = e.index, meshIndex = 0, drawElementsIndirectCommand = command)
                        draw(renderState.vertexIndexBufferStatic, batch, pointShadowPassProgram, true)
                    }
                }
            }
        }
    }
}