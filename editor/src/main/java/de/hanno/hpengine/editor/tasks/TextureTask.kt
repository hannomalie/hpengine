package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.editor.doWithRefresh
import de.hanno.hpengine.editor.grids.TextureGrid
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.selection.addUnselectButton
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.model.texture.FileBasedCubeMap
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
import de.hanno.hpengine.engine.scene.SceneManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.model.RibbonGalleryContentModel
import org.pushingpixels.flamingo.api.ribbon.model.RibbonGalleryPresentationModel
import org.pushingpixels.flamingo.api.ribbon.projection.RibbonGalleryProjection
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.neon.api.icon.ResizableIcon
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser

object TextureTask {

    operator fun invoke(engine: EngineContext, sceneManager: SceneManager, editor: RibbonEditor, selectionSystem: SelectionSystem): RibbonTask {
        fun retrieveTextureCommands(): List<Command> {
            return engine.textureManager.textures.values.mapNotNull {
                if (it is FileBasedTexture2D) {
                    val image = ImageIO.read(File(it.file.absolutePath))
                    Command.builder()
                        .setText(it.file.name)
                        .setIconFactory { EditorComponents.Companion.getResizableIconFromImageSource(image) }
                        .setAction { event ->
                            if (event.command.isToggleSelected) {
                                editor.sidePanel.doWithRefresh {
                                    addUnselectButton()
                                    add(TextureGrid(engine.gpuContext, it))
                                }
                            } else {
                                selectionSystem.unselect()
                            }
                        }
                        .setToggle()
                        .build()
                } else if (it is FileBasedCubeMap) {
                    Command.builder()
                            .setText(it.file.name)
                            .setIconFactory { EditorComponents.Companion.getResizableIconFromImageSource(it.bufferedImage) }
                            .setAction { event ->
                                if (event.command.isToggleSelected) {
                                    editor.sidePanel.doWithRefresh {
                                        addUnselectButton()
                                        add(TextureGrid(engine.gpuContext, it))
                                    }
                                } else {
                                    selectionSystem.unselect()
                                }
                            }
                            .setToggle()
                            .build()
                } else null
            }
        }

        val textureBand = JRibbonBand("Texture", null).apply {
            val addTextureCommand = Command.builder()
                    .setText("Add Texture")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        val fc = JFileChooser()
                        val returnVal = fc.showOpenDialog(editor)
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            val file = fc.selectedFile
                            GlobalScope.launch {
                                engine.textureManager.getTexture(file.name, file = file)
                            }
                        }
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Texture")
                            .addDescriptionSection("Creates a texture from the selected image")
                            .build())
                    .build()
            addRibbonCommand(addTextureCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            val addCubeMapCommand = Command.builder()
                    .setText("Add CubeMap")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        val fc = JFileChooser().apply {
                            isMultiSelectionEnabled = true
                        }
                        val returnVal = fc.showOpenDialog(editor)
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            val files = fc.selectedFiles.toList()
                            GlobalScope.launch {
                                if(files.size > 1) {
                                    engine.textureManager.getCubeMap(files.map { it.name }.minOrNull()!!, files)
                                } else {
                                    val file = files.first()
                                    engine.textureManager.getCubeMap(file.name, file)
                                }
                            }
                        }
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("CubeMap")
                            .addDescriptionSection("Creates a cubemap from the selected image")
                            .build())
                    .build()
            addRibbonCommand(addCubeMapCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)


            val textureCommands = retrieveTextureCommands()
            val contentModel = RibbonGalleryContentModel(ResizableIcon.Factory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") },
                    listOf(CommandGroup("Available textures", textureCommands))
            )
            val stylesGalleryVisibleCommandCounts = mapOf(
                    JRibbonBand.PresentationPriority.LOW to 1,
                    JRibbonBand.PresentationPriority.MEDIUM to 2,
                    JRibbonBand.PresentationPriority.TOP to 3
            )

            val galleryProjection = RibbonGalleryProjection(contentModel, RibbonGalleryPresentationModel.builder()
                    .setPreferredVisibleCommandCounts(stylesGalleryVisibleCommandCounts)
                    .setPreferredPopupMaxVisibleCommandRows(3)
                    .setPreferredPopupMaxCommandColumns(3)
                    .setCommandPresentationState(JRibbonBand.BIG_FIXED_LANDSCAPE)
                    .setExpandKeyTip("L")
                    .build())
            addRibbonGallery(galleryProjection, JRibbonBand.PresentationPriority.TOP)

            val refreshTexturesCommand = Command.builder()
                    .setText("Refresh")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("refresh-24px.svg") }
                    .setAction {
                        contentModel.getCommandGroupByTitle("Available textures").apply {
                            SwingUtils.invokeLater {
                                retrieveTextureCommands().forEach {
                                    addCommand(it)
                                }
                            }
                        }
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Refresh textures")
                            .addDescriptionSection("Populates the gallery with the current set of available textures")
                            .build())
                    .build()
            addRibbonCommand(refreshTexturesCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        return RibbonTask("Texture", textureBand)
    }

}