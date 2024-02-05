package de.hanno.hpengine.graphics.light.point


import InternalTextureFormat.*
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_2D_ARRAY
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.graphics.constants.WrapMode.ClampToEdge
import de.hanno.hpengine.graphics.constants.WrapMode.Repeat
import de.hanno.hpengine.graphics.light.area.AreaLightSystem.Companion.AREALIGHT_SHADOWMAP_RESOLUTION
import de.hanno.hpengine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.TextureDimension3D
import de.hanno.hpengine.graphics.texture.UploadInfo
import de.hanno.hpengine.graphics.texture.UploadInfo.SingleMipLevelTexture2DUploadInfo
import de.hanno.hpengine.math.OmniCamera
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.transform.EntityMovementSystem
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil
import struktgen.api.get
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min

interface PointLightShadowMapStrategy {
    fun renderPointLightShadowMaps(renderState: RenderState)
    fun bindTextures()
}

class CubeShadowMapStrategy(
    private val graphicsApi: GraphicsApi,
    config: Config,
    programManager: ProgramManager,
    private val pointLightStateHolder: PointLightStateHolder,
    private val movementSystem: EntityMovementSystem,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val materialSystem: MaterialSystem,
    private val defaultBatchesSystem: DefaultBatchesSystem,
): PointLightShadowMapStrategy {
    var pointLightShadowMapsRenderedInCycle: Long = 0
    private var pointCubeShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cubemap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cube_fragment.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/pointlight_shadow_cubemap_geometry.glsl")),
        Defines(),
        Uniforms.Empty
    )

    val cubeMapArray = graphicsApi.CubeMapArray(
        TextureDimension(AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS),
        TextureFilterConfig(MinFilter.NEAREST),
        RGBA16F,
        Repeat
    )
    val pointLightDepthMapsArrayCube = cubeMapArray.id
    var cubemapArrayRenderTarget = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(
            graphicsApi.createDepthBuffer(
                graphicsApi.CubeMapArray(
                    TextureDimension(
                    cubeMapArray.dimension.width,
                    cubeMapArray.dimension.height,
                    cubeMapArray.dimension.depth),
                    TextureFilterConfig(minFilter = MinFilter.NEAREST),
                    internalFormat = DEPTH_COMPONENT24,
                    wrapMode = ClampToEdge
                )
            )
        ),
        cubeMapArray.dimension.width,
        cubeMapArray.dimension.height,
        listOf(cubeMapArray),
        "PointLightCubeMapArrayRenderTarget",
        Vector4f(1f, 0f, 0f, 0f)
    )

    override fun bindTextures() {
        graphicsApi.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, pointLightDepthMapsArrayCube)
    }

    private val omniCamera = OmniCamera(Vector3f())
    override fun renderPointLightShadowMaps(renderState: RenderState) = graphicsApi.run {
        val pointLights = renderState[pointLightStateHolder.lightState].pointLightBuffer
        val pointLightCount = renderState[pointLightStateHolder.lightState].pointLightCount
        val needToRedraw = movementSystem.anyEntityHasMoved
        if(!needToRedraw) return

        profiled("PointLight shadowmaps") {
            depthMask = true
            depthTest = true
            cullFace = true
//            cubemapArrayRenderTarget.cubeMapViews.forEach {
//                graphicsApi.clearTexImage(
//                    it.id,
//                    Format.RGBA,
//                    0,
//                    TexelComponentType.Float,
//                    BufferUtils.createFloatBuffer(4).apply {
//                        put(1f)
//                        put(0f)
//                        put(0f)
//                        put(0f)
//                        rewind()
//                    }
//                )
//            }
//            return@profiled
            graphicsApi.clearColor.set(1f,0f,0f,0f)
            cubemapArrayRenderTarget.use(true)

            viewPort(0, 0, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION)

            val entitiesState = renderState[entitiesStateHolder.entitiesState]
            for (i in 0 until min(MAX_POINTLIGHT_SHADOWMAPS, pointLightCount)) {

                pointLights.typedBuffer.byteBuffer.run {
                    val light = pointLights.typedBuffer[i]

                    pointCubeShadowPassProgram.use()
                    pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
                    pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState[entityBuffer.entitiesBuffer])
                    pointCubeShadowPassProgram.bindShaderStorageBuffer(7, entitiesState.vertexIndexBufferStatic.vertexStructArray)
                    pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", light.position.toJoml())
                    pointCubeShadowPassProgram.setUniform("pointLightRadius", light.radius)
                    pointCubeShadowPassProgram.setUniform("lightIndex", i)

                    omniCamera.updatePosition(light.position.toJoml())

                    val viewMatrices = arrayOfNulls<FloatBuffer>(6)
                    val projectionMatrices = arrayOfNulls<FloatBuffer>(6)
                    for (floatBufferIndex in 0..5) {
                        viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)
                        projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16)

                        omniCamera.cameras[floatBufferIndex].viewMatrix.get(viewMatrices[floatBufferIndex])
                        omniCamera.cameras[floatBufferIndex].projectionMatrix.get(projectionMatrices[floatBufferIndex])

                        viewMatrices[floatBufferIndex]!!.rewind()
                        projectionMatrices[floatBufferIndex]!!.rewind()
                        pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[$floatBufferIndex]", viewMatrices[floatBufferIndex]!!)
                        pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[$floatBufferIndex]", projectionMatrices[floatBufferIndex]!!)
                    }
                    pointCubeShadowPassProgram.bind()

                    profiled("PointLight shadowmap entity rendering") {
                        entitiesState.vertexIndexBufferStatic.indexBuffer.bind()
                        val renderBatchesStatic = renderState[defaultBatchesSystem.renderBatchesStatic]
                        for (batch in renderBatchesStatic.filter { it.isShadowCasting }) { // TODO: Better filtering which entity is in light radius
                            entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                                batch.drawElementsIndirectCommand,
                                bindIndexBuffer = false,
                                primitiveType = PrimitiveType.Triangles,
                                mode = RenderingMode.Fill,
                            )
                        }
                    }
                }
            }
        }
        pointLightShadowMapsRenderedInCycle = renderState.cycle
    }
}

class DualParaboloidShadowMapStrategy(
    private val graphicsApi: GraphicsApi,
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
    private val renderTarget = graphicsApi.RenderTarget(
        frameBuffer = graphicsApi.FrameBuffer(
            graphicsApi.DepthBuffer(
                AREALIGHT_SHADOWMAP_RESOLUTION,
                AREALIGHT_SHADOWMAP_RESOLUTION
            )
        ),
        width = AREALIGHT_SHADOWMAP_RESOLUTION,
        height = AREALIGHT_SHADOWMAP_RESOLUTION,
        textures = listOf(
            graphicsApi.Texture2D(
                info = SingleMipLevelTexture2DUploadInfo(
                    TextureDimension(
                        AREALIGHT_SHADOWMAP_RESOLUTION,
                        AREALIGHT_SHADOWMAP_RESOLUTION
                    ),
                    internalFormat = RGBA8,
                    textureFilterConfig = textureFilterConfig,
                ),
                WrapMode.Repeat,
            ),
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
        pointLightDepthMapsArrayFront = graphicsApi.allocateTexture(
            texture3DUploadInfo,
            TEXTURE_2D_ARRAY,
            ClampToEdge
        ).textureId

        pointLightDepthMapsArrayBack = graphicsApi.allocateTexture(
            texture3DUploadInfo,
            TEXTURE_2D_ARRAY,
            ClampToEdge
        ).textureId
    }

    override fun bindTextures() {
        graphicsApi.bindTexture(6, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
        graphicsApi.bindTexture(7, TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
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