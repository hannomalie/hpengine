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
import de.hanno.hpengine.spatial.WorldAABB
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.EntityMovementSystem
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.annotation.Single
import struktgen.api.forIndex


@Single(binds = [BaseSystem::class, DirectionalLightSystem::class])
@All(DirectionalLightComponent::class, TransformComponent::class)
class DirectionalLightSystem(
    private val directionalLightStateHolder: DirectionalLightStateHolder,
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
        val primaryCamera = tagManager.getEntityId(primaryCameraTag)
        val primaryCameraTrafo = if(primaryCamera == -1) null else transformComponentMapper.getOrNull(primaryCamera)

        forEachEntity {
            val transform = transformComponentMapper[it].transform
            val light = directionalLightComponentMapper[it]

//            TODO: Do this for a smaller shadow map only
//            val camera = cameraComponentMapper[it]
//            camera.width = 2 * worldAABB.aabb.boundingSphereRadius
//            camera.height = 2 * worldAABB.aabb.boundingSphereRadius
//            primaryCameraTrafo?.let {
//                val orientation = transform.transformation.getRotation(AxisAngle4f())
//                val c = it.transform.center
//                val ci = Vector3i(c.x.toInt(), c.y.toInt(), c.z.toInt())
//                transform.translation(Vector3f(ci.x.toFloat(), ci.y.toFloat(), ci.z.toFloat()))
//                transform.rotate(orientation)
//            }

            transform.identity()
            transform.lookAt(
                Vector3f(light.direction).mul(-light.height), Vector3f(), Vector3f(0f, 1f, 0f)
            ).invert()
        }
    }

    override fun extract(currentWriteState: RenderState) {
        forFirstEntityIfPresent { entityId ->
            val light = directionalLightComponentMapper.get(entityId)
            val camera = cameraComponentMapper.get(entityId).camera

            currentWriteState[directionalLightStateHolder.entityId].underlying = entityId
            currentWriteState[directionalLightStateHolder.directionalLightHasMovedInCycle].underlying = entityMovementSystem.cycleEntityHasMovedIn(entityId)
            currentWriteState[lightState].typedBuffer.forIndex(0) { directionalLightState ->
                directionalLightState.color.set(light.color)
                directionalLightState.direction.apply {
                    set(light.direction)
                }
                directionalLightState.scatterFactor = light.scatterFactor
                val viewMatrix = Matrix4f(camera.viewMatrix)
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
    val transform = Transform().apply {
        translate(Vector3f(12f, 300f, 2f))
        rotateAroundLocal(Quaternionf(AxisAngle4f(Math.toRadians(100.0).toFloat(), -1f, 0f, 0f)), 0f, 0f, 0f)
    }
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "DirectionalLight"
        }

        create(DirectionalLightComponent::class.java).apply { }
        create(TransformComponent::class.java).apply {
            this.transform = transform
        }
        add(
            CameraComponent(transform).apply {
                camera.width = 1500f
                camera.height = 1500f
                camera.far = 2000f
                camera.perspective = false
            }
        )
    }
}
