package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.scene.SceneManager
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies


object SceneTask {
    operator fun invoke(engineContext: EngineContext, sceneManager: SceneManager, editorComponents: EditorComponents): RibbonTask {
        val entityBand = JRibbonBand("Entity", null).apply {
            val command = Command.builder()
                    .setText("Create")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        sceneManager.add(Entity("NewEntity_${sceneManager.scene.getEntities().count { it.name.startsWith("NewEntity") }}"))
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Entity")
                            .addDescriptionSection("Creates an entity")
                            .build())
                    .build()
            addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }
        val reloadBand = JRibbonBand("Scene", null).apply {
            val command = Command.builder()
                    .setText("Reload")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("refresh-24px.svg") }
                    .setAction {
                        editorComponents.onReload?.invoke()
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Scene")
                            .addDescriptionSection("Reloads the whole scene")
                            .build())
                    .build()
            addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        return RibbonTask("Scene", entityBand, reloadBand)
    }
}