package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.directory.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.DrawParameters
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem.Companion.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.FUNC_ADD
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.Factor.ONE
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode.BACK
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.BLEND
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LESS
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.PixelPerfectPickingExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ForwardRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateRef
import de.hanno.hpengine.engine.model.DataChannels
import de.hanno.hpengine.engine.model.OBJLoader
import de.hanno.hpengine.engine.model.QuadVertexBuffer
import de.hanno.hpengine.engine.model.Update.DYNAMIC
import de.hanno.hpengine.engine.model.VertexBuffer
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager.bindEnvironmentProbePositions
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.log.ConsoleLogger.getLogger
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL43
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.FloatBuffer
import java.util.ArrayList
import java.util.EnumSet
import java.util.function.Consumer
import javax.vecmath.Vector2f

class DeferredRenderer @Throws(Exception::class)
constructor(private val materialManager: MaterialManager, engineContext: EngineContext<OpenGl>) : Renderer<OpenGl> {
    private val backend: Backend<OpenGl> = engineContext.backend
    val gpuContext = engineContext.gpuContext.backend.gpuContext
    val programManager = backend.programManager
    val renderState = engineContext.renderStateManager.renderState
    init {
//        throwIfGpuFeaturesMissing()
    }

    private fun throwIfGpuFeaturesMissing() {
        val requiredFeatures = listOf(BindlessTextures, DrawParameters)
        when (val result = gpuContext.isSupported(requiredFeatures)) {
            is GpuContext.SupportResult.UnSupported -> {
                val msg = "Cannot create DeferredRenderer with given OpenGlContext\n" +
                        "Missing features: ${result.unsupportedFeatures.joinToString(", ")}"
                throw IllegalArgumentException(msg)
            }
        }
    }

    private val sixDebugBuffers: ArrayList<VertexBuffer> = gpuContext.setupBuffers()

    private val gBuffer: DeferredRenderingBuffer = gpuContext.setUpGBuffer()
    private val forwardRenderer = ForwardRenderExtension(engineContext.renderStateManager.renderState, gBuffer, engineContext)

    private val buffer = VertexBuffer(engineContext.gpuContext, floatArrayOf(0f, 0f, 0f, 0f), EnumSet.of(DataChannels.POSITION3)).apply {
        upload()
    }
    private val skyBoxEntity = Entity("Skybox")

    private val skyBox = OBJLoader().loadTexturedModel(this.materialManager, File(DirectoryManager.WORKDIR_NAME + "/assets/models/skybox.obj"))
    private val skyBoxModelComponent = ModelComponent(skyBoxEntity, skyBox).apply {
        skyBoxEntity.addComponent(this)
        skyBoxEntity.init(engineContext)
    }
    private val skyboxVertexIndexBuffer = VertexIndexBuffer(gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)

    private val vertexIndexOffsets = skyBoxEntity.getComponent(ModelComponent::class.java).putToBuffer(engineContext.gpuContext, skyboxVertexIndexBuffer, ModelComponent.DEFAULTCHANNELS)
    private val skyBoxRenderBatch = RenderBatch().init(0, true, false, false, Vector3f(0f, 0f, 0f), true, 1, true, DYNAMIC, Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(), 1000f, skyBox.indices.size, vertexIndexOffsets.indexOffset, vertexIndexOffsets.vertexOffset, false, skyBoxEntity.instanceMinMaxWorlds, skyBoxModelComponent.getMaterial(materialManager).materialInfo)

    private val renderToQuadProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "simpletexture_fragment.glsl")))
    private val debugFrameProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "debugframe_fragment.glsl")))

    private val firstpassProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "first_pass_vertex.glsl")), getShaderSource(File(Shader.directory + "first_pass_fragment.glsl")))
    private val firstpassProgramAnimated = programManager.getProgram(getShaderSource(File(Shader.directory + "first_pass_animated_vertex.glsl")), getShaderSource(File(Shader.directory + "first_pass_fragment.glsl")))

    private val secondPassPointProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_point_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_point_fragment.glsl")))
    private val secondPassTubeProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_point_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_tube_fragment.glsl")))
    private val secondPassAreaProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_area_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_area_fragment.glsl")))
    private val secondPassDirectionalProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_directional_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_directional_fragment.glsl")))
    private val instantRadiosityProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_area_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_instant_radiosity_fragment.glsl")))

    private val secondPassPointComputeProgram = programManager.getComputeProgram("second_pass_point_compute.glsl")

    private val combineProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "combine_pass_vertex.glsl")), getShaderSource(File(Shader.directory + "combine_pass_fragment.glsl")))
    private val postProcessProgram = programManager.getProgram(getShaderSource(File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(File(Shader.directory + "postprocess_fragment.glsl")))

    private val aoScatteringProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "scattering_ao_fragment.glsl")
    private val reflectionProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "reflections_fragment.glsl")
    private val linesProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl")
    private val skyBoxProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "skybox.glsl")
    private val skyBoxDepthProgram = programManager.getProgramFromFileNames("mvp_vertex.glsl", "skybox_depth.glsl")
    private val probeFirstpassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl")
    private val depthPrePassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "depth_prepass_fragment.glsl")
    private val tiledDirectLightingProgram = programManager.getComputeProgram("tiled_direct_lighting_compute.glsl")
    private val tiledProbeLightingProgram = programManager.getComputeProgram("tiled_probe_lighting_compute.glsl")

    private val renderExtensions = ArrayList<RenderExtension<OpenGl>>()
    //	private final DirectionalLightShadowMapExtension directionalLightShadowMapExtension;
    private val mainPipelineRef: StateRef<Pipeline>

    private val modelMatrixBuffer = BufferUtils.createFloatBuffer(16)

    private val linePoints = ArrayList<Vector3f>()

    init {
        if (engineContext.gpuContext !is OpenGLContext) {
            throw IllegalStateException("Cannot use this DeferredRenderer with a non-OpenGlContext!")
        }
        //		directionalLightShadowMapExtension = new DirectionalLightShadowMapExtension(engineContext);

        registerRenderExtension(DrawLinesExtension(this, programManager))
        //		TODO: This seems to be broken with the new texture implementations
        //		registerRenderExtension(new VoxelConeTracingExtension(engineContext, directionalLightShadowMapExtension, this));
        //        registerRenderExtension(new EvaluateProbeRenderExtension(managerContext));
        registerRenderExtension(forwardRenderer)
        registerRenderExtension(PixelPerfectPickingExtension())

        mainPipelineRef = renderState.registerState<Pipeline> { GPUCulledMainPipeline(engineContext, this) }
    }

    private fun registerRenderExtension(extension: RenderExtension<OpenGl>) {
        renderExtensions.add(extension)
    }

    private fun GpuContext<OpenGl>.setupBuffers(): ArrayList<VertexBuffer> {
        return calculate {
            val sixDebugBuffers = object : ArrayList<VertexBuffer>() {
                init {
                    val height = -2f / 3f
                    val width = 2f
                    val widthDiv = width / 6f
                    for (i in 0..5) {
                        val quadVertexBuffer = QuadVertexBuffer(backend.gpuContext, Vector2f(-1f + i * widthDiv, -1f), Vector2f(-1 + (i + 1) * widthDiv, height))
                        add(quadVertexBuffer)
                        quadVertexBuffer.upload()
                    }
                }
            }

            gpuContext.exitOnGLError("setupBuffers")
            sixDebugBuffers
        }
    }

    private fun GpuContext<OpenGl>.setUpGBuffer(): DeferredRenderingBuffer {
        gpuContext.exitOnGLError("Before setupGBuffer")

        execute {
            backend.gpuContext.enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS)
            gpuContext.exitOnGLError("setupGBuffer")
        }
        return backend.gpuContext.calculate { DeferredRenderingBuffer(backend.gpuContext) }
    }

    override fun update(engine: Engine<OpenGl>, seconds: Float) {}


    override fun render(result: DrawResult, renderState: RenderState) {
        GPUProfiler.start("Frame")

        GPUProfiler.start("First pass")
        val firstPassResult = result.firstPassResult

        val pipeline = renderState.getState(mainPipelineRef)

        val camera = renderState.camera

        gpuContext.depthMask(true)
        getGBuffer()!!.use(true)

        gpuContext.disable(CULL_FACE)
        gpuContext.depthMask(false)
        gpuContext.disable(GlCap.BLEND)
        skyBoxEntity.identity().scale(10f)
        skyBoxEntity.setTranslation(camera.getPosition())
        skyBoxProgram.use()
        skyBoxProgram.setUniform("eyeVec", camera.getViewDirection())
        val translation = Vector3f()
        skyBoxProgram.setUniform("eyePos_world", camera.getTranslation(translation))
        skyBoxProgram.setUniform("materialIndex", materialManager.skyboxMaterial.materialIndex)
        skyBoxProgram.setUniformAsMatrix4("modelMatrix", skyBoxEntity.transformation.get(modelMatrixBuffer))
        skyBoxProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        skyBoxProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        gpuContext.bindTexture(6, backend.textureManager.cubeMap!!)
        DrawUtils.draw(gpuContext, skyboxVertexIndexBuffer.vertexBuffer, skyboxVertexIndexBuffer.indexBuffer, skyBoxRenderBatch, skyBoxProgram, false, false)

        GPUProfiler.start("Set GPU state")
        gpuContext.enable(CULL_FACE)
        gpuContext.depthMask(true)
        gpuContext.enable(DEPTH_TEST)
        gpuContext.depthFunc(LESS)
        gpuContext.disable(GlCap.BLEND)
        GPUProfiler.end()

        GPUProfiler.start("Draw entities")

        if (Config.getInstance().isDrawScene) {
            pipeline.draw(renderState, firstpassProgram, firstpassProgramAnimated, firstPassResult)
        }
        GPUProfiler.end()

        if (!Config.getInstance().isUseDirectTextureOutput) {
            backend.gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState.shadowMapId)
            for (extension in renderExtensions) {
                GPUProfiler.start("RenderExtension " + extension.javaClass.simpleName)
                extension.renderFirstPass(backend, gpuContext, firstPassResult, renderState)
                GPUProfiler.end()
            }
        }

        gpuContext.enable(CULL_FACE)

        GPUProfiler.start("Generate Mipmaps of colormap")
        backend.textureManager.generateMipMaps(TEXTURE_2D, gBuffer.colorReflectivenessMap)
        GPUProfiler.end()

        GPUProfiler.end()

        if (!Config.getInstance().isUseDirectTextureOutput) {
            //			GPUProfiler.start("Shadowmap pass");
            //			directionalLightShadowMapExtension.renderFirstPass(backend, gpuContext, result.getFirstPassResult(), renderState);
            //            managerContext.getScene().getAreaLightSystem().renderAreaLightShadowMaps(renderState);
            //            managerContext.getScene().getPointLightSystem().getShadowMapStrategy().renderPointLightShadowMaps(renderState);
            //			GPUProfiler.end();

            GPUProfiler.start("Second pass")
            val secondPassResult = result.secondPassResult
            val camera1 = renderState.camera
            val camPosition = Vector3f(camera1.getPosition())
            camPosition.add(camera1.getViewDirection().mul(-camera1.getNear()))
            val camPositionV4 = Vector4f(camPosition.x, camPosition.y, camPosition.z, 0f)

            val viewMatrix = camera1.viewMatrixAsBuffer
            val projectionMatrix = camera1.projectionMatrixAsBuffer

            GPUProfiler.start("Directional light")
            gpuContext.depthMask(false)
            gpuContext.disable(DEPTH_TEST)
            gpuContext.enable(BLEND)
            gpuContext.blendEquation(FUNC_ADD)
            gpuContext.blendFunc(ONE, ONE)

            gBuffer.lightAccumulationBuffer.use(true)
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.depthBufferTexture)
            backend.gpuContext.clearColor(0f, 0f, 0f, 0f)
            backend.gpuContext.clearColorBuffer()

            GPUProfiler.start("Activate DeferredRenderingBuffer textures")
            gpuContext.bindTexture(0, TEXTURE_2D, gBuffer.positionMap)
            gpuContext.bindTexture(1, TEXTURE_2D, gBuffer.normalMap)
            gpuContext.bindTexture(2, TEXTURE_2D, gBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, TEXTURE_2D, gBuffer.motionMap)
            gpuContext.bindTexture(4, TEXTURE_CUBE_MAP, backend.textureManager.cubeMap!!.textureId)
            gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState.shadowMapId)
            gpuContext.bindTexture(7, TEXTURE_2D, gBuffer.visibilityMap)
            gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)
            GPUProfiler.end()

            secondPassDirectionalProgram.use()
            val camTranslation = Vector3f()
            secondPassDirectionalProgram.setUniform("eyePosition", camera1.getTranslation(camTranslation))
            secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.getInstance().ambientocclusionRadius)
            secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().ambientocclusionTotalStrength)
            secondPassDirectionalProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
            secondPassDirectionalProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
            secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassDirectionalProgram.bindShaderStorageBuffer(2, renderState.directionalLightBuffer)
            bindEnvironmentProbePositions(secondPassDirectionalProgram, renderState.environmentProbesState)
            GPUProfiler.start("Draw fullscreen buffer")
            backend.gpuContext.fullscreenBuffer.draw()
            GPUProfiler.end()

            GPUProfiler.end()

            doTubeLights(renderState.lightState.tubeLights, camPositionV4, viewMatrix, projectionMatrix)

            doAreaLights(renderState.lightState.areaLights, viewMatrix, projectionMatrix, renderState)

            doPointLights(renderState, viewMatrix, projectionMatrix)

            if (!Config.getInstance().isUseDirectTextureOutput) {
                GPUProfiler.start("Extensions")
                gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState.shadowMapId)
                for (extension in renderExtensions) {
                    extension.renderSecondPassFullScreen(renderState, secondPassResult)
                }
                GPUProfiler.end()
            }

            backend.gpuContext.disable(BLEND)
            gBuffer.lightAccumulationBuffer.unuse()

            renderAOAndScattering(renderState)

            GPUProfiler.start("MipMap generation AO and light buffer")
            backend.gpuContext.activeTexture(0)
            backend.textureManager.generateMipMaps(TEXTURE_2D, gBuffer.lightAccumulationMapOneId)
            GPUProfiler.end()

            if (Config.getInstance().isUseGi) {
                GL11.glDepthMask(false)
                backend.gpuContext.disable(DEPTH_TEST)
                backend.gpuContext.disable(BLEND)
                backend.gpuContext.cullFace(BACK)
                renderReflections(viewMatrix, projectionMatrix, renderState)
            } else {
                gBuffer.reflectionBuffer.use(true)
                gBuffer.reflectionBuffer.unuse()
            }

            for (extension in renderExtensions) {
                extension.renderSecondPassHalfScreen(renderState, secondPassResult)
            }

            GPUProfiler.start("Blurring")
            //        GPUProfiler.start("LightAccumulationMap");
            //        TextureManager.getInstance().blurHorinzontal2DTextureRGBA16F(gBuffer.getLightAccumulationMapOneId(), Config.getInstance().WIDTH, Config.getInstance().HEIGHT, 7, 8);
            //        GPUProfiler.end();
            GPUProfiler.start("Scattering texture")
            if (Config.getInstance().isScattering || Config.getInstance().isUseAmbientOcclusion) {
                backend.textureManager.blur2DTextureRGBA16F(gBuffer.halfScreenBuffer.renderedTexture, Config.getInstance().width / 2, Config.getInstance().height / 2, 0, 0)
            }
            GPUProfiler.end()
            //        TextureManager.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), Config.getInstance().WIDTH / 2, Config.getInstance().HEIGHT / 2, 0, 0);
            //        Renderer.getInstance().blur2DTexture(gBuffer.getHalfScreenBuffer().getRenderedTexture(), 0, Config.getInstance().WIDTH / 2, Config.getInstance().HEIGHT / 2, GL30.GL_RGBA16F, false, 1);
            //		renderer.blur2DTexture(gBuffer.getLightAccumulationMapOneId(), 0, Config.getInstance().WIDTH, Config.getInstance().HEIGHT, GL30.GL_RGBA16F, false, 1);
            //		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.getInstance().WIDTH*SECONDPASSSCALE), (int)(Config.getInstance().HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
            //		Renderer.getInstance().blur2DTexture(gBuffer.getAmbientOcclusionMapId(), 0, (int)(Config.getInstance().WIDTH), (int)(Config.getInstance().HEIGHT), GL30.GL_RGBA16F, false, 1);
            //		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

            //		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
            //		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

            backend.gpuContext.cullFace(BACK)
            backend.gpuContext.depthFunc(LESS)
            GPUProfiler.end()

            GPUProfiler.end()
            GPUProfiler.start("Combine pass")
            val gBuffer2 = getGBuffer()
            val finalBuffer = gBuffer2!!.finalBuffer
            backend.textureManager.generateMipMaps(TEXTURE_2D, finalBuffer.getRenderedTexture(0))

            combineProgram.use()
            combineProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
            combineProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            combineProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
            combineProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
            combineProgram.setUniform("camPosition", renderState.camera.getPosition())
            combineProgram.setUniform("ambientColor", Config.getInstance().ambientLight)
            combineProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion)
            combineProgram.setUniform("worldExposure", Config.getInstance().exposure)
            combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.getInstance().isAutoExposureEnabled)
            combineProgram.setUniform("fullScreenMipmapCount", gBuffer2.fullScreenMipmapCount)
            combineProgram.setUniform("activeProbeCount", renderState.environmentProbesState.activeProbeCount)
            combineProgram.bindShaderStorageBuffer(0, gBuffer2.storageBuffer)

            finalBuffer.use(true)
            backend.gpuContext.disable(DEPTH_TEST)

            backend.gpuContext.bindTexture(0, TEXTURE_2D, gBuffer2.colorReflectivenessMap)
            backend.gpuContext.bindTexture(1, TEXTURE_2D, gBuffer2.lightAccumulationMapOneId)
            backend.gpuContext.bindTexture(2, TEXTURE_2D, gBuffer2.lightAccumulationBuffer.getRenderedTexture(1))
            backend.gpuContext.bindTexture(3, TEXTURE_2D, gBuffer2.motionMap)
            backend.gpuContext.bindTexture(4, TEXTURE_2D, gBuffer2.positionMap)
            backend.gpuContext.bindTexture(5, TEXTURE_2D, gBuffer2.normalMap)
            backend.gpuContext.bindTexture(6, TEXTURE_2D, gBuffer2.forwardBuffer.getRenderedTexture(0))
            backend.gpuContext.bindTexture(7, TEXTURE_2D, gBuffer2.forwardBuffer.getRenderedTexture(1))
            //			backend.getGpuContext().bindTexture(7, TEXTURE_CUBE_MAP_ARRAY, renderState.getEnvironmentProbesState().getEnvironmapsArray0Id());
            backend.gpuContext.bindTexture(8, TEXTURE_2D, gBuffer2.reflectionMap)
            backend.gpuContext.bindTexture(9, TEXTURE_2D, gBuffer2.refractedMap)
            backend.gpuContext.bindTexture(11, TEXTURE_2D, gBuffer2.ambientOcclusionScatteringMap)
            backend.gpuContext.bindTexture(14, TEXTURE_CUBE_MAP, backend.textureManager.cubeMap!!.textureId)

            gpuContext.fullscreenBuffer.draw()

            if (null == null) {
                backend.gpuContext.frontBuffer.use(true)
            } else {
                (null as RenderTarget).use(true)
            }
            GPUProfiler.start("Post processing")
            postProcessProgram.use()
            backend.gpuContext.bindTexture(0, TEXTURE_2D, finalBuffer.getRenderedTexture(0))
            postProcessProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
            postProcessProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
            postProcessProgram.setUniform("worldExposure", Config.getInstance().exposure)
            postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.getInstance().isAutoExposureEnabled)
            postProcessProgram.setUniform("usePostProcessing", Config.getInstance().isEnablePostprocessing)
            try {
                postProcessProgram.setUniform("cameraRightDirection", renderState.camera.getRightDirection())
                postProcessProgram.setUniform("cameraViewDirection", renderState.camera.getViewDirection())
            } catch (e: IllegalStateException) {
                // Normalizing zero length vector
            }

            postProcessProgram.setUniform("seconds", renderState.deltaInS)
            postProcessProgram.bindShaderStorageBuffer(0, gBuffer2.storageBuffer)
            //        postProcessProgram.bindShaderStorageBuffer(1, managerContext.getRenderer().getMaterialManager().getMaterialBuffer());
            backend.gpuContext.bindTexture(1, TEXTURE_2D, gBuffer2.normalMap)
            backend.gpuContext.bindTexture(2, TEXTURE_2D, gBuffer2.motionMap)
            backend.gpuContext.bindTexture(3, TEXTURE_2D, gBuffer2.lightAccumulationMapOneId)
            backend.gpuContext.bindTexture(4, TEXTURE_2D, backend.textureManager.lensFlareTexture.textureId)
            gpuContext.fullscreenBuffer.draw()

            GPUProfiler.end()
            GPUProfiler.end()
        } else {
            backend.gpuContext.disable(DEPTH_TEST)
            backend.gpuContext.frontBuffer.use(true)
            drawToQuad(Config.getInstance().directTextureOutputTextureIndex)
        }
        if (Config.getInstance().isDebugframeEnabled) {
            //			ArrayList<Texture> textures = new ArrayList<>(backend.getTextureManager().getTextures().values());
            drawToQuad(gBuffer.forwardBuffer.renderedTexture, backend.gpuContext.debugBuffer, debugFrameProgram)
            //			drawToQuad(managerContext.getScene().getAreaLightSystem().getDepthMapForAreaLight(managerContext.getScene().getAreaLightSystem().getAreaLights().get(0)), managerContext.getGpuContext().getDebugBuffer(), managerContext.getProgramManager().getDebugFrameProgram());

            //			for(int i = 0; i < 6; i++) {
            //				drawToQuad(managerContext.getEnvironmentProbeManager().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
            //			}


            //			DEBUG POINT LIGHT SHADOWS

            //			int faceView = OpenGLContext.getInstance().genTextures();
            //			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayBack(),
            //					GL30.GL_RGBA16F, 0, 1, 0, 1);
            //			drawToQuad(faceView, sixDebugBuffers.get(0));
            //			faceView = OpenGLContext.getInstance().genTextures();
            //			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayFront(),
            //					GL30.GL_RGBA16F, 0, 1, 0, 1);
            //			drawToQuad(faceView, sixDebugBuffers.get(1));
            //			GL11.glDeleteTextures(faceView);


            //			DEBUG PROBES

            //            int[] faceViews = new int[6];
            //			int index = 0;
            //            for(int i = 0; i < 6; i++) {
            //                faceViews[i] = managerContext.getGpuContext().genTextures();
            //				int cubeMapArray = managerContext.getScene().getProbeSystem().getStrategy().getCubemapArrayRenderTarget().getCubeMapArray().getTextureID();
            //				GL43.glTextureView(faceViews[i], GlTextureTarget.TEXTURE_2D.glTarget, cubeMapArray, GL_RGBA16F, 0, 10, (6*index)+i, 1);
            //				drawToQuad(faceViews[i], sixDebugBuffers.get(i), managerContext.getProgramManager().getDebugFrameProgram());
            //			}
            //            for(int i = 0; i < 6; i++) {
            //                GL11.glDeleteTextures(faceViews[i]);
            //            }

            GPUProfiler.end()
        }
    }

    override fun drawToQuad(texture: Int) {
        drawToQuad(texture, backend.gpuContext.fullscreenBuffer, renderToQuadProgram)
    }

    fun drawToQuad(texture: Int, buffer: VertexBuffer) {
        drawToQuad(texture, buffer, renderToQuadProgram)
    }

    private fun drawToQuad(texture: Int, buffer: VertexBuffer, program: Program) {
        program.use()
        backend.gpuContext.disable(GlCap.DEPTH_TEST)

        backend.gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture)
        backend.gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.normalMap)

        buffer.draw()
    }

    override fun drawLines(program: Program): Int {
        val points = FloatArray(linePoints.size * 3)
        for (i in linePoints.indices) {
            val point = linePoints[i]
            points[3 * i] = point.x
            points[3 * i + 1] = point.y
            points[3 * i + 2] = point.z
        }
        buffer.putValues(*points)
        buffer.upload().join()
        buffer.drawDebugLines()
        glFinish()
        linePoints.clear()
        return points.size / 3 / 2
    }

    override fun batchLine(from: Vector3f, to: Vector3f) {
        linePoints.add(from)
        linePoints.add(to)
    }

    override fun drawAllLines(action: Consumer<Program>) {
        linePoints.clear()
        linesProgram.use()
        action.accept(linesProgram)
        drawLines(linesProgram)
    }

    override fun getGBuffer(): DeferredRenderingBuffer {
        return gBuffer
    }

    override fun getRenderExtensions(): List<RenderExtension<OpenGl>> {
        return renderExtensions
    }

    private fun doPointLights(renderState: RenderState, viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer) {
        if (renderState.lightState.pointLights.isEmpty()) {
            return
        }
        GPUProfiler.start("Seconds pass PointLights")
        gpuContext.bindTexture(0, TEXTURE_2D, getGBuffer()!!.positionMap)
        gpuContext.bindTexture(1, TEXTURE_2D, getGBuffer()!!.normalMap)
        gpuContext.bindTexture(2, TEXTURE_2D, getGBuffer()!!.colorReflectivenessMap)
        gpuContext.bindTexture(3, TEXTURE_2D, getGBuffer()!!.motionMap)
        gpuContext.bindTexture(4, TEXTURE_2D, getGBuffer()!!.lightAccumulationMapOneId)
        gpuContext.bindTexture(5, TEXTURE_2D, getGBuffer()!!.visibilityMap)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()
        // TODO: Add glbindimagetexture to openglcontext class
        GL42.glBindImageTexture(4, getGBuffer()!!.lightAccumulationMapOneId, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F)
        secondPassPointComputeProgram.use()
        secondPassPointComputeProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
        secondPassPointComputeProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
        secondPassPointComputeProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
        secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        secondPassPointComputeProgram.setUniform("maxPointLightShadowmaps", MAX_POINTLIGHT_SHADOWMAPS)
        secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
        secondPassPointComputeProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
        secondPassPointComputeProgram.dispatchCompute(Config.getInstance().width / 16, Config.getInstance().height / 16, 1)
        GPUProfiler.end()
    }

    private fun doTubeLights(tubeLights: List<TubeLight>,
                             camPositionV4: Vector4f, viewMatrix: FloatBuffer,
                             projectionMatrix: FloatBuffer) {


        if (tubeLights.isEmpty()) {
            return
        }

        secondPassTubeProgram.use()
        secondPassTubeProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
        secondPassTubeProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
        secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        for (tubeLight in tubeLights) {
            val camInsideLightVolume = tubeLight.minMaxWorld.contains(camPositionV4)
            if (camInsideLightVolume) {
                GL11.glCullFace(GL11.GL_FRONT)
                GL11.glDepthFunc(GL11.GL_GEQUAL)
            } else {
                GL11.glCullFace(GL11.GL_BACK)
                GL11.glDepthFunc(GL11.GL_LEQUAL)
            }
            secondPassTubeProgram.setUniform("lightPosition", tubeLight.entity.position)
            secondPassTubeProgram.setUniform("lightStart", tubeLight.start)
            secondPassTubeProgram.setUniform("lightEnd", tubeLight.end)
            secondPassTubeProgram.setUniform("lightOuterLeft", tubeLight.outerLeft)
            secondPassTubeProgram.setUniform("lightOuterRight", tubeLight.outerRight)
            secondPassTubeProgram.setUniform("lightRadius", tubeLight.radius)
            secondPassTubeProgram.setUniform("lightLength", tubeLight.length)
            secondPassTubeProgram.setUniform("lightDiffuse", tubeLight.color)
            tubeLight.draw(secondPassTubeProgram)
        }
    }

    private fun doAreaLights(areaLights: List<AreaLight>, viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer, renderState: RenderState) {

        backend.gpuContext.disable(CULL_FACE)
        backend.gpuContext.disable(DEPTH_TEST)
        if (areaLights.isEmpty()) {
            return
        }

        GPUProfiler.start("Area light: " + areaLights.size)

        secondPassAreaProgram.use()
        secondPassAreaProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
        secondPassAreaProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
        secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        for (areaLight in areaLights) {
            //            boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
            //            if (camInsideLightVolume) {
            //                GL11.glCullFace(GL11.GL_FRONT);
            //                GL11.glDepthFunc(GL11.GL_GEQUAL);
            //            } else {
            //                GL11.glCullFace(GL11.GL_BACK);
            //                GL11.glDepthFunc(GL11.GL_LEQUAL);
            //            }
            secondPassAreaProgram.setUniform("lightPosition", areaLight.entity.position)
            secondPassAreaProgram.setUniform("lightRightDirection", areaLight.entity.rightDirection)
            secondPassAreaProgram.setUniform("lightViewDirection", areaLight.entity.viewDirection)
            secondPassAreaProgram.setUniform("lightUpDirection", areaLight.entity.upDirection)
            secondPassAreaProgram.setUniform("lightWidth", areaLight.width)
            secondPassAreaProgram.setUniform("lightHeight", areaLight.height)
            secondPassAreaProgram.setUniform("lightRange", areaLight.range)
            secondPassAreaProgram.setUniform("lightDiffuse", areaLight.color)
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", areaLight.viewProjectionMatrixAsBuffer)

            // TODO: Add textures to arealights
            //            try {
            //                GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
            //                Texture lightTexture = renderer.getTextureManager().getDiffuseTexture("brick.hptexture");
            //                GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureId());
            //            } catch (IOException e) {
            //                e.printStackTrace();
            //            }
            gpuContext.bindTexture(9, GlTextureTarget.TEXTURE_2D, AreaLightSystem.getDepthMapForAreaLight(renderState.lightState.areaLights, renderState.lightState.areaLightDepthMaps, areaLight))
            gpuContext.fullscreenBuffer.draw()
            //            areaLight.getVertexBuffer().drawDebug();
        }

        GPUProfiler.end()
    }

    private fun renderAOAndScattering(renderState: RenderState) {
        if (!Config.getInstance().isUseAmbientOcclusion && !Config.getInstance().isScattering) {
            return
        }
        GPUProfiler.start("Scattering and AO")
        gBuffer.halfScreenBuffer.use(true)
        backend.gpuContext.disable(DEPTH_TEST)
        backend.gpuContext.bindTexture(0, TEXTURE_2D, gBuffer.positionMap)
        backend.gpuContext.bindTexture(1, TEXTURE_2D, gBuffer.normalMap)
        backend.gpuContext.bindTexture(2, TEXTURE_2D, gBuffer.colorReflectivenessMap)
        backend.gpuContext.bindTexture(3, TEXTURE_2D, gBuffer.motionMap)
        backend.gpuContext.bindTexture(6, TEXTURE_2D, renderState.directionalLightState.shadowMapId)
        renderState.lightState.pointLightShadowMapStrategy.bindTextures()
        backend.gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)

        //		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
        //			gpuContext.bindTexture(13, TEXTURE_3D, directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGrids().get(0).getCurrentVoxelSource());
        //		}

        //		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
        aoScatteringProgram.use()
        aoScatteringProgram.setUniform("eyePosition", renderState.camera.getPosition())
        aoScatteringProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion)
        aoScatteringProgram.setUniform("ambientOcclusionRadius", Config.getInstance().ambientocclusionRadius)
        aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().ambientocclusionTotalStrength)
        aoScatteringProgram.setUniform("screenWidth", Config.getInstance().width.toFloat() / 2)
        aoScatteringProgram.setUniform("screenHeight", Config.getInstance().height.toFloat() / 2)
        aoScatteringProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        aoScatteringProgram.setUniform("time", System.currentTimeMillis().toInt())
        //		aoScatteringProgram.setUniform("useVoxelGrid", directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null);
        //		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
        //			aoScatteringProgram.bindShaderStorageBuffer(5, renderState.getState(directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGridBufferRef()).getVoxelGridBuffer());
        //		}

        aoScatteringProgram.setUniform("maxPointLightShadowmaps", MAX_POINTLIGHT_SHADOWMAPS)
        aoScatteringProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
        aoScatteringProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
        aoScatteringProgram.bindShaderStorageBuffer(3, renderState.directionalLightBuffer)

        bindEnvironmentProbePositions(aoScatteringProgram, renderState.environmentProbesState)
        gpuContext.fullscreenBuffer.draw()
        backend.gpuContext.enable(DEPTH_TEST)
        backend.textureManager.generateMipMaps(TEXTURE_2D, gBuffer.halfScreenBuffer.renderedTexture)
        GPUProfiler.end()
    }

    private fun renderReflections(viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer, renderState: RenderState) {
        GPUProfiler.start("Reflections and AO")
        val gBuffer = getGBuffer()
        val reflectionBuffer = gBuffer.reflectionBuffer

        backend.gpuContext.bindTexture(0, TEXTURE_2D, gBuffer.positionMap)
        backend.gpuContext.bindTexture(1, TEXTURE_2D, gBuffer.normalMap)
        backend.gpuContext.bindTexture(2, TEXTURE_2D, gBuffer.colorReflectivenessMap)
        backend.gpuContext.bindTexture(3, TEXTURE_2D, gBuffer.motionMap)
        backend.gpuContext.bindTexture(4, TEXTURE_2D, gBuffer.lightAccumulationMapOneId)
        backend.gpuContext.bindTexture(5, TEXTURE_2D, gBuffer.finalMap)
        backend.gpuContext.bindTexture(6, backend.textureManager.cubeMap!!)
        //        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
        //        reflectionBuffer.getRenderedTexture(0);
        backend.gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)
        backend.gpuContext.bindTexture(9, backend.textureManager.cubeMap!!)
        backend.gpuContext.bindTexture(10, TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray0Id)
        backend.gpuContext.bindTexture(11, TEXTURE_2D, reflectionBuffer.renderedTexture)

        val copyTextureId = GL11.glGenTextures()
        backend.gpuContext.bindTexture(11, TEXTURE_2D, copyTextureId)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.width, reflectionBuffer.height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, null as FloatBuffer?)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL43.glCopyImageSubData(reflectionBuffer.renderedTexture, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                reflectionBuffer.width, reflectionBuffer.height, 1)
        backend.gpuContext.bindTexture(11, TEXTURE_2D, copyTextureId)

        if (!USE_COMPUTESHADER_FOR_REFLECTIONS) {
            reflectionBuffer.use(true)
            reflectionProgram.use()
            reflectionProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT)
            reflectionProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion)
            reflectionProgram.setUniform("useSSR", Config.getInstance().isUseSSR)
            reflectionProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
            reflectionProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
            reflectionProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            reflectionProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            bindEnvironmentProbePositions(reflectionProgram, renderState.environmentProbesState)
            reflectionProgram.setUniform("activeProbeCount", renderState.environmentProbesState.activeProbeCount)
            reflectionProgram.bindShaderStorageBuffer(0, gBuffer.storageBuffer)
            gpuContext.fullscreenBuffer.draw()
            reflectionBuffer.unuse()
        } else {
            GL42.glBindImageTexture(6, reflectionBuffer.getRenderedTexture(0), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F)
            tiledProbeLightingProgram.use()
            tiledProbeLightingProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT)
            tiledProbeLightingProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion)
            tiledProbeLightingProgram.setUniform("useSSR", Config.getInstance().isUseSSR)
            tiledProbeLightingProgram.setUniform("screenWidth", Config.getInstance().width.toFloat())
            tiledProbeLightingProgram.setUniform("screenHeight", Config.getInstance().height.toFloat())
            tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            tiledProbeLightingProgram.setUniform("activeProbeCount", renderState.environmentProbesState.activeProbeCount)
            bindEnvironmentProbePositions(tiledProbeLightingProgram, renderState.environmentProbesState)
            tiledProbeLightingProgram.dispatchCompute(reflectionBuffer.width / 16, reflectionBuffer.height / 16, 1) //16+1
            //		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        }

        GL11.glDeleteTextures(copyTextureId)
        GPUProfiler.end()
    }

    companion object {
        private val LOGGER = getLogger()

        @Volatile
        var USE_COMPUTESHADER_FOR_REFLECTIONS = false
        @Volatile
        var IMPORTANCE_SAMPLE_COUNT = 8
    }


}