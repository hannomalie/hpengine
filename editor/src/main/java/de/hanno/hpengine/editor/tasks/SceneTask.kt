package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.graphics.EditorRendersystem
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.scene.SceneManager
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.ribbon.AbstractRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies

class SceneRibbonTask(
    val sceneRibbonBands: SceneRibbonBands
) : RibbonTask("Scene", *sceneRibbonBands.bands)

class SceneRibbonBands(
    val sceneManager: SceneManager,
    val editor: RibbonEditor
) {
    val entityBand = JRibbonBand("Entity", null).apply {
        val command = Command.builder()
            .setText("Create")
            .setIconFactory { EditorRendersystem.getResizableIconFromSvgResource("add-24px.svg") }
            .setAction {
                sceneManager.scene.addAll(
                    listOf(
                        Entity(
                            "NewEntity_${
                                sceneManager.scene.getEntities().count { it.name.startsWith("NewEntity") }
                            }"
                        )
                    )
                )
            }
            .setActionRichTooltip(
                RichTooltip.builder()
                    .setTitle("Entity")
                    .addDescriptionSection("Creates an entity")
                    .build()
            )
            .build()
        addRibbonCommand(
            command.project(
                CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()
            ), JRibbonBand.PresentationPriority.TOP
        )
        resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
    }
    val reloadBand = JRibbonBand("Scene", null).apply {
        val command = Command.builder()
            .setText("Reload")
            .setIconFactory { EditorRendersystem.getResizableIconFromSvgResource("refresh-24px.svg") }
            .setAction {
                editor.onSceneReload?.invoke()
            }
            .setActionRichTooltip(
                RichTooltip.builder()
                    .setTitle("Scene")
                    .addDescriptionSection("Reloads the whole scene")
                    .build()
            )
            .build()
        addRibbonCommand(
            command.project(
                CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()
            ), JRibbonBand.PresentationPriority.TOP
        )
        resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
    }
    val bands: Array<AbstractRibbonBand> = arrayOf(entityBand, reloadBand)
}