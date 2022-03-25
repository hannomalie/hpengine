package de.hanno.hpengine.engine.graphics.light.directional

import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.engine.component.artemis.DirectionalLightComponent
import de.hanno.hpengine.engine.component.artemis.forEachEntity
import de.hanno.hpengine.engine.entity.CycleSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.input.Input
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW

@All(DirectionalLightComponent::class)
class DirectionalLightSystem: BaseEntitySystem() {
    lateinit var input: Input
    lateinit var cycleSystem: CycleSystem
    lateinit var  directionalLightComponentMapper: ComponentMapper<DirectionalLightComponent>

    override fun processSystem() {
        val moveAmount = 100 * world.delta
        val degreesPerSecond = 45f
        val rotateAmount = Math.toRadians(degreesPerSecond.toDouble()).toFloat() * world.delta

        forEachEntity {
            val transform = directionalLightComponentMapper[it].transform

            if (input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                transform.rotateAround(Vector3f(0f, 1f, 0f), rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                transform.rotateAround(Vector3f(0f, 1f, 0f), -rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
                transform.rotateAround(Vector3f(1f, 0f, 0f), rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
                transform.rotateAround(Vector3f(1f, 0f, 0f), -rotateAmount, Vector3f())
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_8)) {
                transform.translate(Vector3f(0f, -moveAmount, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_2)) {
                transform.translate(Vector3f(0f, moveAmount, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_4)) {
                transform.translate(Vector3f(-moveAmount, 0f, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_6)) {
                transform.translate(Vector3f(moveAmount, 0f, 0f))
            }
        }
    }

    fun extract(renderState: RenderState) {
//        TODO: Reimplement
//        renderState.directionalLightHasMovedInCycle = this.directionalLightMovedInCycle
//
//        renderState.directionalLightHasMovedInCycle = this.directionalLightMovedInCycle
//        val light = getDirectionalLight() ?: return
//
//        with(light) {
//            val directionalLightState = renderState.directionalLightState[0]
//
//            directionalLightState.color.set(color)
//            directionalLightState.direction.set(direction)
//            directionalLightState.scatterFactor = scatterFactor
//            directionalLightState.viewMatrix.set(viewMatrix)
//            directionalLightState.projectionMatrix.set(projectionMatrix)
//            directionalLightState.viewProjectionMatrix.set(viewProjectionMatrix)
//        }
    }
}
