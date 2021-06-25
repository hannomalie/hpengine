package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.scene.api.Directory
import de.hanno.hpengine.engine.scene.api.StaticModelComponent
import de.hanno.hpengine.engine.scene.api.convert
import de.hanno.hpengine.engine.scene.api.entity
import de.hanno.hpengine.engine.scene.api.scene

fun main() {
    val scene = scene("MyScene") {
        entity("myEntity") {
            add(StaticModelComponent("assets/models/cube.obj", Directory.Engine))
//            add(AnimatedModelComponent("assets/models/doom3monster/hellknight.md5mesh", Directory.Game))
//            add(CustomComponent { scene, deltaSeconds -> println("CustomComponent in scene ${scene.name}") })
        }
    }

    val (engine, editor) = EngineWithEditor()

    engine.scene = scene.convert(engine.engineContext)
    editor.frame.onSceneReload = {
        engine.scene = scene.convert(engine.engineContext)
    }
}