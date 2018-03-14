package de.hanno.hpengine.engine.graphics.light.area

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
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
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import java.io.File
import java.nio.FloatBuffer
import java.util.*

class AreaLightComponentSystem: SimpleComponentSystem<AreaLight>(theComponentClass = AreaLight::class.java, factory = { TODO("not implemented") })

class AreaLightSystem(engine: Engine, scene: Scene) : SimpleEntitySystem(engine, scene, listOf(AreaLight::class.java)), StateConsumer {
    private val cameraEntity: Entity = Entity()
    private val camera = Camera(cameraEntity, Util.createPerspective(90f, 1f, 1f, 500f), 1f, 500f, 90f, 1f)

    private val renderTarget: RenderTarget = RenderTargetBuilder(engine.gpuContext)
            .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
            .setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
            .add(ColorAttachmentDefinition()
                    .setInternalFormat(GL30.GL_RGBA32F)
                    .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
            .build()

    private val areaShadowPassProgram: Program = engine.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "mvp_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(File(Shader.getDirectory() + "shadowmap_fragment.glsl")), Defines())
    private val areaLightDepthMaps = ArrayList<Int>().apply {
        engine.gpuContext.execute {
            for (i in 0 until MAX_AREALIGHT_SHADOWMAPS) {
                val renderedTextureTemp = engine.gpuContext.genTextures()
                engine.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp)
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, AREALIGHT_SHADOWMAP_RESOLUTION / 2, AREALIGHT_SHADOWMAP_RESOLUTION / 2, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, null as FloatBuffer?)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                add(renderedTextureTemp)
            }
        }
    }

    private val areaLightsForwardMaxCount = 5
    private var areaLightPositions = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
    var areaLightColors = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
    var areaLightWidthHeightRanges = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
    var areaLightViewDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
    var areaLightUpDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)
    var areaLightRightDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3)


    fun getAreaLightPositions(): FloatBuffer {
        updateAreaLightArrays()
        return areaLightPositions
    }

    private fun updateAreaLightArrays() {
        val positions = FloatArray(areaLightsForwardMaxCount * 3)
        val colors = FloatArray(areaLightsForwardMaxCount * 3)
        val widthHeightRanges = FloatArray(areaLightsForwardMaxCount * 3)
        val viewDirections = FloatArray(areaLightsForwardMaxCount * 3)
        val upDirections = FloatArray(areaLightsForwardMaxCount * 3)
        val rightDirections = FloatArray(areaLightsForwardMaxCount * 3)

        val areaLights = getComponents(AreaLight::class.java)
        for (i in 0 until Math.min(areaLightsForwardMaxCount, areaLights.size)) {
            val light = areaLights[i]
            positions[3 * i] = light.entity.position.x
            positions[3 * i + 1] = light.entity.position.y
            positions[3 * i + 2] = light.entity.position.z

            colors[3 * i] = light.color.x
            colors[3 * i + 1] = light.color.y
            colors[3 * i + 2] = light.color.z

            widthHeightRanges[3 * i] = light.width
            widthHeightRanges[3 * i + 1] = light.height
            widthHeightRanges[3 * i + 2] = light.range

            viewDirections[3 * i] = light.entity.viewDirection.x
            viewDirections[3 * i + 1] = light.entity.viewDirection.y
            viewDirections[3 * i + 2] = light.entity.viewDirection.z

            upDirections[3 * i] = light.entity.upDirection.x
            upDirections[3 * i + 1] = light.entity.upDirection.y
            upDirections[3 * i + 2] = light.entity.upDirection.z

            rightDirections[3 * i] = light.entity.rightDirection.x
            rightDirections[3 * i + 1] = light.entity.rightDirection.y
            rightDirections[3 * i + 2] = light.entity.rightDirection.z
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
            areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", light.entity.viewMatrixAsBuffer)
            areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", light.camera.projectionMatrixAsBuffer)
            //			directionalShadowPassProgram.setUniform("near", de.hanno.hpengine.camera.getNear());
            //			directionalShadowPassProgram.setUniform("far", de.hanno.hpengine.camera.getFar());

            for (e in renderState.renderBatchesStatic) {
                //				TODO: Use model component index here
                //				areaShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
                //				modelComponent.getMaterials().setTexturesActive(areaShadowPassProgram);
                //				areaShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterials().hasDiffuseMap());
                //				areaShadowPassProgram.setUniform("color", modelComponent.getMaterials().getDiffuse());

                DrawStrategy.draw(engine.gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, e, areaShadowPassProgram, e.isVisible)
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
        cameraEntity.setTranslation(light.entity.position.negate(null!!))
        //        de.hanno.hpengine.camera.getOrientation().x = -lights.getOrientation().x;
        //        de.hanno.hpengine.camera.getOrientation().y = -lights.getOrientation().y;
        //        de.hanno.hpengine.camera.getOrientation().z = -lights.getOrientation().z;
        //        de.hanno.hpengine.camera.getOrientation().w = -lights.getOrientation().w;
        //        de.hanno.hpengine.camera.rotate(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
        //return de.hanno.hpengine.camera.getComponent(CameraComponent.class).getCamera();
        return camera
    }
    fun getShadowMatrixForAreaLight(light: AreaLight): FloatBuffer {
        //        c.getOrientation().x = lights.getOrientation().negate(null).x;
        //        c.getOrientation().y = lights.getOrientation().negate(null).y;
        //        c.getOrientation().z = lights.getOrientation().negate(null).z;
        //        c.getOrientation().w = lights.getOrientation().negate(null).w;
        //        c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP

        //        Vector3f newPosition = new Vector3f(-lights.getPosition().x, -lights.getPosition().y, -lights.getPosition().y);
        //        newPosition = new Vector3f(0, 0, 10);
        //        c.setPosition(newPosition);
        //        c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
        //        c.updateShadow();
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