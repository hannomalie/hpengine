package de.hanno.hpengine.component

import com.artemis.*
import com.artemis.annotations.All
import com.artemis.managers.TagManager
import de.hanno.hpengine.Transform
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.system.Extractor
import org.koin.core.annotation.Single

class CameraComponent(var camera: Camera): Component() {
    constructor(transform: Transform): this(Camera(transform))
    constructor(): this(Camera(Transform()))
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
                    val cameraState = currentWriteState[primaryCameraStateHolder.camera]
                    cameraState.setFrom(component.camera)
                }
            }
        }
    }
}

const val primaryCameraTag = "PRIMARY_CAMERA"

class DefaultPrimaryCameraComponent: Component()
fun World.addPrimaryCamera() {
    edit(create()).apply {
        val transform = create(TransformComponent::class.java).transform
        create(DefaultPrimaryCameraComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "PrimaryCamera"
        }
        add(CameraComponent(Camera(transform)))

        getSystem(TagManager::class.java).register(primaryCameraTag, entityId)
    }
}