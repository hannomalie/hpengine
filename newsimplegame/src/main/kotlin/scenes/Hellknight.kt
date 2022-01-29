package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f

val Engine.hellknightScene
    get() = de.hanno.hpengine.engine.scene.dsl.scene("Hellknight") {
        entity("hellknight") {
            add(
                AnimatedModelComponentDescription(
                    "assets/models/doom3monster/monster.md5mesh",
                    Directory.Game,
                    AABBData(
                        Vector3f(-60f, -10f, -35f),
                        Vector3f(60f, 130f, 50f)
                    )
                )
            )
        }
    }