package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.convert
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.engine.scene.dsl.scene

fun main() {
    val scene = scene("MyScene") {
        entity("myEntity") {
            add(StaticModelComponentDescription("assets/models/cube.obj", Directory.Engine))
//            add(AnimatedModelComponent("assets/models/doom3monster/hellknight.md5mesh", Directory.Game))
//            add(CustomComponent { scene, deltaSeconds -> println("CustomComponent in scene ${scene.name}") })
        }
    }

    val engine = EngineWithEditor()

    val editor = engine.application.koin.get<AWTEditorWindow>()

    editor.frame.onSceneReload = {
        engine.scene = scene.convert(engine.application.koin.get(), engine.application.koin.get())
    }.apply { invoke() }
}