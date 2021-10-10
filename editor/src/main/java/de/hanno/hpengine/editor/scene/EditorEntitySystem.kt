package de.hanno.hpengine.editor.scene

import de.hanno.hpengine.editor.graphics.EditorRenderSystem
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.Scene

class EditorEntitySystem(val editorRenderSystem: EditorRenderSystem): SimpleEntitySystem(emptyList()) {
    override fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        super.onEntityAdded(scene, entities)
        editorRenderSystem.sceneTree.reload(this.entities)
    }

    override fun onComponentAdded(scene: Scene, component: de.hanno.hpengine.engine.component.Component) {
        super.onComponentAdded(scene, component)
        editorRenderSystem.sceneTree.reload(this.entities)
    }
}