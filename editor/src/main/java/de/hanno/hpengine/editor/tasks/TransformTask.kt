package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.input.AxisConstraint
import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.RotateAround
import de.hanno.hpengine.editor.input.TransformMode
import de.hanno.hpengine.editor.input.TransformSpace
import org.pushingpixels.flamingo.api.common.CommandButtonPresentationState
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.common.model.CommandStripPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandToggleGroupModel
import org.pushingpixels.flamingo.api.common.projection.CommandStripProjection
import org.pushingpixels.flamingo.api.ribbon.JFlowRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies

object TransformTask {

    operator fun invoke(inputConfig: EditorInputConfig): RibbonTask {
        val activeAxesBand = JFlowRibbonBand("Active Axes", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val transformAxisToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(AxisConstraint.X, inputConfig::constraintAxis),
                    Pair(AxisConstraint.Y, inputConfig::constraintAxis),
                    Pair(AxisConstraint.Z, inputConfig::constraintAxis)).map {
                Command.builder()
                        .setToggle()
                        .setText(it.first.toString())
                        .setToggleSelected(inputConfig.constraintAxis == it.first)
                        .setIconFactory { EditorComponents.getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(transformAxisToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(AxisConstraint.None) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val translateCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(translateCommandGroupProjection)
        }

        val transformModeBand = JFlowRibbonBand("Transform Mode", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val transformModeToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(TransformMode.Translate, inputConfig::transformMode),
                    Pair(TransformMode.Rotate, inputConfig::transformMode),
                    Pair(TransformMode.Scale, inputConfig::transformMode)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(inputConfig.transformMode == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { EditorComponents.getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(transformModeToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(TransformMode.None) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val transformModeCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(transformModeCommandGroupProjection)
        }

        val transformSpaceBand = JFlowRibbonBand("Transform Space", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val transformSpaceToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(TransformSpace.World, inputConfig::transformSpace),
                    Pair(TransformSpace.Local, inputConfig::transformSpace),
                    Pair(TransformSpace.View, inputConfig::transformSpace)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(inputConfig.transformSpace == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { EditorComponents.getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(transformSpaceToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(TransformSpace.World) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val transformSpaceCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(transformSpaceCommandGroupProjection)
        }
        val rotateAroundBand = JFlowRibbonBand("Rotate Around", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val rotateAroundToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(RotateAround.Self, inputConfig::rotateAround),
                    Pair(RotateAround.Pivot, inputConfig::rotateAround)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(inputConfig.rotateAround == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { EditorComponents.getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(rotateAroundToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(RotateAround.Self) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val rotateAroundCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(rotateAroundCommandGroupProjection)
        }


        return RibbonTask("Transform", activeAxesBand, transformModeBand, transformSpaceBand, rotateAroundBand)
    }
}