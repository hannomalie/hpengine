package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
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

    operator fun invoke(engine: Engine<*>, editor: RibbonEditor): RibbonTask {
        fun retrieveTextureCommands(): List<Command> {
            return engine.textureManager.textures.values.mapNotNull {
                if (it is FileBasedTexture2D) {
                    val image = ImageIO.read(File(it.file.absolutePath))
                    Command.builder()
                            .setText(it.file.name)
                            .setIconFactory { EditorComponents.Companion.getResizableIconFromImageSource(image) }
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
                        val fc = JFileChooser()
                        val returnVal = fc.showOpenDialog(editor)
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            val file = fc.selectedFile
                            GlobalScope.launch {
                                engine.textureManager.getCubeMap(file.name, file = file)
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
                                removeAllCommands()
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