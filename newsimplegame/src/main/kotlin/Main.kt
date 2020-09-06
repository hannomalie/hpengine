import de.hanno.hpengine.editor.EngineWithEditor
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.joml.Vector3f
import scenes.hellknightScene
import java.io.File
object Game {

    @JvmStatic
    fun main(args: Array<String>) {

        val config = ConfigImpl(
            Directories(
                gameDir = GameDirectory<Game>(File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game"))
            )
        )
        val (engine) = EngineWithEditor(config)

        engine.scene = engine.hellknightScene
    }
}

