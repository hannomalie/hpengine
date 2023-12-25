package de.hanno.hpengine.graphics.light.directional

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.managers.TagManager
import de.hanno.hpengine.Transform
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.forFirstEntityIfPresent
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.component.primaryCameraTag
import de.hanno.hpengine.cycle.CycleSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.spatial.WorldAABB
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.EntityMovementSystem
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW
import struktgen.api.forIndex


@Single(binds = [BaseSystem::class, DirectionalLightSystem::class])
@All(DirectionalLightComponent::class, TransformComponent::class)
class DirectionalLightSystem(
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val input: Input,
    private val entityMovementSystem: EntityMovementSystem,
    private val worldAABB: WorldAABB,
    private val tagManager: TagManager,
) : BaseEntitySystem(), Extractor, WorldPopulator {
    lateinit var cycleSystem: CycleSystem
    lateinit var directionalLightComponentMapper: ComponentMapper<DirectionalLightComponent>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var cameraComponentMapper: ComponentMapper<CameraComponent>

    private val lightState = directionalLightStateHolder.lightState

    override fun processSystem() {
        val moveAmount = 100 * world.delta
        val degreesPerSecond = 45f
        val rotateAmount = Math.toRadians(degreesPerSecond.toDouble()).toFloat() * world.delta

        val primaryCamera = tagManager.getEntityId(primaryCameraTag)
        val primaryCameraTrafo = if(primaryCamera == -1) null else transformComponentMapper.getOrNull(primaryCamera)

        forEachEntity {
            val camera = cameraComponentMapper[it]
            camera.width = 2 * worldAABB.aabb.boundingSphereRadius
            camera.height = 2 * worldAABB.aabb.boundingSphereRadius

            val transform = transformComponentMapper[it].transform

            primaryCameraTrafo?.let {
                val orientation = transform.transformation.getRotation(AxisAngle4f())
                val c = it.transform.center
                val ci = Vector3i(c.x.toInt(), c.y.toInt(), c.z.toInt())
                transform.translation(Vector3f(ci.x.toFloat(), ci.y.toFloat(), ci.z.toFloat()))
                transform.rotate(orientation)
            }

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
        }
    }

    override fun extract(currentWriteState: RenderState) {
        forFirstEntityIfPresent { entityId ->
            val light = directionalLightComponentMapper.get(entityId)
            val transform = transformComponentMapper.get(entityId).transform
            val camera = cameraComponentMapper.get(entityId)

            currentWriteState[directionalLightStateHolder.entityId].underlying = entityId
            currentWriteState[directionalLightStateHolder.directionalLightHasMovedInCycle].underlying = entityMovementSystem.cycleEntityHasMovedIn(entityId)
            currentWriteState[lightState].typedBuffer.forIndex(0) { directionalLightState ->
                directionalLightState.color.set(light.color)
                directionalLightState.direction.set(transform.viewDirection)
                directionalLightState.scatterFactor = light.scatterFactor
                val viewMatrix = Matrix4f(transform).invert()
                directionalLightState.viewMatrix.set(viewMatrix)
                directionalLightState.projectionMatrix.set(camera.projectionMatrix)
                directionalLightState.viewProjectionMatrix.set(Matrix4f(camera.projectionMatrix).mul(viewMatrix))
            }
        }
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
        create(
            CameraComponent()::class.java
        ).apply {
            width = 1500f
            height = 1500f
            far = (-5000).toFloat()
            perspective = false
        }
    }
}
