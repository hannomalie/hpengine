import de.hanno.hpengine.editor.EngineWithEditor
import de.hanno.hpengine.editor.window.AWTEditorWindow
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import scenes.sponzaScene
import java.io.File

object GameEditor {

    @JvmStatic
    fun main(args: Array<String>) {

        val config = ConfigImpl(
                Directories(
                        gameDir = GameDirectory<GameEditor>(File("/home/tenter/workspace/hpengine/newsimplegame/src/main/resources/game")),
                        engineDir = EngineDirectory(File("/home/tenter/workspace/hpengine/engine/src/main/resources/hp"))
                ),
                debug = DebugConfig(isUseFileReloading = true)
        )
        val engine = EngineWithEditor(config)

        engine.application.koin.get<AWTEditorWindow>().apply {
            frame.onSceneReload = {
            }
        }
    }
}
