package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
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

    val application = EngineWithEditorXXX()

    val engine = Engine(application)
    val editor = application.koin.get<AWTEditorWindow>()

    engine.scene = scene.convert(application)
    editor.frame.onSceneReload = {
        engine.scene = scene.convert(application)
    }
}