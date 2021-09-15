package de.hanno.hpengine.editor.appmenu

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.dsl.SceneDescription
import de.hanno.hpengine.engine.scene.dsl.convert
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.get
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.ribbon.RibbonApplicationMenu
import org.pushingpixels.flamingo.api.ribbon.projection.RibbonApplicationMenuCommandButtonProjection

class ApplicationMenu(sceneManager: SceneManager) {
    val appMenuNew = Command.builder()
        .setText("New Scene")
        .setIconFactory { EditorComponents.getResizableIconFromSvgResource("create_new_folder-24px.svg") }
        .setExtraText("Creates an empty scene")
        .setAction {
            GlobalScope.launch {
                sceneManager.scene = SceneDescription("Scene_${System.currentTimeMillis()}").convert(
                    sceneManager.scene.get(),
                    sceneManager.scene.get()
                )
            }
        }
        .build()
    val applicationMenu = RibbonApplicationMenu(CommandGroup(appMenuNew))

    val commandProjection = RibbonApplicationMenuCommandButtonProjection(
        Command.builder()
            .setText("Application")
            .setSecondaryContentModel(applicationMenu)
            .build(), CommandButtonPresentationModel.builder().build()
    )
}