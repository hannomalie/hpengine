package de.hanno.hpengine.component

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.managers.TagManager
import de.hanno.hpengine.Transform
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.math.createOrthogonal
import de.hanno.hpengine.math.createPerspective
import de.hanno.hpengine.system.Extractor
import org.joml.Matrix4f
import org.koin.core.annotation.Single

class CameraComponent: Component() {
    val camera = Camera(Transform())
}

@Single(binds=[BaseSystem::class, CameraSystem::class])
@All(CameraComponent::class)
class CameraSystem(
    private val tagManager: TagManager,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : BaseEntitySystem(), WorldPopulator , Extractor{
    lateinit var cameraComponentMapper: ComponentMapper<CameraComponent>
    override fun processSystem() { }

    override fun World.populate() {
        addPrimaryCamera()
    }

    override fun extract(currentWriteState: RenderState) {
        tagManager.getEntity(primaryCameraTag)?.let { primaryCamera ->
            currentWriteState[primaryCameraStateHolder.camera].apply {
                cameraComponentMapper[primaryCamera].let { component ->
                    // TODO: Set other values here
                    lensFlare = component.camera.lensFlare
                }
            }
        }
    }
}

const val primaryCameraTag = "PRIMARY_CAMERA"

fun World.addPrimaryCamera() {
    edit(create()).apply {
        create(TransformComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "PrimaryCamera"
        }
        create(CameraComponent::class.java)

        getSystem(TagManager::class.java).register(primaryCameraTag, entityId)
    }
}