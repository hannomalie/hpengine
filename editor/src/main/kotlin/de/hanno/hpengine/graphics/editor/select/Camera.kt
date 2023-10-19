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


data class CameraSelection(override val entity: Int, override val components: Bag<Component>, val cameraComponent: CameraComponent): EntitySelection

@Single(binds = [EditorExtension::class])
class CameraEditorExtension(
    private val tagManager: TagManager
) : EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = component as? CameraSelection ?: (component as? CameraComponent)?.let { CameraSelection(
        entity!!,
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

private fun Window.cameraInputs(
    selection: CameraSelection,
    tagManager: TagManager,
) {
    val isPrimaryCamera = tagManager.getEntityId(primaryCameraTag) == selection.entity
    checkBox("Active", isPrimaryCamera) {
        tagManager.register(primaryCameraTag, selection.entity)
    }
    floatInput(
        "Near plane",
        selection.cameraComponent.near,
        min = 0.0001f,
        max = 10f
    ) { floatArray ->
        selection.cameraComponent.near = floatArray[0]
    }
    floatInput("Far plane", selection.cameraComponent.far, min = 1f, max = 2000f) { floatArray ->
        selection.cameraComponent.far = floatArray[0]
    }
    floatInput(
        "Field of view",
        selection.cameraComponent.fov,
        min = 45f,
        max = 170f
    ) { floatArray ->
        selection.cameraComponent.fov = floatArray[0]
    }
}
