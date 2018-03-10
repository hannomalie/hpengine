package de.hanno.hpengine.engine.graphics.light

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.LightChangedEvent
import de.hanno.hpengine.engine.event.PointLightMovedEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.OBJLoader
import de.hanno.hpengine.engine.model.StaticModel
import de.hanno.hpengine.engine.model.instanceCount
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.CubeMapArray
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import net.engio.mbassy.listener.Handler
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import java.io.File
import java.io.IOException
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class LightManager(private val engine: Engine, val eventBus: EventBus, private val materialManager: MaterialManager, private val sceneManager: SceneManager, private val gpuContext: GpuContext, private val programManager: ProgramManager, inputControllerSystem: InputComponentSystem, private val modelComponentSystem: ModelComponentSystem) {

    var pointLightMovedInCycle: Long = 0

    var pointLightDepthMapsArrayCube: Int = 0
        private set
    var pointLightDepthMapsArrayFront: Int = 0
        private set
    var pointLightDepthMapsArrayBack: Int = 0
        private set
    private val renderTarget: RenderTarget
    var cubemapArrayRenderTarget: CubeMapArrayRenderTarget? = null
        private set
    private val areaLightDepthMaps = ArrayList<Int>()
    private val areaShadowPassProgram: Program
    private var pointShadowPassProgram: Program? = null
    private var pointCubeShadowPassProgram: Program? = null
    private val cameraEntity: Entity
    private val camera: Camera

    private val pointLightsForwardMaxCount = 20
    private var pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
    var pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3)
        private set
    var pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount)
        private set

    private val areaLightsForwardMaxCount = 5
    private var areaLightPositions = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
    var areaLightColors = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        private set
    var areaLightWidthHeightRanges = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        private set
    var areaLightViewDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        private set
    var areaLightUpDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        private set
    var areaLightRightDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        private set

    private var sphereMesh: StaticModel<*>? = null
    private var cubeMesh: StaticModel<*>? = null
    private var planeMesh: StaticModel<*>? = null

    val lightBuffer: GPUBuffer<PointLight>

    var pointLights: MutableList<PointLight> = CopyOnWriteArrayList()
    var tubeLights: MutableList<TubeLight> = CopyOnWriteArrayList()
    var areaLights: MutableList<AreaLight> = CopyOnWriteArrayList()
    var directionalLight = DirectionalLight(Entity())
    //                    .apply { addComponent(cameraComponentSystem.create(this, Util.createOrthogonal(-1000f, 1000f, 1000f, -1000f, -2500f, 2500f), -2500f, 2500f, 60f, 16f / 9f)) }

    private val directionalLightComponent: DirectionalLight

    private var pointlightShadowMapsRenderedInCycle: Long = 0
    @Transient
    var directionalLightMovedInCycle: Long = 0
        private set

    init {
        sphereMesh = null
        try {
            sphereMesh = OBJLoader().loadTexturedModel(this.materialManager, File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"))
            sphereMesh!!.setMaterial(this.materialManager.defaultMaterial)
            cubeMesh = OBJLoader().loadTexturedModel(this.materialManager, File(DirectoryManager.WORKDIR_NAME + "/assets/models/cube.obj"))
            cubeMesh!!.setMaterial(this.materialManager.defaultMaterial)
            planeMesh = OBJLoader().loadTexturedModel(this.materialManager, File(DirectoryManager.WORKDIR_NAME + "/assets/models/planeRotated.obj"))
            planeMesh!!.setMaterial(this.materialManager.defaultMaterial)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        this.renderTarget = RenderTargetBuilder(this.gpuContext)
                .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
                .setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
                .add(ColorAttachmentDefinition()
                        .setInternalFormat(GL30.GL_RGBA32F)
                        .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
                .build()

        if (Config.getInstance().isUseDpsm) {
            // TODO: Use wrapper
            this.pointShadowPassProgram = this.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_fragment.glsl")), Defines())

            pointLightDepthMapsArrayFront = GL11.glGenTextures()
            this.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront)
            GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

            pointLightDepthMapsArrayBack = GL11.glGenTextures()
            this.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack)
            GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        } else {
            this.pointCubeShadowPassProgram = this.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cubemap_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cubemap_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "pointlight_shadow_cube_fragment.glsl")), Defines())

            val cubeMapArray = CubeMapArray(this.gpuContext, MAX_POINTLIGHT_SHADOWMAPS, GL11.GL_LINEAR, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION)
            pointLightDepthMapsArrayCube = cubeMapArray.textureID
            this.cubemapArrayRenderTarget = CubeMapArrayRenderTarget(
                    gpuContext, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS, cubeMapArray)
        }

        this.areaShadowPassProgram = this.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "mvp_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "shadowmap_fragment.glsl")), Defines())
        this.cameraEntity = Entity()
        this.camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

        // TODO: WRAP METHODS SEPARATELY
        this.gpuContext.execute {
            for (i in 0 until MAX_AREALIGHT_SHADOWMAPS) {
                val renderedTextureTemp = this.gpuContext.genTextures()
                this.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp)
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, AREALIGHT_SHADOWMAP_RESOLUTION / 2, AREALIGHT_SHADOWMAP_RESOLUTION / 2, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, null as FloatBuffer?)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                areaLightDepthMaps.add(renderedTextureTemp)
            }
        }

        //		lightBuffer = OpenGLContext.getInstance().calculate(() -> new StorageBuffer(1000));
        lightBuffer = this.gpuContext.calculate { PersistentMappedBuffer<PointLight>(this.gpuContext, 1000) }
        eventBus.register(this)
        directionalLightComponent = createDirectionalLight(directionalLight.getEntity())
        directionalLight.getEntity().addComponent(directionalLightComponent)
        inputControllerSystem.addComponent(directionalLight.addInputController(this.engine))
        initLights()
    }

    private fun initLights() {
        for (pointLight in pointLights) {
        }
        for (areaLight in areaLights) {
        }

    }

    fun getPointLight(entity: Entity, range: Float): PointLight {
        return getPointLight(entity, Vector4f(1f, 1f, 1f, 1f), range)
    }

    @JvmOverloads
    fun getPointLight(entity: Entity, colorIntensity: Vector4f = Vector4f(1f, 1f, 1f, 1f), range: Float = PointLight.DEFAULT_RANGE): PointLight {
        val light = PointLight(entity, colorIntensity, range)
        entity.addComponent(light)
        updatePointLightArrays()
        return light
    }

    private fun updatePointLightArrays() {
        val positions = FloatArray(pointLightsForwardMaxCount * 3)
        val colors = FloatArray(pointLightsForwardMaxCount * 3)
        val radiuses = FloatArray(pointLightsForwardMaxCount)

        for (i in 0 until Math.min(pointLightsForwardMaxCount, this.pointLights.size)) {
            val light = this.pointLights[i]
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

    fun getPointLightPositions(): FloatBuffer {
        updatePointLightArrays()
        return pointLightPositions
    }

    fun getAreaLightPositions(): FloatBuffer {
        updateAreaLightArrays()
        return areaLightPositions
    }

    fun getTubeLight(length: Float, radius: Float): TubeLight {
        return TubeLight(Vector3f(), cubeMesh, Vector3f(1f, 1f, 1f), length, radius)
    }

    fun getAreaLight(width: Int, height: Int, range: Int): AreaLight {
        return getAreaLight(Vector3f(), Vector3f(1f, 1f, 1f), width, height, range)
    }

    fun getAreaLight(position: Vector3f, color: Vector3f, width: Int, height: Int, range: Int): AreaLight {
        return getAreaLight(position, Quaternionf(), color, width, height, range)
    }

    fun getAreaLight(position: Vector3f, width: Int, height: Int, range: Int): AreaLight {
        return getAreaLight(position, Quaternionf(), Vector3f(1f, 1f, 1f), width, height, range)
    }

    fun getAreaLight(position: Vector3f, orientation: Quaternionf, color: Vector3f, width: Float, height: Float, range: Float): AreaLight {
        return getAreaLight(position, orientation, color, width.toInt(), height.toInt(), range.toInt())
    }

    fun getAreaLight(position: Vector3f, orientation: Quaternionf, color: Vector3f, width: Int, height: Int, range: Int): AreaLight {
        val areaLight = AreaLight(position, color, Vector3f(width.toFloat(), height.toFloat(), range.toFloat()))
        areaLight.orientation = orientation
        return areaLight
    }

    private fun updateAreaLightArrays() {
        val positions = FloatArray(areaLightsForwardMaxCount * 3)
        val colors = FloatArray(areaLightsForwardMaxCount * 3)
        val widthHeightRanges = FloatArray(areaLightsForwardMaxCount * 3)
        val viewDirections = FloatArray(areaLightsForwardMaxCount * 3)
        val upDirections = FloatArray(areaLightsForwardMaxCount * 3)
        val rightDirections = FloatArray(areaLightsForwardMaxCount * 3)

        for (i in 0 until Math.min(areaLightsForwardMaxCount, areaLights.size)) {
            val light = areaLights[i]
            positions[3 * i] = light.position.x
            positions[3 * i + 1] = light.position.y
            positions[3 * i + 2] = light.position.z

            colors[3 * i] = light.color.x
            colors[3 * i + 1] = light.color.y
            colors[3 * i + 2] = light.color.z

            widthHeightRanges[3 * i] = light.width
            widthHeightRanges[3 * i + 1] = light.height
            widthHeightRanges[3 * i + 2] = light.range

            viewDirections[3 * i] = light.viewDirection.x
            viewDirections[3 * i + 1] = light.viewDirection.y
            viewDirections[3 * i + 2] = light.viewDirection.z

            upDirections[3 * i] = light.upDirection.x
            upDirections[3 * i + 1] = light.upDirection.y
            upDirections[3 * i + 2] = light.upDirection.z

            rightDirections[3 * i] = light.rightDirection.x
            rightDirections[3 * i + 1] = light.rightDirection.y
            rightDirections[3 * i + 2] = light.rightDirection.z
        }

        areaLightPositions = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        areaLightPositions.put(positions)
        areaLightPositions.rewind()
        areaLightColors = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        areaLightColors.put(colors)
        areaLightColors.rewind()
        areaLightWidthHeightRanges = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        areaLightWidthHeightRanges.put(widthHeightRanges)
        areaLightWidthHeightRanges.rewind()
        areaLightViewDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        areaLightViewDirections.put(viewDirections)
        areaLightViewDirections.rewind()
        areaLightUpDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        areaLightUpDirections.put(upDirections)
        areaLightUpDirections.rewind()
        areaLightRightDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
        areaLightRightDirections.put(rightDirections)
        areaLightRightDirections.rewind()
    }

    fun renderAreaLightShadowMaps(renderState: RenderState) {
        GPUProfiler.start("Arealight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(DEPTH_TEST)
        gpuContext.disable(CULL_FACE)
        renderTarget.use(true)

        for (i in 0 until Math.min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size)) {

            renderTarget.setTargetTexture(areaLightDepthMaps[i], 0)

            gpuContext.clearDepthAndColorBuffer()

            val light = areaLights[i]

            areaShadowPassProgram.use()
            areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", light.viewMatrixAsBuffer)
            areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", light.getComponent(Camera::class.java).projectionMatrixAsBuffer)
            //			directionalShadowPassProgram.setUniform("near", de.hanno.hpengine.camera.getNear());
            //			directionalShadowPassProgram.setUniform("far", de.hanno.hpengine.camera.getFar());

            for (e in renderState.renderBatchesStatic) {
                //				TODO: Use model component index here
                //				areaShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
                //				modelComponent.getMaterials().setTexturesActive(areaShadowPassProgram);
                //				areaShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterials().hasDiffuseMap());
                //				areaShadowPassProgram.setUniform("color", modelComponent.getMaterials().getDiffuse());

                DrawStrategy.draw(gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, areaShadowPassProgram, e.isVisible)
            }
        }
        GPUProfiler.end()
    }

    fun renderPointLightShadowMaps(renderState: RenderState) {
        val needToRedraw = pointlightShadowMapsRenderedInCycle < renderState.entitiesState.entityMovedInCycle || pointlightShadowMapsRenderedInCycle < renderState.pointlightMovedInCycle
        if (!needToRedraw) {
            return
        }

        GPUProfiler.start("PointLight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(DEPTH_TEST)
        gpuContext.enable(CULL_FACE)
        cubemapArrayRenderTarget!!.use(false)
        gpuContext.clearDepthAndColorBuffer()
        gpuContext.viewPort(0, 0, 2 * 128, 2 * 128)
        //TODO: WTF is with the 256...

        for (i in 0 until Math.min(MAX_POINTLIGHT_SHADOWMAPS, this.pointLights.size)) {

            val light = this.pointLights[i]
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
        GPUProfiler.start("PointLight shadowmaps")
        gpuContext.depthMask(true)
        gpuContext.enable(DEPTH_TEST)
        gpuContext.disable(CULL_FACE)
        renderTarget.use(false)

        pointShadowPassProgram!!.use()
        for (i in 0 until Math.min(MAX_POINTLIGHT_SHADOWMAPS, this.pointLights.size)) {
            renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i)

            gpuContext.clearDepthAndColorBuffer()
            val light = this.pointLights[i]
            pointShadowPassProgram!!.setUniform("pointLightPositionWorld", light.entity.position)
            pointShadowPassProgram!!.setUniform("pointLightRadius", light.radius)
            pointShadowPassProgram!!.setUniform("isBack", false)

            for (e in entities) {
                e.getComponentOption(ModelComponent::class.java, ModelComponent.COMPONENT_KEY).ifPresent { modelComponent ->
                    pointShadowPassProgram!!.setUniformAsMatrix4("modelMatrix", e.transformationBuffer)
                    modelComponent.getMaterial(materialManager).setTexturesActive(pointShadowPassProgram)
                    pointShadowPassProgram!!.setUniform("hasDiffuseMap", modelComponent.getMaterial(materialManager).hasDiffuseMap())
                    pointShadowPassProgram!!.setUniform("color", modelComponent.getMaterial(materialManager).diffuse)

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
                    modelComponent.getMaterial(materialManager).setTexturesActive(pointShadowPassProgram)
                    pointShadowPassProgram!!.setUniform("hasDiffuseMap", modelComponent.getMaterial(materialManager).hasDiffuseMap())
                    pointShadowPassProgram!!.setUniform("color", modelComponent.getMaterial(materialManager).diffuse)

                    val batch = RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent::class.java, ModelComponent.COMPONENT_KEY)!!.entityBufferIndex, e.isVisible, e.isSelected, Config.getInstance().isDrawLines, cameraEntity.position, true, e.instanceCount, true, e.update, e.minMaxWorld.min, e.minMaxWorld.max, e.centerWorld, e.boundingSphereRadius, modelComponent.indexCount, modelComponent.indexOffset, modelComponent.baseVertex, false, e.instanceMinMaxWorlds)
                    DrawStrategy.draw(gpuContext, renderState, batch)
                }
            }
        }
        GPUProfiler.end()
    }

    fun update(deltaSeconds: Float, currentCycle: Long) {

        for (i in 0 until pointLights.size) {
            val pointLight = pointLights[i]
            if (!pointLight.entity.hasMoved()) {
                continue
            }
            pointLightMovedInCycle = currentCycle
            engine.eventBus.post(PointLightMovedEvent())
            pointLight.entity.isHasMoved = false
        }

        val pointLightsIterator = pointLights.iterator()
        while (pointLightsIterator.hasNext()) {
            pointLightsIterator.next().update(deltaSeconds)
        }

        for (i in areaLights.indices) {
            areaLights[i].update(deltaSeconds)
        }
        directionalLight.update(deltaSeconds)

        if (directionalLight.getEntity().hasMoved()) {
            directionalLightMovedInCycle = currentCycle
            directionalLight.entity.isHasMoved = false
        }
    }

    fun getDepthMapForAreaLight(light: AreaLight): Int {
        val index = areaLights.indexOf(light)
        return if (index >= MAX_AREALIGHT_SHADOWMAPS) {
            -1
        } else areaLightDepthMaps[index]

    }

    fun getCameraForAreaLight(light: AreaLight): Camera {
        cameraEntity.setTranslation(light.position.negate(null!!))
        //		de.hanno.hpengine.camera.getOrientation().x = -lights.getOrientation().x;
        //		de.hanno.hpengine.camera.getOrientation().y = -lights.getOrientation().y;
        //		de.hanno.hpengine.camera.getOrientation().z = -lights.getOrientation().z;
        //		de.hanno.hpengine.camera.getOrientation().w = -lights.getOrientation().w;
        //		de.hanno.hpengine.camera.rotate(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
        //return de.hanno.hpengine.camera.getComponent(CameraComponent.class).getCamera();
        return camera
    }

    fun getShadowMatrixForAreaLight(light: AreaLight): FloatBuffer {
        //		c.getOrientation().x = lights.getOrientation().negate(null).x;
        //		c.getOrientation().y = lights.getOrientation().negate(null).y;
        //		c.getOrientation().z = lights.getOrientation().negate(null).z;
        //		c.getOrientation().w = lights.getOrientation().negate(null).w;
        //		c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP

        //		Vector3f newPosition = new Vector3f(-lights.getPosition().x, -lights.getPosition().y, -lights.getPosition().y);
        //		newPosition = new Vector3f(0, 0, 10);
        //		c.setPosition(newPosition);
        //		c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
        //		c.updateShadow();
        return light.getComponent(Camera::class.java).viewProjectionMatrixAsBuffer
    }

    private fun bufferLights() {
        val pointLights = this.pointLights
        gpuContext.execute {
            //			lightBuffer.putValues(0, pointLights.size());
            if (pointLights.isNotEmpty()) {
                lightBuffer.put(*Util.toArray<PointLight>(pointLights, PointLight::class.java))
            }
        }
        //		Util.printFloatBuffer(lightBuffer.getValues());
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

    fun createDirectionalLight(entity: Entity): DirectionalLight {
        return DirectionalLight(entity)
    }

    inline fun <reified T> addLight(light: T) {
        if(light is PointLight) {
            this.pointLights.add(light)
        } else if(light is TubeLight) {
            tubeLights.add(light)
        }
        eventBus.post(LightChangedEvent())
    }

    companion object {

        @JvmField var MAX_AREALIGHT_SHADOWMAPS = 2
        @JvmField var MAX_POINTLIGHT_SHADOWMAPS = 5
        @JvmField var AREALIGHT_SHADOWMAP_RESOLUTION = 512
    }
}
