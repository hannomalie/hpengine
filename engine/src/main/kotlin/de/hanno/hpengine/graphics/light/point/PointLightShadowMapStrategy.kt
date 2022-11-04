package de.hanno.hpengine.graphics.light.point


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.light.area.AreaLightSystem.Companion.AREALIGHT_SHADOWMAP_RESOLUTION
import de.hanno.hpengine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_2D_ARRAY
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTargetImpl
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLCubeMapArray
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D.TextureUploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import java.io.File

interface PointLightShadowMapStrategy {
    fun renderPointLightShadowMaps(renderState: RenderState)
    fun bindTextures()
}

class CubeShadowMapStrategy(
    config: Config,
    val gpuContext: GpuContext,
    programManager: ProgramManager
): PointLightShadowMapStrategy {
    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cubemap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cube_fragment.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cubemap_geometry.glsl")),
        Defines(),
        Uniforms.Empty
    )

    val cubeMapArray = OpenGLCubeMapArray(
        gpuContext,
        TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS),
        TextureFilterConfig(MinFilter.NEAREST),
        GL30.GL_RGBA16F,
        GL_REPEAT
    )
    val pointLightDepthMapsArrayCube = cubeMapArray.id
    var cubemapArrayRenderTarget: CubeMapArrayRenderTarget = CubeMapArrayRenderTarget(
        gpuContext,
        cubeMapArray.dimension.width,
        cubeMapArray.dimension.height,
        "PointlightCubeMapArrayRenderTarget",
        Vector4f(0f, 0f, 0f, 0f),
        cubeMapArray
    )
    // TODO: Remove CubeMapArrayRenderTarget to new api

    override fun bindTextures() {
        gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, pointLightDepthMapsArrayCube)
    }

    override fun renderPointLightShadowMaps(renderState: RenderState) {
//        TODO: Reimplement properly
//        val pointLights = renderState.componentExtracts[PointLightComponent::class.java] as List<PointLightComponent>
//        val gpuContext = gpuContext
//        val needToRedraw =
//            pointLightShadowMapsRenderedInCycle < renderState.entitiesState.entityMovedInCycle || pointLightShadowMapsRenderedInCycle < renderState.pointLightMovedInCycle
//        if (!needToRedraw) {
//            return
//        }
//
//        profiled("PointLight shadowmaps") {
//
//            gpuContext.depthMask = true
//            gpuContext.enable(GlCap.DEPTH_TEST)
//            gpuContext.enable(GlCap.CULL_FACE)
//            cubemapArrayRenderTarget.use(gpuContext, true)
////            gpuContext.clearDepthAndColorBuffer()
//            gpuContext.viewPort(0, 0, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)
//
//            for (i in 0 until min(MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {
//
//                val light = pointLights[i]
//                pointCubeShadowPassProgram.use()
//                pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.entitiesState.materialBuffer)
//                pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
//                pointCubeShadowPassProgram.bindShaderStorageBuffer(
//                    7,
//                    renderState.entitiesState.vertexIndexBufferStatic.vertexStructArray
//                )
//                pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", light.entity.transform.position)
//                pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
//                pointCubeShadowPassProgram.setUniform("lightIndex", i)
//                val viewProjectionMatrices =
//                    Util.getCubeViewProjectionMatricesForPosition(light.entity.transform.position)
//                val viewMatrices = arrayOfNulls<FloatBuffer>(6)
//                val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
//                for (floatBufferIndex in 0..5) {
//                    viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
//                    projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
//
//                    viewProjectionMatrices.left[floatBufferIndex].get(viewMatrices[floatBufferIndex])
//                    viewProjectionMatrices.right[floatBufferIndex].get(projectionMatrices[floatBufferIndex])
//
//                    viewMatrices[floatBufferIndex]!!.rewind()
//                    projectionMatrices[floatBufferIndex]!!.rewind()
//                    pointCubeShadowPassProgram.setUniformAsMatrix4(
//                        "viewMatrices[$floatBufferIndex]",
//                        viewMatrices[floatBufferIndex]!!
//                    )
//                    pointCubeShadowPassProgram.setUniformAsMatrix4(
//                        "projectionMatrices[$floatBufferIndex]",
//                        projectionMatrices[floatBufferIndex]!!
//                    )
//                }
//
//                profiled("PointLight shadowmap entity rendering") {
//                    renderState.vertexIndexBufferStatic.indexBuffer.bind()
//                    for (batch in renderState.renderBatchesStatic.filter { it.materialInfo.isShadowCasting }) { // TODO: Better filtering which entity is in light radius
//                        renderState.vertexIndexBufferStatic.indexBuffer.draw(
//                            batch,
//                            pointCubeShadowPassProgram,
//                            bindIndexBuffer = false
//                        )
//                    }
//                }
//            }
//        }
//        pointLightShadowMapsRenderedInCycle = renderState.cycle
    }
}

class DualParaboloidShadowMapStrategy(
    private val pointLightSystem: PointLightSystem,
    programManager: ProgramManager,
    val gpuContext: GpuContext,
    val config: Config
): PointLightShadowMapStrategy {
    private var pointShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(File("shaders/" + "pointlight_shadow_vertex.glsl")),
        FileBasedCodeSource(File("shaders/" + "pointlight_shadow_fragment.glsl")),
        Uniforms.Empty,
 Defines()
    )

    var pointLightDepthMapsArrayFront: Int = 0
    var pointLightDepthMapsArrayBack: Int = 0

    private val renderTarget = RenderTargetImpl(
        gpuContext,
        frameBuffer = FrameBuffer(
            gpuContext,
            DepthBuffer(
                gpuContext,
                AREALIGHT_SHADOWMAP_RESOLUTION,
                AREALIGHT_SHADOWMAP_RESOLUTION
            )
        ),
        width = AREALIGHT_SHADOWMAP_RESOLUTION,
        height = AREALIGHT_SHADOWMAP_RESOLUTION,
        textures = listOf(
            OpenGLTexture2D.invoke(
                gpuContext = gpuContext,
                info = Texture2DUploadInfo(
                    TextureDimension(
                        AREALIGHT_SHADOWMAP_RESOLUTION,
                        AREALIGHT_SHADOWMAP_RESOLUTION
                    )
                ),
                textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR),
                internalFormat = GL30.GL_RGBA32F
            )
        ),
        name = "PointLight Shadow"
    )

    init {
        pointLightDepthMapsArrayFront = GL11.glGenTextures()
        gpuContext.bindTexture(TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
        GL42.glTexStorage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            1,
            GL30.GL_RGBA16F,
            AREALIGHT_SHADOWMAP_RESOLUTION,
            AREALIGHT_SHADOWMAP_RESOLUTION,
            MAX_POINTLIGHT_SHADOWMAPS
        )
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

        pointLightDepthMapsArrayBack = GL11.glGenTextures()
        gpuContext.bindTexture(TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
        GL42.glTexStorage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            1,
            GL30.GL_RGBA16F,
            AREALIGHT_SHADOWMAP_RESOLUTION,
            AREALIGHT_SHADOWMAP_RESOLUTION,
            MAX_POINTLIGHT_SHADOWMAPS
        )
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
    }

    override fun bindTextures() {
        gpuContext.bindTexture(6, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
        gpuContext.bindTexture(7, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
    }

    private val modelMatrixBuffer = BufferUtils.createFloatBuffer(16)
    override fun renderPointLightShadowMaps(renderState: RenderState) {
        // TODO: Reimplement properly
//        val entities: List<Entity> = TODO("Reimplement properly with extracted state etc.")//entityManager.getEntities()
//        val modelComponentEntitySystem: ModelComponentEntitySystem =
//            TODO("Reimplement properly with extracted state etc.")//modelComponentSystem
//
//        profiled("PointLight shadowmaps") {
//
//            gpuContext.depthMask = true
//            gpuContext.enable(GlCap.DEPTH_TEST)
//            gpuContext.disable(GlCap.CULL_FACE)
//            renderTarget.use(gpuContext, false)
//
//            val pointLights = pointLightSystem.getPointLights()
//
//            pointShadowPassProgram.use()
//            for (i in 0 until Math.min(MAX_POINTLIGHT_SHADOWMAPS, pointLights.size)) {
//                renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i)
//
//                gpuContext.clearDepthAndColorBuffer()
//                val light = pointLights[i]
//                pointShadowPassProgram.setUniform("pointLightPositionWorld", light.entity.transform.position)
//                pointShadowPassProgram.setUniform("pointLightRadius", light.radius)
//                pointShadowPassProgram.setUniform("isBack", false)
//
//                for (e in entities) {
//                    e.getComponentOption(ModelComponent::class.java).ifPresent { modelComponent ->
//
//                        val allocation = modelComponentEntitySystem.allocations[modelComponent]!!
//                        pointShadowPassProgram.setUniformAsMatrix4(
//                            "modelMatrix",
//                            e.transform.transformation.get(modelMatrixBuffer)
//                        )
//                        pointShadowPassProgram.setUniform(
//                            "hasDiffuseMap",
//                            modelComponent.material.materialInfo.getHasDiffuseMap()
//                        )
//                        pointShadowPassProgram.setUniform("color", modelComponent.material.materialInfo.diffuse)
//
//                        val command = DrawElementsIndirectCommand().apply {
//                            primCount = e.instanceCount
//                            count = allocation.indexOffset
//                            firstIndex = allocation.indexOffset
//                            baseVertex = allocation.vertexOffset
//                        }
//                        val batch = RenderBatch(
//                            entityBufferIndex = 0,//TODO reimplement modelComponentSystem.entityIndices[modelComponent]!!,
//                            isDrawLines = config.debug.isDrawLines,
//                            cameraWorldPosition = renderState.camera.getPosition(),
//                            isVisibleForCamera = true,
//                            update = e.updateType,
//                            entityMinWorld = Vector3f(e.boundingVolume.min),
//                            entityMaxWorld = Vector3f(e.boundingVolume.max),
//                            centerWorld = e.centerWorld,
//                            boundingSphereRadius = e.boundingSphereRadius,
//                            animated = false,
//                            materialInfo = modelComponent.material.materialInfo,
//                            entityIndex = e.index,
//                            meshIndex = 0,
//                            drawElementsIndirectCommand = command
//                        )
//
//                        renderState.vertexIndexBufferStatic.draw(batch, pointShadowPassProgram, true)
//                    }
//                }
//
//                pointShadowPassProgram.setUniform("isBack", true)
//                renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayBack, i)
//                gpuContext.clearDepthAndColorBuffer()
//                for (e in entities) {
//                    e.getComponentOption(ModelComponent::class.java).ifPresent { modelComponent ->
//                        val allocation = modelComponentEntitySystem.allocations[modelComponent]!!
//
//                        pointShadowPassProgram.setUniformAsMatrix4(
//                            "modelMatrix",
//                            e.transform.transformation.get(modelMatrixBuffer)
//                        )
//                        pointShadowPassProgram.setUniform(
//                            "hasDiffuseMap",
//                            modelComponent.material.materialInfo.getHasDiffuseMap()
//                        )
//                        pointShadowPassProgram.setUniform("color", modelComponent.material.materialInfo.diffuse)
//
//                        val command = DrawElementsIndirectCommand().apply {
//                            primCount = e.instanceCount
//                            count = allocation.indexOffset // what?
//                            firstIndex = allocation.indexOffset
//                            baseVertex = allocation.vertexOffset
//                        }
//                        val batch = RenderBatch(
//                            entityBufferIndex = 0,//TODO reimplement modelComponentSystem.entityIndices[modelComponent]!!,
//                            isDrawLines = config.debug.isDrawLines,
//                            cameraWorldPosition = renderState.camera.getPosition(),
//                            isVisibleForCamera = true,
//                            update = e.updateType,
//                            entityMinWorld = Vector3f(e.boundingVolume.min),
//                            entityMaxWorld = Vector3f(e.boundingVolume.max),
//                            centerWorld = e.centerWorld,
//                            boundingSphereRadius = e.boundingSphereRadius,
//                            animated = false,
//                            materialInfo = modelComponent.material.materialInfo,
//                            entityIndex = e.index,
//                            meshIndex = 0,
//                            drawElementsIndirectCommand = command
//                        )
//                        renderState.vertexIndexBufferStatic.draw(batch, pointShadowPassProgram)
//                    }
//                }
//            }
//        }
    }
}