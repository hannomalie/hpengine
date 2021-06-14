import de.hanno.hpengine.editor.EngineWithEditor
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pushingpixels.substance.api.SubstanceCortex
import scenes.materialsDemo
import scenes.oceanDemo
import scenes.sponzaScene
import java.io.File

object GameEditor {

    @JvmStatic
    fun main(args: Array<String>) {

        val config = ConfigImpl(
                Directories(
                        gameDir = GameDirectory<Game>(File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game")),
                        engineDir = EngineDirectory(File("/home/tenter/workspace/hpengine/engine/src/main/resources/hp"))
                ),
                debug = DebugConfig(isUseFileReloading = true)
        )
        val (engine, editor) = EngineWithEditor(config)

        editor.frame.onSceneReload = {
            engine.scene = engine.oceanDemo
        }
        editor.frame.onSceneReload?.invoke()
    }
}

object Game {

    @JvmStatic
    fun main(args: Array<String>) {

        val config = ConfigImpl(
                Directories(
                        gameDir = GameDirectory<Game>(File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game")),
                        engineDir = EngineDirectory(File("/home/tenter/workspace/hpengine/engine/src/main/resources/hp"))
                ),
                debug = DebugConfig(isUseFileReloading = true)
        )
        val engine = Engine(config)
    }
}

