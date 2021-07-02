package de.hanno.hpengine.editor.appmenu

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.ribbon.RibbonApplicationMenu
import org.pushingpixels.flamingo.api.ribbon.projection.RibbonApplicationMenuCommandButtonProjection

object ApplicationMenu {
    operator fun invoke(sceneManager: SceneManager): RibbonApplicationMenuCommandButtonProjection {

        val appMenuNew = Command.builder()
                .setText("New Scene")
                .setIconFactory { EditorComponents.getResizableIconFromSvgResource("create_new_folder-24px.svg") }
                .setExtraText("Creates an empty scene")
                .setAction {
                    GlobalScope.launch {
                        sceneManager.scene = Scene("Scene_${System.currentTimeMillis()}")
                    }
                }
                .build()
        val applicationMenu = RibbonApplicationMenu(CommandGroup(appMenuNew))

        return RibbonApplicationMenuCommandButtonProjection(
                Command.builder()
                        .setText("Application")
                        .setSecondaryContentModel(applicationMenu)
                        .build(), CommandButtonPresentationModel.builder().build()).apply {
        }
    }
}