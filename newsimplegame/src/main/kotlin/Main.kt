import de.hanno.hpengine.editor.EngineWithEditor
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.GameDirectory
import scenes.sponzaScene
import java.io.File
object Game {

    @JvmStatic
    fun main(args: Array<String>) {

        val config = ConfigImpl(
            Directories(
                gameDir = GameDirectory<Game>(File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game"))
            )
        )
        val (engine, editor) = EngineWithEditor(config)

        editor.frame.onSceneReload = { engine.scene = engine.sponzaScene }
//        val hellknightScene = engine.hellknightScene
//        engine.scene = hellknightScene

        val sponzaScene = engine.sponzaScene
        engine.scene = sponzaScene

    }
}

