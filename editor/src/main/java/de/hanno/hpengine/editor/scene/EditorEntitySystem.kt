package de.hanno.hpengine.editor.scene

import de.hanno.hpengine.editor.graphics.EditorRendersystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.Scene

class EditorEntitySystem(val editorRendersystem: EditorRendersystem): SimpleEntitySystem(emptyList()) {
    override fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        super.onEntityAdded(scene, entities)
        editorRendersystem.sceneTree.reload(this.entities)
    }

    override fun onComponentAdded(scene: Scene, component: de.hanno.hpengine.engine.component.Component) {
        super.onComponentAdded(scene, component)
        editorRendersystem.sceneTree.reload(this.entities)
    }
}