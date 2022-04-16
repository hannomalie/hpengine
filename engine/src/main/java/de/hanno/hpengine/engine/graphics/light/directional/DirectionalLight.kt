package de.hanno.hpengine.engine.graphics.light.directional

import com.artemis.*
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.WorldPopulator
import de.hanno.hpengine.engine.component.artemis.DirectionalLightComponent
import de.hanno.hpengine.engine.component.artemis.NameComponent
import de.hanno.hpengine.engine.component.artemis.TransformComponent
import de.hanno.hpengine.engine.component.artemis.forEachEntity
import de.hanno.hpengine.engine.entity.CycleSystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.system.Extractor
import de.hanno.hpengine.engine.transform.Transform
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW


@All(DirectionalLightComponent::class, TransformComponent::class)
class DirectionalLightSystem: BaseEntitySystem(), Extractor, WorldPopulator {
    @Wire
    lateinit var input: Input
    lateinit var cycleSystem: CycleSystem
    lateinit var directionalLightComponentMapper: ComponentMapper<DirectionalLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    override fun processSystem() {
        val moveAmount = 100 * world.delta
        val degreesPerSecond = 45f
        val rotateAmount = Math.toRadians(degreesPerSecond.toDouble()).toFloat() * world.delta

        forEachEntity {
            val transform = transformComponentMapper[it].transform

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

    override fun extract(currentWriteState: RenderState) {
        currentWriteState.directionalLightHasMovedInCycle = currentWriteState.cycle
        val entries: Map<Int, Bag<Component>> = currentWriteState.componentsForEntities.filterValues { it.firstIsInstanceOrNull<DirectionalLightComponent>() != null }
        if(entries.isEmpty()) return

        val light = entries.entries.first().value.firstIsInstance<DirectionalLightComponent>()
        val transform = entries.entries.first().value.firstIsInstance<TransformComponent>().transform

        val directionalLightState = currentWriteState.directionalLightState[0]

        directionalLightState.color.set(light.color)
        directionalLightState.direction.set(transform.viewDirection)
        directionalLightState.scatterFactor = light.scatterFactor
        val viewMatrix = Matrix4f(transform).invert()
        directionalLightState.viewMatrix.set(viewMatrix)
        directionalLightState.projectionMatrix.set(light.camera.projectionMatrix)
        directionalLightState.viewProjectionMatrix.set(Matrix4f(light.camera.projectionMatrix).mul(viewMatrix))
    }

    override fun World.populate() {
        addDirectionalLight()
    }
}


fun World.addDirectionalLight() {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "DirectionalLight"
        }

        create(DirectionalLightComponent::class.java).apply { }
        create(TransformComponent::class.java).apply {
            transform = Transform().apply {
                translate(Vector3f(12f, 300f, 2f))
                rotateAroundLocal(Quaternionf(AxisAngle4f(Math.toRadians(100.0).toFloat(), 1f, 0f, 0f)), 0f, 0f, 0f)
            }
        }
    }
}
