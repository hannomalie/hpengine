package de.hanno.hpengine.engine.camera

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.renderer.addLine
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.koin.core.component.get

class CameraRenderSystem(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>
): RenderSystem {

    private val frustumLines = renderStateManager.renderState.registerState { mutableListOf<Vector3fc>() }
    private val lineVertices = PersistentMappedStructBuffer(24, gpuContext, { HpVector4f() })

    override fun render(result: DrawResult, renderState: RenderState) {
        if (config.debug.isDrawCameras) {
            drawLines(renderStateManager, programManager, lineVertices, renderState[frustumLines], color = Vector3f(1f, 0f, 0f))
        }
    }

    override fun extract(scene: Scene, renderState: RenderState) {
        if (config.debug.isDrawCameras) {
            renderState[frustumLines].apply {
                clear()
                val components = scene.get<CameraComponentSystem>().components
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
}
class CameraComponentSystem(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>
): ComponentSystem<Camera> {
    override val componentClass: Class<Camera> = Camera::class.java
    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        for (component in components) {
            component.update(scene, deltaSeconds)
        }
    }
    private val _components = mutableListOf<Camera>()
    override val components: List<Camera>
        get() = _components

    fun create(entity: Entity, projectionMatrix: Matrix4f, near:Float, far:Float, fov:Float, ratio:Float, perspective:Boolean): Camera {
        return Camera(entity, projectionMatrix, near, far, fov, ratio).apply { this.perspective = perspective }.also { _components.add(it); }
    }

    override fun addComponent(component: Camera) {
        _components.add(component)
    }
    override fun clear() = _components.clear()

}

fun Camera(
    engineContext: EngineContext,
    entity: Entity
) = Camera(entity, engineContext.config.width.toFloat() / engineContext.config.height.toFloat())