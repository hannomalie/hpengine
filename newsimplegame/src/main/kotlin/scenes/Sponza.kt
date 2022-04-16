package scenes

import de.hanno.hpengine.engine.*
import de.hanno.hpengine.engine.component.artemis.ModelComponent
import de.hanno.hpengine.engine.component.artemis.NameComponent
import de.hanno.hpengine.engine.component.artemis.SpatialComponent
import de.hanno.hpengine.engine.component.artemis.TransformComponent
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import java.io.File

fun main() {

    val config = ConfigImpl(
        directories = Directories(
//                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
            EngineDirectory(File("C:\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
            GameDirectory(File("C:\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game"), null)
        ),
    )

    Engine(config) {
        loadSponzaScene()
    }
}

fun Engine.loadSponzaScene() = world.run {
    clear()

    edit(create()).apply {
        create(TransformComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription("assets/models/sponza.obj", Directory.Game)
        }
        create(SpatialComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "Sponza"
        }
    }
    addDirectionalLight()
    addSkyBox(config)
    addPrimaryCamera()
}
