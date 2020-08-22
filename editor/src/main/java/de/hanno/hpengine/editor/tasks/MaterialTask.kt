package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.editor.doWithRefresh
import de.hanno.hpengine.editor.grids.MaterialGrid
import de.hanno.hpengine.editor.selection.MaterialSelection
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.selection.addUnselectButton
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
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

object MaterialTask {

    operator fun invoke(engine: Engine,
                        editor: RibbonEditor,
                        selectionSystem: SelectionSystem): RibbonTask {

        fun retrieveMaterialCommands(): List<Command> = engine.scene.materialManager.materials.mapNotNull { material ->
            val icon = if (material.materialInfo.maps.containsKey(SimpleMaterial.MAP.DIFFUSE)) {
                val diffuseMap = material.materialInfo.maps[SimpleMaterial.MAP.DIFFUSE] as? FileBasedTexture2D
                if (diffuseMap != null) {
                    val image = ImageIO.read(File(diffuseMap.file.absolutePath))
                    EditorComponents.getResizableIconFromImageSource(image)
                } else {
                    EditorComponents.getResizableIconFromSvgResource("add-24px.svg")
                }
            } else {
                EditorComponents.getResizableIconFromSvgResource("add-24px.svg")
            }

            Command.builder()
                    .setText(material.materialInfo.name)
                    .setAction { event ->
                        // unselect and select material here
                        if (event.command.isToggleSelected) {
                            val selection = selectionSystem.selection
                            if (selection is MaterialSelection && selection.material == material) {
                                selectionSystem.unselect()
                            } else {
                                editor.sidePanel.doWithRefresh {
                                    addUnselectButton()
                                    add(MaterialGrid(engine.textureManager, material))
                                }
                            }
                        } else {
                            selectionSystem.unselect()
                        }
                    }
                    .setIconFactory { icon }
                    .setToggle()
                    .build()
        }

        val materialBand = JRibbonBand("Material", null).apply {
            val addMaterialCommand = Command.builder()
                    .setText("Create")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        val fc = JFileChooser()
                        val returnVal = fc.showOpenDialog(editor)
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            val file = fc.selectedFile
                            engine.textureManager.getCubeMap(file.name, file = file)
                        }
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Material")
                            .addDescriptionSection("Creates a texture from the selected image")
                            .build())
                    .build()
            addRibbonCommand(addMaterialCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)


            val materialCommands = retrieveMaterialCommands()
            val contentModel = RibbonGalleryContentModel(ResizableIcon.Factory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") },
                    listOf(CommandGroup("Available materials", materialCommands))
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
                    .setExpandKeyTip("M")
                    .build())
            addRibbonGallery(galleryProjection, JRibbonBand.PresentationPriority.TOP)

            val refreshMaterialsCommand = Command.builder()
                    .setText("Refresh")
                    .setIconFactory { EditorComponents.getResizableIconFromSvgResource("refresh-24px.svg") }
                    .setAction {
                        GlobalScope.launch {
                            val retrievedMaterialCommands = retrieveMaterialCommands()

                            contentModel.getCommandGroupByTitle("Available materials").apply {
                                SwingUtils.invokeLater {
                                    removeAllCommands()
                                    retrievedMaterialCommands.forEach {
                                        addCommand(it)
                                    }
                                }
                            }
                        }
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Refresh materials")
                            .addDescriptionSection("Populates the gallery with the current set of available materials")
                            .build())
                    .build()
            addRibbonCommand(refreshMaterialsCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        return RibbonTask("Material", materialBand)
    }
}