package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.graphics.EditorRenderSystem
import de.hanno.hpengine.editor.graphics.OutputConfig
import de.hanno.hpengine.editor.graphics.OutputConfigHolder
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.SelectionMode
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.window.SwingUtils
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapRenderTarget
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.scene.AddResourceContext
import org.pushingpixels.flamingo.api.common.CommandButtonPresentationState
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.common.model.CommandStripPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandToggleGroupModel
import org.pushingpixels.flamingo.api.common.projection.CommandStripProjection
import org.pushingpixels.flamingo.api.ribbon.AbstractRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JFlowRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.flamingo.api.ribbon.synapse.model.ComponentPresentationModel
import org.pushingpixels.flamingo.api.ribbon.synapse.model.RibbonDefaultComboBoxContentModel
import org.pushingpixels.flamingo.api.ribbon.synapse.projection.RibbonComboBoxProjection
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ViewRibbonTask(
    viewRibbonBands: ViewRibbonBands
) : RibbonTask("Viewport", *viewRibbonBands.bands)


class ViewRibbonBands(
    val gpuContext: GpuContext<OpenGl>,
    val inputConfig: EditorInputConfig,
    val outputConfigHolder: OutputConfigHolder,
    val addResourceContext: AddResourceContext,
    val selectionSystem: SelectionSystem
) {
    private var outputConfig by outputConfigHolder::outputConfig

    fun GpuContext<OpenGl>.retrieveRenderTargetTextures(): MutableList<OutputConfig> {
        val renderTargetTextures = mutableListOf<OutputConfig>(OutputConfig.Default)
        for (target in registeredRenderTargets) {
            for (i in target.textures.indices) {
                val name = target.name + " - " + i // TODO: Revive names here
                if (target is CubeMapArrayRenderTarget) {
                    renderTargetTextures.add(
                        OutputConfig.RenderTargetCubeMapArray(
                            target,
                            i.coerceIn(0, target.textures[i].dimension.depth)
                        )
                    )
                } else if (target is CubeMapRenderTarget) {
                    renderTargetTextures.add(OutputConfig.TextureCubeMap(name, target.textures[i]))
                } else {
                    renderTargetTextures.add(
                        OutputConfig.Texture2D(
                            name,
                            target.textures[i] as Texture2D,
                            target.factorsForDebugRendering[i]
                        )
                    )
                }
            }
        }
        return renderTargetTextures
    }


    val directTextureOutputArrayIndexComboBoxModel = RibbonDefaultComboBoxContentModel.builder<Int>()
        .setItems((0 until 100).toList().toTypedArray())
        .build()
        .apply {
            addListDataListener(object : ListDataListener {
                override fun intervalRemoved(e: ListDataEvent?) {}
                override fun intervalAdded(e: ListDataEvent?) {}

                override fun contentsChanged(e: ListDataEvent) {
                    when (val currentConfig = outputConfig) {
                        OutputConfig.Default, is OutputConfig.Texture2D, is OutputConfig.TextureCubeMap -> Unit
                        is OutputConfig.RenderTargetCubeMapArray -> {
                            outputConfig = currentConfig.copy(
                                cubeMapIndex = (selectedItem as Int).coerceIn(
                                    0,
                                    currentConfig.renderTarget.arraySize - 1
                                )
                            )
                        }
                    }.let { }
                }
            })
        }

    val outputFlowBandModel = RibbonDefaultComboBoxContentModel.builder<OutputConfig>()
        .setItems(gpuContext.retrieveRenderTargetTextures().toTypedArray())
        .build().apply {
            addListDataListener(object : ListDataListener {
                override fun intervalRemoved(e: ListDataEvent?) {}
                override fun intervalAdded(e: ListDataEvent?) {}

                override fun contentsChanged(e: ListDataEvent) {
                    val newConfig = when (val selectedItem = selectedItem) {
                        OutputConfig.Default, is OutputConfig.Texture2D, is OutputConfig.TextureCubeMap -> selectedItem as OutputConfig
                        is OutputConfig.RenderTargetCubeMapArray -> selectedItem.copy(cubeMapIndex = directTextureOutputArrayIndexComboBoxModel.selectedItem as Int)
                        else -> throw IllegalStateException("This should never happen")
                    }
                    outputConfig = newConfig
                }
            })
            this.selectedItem = OutputConfig.Default
        }

    val outputFlowBand = JFlowRibbonBand("Output", null).apply {

        addFlowComponent(RibbonComboBoxProjection(outputFlowBandModel, ComponentPresentationModel.builder().build()))
    }

    val outputArrayIndexBand = JFlowRibbonBand("CubeMap Index", null).apply {

        addFlowComponent(
            RibbonComboBoxProjection(
                directTextureOutputArrayIndexComboBoxModel,
                ComponentPresentationModel.builder().build()
            )
        )

    }
    val selectionModeBand = JFlowRibbonBand("Selection Mode", null).apply {
        resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

        val selectionModeToggleGroup = CommandToggleGroupModel()

        val commands = listOf(
            Pair(SelectionMode.Entity, inputConfig::selectionMode),
            Pair(SelectionMode.Mesh, inputConfig::selectionMode)
        ).map {
            Command.builder()
                .setToggle()
                .setToggleSelected(inputConfig.selectionMode == it.first)
                .setText(it.first.toString())
                .setIconFactory { EditorRenderSystem.getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                .inToggleGroup(selectionModeToggleGroup)
                .setAction { event ->
                    if (it.second.get() == it.first) it.second.set(SelectionMode.Entity) else it.second.set(it.first)
                    event.command.isToggleSelected = it.second.get() == it.first
                }
                .build()
        }

        val refreshRenderTargetsCommand = Command.builder()
            .setText("Refresh")
            .setIconFactory { EditorRenderSystem.getResizableIconFromSvgResource("refresh-24px.svg") }
            .setAction {
                SwingUtils.invokeLater {
                    outputFlowBandModel.removeAllElements()
                    outputFlowBandModel.addAll(gpuContext.retrieveRenderTargetTextures())
                }
            }
            .setActionRichTooltip(
                RichTooltip.builder()
                    .setTitle("Refresh rendertargets")
                    .addDescriptionSection("Refreshes with all registered rendertargets")
                    .build()
            )
            .build()

        val selectionModeCommandGroupProjection = CommandStripProjection(
            CommandGroup(commands + refreshRenderTargetsCommand),
            CommandStripPresentationModel.builder()
                .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                .build()
        )
        addFlowComponent(selectionModeCommandGroupProjection)

    }

    val unselectBand = JRibbonBand("Unselect", null).apply {
        val command = Command.builder()
            .setText("Unselect")
            .setIconFactory { EditorRenderSystem.getResizableIconFromSvgResource("dash-circle.svg") }
            .setAction {
                selectionSystem.unselect()
            }
            .setActionRichTooltip(
                RichTooltip.builder()
                    .setTitle("Unselect")
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

    val bands: Array<AbstractRibbonBand> =
        arrayOf(outputFlowBand, outputArrayIndexBand, selectionModeBand, unselectBand)
}