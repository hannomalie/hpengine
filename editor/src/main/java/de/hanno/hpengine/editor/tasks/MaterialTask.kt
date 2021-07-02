package de.hanno.hpengine.editor.tasks

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.editor.grids.MaterialGrid
import de.hanno.hpengine.editor.selection.MaterialSelection
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.verticalBox
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.SceneManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.koin.core.component.get
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
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser

class MaterialRibbonTask(val addResourceContext: AddResourceContext,
                         val textureManager: TextureManager,
                         val programManager: ProgramManager<OpenGl>,
                         val sceneManager: SceneManager,
                         val editor: RibbonEditor,
                         val selectionSystem: SelectionSystem): RibbonTask("Material", MaterialRibbonBand(addResourceContext, textureManager, programManager, sceneManager, editor, selectionSystem)), EditorRibbonTask {

    override fun reloadContent() {
        bands.toList().firstIsInstance<MaterialRibbonBand>().updateMaterials()
    }
    class MaterialRibbonBand(val addResourceContext: AddResourceContext,
                             val textureManager: TextureManager,
                             val programManager: ProgramManager<OpenGl>,
                             val sceneManager: SceneManager,
                             val editor: RibbonEditor,
                             val selectionSystem: SelectionSystem): JRibbonBand("Material", null) {

        val addMaterialCommand = Command.builder()
                .setText("Create")
                .setIconFactory { EditorComponents.getResizableIconFromSvgResource("add-24px.svg") }
                .setAction {
                    val fc = JFileChooser()
                    val returnVal = fc.showOpenDialog(editor)
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        val file = fc.selectedFile
                        textureManager.getCubeMap(file.name, file = file)
                    }
                }
                .setActionRichTooltip(RichTooltip.builder()
                        .setTitle("Material")
                        .addDescriptionSection("Creates a texture from the selected image")
                        .build())
                .build().apply {
                    addRibbonCommand(project(CommandButtonPresentationModel.builder()
                            .setTextClickAction()
                            .build()), PresentationPriority.TOP
                    )
                }


        val materialCommands = emptyList<Command>()//retrieveMaterialCommands()
        val contentModel = RibbonGalleryContentModel({ EditorComponents.getResizableIconFromSvgResource("add-24px.svg") },
                listOf(CommandGroup("Available materials", materialCommands))
        )
        val stylesGalleryVisibleCommandCounts = mapOf(
                PresentationPriority.LOW to 1,
                PresentationPriority.MEDIUM to 2,
                PresentationPriority.TOP to 6
        )

        val galleryProjection = RibbonGalleryProjection(contentModel, RibbonGalleryPresentationModel.builder()
                .setPreferredVisibleCommandCounts(stylesGalleryVisibleCommandCounts)
                .setPreferredPopupMaxVisibleCommandRows(3)
                .setPreferredPopupMaxCommandColumns(3)
                .setCommandPresentationState(BIG_FIXED_LANDSCAPE)
                .setExpandKeyTip("M")
                .build()).apply {
            addRibbonGallery(this, PresentationPriority.TOP)
        }

        val refreshMaterialsCommand = Command.builder()
                .setText("Refresh")
                .setIconFactory { EditorComponents.getResizableIconFromSvgResource("refresh-24px.svg") }
                .setAction {
                    updateMaterials()
                }
                .setActionRichTooltip(RichTooltip.builder()
                        .setTitle("Refresh materials")
                        .addDescriptionSection("Populates the gallery with the current set of available materials")
                        .build())
                .build().apply {

                    addRibbonCommand(project(CommandButtonPresentationModel.builder()
                            .setTextClickAction()
                            .build()), PresentationPriority.TOP)
                }

        fun updateMaterials() {
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

        init {
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        fun retrieveMaterialCommands(): List<Command> {
            var result = emptyList<Command>()
            addResourceContext.locked {
                result = sceneManager.scene.get<MaterialManager>().materials.mapNotNull { material ->
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
                        .setText(material.name)
                        .setAction { event ->
                            // unselect and select material here
                            if (event.command.isToggleSelected) {
                                val selection = selectionSystem.selection
                                if (selection is MaterialSelection && selection.material == material) {
                                    selectionSystem.unselect()
                                } else {
                                    editor.sidePanel.verticalBox(
                                        selectionSystem.unselectButton,
                                        MaterialGrid(programManager, textureManager, material)
                                    )
                                }
                            } else {
                                selectionSystem.unselect()
                            }
                        }
                        .setIconFactory { icon }
                        .setToggle()
                        .build()
                }
            }
            return result
        }
    }
}
