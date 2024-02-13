package de.hanno.hpengine.graphics.editor.select

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.Entity
import com.artemis.annotations.All
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.artemis.forFirstEntityIfPresent
import de.hanno.hpengine.component.*
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.light.point.PointLightComponent
import de.hanno.hpengine.math.OmniCamera
import org.jetbrains.kotlin.ir.util.transformIfNeeded
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Vector3f
import org.koin.core.annotation.Single


data class PointLightSelection(
    val entity: Int,
    val pointLightComponent: PointLightComponent,
    val transformComponent: TransformComponent,
    val cameraComponent: CameraComponent,
    val omniCamera: OmniCamera,
): Selection

@Single(binds = [EditorExtension::class, BaseSystem::class])
@All(DefaultPrimaryCameraComponent::class)
class PointLightEditorExtension(
    private val tagManager: TagManager,
) : EditorExtension, BaseEntitySystem() {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = if (component is PointLightComponent) {
        PointLightSelection(
            entity,
            component,
            components.firstIsInstance(),
            components.firstIsInstance(),
            OmniCamera(Vector3f()).apply {
                updatePosition(components.firstIsInstance<TransformComponent>().transform.position)
            }
        )
    } else null

    override fun Window.renderRightPanel(selection: Selection?): Boolean = if (selection is PointLightSelection) {
        floatInput("Radius", selection.pointLightComponent::radius, min = 1f, max = 100f)
        checkBox("Shadow", selection.pointLightComponent::shadow)

        selection.omniCamera.cameras.forEachIndexed { index, camera ->
            val isPrimaryCamera = tagManager.getEntityId(primaryCameraTag) == selection.entity && selection.cameraComponent.camera == camera

            checkBox("Active[$index]", isPrimaryCamera) {
                if (it) {
                    selection.cameraComponent.camera = camera
                    tagManager.register(primaryCameraTag, selection.entity)
                } else {
                    if (defaultPrimaryCameraId != -1) {
                        tagManager.register(primaryCameraTag, defaultPrimaryCameraId)
                    }
                }
            }
        }

        true
    } else {
        false
    }

    private var defaultPrimaryCameraId = -1
    override fun processSystem() {
        forFirstEntityIfPresent {
            defaultPrimaryCameraId = it
        }
    }
}


