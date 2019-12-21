package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.SelectionMode
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.util.gui.DirectTextureOutputItem
import org.pushingpixels.flamingo.api.common.CommandButtonPresentationState
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.common.model.CommandStripPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandToggleGroupModel
import org.pushingpixels.flamingo.api.common.projection.CommandStripProjection
import org.pushingpixels.flamingo.api.ribbon.JFlowRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.flamingo.api.ribbon.synapse.model.ComponentPresentationModel
import org.pushingpixels.flamingo.api.ribbon.synapse.model.RibbonDefaultComboBoxContentModel
import org.pushingpixels.flamingo.api.ribbon.synapse.projection.RibbonComboBoxProjection
import java.util.ArrayList
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object ViewTask {
    operator fun invoke(engine: Engine<*>, config: ConfigImpl, inputConfig: EditorInputConfig): RibbonTask {
        val outputBand = JRibbonBand("Output", null).apply {

            val command = Command.builder()
                    .setText("Direct texture output")
                    .setToggle()
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        config.debug.isUseDirectTextureOutput = it.command.isToggleSelected
                    }
                    .build()
            addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)

            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }


        val outputIndexBand = JFlowRibbonBand("Output", null).apply {
            val renderTargetTextures = ArrayList<DirectTextureOutputItem>()
            for (target in engine.gpuContext.registeredRenderTargets) {
                for (i in 0 until target.textures.size) {
                    val name = target.name + " - " + i // TODO: Revive names here
                    renderTargetTextures.add(DirectTextureOutputItem(target, name, target.getRenderedTexture(i)))
                }
            }

            val directTextureOutputComboBoxModel = RibbonDefaultComboBoxContentModel.builder<DirectTextureOutputItem>()
                    .setItems(renderTargetTextures.toTypedArray())
                    .build()

            directTextureOutputComboBoxModel.addListDataListener(object : ListDataListener {
                override fun intervalRemoved(e: ListDataEvent?) {}
                override fun intervalAdded(e: ListDataEvent?) {}

                override fun contentsChanged(e: ListDataEvent) {
                    val newSelection = directTextureOutputComboBoxModel.selectedItem as DirectTextureOutputItem
                    config.debug.directTextureOutputTextureIndex = newSelection.textureId
                }
            })

            addFlowComponent(RibbonComboBoxProjection(directTextureOutputComboBoxModel, ComponentPresentationModel.builder().build()))

        }

        val selectionModeBand = JFlowRibbonBand("Selection Mode", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val selectionModeToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(SelectionMode.Entity, inputConfig::selectionMode),
                    Pair(SelectionMode.Mesh, inputConfig::selectionMode)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(inputConfig.selectionMode == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { EditorComponents.getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(selectionModeToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(SelectionMode.Entity) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val selectionModeCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(selectionModeCommandGroupProjection)
        }

         return RibbonTask("Viewport", outputBand, outputIndexBand, selectionModeBand)
    }
}