package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.addResourceContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.entity.Entity
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies


object SceneTask {
    operator fun invoke(engine: Engine): RibbonTask {
        val entityBand = JRibbonBand("Entity", null).apply {
            val command = Command.builder()
                    .setText("Create")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        engine.addResourceContext.launch {
                            with(engine.sceneManager) {
                                add(Entity("NewEntity_${engine.scene.getEntities().count { it.name.startsWith("NewEntity") }}"))
                            }
                        }
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

        return RibbonTask("Scene", entityBand)
    }
}