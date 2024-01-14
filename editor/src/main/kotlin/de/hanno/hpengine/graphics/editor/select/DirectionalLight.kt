package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.graphics.imgui.float2Input
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.light.directional.DirectionalLightComponent
import org.koin.core.annotation.Single

data class DirectionalLightSelection(val directionalLight: DirectionalLightComponent): Selection

@Single(binds = [EditorExtension::class])
class DirectionalLightEditorExtension(
) : EditorExtension {
    override fun getSelectionForComponentOrNull(
        component: Component,
        entity: Int,
        components: Bag<Component>
    ): Selection? {
        return when (component) {
            is DirectionalLightComponent -> return DirectionalLightSelection(component)
            else -> null
        }
    }
    override fun Window.renderRightPanel(selection: Selection?): Boolean {
        return if(selection is DirectionalLightSelection) {
            float2Input(
                "Phi | Theta",
                initial0 = selection.directionalLight.phiDegrees,
                initial1 = selection.directionalLight.thetaDegrees,
                min = -179.9f,
                max = 179.9f
            ) {
                selection.directionalLight.phiDegrees = it[0]
                selection.directionalLight.thetaDegrees = it[1]
            }
            floatInput("Height", min = 500f, max = 2500f, property = selection.directionalLight::height)
            true
        } else false
    }

}