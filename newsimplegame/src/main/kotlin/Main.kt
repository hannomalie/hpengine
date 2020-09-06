import de.hanno.hpengine.editor.EngineWithEditor
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.input
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.jetbrains.kotlin.types.checker.findCorrespondingSupertype
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.GLFW_KEY_P
import scenes.hellknightScene
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
        val engine = Engine(config)

//        val hellknightScene = engine.hellknightScene
//        engine.scene = hellknightScene

        val sponzaScene = engine.sponzaScene
        engine.scene = sponzaScene

    }
}

