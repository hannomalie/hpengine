package de.hanno.hpengine.engine.camera

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.addLine
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc

class CameraComponentSystem(val engineContext: EngineContext): ComponentSystem<Camera>, RenderSystem {
    private val frustumLines = engineContext.renderStateManager.renderState.registerState { mutableListOf<Vector3fc>() }
    private val lineVertices = PersistentMappedStructBuffer(24, engineContext.gpuContext, { HpVector4f() })
    override val componentClass: Class<Camera> = Camera::class.java
    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        for (component in getComponents()) {
            component.update(scene, deltaSeconds)
        }
    }
    private val components = mutableListOf<Camera>()
    override fun getComponents(): List<Camera> = components

    fun create(entity: Entity) = Camera(entity, engineContext.config.width.toFloat() / engineContext.config.height.toFloat())
    fun create(entity: Entity, projectionMatrix: Matrix4f, near:Float, far:Float, fov:Float, ratio:Float, perspective:Boolean) = Camera(entity, projectionMatrix, near, far, fov, ratio).apply { this.perspective = perspective }.also { components.add(it); }

    override fun addComponent(component: Camera) {
        components.add(component)
    }
    override fun clear() = components.clear()

    override fun extract(renderState: RenderState) {
        if (engineContext.config.debug.isDrawCameras) {
            renderState[frustumLines].apply {
                clear()
                components.indices.forEach { i ->
                    val corners = components[i].frustumCorners

                    addLine(corners[0], corners[1])
                    addLine(corners[1], corners[2])
                    addLine(corners[2], corners[3])
                    addLine(corners[3], corners[0])

                    addLine(corners[4], corners[5])
                    addLine(corners[5], corners[6])
                    addLine(corners[6], corners[7])
                    addLine(corners[7], corners[4])

                    addLine(corners[0], corners[6])
                    addLine(corners[1], corners[7])
                    addLine(corners[2], corners[4])
                    addLine(corners[3], corners[5])
                }
            }
        }

    }
    override fun render(result: DrawResult, renderState: RenderState) {
        if (engineContext.config.debug.isDrawCameras) {
            engineContext.drawLines(lineVertices, renderState[frustumLines], color = Vector3f(1f, 0f, 0f))
        }
    }
}