package de.hanno.hpengine.graphics.light.point


import InternalTextureFormat.*
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.light.area.AreaLightSystem.Companion.AREALIGHT_SHADOWMAP_RESOLUTION
import de.hanno.hpengine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_2D_ARRAY
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.graphics.renderer.constants.MagFilter
import de.hanno.hpengine.graphics.renderer.constants.MinFilter
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.WrapMode.*
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.graphics.texture.UploadInfo.Texture2DUploadInfo
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.io.File

interface PointLightShadowMapStrategy {
    fun renderPointLightShadowMaps(renderState: RenderState)
    fun bindTextures()
}

context(GpuContext)
class CubeShadowMapStrategy(
    config: Config,
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

    val cubeMapArray = CubeMapArray(
        TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS),
        TextureFilterConfig(MinFilter.NEAREST),
        RGBA16F,
        Repeat
    )
    val pointLightDepthMapsArrayCube = cubeMapArray.id
    var cubemapArrayRenderTarget: CubeMapArrayRenderTarget = CubeMapArrayRenderTarget(
        cubeMapArray.dimension.width,
        cubeMapArray.dimension.height,
        "PointlightCubeMapArrayRenderTarget",
        Vector4f(0f, 0f, 0f, 0f),
        cubeMapArray
    )
    // TODO: Remove CubeMapArrayRenderTarget to new api

    override fun bindTextures() {
        bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, pointLightDepthMapsArrayCube)
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

context(GpuContext)
class DualParaboloidShadowMapStrategy(
    private val pointLightSystem: PointLightSystem,
    programManager: ProgramManager,
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

    private val textureFilterConfig = TextureFilterConfig(MinFilter.NEAREST_MIPMAP_LINEAR, MagFilter.LINEAR)
    private val renderTarget = RenderTarget(
        frameBuffer = OpenGLFrameBuffer(
            DepthBuffer(
                AREALIGHT_SHADOWMAP_RESOLUTION,
                AREALIGHT_SHADOWMAP_RESOLUTION
            )
        ),
        width = AREALIGHT_SHADOWMAP_RESOLUTION,
        height = AREALIGHT_SHADOWMAP_RESOLUTION,
        textures = listOf(
            OpenGLTexture2D(
                info = Texture2DUploadInfo(
                    TextureDimension(
                        AREALIGHT_SHADOWMAP_RESOLUTION,
                        AREALIGHT_SHADOWMAP_RESOLUTION
                    ),
                    internalFormat = RGBA8,
                    textureFilterConfig = textureFilterConfig
                ),
                textureFilterConfig = textureFilterConfig
            )
        ),
        name = "PointLight Shadow",
        clear = Vector4f()
    )

    init {
        val texture3DUploadInfo = UploadInfo.Texture3DUploadInfo(
            TextureDimension3D(
                AREALIGHT_SHADOWMAP_RESOLUTION,
                AREALIGHT_SHADOWMAP_RESOLUTION,
                MAX_POINTLIGHT_SHADOWMAPS,
            ),
            RGBA16F,
            textureFilterConfig = textureFilterConfig
        )
        pointLightDepthMapsArrayFront = allocateTexture(
            texture3DUploadInfo,
            TEXTURE_2D_ARRAY,
            ClampToEdge
        ).textureId

        pointLightDepthMapsArrayBack = allocateTexture(
            texture3DUploadInfo,
            TEXTURE_2D_ARRAY,
            ClampToEdge
        ).textureId
    }

    override fun bindTextures() {
        bindTexture(6, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
        bindTexture(7, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
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