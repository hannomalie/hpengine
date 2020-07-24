package de.hanno.hpengine.engine.graphics.renderer.environmentsampler

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.GpuContext.Companion.exitOnGLError
import de.hanno.hpengine.engine.graphics.light.area.AreaLight
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.Factor
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer.Companion.invoke
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer.Companion.invoke
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget.Companion.invoke
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.toTextures
import de.hanno.hpengine.engine.graphics.shader.ComputeProgram
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.EnvironmentProbe
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Spatial.Companion.isInFrustum
import de.hanno.hpengine.engine.transform.containsOrIntersectsSphere
import de.hanno.hpengine.engine.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL43
import java.io.File
import java.nio.FloatBuffer
import java.util.HashSet
import java.util.stream.Collectors

class EnvironmentSampler(val entity: Entity,
                         probe: EnvironmentProbe,
                         position: Vector3f?,
                         width: Int, height: Int, probeIndex: Int,
                         private val environmentProbeManager: EnvironmentProbeManager,
                         programManager: ProgramManager<OpenGl>, config: Config,
                         textureManager: TextureManager,
                         private val scene: Scene) {
    private val cubeMapProgram: Program
    private val cubeMapLightingProgram: Program
    private val depthPrePassProgram: Program
    private val tiledProbeLightingProgram: ComputeProgram
    private val cubemapRadianceProgram: ComputeProgram
    private val cubemapRadianceFragmentProgram: Program
    private val entityBuffer = BufferUtils.createFloatBuffer(16)

    @Transient
    private var drawnOnce = false

    @Transient
    var sidesDrawn: MutableSet<Int> = HashSet()

    @Transient
    private val probe: EnvironmentProbe
    private val fullscreenBuffer: VertexBuffer
    val cubeMapView: Int
    val cubeMapView1: Int
    val cubeMapView2: Int
    val cubeMapFaceViews = Array(4) { IntArray(6) }
    private val secondPassPointProgram: Program
    private val secondPassTubeProgram: Program
    private val secondPassAreaProgram: Program
    private val secondPassDirectionalProgram: Program
    private val firstPassDefaultProgram: Program
    private val renderTarget: RenderTarget<Texture2D>
    val camera: Camera

    var gpuContext: GpuContext<OpenGl>
    private val config: Config
    private val textureManager: TextureManager
    fun drawCubeMap(urgent: Boolean, extract: RenderState) {
        drawCubeMapSides(urgent, extract)
    }

    private fun drawCubeMapSides(urgent: Boolean, renderState: RenderState) = scene.entityManager.run {
        val initialOrientation = entity.transform.rotation
        val initialPosition = entity.transform.position
        val light = scene.entitySystems.get(DirectionalLightSystem::class.java).getDirectionalLight()
        gpuContext.bindTexture(8, environmentProbeManager.environmentMapsArray)
        gpuContext.bindTexture(10, environmentProbeManager.getEnvironmentMapsArray(0))
        gpuContext.disable(GlCap.DEPTH_TEST)
        gpuContext.depthFunc = GlDepthFunc.LEQUAL
        renderTarget.use(gpuContext, false)
        val cubeMapProgram = cubeMapProgram
        bindProgramSpecificsPerCubeMap(cubeMapProgram, renderState)
        var filteringRequired = false
        val viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(entity.transform.position)
        val viewMatrixBuffer = BufferUtils.createFloatBuffer(16)
        val projectionMatrixBuffer = BufferUtils.createFloatBuffer(16)
        val viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16)
        for (i in 0..5) {
            viewProjectionMatrices.left[i][viewMatrixBuffer]
            viewProjectionMatrices.right[i][projectionMatrixBuffer]
            Matrix4f().set(viewProjectionMatrices.right[i]).mul(viewProjectionMatrices.left[i])[viewProjectionMatrixBuffer]
            rotateForIndex(i, entity)
            val fullReRenderRequired = urgent || !drawnOnce
            val aPointLightHasMoved = !scene.getPointLights().stream()
                    .filter { e: PointLight -> probe.box.containsOrIntersectsSphere(e.entity.transform.position, e.radius) }
                    .filter { e: PointLight -> e.entity.hasMoved }.collect(Collectors.toList()).isEmpty()
            val areaLightHasMoved = !scene.getAreaLightSystem().getAreaLights().any { it.entity.hasMoved }
            val reRenderLightingRequired = light!!.entity.hasMoved || aPointLightHasMoved || areaLightHasMoved
            val noNeedToRedraw = !urgent && !fullReRenderRequired && !reRenderLightingRequired
            if (noNeedToRedraw) {  // early exit if only static objects visible and lights didn't change
//				continue;
            } else if (reRenderLightingRequired) {
//				cubeMapLightingProgram.use();
            } else if (fullReRenderRequired) {
//				cubeMapProgram.use();
            }
            filteringRequired = true
            if (deferredRenderingForProbes) {
                if (!sidesDrawn.contains(i)) {
                    environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(0, probe.index, i)
                    environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(1, probe.index, i)
                    environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(2, probe.index, i)
                    gpuContext.clearDepthAndColorBuffer()
                    drawFirstPass(i, camera, renderState)
                    environmentProbeManager.cubeMapArrayRenderTarget.resetAttachments()
                }
                environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(3, 0, probe.index, i)
                gpuContext.clearDepthAndColorBuffer()
                drawSecondPass(i, light, scene.getPointLights(), scene.getTubeLights(), scene.getAreaLights())
                registerSideAsDrawn(i)
            } else {
                gpuContext.depthMask = true
                gpuContext.enable(GlCap.DEPTH_TEST)
                gpuContext.depthFunc = GlDepthFunc.LEQUAL
                environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(3, 0, probe.index, i)
                gpuContext.clearDepthAndColorBuffer()
                drawEntities(renderState, cubeMapProgram, viewMatrixBuffer, projectionMatrixBuffer, viewProjectionMatrixBuffer)
            }
        }
        if (filteringRequired) {
            generateCubeMapMipMaps()
        }
        entity.transform.translation(initialPosition)
        entity.transform.rotation(initialOrientation)
    }

    private fun registerSideAsDrawn(i: Int) {
        sidesDrawn.add(i)
        if (sidesDrawn.size == 6) {
            drawnOnce = true
        }
    }

    fun resetDrawing() {
        sidesDrawn.clear()
        drawnOnce = false
    }

    @Subscribe
    fun handle(e: MaterialChangedEvent?) {
        resetDrawing()
        scene.environmentProbeManager.addRenderProbeCommand(probe, true)
        scene.environmentProbeManager.addRenderProbeCommand(probe, true)
        scene.environmentProbeManager.addRenderProbeCommand(probe, true)
    }

    private fun bindProgramSpecificsPerCubeMap(program: Program, renderState: RenderState) {
        program.use()
        program.setUniform("firstBounceForProbe", DeferredRenderingBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE)
        program.setUniform("probePosition", probe.entity.transform.center)
        program.setUniform("probeSize", probe.size)
        program.setUniform("activePointLightCount", scene.getPointLights().size)
        program.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
        program.bindShaderStorageBuffer(5, renderState.lightState.pointLightBuffer)
        program.setUniform("activeAreaLightCount", scene.getAreaLights().size)
        program.bindShaderStorageBuffer(6, scene.getAreaLightSystem().lightBuffer)
        for (i in 0 until Math.min(scene.getAreaLights().size, AreaLightSystem.MAX_AREALIGHT_SHADOWMAPS)) {
            val areaLight = scene.getAreaLights()[i]
            gpuContext.bindTexture(9 + i, GlTextureTarget.TEXTURE_2D, scene.getAreaLightSystem().getDepthMapForAreaLight(areaLight))
            program.setUniformAsMatrix4("areaLightShadowMatrices[$i]", scene.getAreaLightSystem().getShadowMatrixForAreaLight(areaLight))
        }
        program.setUniform("probeIndex", probe.index)
        environmentProbeManager.bindEnvironmentProbePositions(cubeMapProgram)
    }

    private fun drawEntities(renderState: RenderState, program: Program, viewMatrixAsBuffer: FloatBuffer, projectionMatrixAsBuffer: FloatBuffer, viewProjectionMatrixAsBuffer: FloatBuffer) {
        program.use()
        bindShaderSpecificsPerCubeMapSide(viewMatrixAsBuffer, projectionMatrixAsBuffer, viewProjectionMatrixAsBuffer, program)
        for (e in renderState.renderBatchesStatic) {
            if (!isInFrustum(camera, e.centerWorld, e.entityMinWorld, e.entityMaxWorld)) {
//				continue;
            }
            draw(renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, program, false, true)
        }
    }

    fun drawFirstPass(sideIndex: Int, camera: Camera, extract: RenderState) {
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, EnvironmentProbeManager.getInstance().getCubeMapArrayRenderTarget().getFrameBufferLocation());
//		EnvironmentProbeManager.getInstance().getCubeMapArrayRenderTarget().resetAttachments();
        environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(0, probe.index, sideIndex)
        environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(1, probe.index, sideIndex)
        environmentProbeManager.cubeMapArrayRenderTarget.setCubeMapFace(2, probe.index, sideIndex)
        gpuContext.clearDepthAndColorBuffer()
        gpuContext.enable(GlCap.CULL_FACE)
        gpuContext.depthMask = true
        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.depthFunc = GlDepthFunc.LEQUAL
        gpuContext.disable(GlCap.BLEND)
        firstPassDefaultProgram.use()
        firstPassDefaultProgram.bindShaderStorageBuffer(1, extract.materialBuffer)
        firstPassDefaultProgram.setUniform("useRainEffect", config.effects.rainEffect.toDouble() != 0.0)
        firstPassDefaultProgram.setUniform("rainEffect", config.effects.rainEffect)
        firstPassDefaultProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        firstPassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", camera.lastViewMatrixAsBuffer)
        firstPassDefaultProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
        firstPassDefaultProgram.setUniform("eyePosition", camera.entity.transform.position)
        firstPassDefaultProgram.setUniform("lightDirection", scene.entitySystems.get(DirectionalLightSystem::class.java).getDirectionalLight()!!.getViewDirection())
        firstPassDefaultProgram.setUniform("near", camera.near)
        firstPassDefaultProgram.setUniform("far", camera.far)
        firstPassDefaultProgram.setUniform("timeGpu", System.currentTimeMillis().toInt())
        for (entity in extract.renderBatchesStatic) {
            draw(extract.vertexIndexBufferStatic.vertexBuffer, extract.vertexIndexBufferStatic.indexBuffer, entity, firstPassDefaultProgram, !entity.isVisibleForCamera, true)
        }
        for (entity in extract.renderBatchesAnimated) {
//			TODO: program usage is wrong for animated things..
            draw(extract.vertexIndexBufferStatic.vertexBuffer, extract.vertexIndexBufferStatic.indexBuffer, entity, firstPassDefaultProgram, !entity.isVisibleForCamera, true)
        }
        gpuContext.enable(GlCap.CULL_FACE)
    }

    fun drawSecondPass(sideIndex: Int, directionalLight: DirectionalLight?, pointLights: List<PointLight>, tubeLights: List<TubeLight>, areaLights: List<AreaLight>) {
        val cubeMapArrayRenderTarget = environmentProbeManager.cubeMapArrayRenderTarget
        val camPosition = entity.transform.position //.negate(null);
        camPosition.add(entity.transform.viewDirection.mul(camera.near))
        val camPositionV4 = Vector4f(camPosition.x, camPosition.y, camPosition.z, 0f)
        gpuContext.depthMask = true
        gpuContext.enable(GlCap.DEPTH_TEST)
        gpuContext.depthFunc = GlDepthFunc.LESS
        gpuContext.enable(GlCap.BLEND)
        gpuContext.blendEquation = BlendMode.FUNC_ADD
        gpuContext.blendFunc(Factor.ONE, Factor.ONE)
        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, cubeMapFaceViews[0][sideIndex])
        gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, cubeMapFaceViews[1][sideIndex])
        gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, cubeMapFaceViews[2][sideIndex])
        gpuContext.bindTexture(4, textureManager.cubeMap)
        secondPassDirectionalProgram.use()
        secondPassDirectionalProgram.setUniform("eyePosition", entity.transform.position)
        secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", config.effects.ambientocclusionRadius)
        secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", config.effects.ambientocclusionTotalStrength)
        secondPassDirectionalProgram.setUniform("screenWidth", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassDirectionalProgram.setUniform("screenHeight", EnvironmentProbeManager.RESOLUTION.toFloat())
        val viewMatrix = camera.viewMatrixAsBuffer
        secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        val projectionMatrix = camera.projectionMatrixAsBuffer
        secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight!!.viewProjectionMatrixAsBuffer)
        secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.entity.transform.viewDirection)
        secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.color)
        secondPassDirectionalProgram.setUniform("currentProbe", probe.index)
        secondPassDirectionalProgram.setUniform("activeProbeCount", environmentProbeManager.probes.size)
        environmentProbeManager.bindEnvironmentProbePositions(secondPassDirectionalProgram)
        //		LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
        fullscreenBuffer.draw()
        gpuContext.enable(GlCap.CULL_FACE)
        doPointLights(camera, pointLights, camPosition, viewMatrix, projectionMatrix)
        gpuContext.disable(GlCap.CULL_FACE)
        doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix)
        doAreaLights(areaLights, viewMatrix, projectionMatrix)
        gpuContext.disable(GlCap.BLEND)
        if (DeferredRenderingBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE) {
            renderReflectionsSecondBounce(viewMatrix, projectionMatrix,
                    cubeMapFaceViews[0][sideIndex],
                    cubeMapFaceViews[1][sideIndex],
                    cubeMapFaceViews[2][sideIndex], sideIndex)
        }
        gpuContext.enable(GlCap.CULL_FACE)
        gpuContext.cullFace = true
        gpuContext.cullMode = CullMode.BACK
        gpuContext.depthFunc = GlDepthFunc.LESS

//		GL11.glDeleteTextures(cubeMapFaceView);
//		GL11.glDeleteTextures(cubeMapFaceView1);
//		GL11.glDeleteTextures(cubeMapFaceView2);
    }

    private fun renderReflectionsSecondBounce(viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer, positionMap: Int, normalMap: Int, colorMap: Int, sideIndex: Int) {
        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, positionMap)
        gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, normalMap)
        gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, colorMap)
        gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_2D, colorMap)
        gpuContext.bindTexture(8, environmentProbeManager.getEnvironmentMapsArray(3))
        gpuContext.bindTexture(9, textureManager.cubeMap)
        gpuContext.bindImageTexture(6, environmentProbeManager.cubeMapArrayRenderTarget.getCubeMapArray(3).id, 0, false, 6 * probe.index + sideIndex, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F)
        tiledProbeLightingProgram.use()
        tiledProbeLightingProgram.setUniform("secondBounce", DeferredRenderingBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE)
        tiledProbeLightingProgram.setUniform("screenWidth", EnvironmentProbeManager.RESOLUTION.toFloat())
        tiledProbeLightingProgram.setUniform("screenHeight", EnvironmentProbeManager.RESOLUTION.toFloat())
        tiledProbeLightingProgram.setUniform("currentProbe", probe.index)
        tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        tiledProbeLightingProgram.setUniform("activeProbeCount", environmentProbeManager.probes.size)
        environmentProbeManager.bindEnvironmentProbePositions(tiledProbeLightingProgram)
        tiledProbeLightingProgram.dispatchCompute(EnvironmentProbeManager.RESOLUTION / 16, EnvironmentProbeManager.RESOLUTION / 16 + 1, 1)
        //		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
    }

    private fun bindShaderSpecificsPerCubeMapSide(viewMatrixAsBuffer: FloatBuffer, projectionMatrixAsBuffer: FloatBuffer, viewProjectionMatrixAsBuffer: FloatBuffer, program: Program) {
        val light = scene!!.entitySystems.get(DirectionalLightSystem::class.java).getDirectionalLight()
        program.setUniform("lightDirection", light!!.entity.transform.viewDirection)
        program.setUniform("lightDiffuse", light.color)
        program.setUniform("lightAmbient", config.effects.ambientLight)
        program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
        program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
        program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)
        program.setUniformAsMatrix4("shadowMatrix", light.viewProjectionMatrixAsBuffer)
    }

    private fun generateCubeMapMipMaps() {
        if (config.quality.isUsePrecomputedRadiance) {
            _generateCubeMapMipMaps()
            if (config.quality.isCalculateActualRadiance) {
                val (_, cubemapArrayColorTextureId, _, internalFormat) = environmentProbeManager.getEnvironmentMapsArray(3)
                val cubeMapView = GL11.glGenTextures()
                GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubemapArrayColorTextureId,
                        internalFormat, 0, EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT,
                        6 * probe.index, 6)
                val cubemapCopy = cubeMapView //TextureManager.copyCubeMap(cubeMapView, EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION, internalFormat);
                //				renderer.getTextureManager().generateMipMapsCubeMap(cubemapCopy);
                val USE_OMPUTE_SHADER_FOR_RADIANCE = true
                if (USE_OMPUTE_SHADER_FOR_RADIANCE) {
                    calculateRadianceCompute(internalFormat, cubemapArrayColorTextureId, cubeMapView, cubemapCopy)
                } else {
                    calculateRadianceFragment(internalFormat, cubemapArrayColorTextureId, cubeMapView, cubemapCopy)
                }
                GL11.glDeleteTextures(cubeMapView)
                //				GL11.glDeleteTextures(cubemapCopy);
            }
        } else {
            _generateCubeMapMipMaps()
        }
    }

    private fun calculateRadianceFragment(internalFormat: Int,
                                          cubemapArrayColorTextureId: Int, cubeMapView: Int, cubemapCopy: Int) {
        cubemapRadianceFragmentProgram.use()
        val cubeMapArrayRenderTarget = environmentProbeManager.cubeMapArrayRenderTarget
        renderTarget.use(gpuContext, false)
        gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP, cubeMapView)
        //GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, renderer.getEnvironmentMap().getTextureId());
        for (i in 0..5) {
            var width = EnvironmentProbeManager.RESOLUTION
            var height = EnvironmentProbeManager.RESOLUTION
            val indexOfFace = 6 * probe.index + i
            for (z in 0 until EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT) {
                //cubeMapArrayRenderTarget.setCubeMapFace(0, probe.getIndex(), indexOfFace);
                renderTarget.setCubeMapFace(0, cubeMapView, i, z)
                width /= 2
                height /= 2
                gpuContext.clearColorBuffer()
                gpuContext.clearColor(0f, 0f, 0f, 0f)
                cubemapRadianceFragmentProgram.setUniform("currentCubemapSide", i)
                cubemapRadianceFragmentProgram.setUniform("currentProbe", probe.index)
                cubemapRadianceFragmentProgram.setUniform("screenWidth", width.toFloat())
                cubemapRadianceFragmentProgram.setUniform("screenHeight", height.toFloat())
                environmentProbeManager.bindEnvironmentProbePositions(cubemapRadianceFragmentProgram)
                gpuContext.viewPort(0, 0, width, height)
                fullscreenBuffer.draw()
            }
        }
    }

    private fun calculateRadianceCompute(internalFormat: Int,
                                         cubemapArrayColorTextureId: Int, cubeMapView: Int, cubemapCopy: Int) {
        cubemapRadianceProgram.use()
        val width = EnvironmentProbeManager.RESOLUTION / 2
        val height = EnvironmentProbeManager.RESOLUTION / 2
        for (i in 0..5) {
            val indexOfFace = 6 * probe.index + i
            for (z in 0 until EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT) {
                GL42.glBindImageTexture(z, cubemapArrayColorTextureId, z + 1, false, indexOfFace, GL15.GL_WRITE_ONLY, internalFormat)
            }
            gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP, cubemapCopy)
            cubemapRadianceProgram.setUniform("currentCubemapSide", i)
            cubemapRadianceProgram.setUniform("currentProbe", probe.index)
            cubemapRadianceProgram.setUniform("screenWidth", width.toFloat())
            cubemapRadianceProgram.setUniform("screenHeight", height.toFloat())
            environmentProbeManager.bindEnvironmentProbePositions(cubemapRadianceProgram)
            cubemapRadianceProgram.dispatchCompute(EnvironmentProbeManager.RESOLUTION / 2 / 32, EnvironmentProbeManager.RESOLUTION / 2 / 32, 1)
        }
    }

    private fun _generateCubeMapMipMaps() {
        val use2DMipMapping = false
        val (_, id, _, internalFormat) = environmentProbeManager.getEnvironmentMapsArray(3)
        if (use2DMipMapping) {
            for (i in 0..5) {
                val cubeMapFaceView = GL11.glGenTextures()
                GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, id, internalFormat, 0, EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6 * probe.index + i, 1)
                textureManager.generateMipMaps(GlTextureTarget.TEXTURE_2D, cubeMapFaceView)
                GL11.glDeleteTextures(cubeMapFaceView)
            }
        } else {
            val cubeMapView = GL11.glGenTextures()
            GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, id, internalFormat, 0, EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6 * probe.index, 6)
            textureManager.generateMipMaps(GlTextureTarget.TEXTURE_CUBE_MAP, cubeMapView)
            GL11.glDeleteTextures(cubeMapView)
        }
    }

    private fun doPointLights(camera: Camera, pointLights: List<PointLight>,
                              camPosition: Vector3f, viewMatrix: FloatBuffer,
                              projectionMatrix: FloatBuffer) {
        if (pointLights.isEmpty()) {
            return
        }
        secondPassPointProgram.use()

//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
        secondPassPointProgram.setUniform("screenWidth", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassPointProgram.setUniform("screenHeight", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassPointProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        var firstLightDrawn = false
        for (i in pointLights.indices) {
            val light = pointLights[i]
            if (!light.isInFrustum(camera)) {
                continue
            }
            val distance = Vector3f()
            light.entity.transform.position.sub(camPosition, distance)
            val lightRadius = light.radius

            // de.hanno.hpengine.camera is inside lights
            if (distance.length() < lightRadius) {
                GL11.glCullFace(GL11.GL_FRONT)
                GL11.glDepthFunc(GL11.GL_GEQUAL)
            } else {
                // de.hanno.hpengine.camera is outside lights, cull back sides
                GL11.glCullFace(GL11.GL_BACK)
                GL11.glDepthFunc(GL11.GL_LEQUAL)
            }

//			secondPassPointProgram.setUniform("currentLightIndex", i);
            secondPassPointProgram.setUniform("lightPosition", light.entity.transform.position)
            secondPassPointProgram.setUniform("lightRadius", lightRadius)
            secondPassPointProgram.setUniform("lightDiffuse", light.color.x, light.color.y, light.color.z)
            fullscreenBuffer.draw()
            light.draw(secondPassPointProgram)
            if (firstLightDrawn) {
                light.drawAgain(null, secondPassPointProgram)
            } else {
                light.draw(secondPassPointProgram)
            }
            firstLightDrawn = true
        }
    }

    private fun doTubeLights(tubeLights: List<TubeLight>,
                             camPositionV4: Vector4f, viewMatrix: FloatBuffer,
                             projectionMatrix: FloatBuffer) {
        if (tubeLights.isEmpty()) {
            return
        }
        secondPassTubeProgram.use()
        secondPassTubeProgram.setUniform("screenWidth", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassTubeProgram.setUniform("screenHeight", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        for (tubeLight in tubeLights) {
            val camInsideLightVolume = AABB(tubeLight.entity.transform.position, Vector3f(tubeLight.length, tubeLight.radius, tubeLight.radius)).contains(camPositionV4)
            if (camInsideLightVolume) {
                GL11.glCullFace(GL11.GL_FRONT)
                GL11.glDepthFunc(GL11.GL_GEQUAL)
            } else {
                GL11.glCullFace(GL11.GL_BACK)
                GL11.glDepthFunc(GL11.GL_LEQUAL)
            }
            secondPassTubeProgram.setUniform("lightPosition", tubeLight.entity.transform.position)
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

    private fun doAreaLights(areaLights: List<AreaLight>, viewMatrix: FloatBuffer, projectionMatrix: FloatBuffer) {
        if (areaLights.isEmpty()) {
            return
        }
        secondPassAreaProgram.use()
        secondPassAreaProgram.setUniform("screenWidth", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassAreaProgram.setUniform("screenHeight", EnvironmentProbeManager.RESOLUTION.toFloat())
        secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
        secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
        gpuContext.disable(GlCap.CULL_FACE)
        gpuContext.disable(GlCap.DEPTH_TEST)
        for (areaLight in areaLights) {
//			boolean camInsideLightVolume = new AABB(areaLight.position, 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
//			if (camInsideLightVolume) {
//				GL11.glCullFace(GL11.GL_FRONT);
//				GL11.glDepthFunc(GL11.GL_GEQUAL);
//			} else {
//				GL11.glCullFace(GL11.GL_BACK);
//				GL11.glDepthFunc(GL11.GL_LEQUAL);
//			}
            secondPassAreaProgram.setUniform("lightPosition", areaLight.entity.transform.position)
            secondPassAreaProgram.setUniform("lightRightDirection", areaLight.entity.transform.rightDirection)
            secondPassAreaProgram.setUniform("lightViewDirection", areaLight.entity.transform.viewDirection)
            secondPassAreaProgram.setUniform("lightUpDirection", areaLight.entity.transform.upDirection)
            secondPassAreaProgram.setUniform("lightWidth", areaLight.width)
            secondPassAreaProgram.setUniform("lightHeight", areaLight.height)
            secondPassAreaProgram.setUniform("lightRange", areaLight.range)
            secondPassAreaProgram.setUniform("lightDiffuse", areaLight.color)
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", scene.getAreaLightSystem().getShadowMatrixForAreaLight(areaLight))

            // TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = TextureManager.getInstance().getDiffuseTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureId());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
            gpuContext.bindTexture(9, GlTextureTarget.TEXTURE_2D, scene.getAreaLightSystem().getDepthMapForAreaLight(areaLight))
            fullscreenBuffer.draw()
            //			areaLight.getVertexBuffer().drawDebug();
        }
    }

    private fun rotateForIndex(i: Int, camera: Entity) {
        val deltaNear = 0.0f
        val deltaFar = 100.0f
        val halfSizeX = probe.size.x / 2
        val halfSizeY = probe.size.y / 2
        val halfSizeZ = probe.size.z / 2
        val position = camera.transform.position
        val width = probe.size.x
        val height = probe.size.y
        when (i) {
            0 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(-90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
                probe.camera.far = halfSizeX * deltaFar
            }
            1 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
                probe.camera.far = halfSizeX * deltaFar
            }
            2 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(1f, 0f, 0f, Math.toRadians(90.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(180.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
                probe.camera.far = halfSizeY * deltaFar
            }
            3 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(1f, 0f, 0f, Math.toRadians(-90.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
                probe.camera.far = halfSizeY * deltaFar
            }
            4 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                camera.transform.rotate(AxisAngle4f(0f, 1f, 0f, Math.toRadians(-180.0).toFloat()))
                //			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
                probe.camera.far = halfSizeZ * deltaFar
            }
            5 -> {
                camera.transform.rotation(Quaternionf().identity())
                camera.transform.rotate(AxisAngle4f(0f, 0f, 1f, Math.toRadians(180.0).toFloat()))
                //			de.hanno.hpengine.camera.rotateWorld(new Vector4f(0, 1, 0, 180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
                probe.camera.far = halfSizeZ * deltaFar
            }
            else -> {
            }
        }
    }

    companion object {
        @Volatile
        var deferredRenderingForProbes = false
    }

    init {
        gpuContext = programManager.gpuContext
        this.config = config
        this.textureManager = textureManager
        val camera = Camera(entity, 0.1f, 5000f, 90f, 1f)
        entity.addComponent(camera)
        camera.width = width.toFloat()
        camera.width = height.toFloat()
        this.camera = camera
        entity.transform.translate(position)
        this.probe = probe
        val far = 5000f
        val near = 0.1f
        val fov = 90f
        camera.far = far
        camera.near = near
        camera.fov = fov
        camera.ratio = 1f
        entity.parent = probe.entity
        val cubeMapCamInitialOrientation = Quaternionf().identity()
        entity.transform.rotate(cubeMapCamInitialOrientation)
        cubeMapProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl")
        depthPrePassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl")
        cubeMapLightingProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_lighting_fragment.glsl")
        tiledProbeLightingProgram = programManager.getComputeProgram("tiled_probe_lighting_probe_rendering_compute.glsl")
        cubemapRadianceProgram = programManager.getComputeProgram("cubemap_radiance_compute.glsl")
        cubemapRadianceFragmentProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "cubemap_radiance_fragment.glsl")
        secondPassPointProgram = programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "second_pass_point_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "second_pass_point_fragment.glsl")))
        secondPassTubeProgram = programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "second_pass_point_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "second_pass_tube_fragment.glsl")))
        secondPassAreaProgram = programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "second_pass_area_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "second_pass_area_fragment.glsl")))
        secondPassDirectionalProgram = programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "second_pass_directional_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "second_pass_directional_fragment.glsl")))
        firstPassDefaultProgram = programManager.getProgram(FileBasedCodeSource(File(Shader.directory + "first_pass_vertex.glsl")), FileBasedCodeSource(File(Shader.directory + "first_pass_fragment.glsl")))
        val cubeMapArrayRenderTarget = environmentProbeManager.cubeMapArrayRenderTarget
        cubeMapView = GL11.glGenTextures()
        cubeMapView1 = GL11.glGenTextures()
        cubeMapView2 = GL11.glGenTextures()
        exitOnGLError("EnvironmentSampler before view creation")
        val diffuseInternalFormat = cubeMapArrayRenderTarget.getCubeMapArray(3).internalFormat
        for (z in 0..5) {
            cubeMapFaceViews[0][z] = GL11.glGenTextures()
            cubeMapFaceViews[1][z] = GL11.glGenTextures()
            cubeMapFaceViews[2][z] = GL11.glGenTextures()
            cubeMapFaceViews[3][z] = GL11.glGenTextures()
            //GL43.glTextureView(cubeMapFaceViews[i][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(i).getTextureId(), cubeMapArrayRenderTarget.getCubeMapArray(i).getInternalFormat(), 0, 1, 6 * probe.getIndex() + z, 1);
            GL43.glTextureView(cubeMapFaceViews[0][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).id, cubeMapArrayRenderTarget.getCubeMapArray(0).internalFormat, 0, 1, 6 * probeIndex + z, 1)
            GL43.glTextureView(cubeMapFaceViews[1][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).id, cubeMapArrayRenderTarget.getCubeMapArray(1).internalFormat, 0, 1, 6 * probeIndex + z, 1)
            GL43.glTextureView(cubeMapFaceViews[2][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).id, cubeMapArrayRenderTarget.getCubeMapArray(2).internalFormat, 0, 1, 6 * probeIndex + z, 1)
            GL43.glTextureView(cubeMapFaceViews[3][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(3).id, diffuseInternalFormat, 0, 1, 6 * probeIndex + z, 1)
        }
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).id, cubeMapArrayRenderTarget.getCubeMapArray(0).internalFormat, 0, EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6 * probeIndex, 6)
        GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).id, cubeMapArrayRenderTarget.getCubeMapArray(1).internalFormat, 0, EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6 * probeIndex, 6)
        GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).id, cubeMapArrayRenderTarget.getCubeMapArray(2).internalFormat, 0, EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6 * probeIndex, 6)
        renderTarget = invoke(
                gpuContext,
                invoke(gpuContext, invoke(gpuContext, EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION)),
                EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION,
                listOf(ColorAttachmentDefinition("Environment Diffuse", diffuseInternalFormat)).toTextures(gpuContext, EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION),
                "Environment Sampler"
        )
        fullscreenBuffer = QuadVertexBuffer(gpuContext, true)
        fullscreenBuffer.upload()
        exitOnGLError("EnvironmentSampler constructor")
    }
}