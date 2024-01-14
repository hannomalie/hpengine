package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.primaryCameraTag
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import org.koin.core.annotation.Single
import kotlin.reflect.KMutableProperty0


data class CameraSelection(override val entity: Int, override val components: Bag<Component>, val cameraComponent: CameraComponent): EntitySelection

@Single(binds = [EditorExtension::class])
class CameraEditorExtension(
    private val tagManager: TagManager
) : EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = component as? CameraSelection ?: (component as? CameraComponent)?.let { CameraSelection(
        entity,
        components,
        it,
    ) }

    override fun Window.renderRightPanel(selection: Selection?): Boolean = if(selection is CameraSelection) {
        cameraInputs(selection, tagManager)
        true
    } else {
        false
    }
}

private fun Window.cameraInputs(selection: CameraSelection, tagManager: TagManager) {
    val isPrimaryCamera = tagManager.getEntityId(primaryCameraTag) == selection.entity
    checkBox("Active", isPrimaryCamera) {
        tagManager.register(primaryCameraTag, selection.entity)
    }
    floatInput("Near plane", selection.cameraComponent.camera::near, min = 0.0001f, max = 10f)
    floatInput("Far plane", selection.cameraComponent.camera::far, min = 1f, max = 2000f)
    floatInput("Field of view", selection.cameraComponent.camera::fov, min = 45f, max = 170f)
    floatInput("Exposure", selection.cameraComponent.camera::exposure, min = 0.1f, max = 10f)
    checkBox("Lens flare", selection.cameraComponent.camera::lensFlare)
    checkBox("Depth of field", selection.cameraComponent.camera::dof)
    checkBox("Auto exposure", selection.cameraComponent.camera::autoExposure)
}

