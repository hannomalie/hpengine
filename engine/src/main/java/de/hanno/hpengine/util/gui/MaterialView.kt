package de.hanno.hpengine.util.gui

import com.alee.extended.panel.GridPanel
import com.alee.extended.panel.GroupPanel
import com.alee.extended.panel.WebComponentPanel
import com.alee.laf.button.WebButton
import com.alee.laf.combobox.WebComboBox
import com.alee.laf.label.WebLabel
import com.alee.laf.panel.WebPanel
import com.alee.laf.scroll.WebScrollPane
import com.alee.laf.slider.WebSlider
import com.alee.laf.text.WebTextField
import com.alee.managers.notification.NotificationIcon
import com.alee.managers.notification.NotificationManager
import com.alee.managers.notification.WebNotificationPopup
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.graphics.renderer.command.GetMaterialCommand
import de.hanno.hpengine.engine.graphics.renderer.command.InitMaterialCommand
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.util.gui.input.*
import org.apache.commons.io.FileUtils
import org.joml.Vector3f
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.Comparator

class MaterialView(private val engine: Engine<*>, var material: SimpleMaterial) : WebPanel() {
    private val nameField = WebTextField(material.materialInfo.name)

    private val allTexturesSorted: List<Texture<*>>
        get() = engine.textureManager.textures.values.sortedWith(Comparator { o1, o2 -> o1.toString().compareTo(o2.toString()) })

    init {
        isUndecorated = true
        this.setSize(600, 600)
        setMargin(5)

        init(material)
    }

    private fun init(material: SimpleMaterial) {
        this.material = material
        nameField.text = material.materialInfo.name
        this.removeAll()
        val panels = ArrayList<Component>()

        addTexturePanel(panels)
        addValuePanels(panels)

        val components = panels.toTypedArray()

        val materialAttributesPane = WebScrollPane(GridPanel(panels.size, 1, *components))
        this.layout = BorderLayout()
        this.add(materialAttributesPane, BorderLayout.CENTER)
    }

    private fun addTexturePanel(panels: MutableList<Component>) {

        val webComponentPanel = WebComponentPanel(true)
        webComponentPanel.setElementMargin(4)

        addExistingTexturesPanels(webComponentPanel)
        addMissingTexturesPanels(webComponentPanel)

        panels.add(webComponentPanel)
    }

    private fun addMissingTexturesPanels(webComponentPanel: WebComponentPanel) {
        val missingMaps = EnumSet.allOf(MAP::class.java)
        missingMaps.removeAll(material.materialInfo.maps.keys)
        for (map in missingMaps) {
            val label = WebLabel(map.name)

            val textures = allTexturesSorted
            val select = WebComboBox(Vector(textures))
            select.selectedIndex = -1

            select.addActionListener { e ->
                val cb = e.source as WebComboBox
                val selectedTexture = textures[cb.selectedIndex]
                engine.scene.materialManager.changeMaterial(material.put(map, selectedTexture as Texture<TextureDimension2D>))
//                material.materialInfo.maps.put(map, selectedTexture)
                addMaterialInitCommand(material)
            }

            val removeTextureButton = WebButton("Remove")
            removeTextureButton.addActionListener { e ->
                engine.scene.materialManager.changeMaterial(material.remove(map))
//                material.materialInfo.maps.remove(map)
                select.selectedIndex = -1
                addMaterialInitCommand(material)
            }

            val groupPanel = GroupPanel(4, label, select, removeTextureButton)
            webComponentPanel.addElement(groupPanel)
        }
    }

    private fun addExistingTexturesPanels(webComponentPanel: WebComponentPanel) {
        for (map in material.materialInfo.maps.keys) {
            val texture: Texture<TextureDimension2D> = material.materialInfo.maps[map]!!

            val label = WebLabel(map.name)

            val textures = allTexturesSorted
            val select = WebComboBox(Vector(textures))

            val assignedTexture = textures.indexOf(texture)
            select.selectedIndex = assignedTexture

            select.addActionListener { e ->
                val cb = e.source as WebComboBox
                val selectedTexture: Texture<TextureDimension2D> = textures[cb.selectedIndex] as Texture<TextureDimension2D>
                engine.scene.materialManager.changeMaterial(material.materialInfo.put(map, selectedTexture))
//                material.materialInfo.maps.put(map, selectedTexture)
                engine.eventBus.post(MaterialChangedEvent(material))
            }

            val removeTextureButton = WebButton("Remove")
            removeTextureButton.addActionListener { e ->
                engine.scene.materialManager.changeMaterial(material.materialInfo.remove(map))
//                material.materialInfo.maps.remove(map)
                addMaterialInitCommand(material)
            }

            val groupPanel = GroupPanel(4, label, select, removeTextureButton)
            webComponentPanel.addElement(groupPanel)
        }
    }

    private fun addValuePanels(panels: MutableList<Component>) {
        val webComponentPanel = WebComponentPanel(true)
        webComponentPanel.setElementMargin(4)
        webComponentPanel.layout = FlowLayout()

        webComponentPanel.addElement(nameField)

        webComponentPanel.addElement(object : WebFormattedVec3Field("Diffuse", material.materialInfo.diffuse) {
            override fun onValueChange(current: Vector3f) {
                engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(diffuse = current))
                engine.eventBus.post(MaterialChangedEvent(material))
            }
        })
        webComponentPanel.addElement(ColorChooserButton("Diffuse", object : ColorChooserFrame() {
            override fun onColorChange(color: Vector3f) {
                engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(diffuse = color))
                engine.eventBus.post(MaterialChangedEvent(material))
            }
        }))

        run {
            val roughnessInput = object : LimitedWebFormattedTextField(0f, 1f) {
                override fun onChange(currentValue: Float) {
                    engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(roughness = currentValue))
                    engine.eventBus.post(MaterialChangedEvent(material))
                }
            }
            roughnessInput.setValue(material.materialInfo.roughness)
            run {
                val roughnessSliderInput = object : SliderInput("Roughness", WebSlider.HORIZONTAL, 0, 100, (material.materialInfo.roughness * 100).toInt()) {

                    override fun onValueChange(value: Int, delta: Int) {
                        roughnessInput.setValue(value.toFloat() / 100f)
                        engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(roughness = value.toFloat() / 100f))
                        engine.eventBus.post(MaterialChangedEvent(material))
                    }
                }
                val groupPanelRoughness = GroupPanel(4, WebLabel("Roughness"), roughnessInput, roughnessSliderInput)
                webComponentPanel.addElement(groupPanelRoughness)
            }
        }
        run {
            val metallicInput = object : LimitedWebFormattedTextField(0f, 1f) {
                override fun onChange(currentValue: Float) {
                    engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(metallic = currentValue))
                    engine.eventBus.post(MaterialChangedEvent(material))
                }
            }
            metallicInput.setValue(material.materialInfo.metallic)
            run {
                val metallicSliderInput = object : SliderInput("Metallic", WebSlider.HORIZONTAL, 0, 100, (material.materialInfo.metallic * 100).toInt()) {

                    override fun onValueChange(value: Int, delta: Int) {
                        metallicInput.setValue(value.toFloat() / 100f)
                        engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(metallic = value.toFloat() / 100f))
                        engine.eventBus.post(MaterialChangedEvent(material))
                    }
                }

                val groupPanelMetallic = GroupPanel(4, WebLabel("Metallic"), metallicInput, metallicSliderInput)
                webComponentPanel.addElement(groupPanelMetallic)
            }
        }
        run {
            val ambientInput = object : LimitedWebFormattedTextField(0f, 1f) {
                override fun onChange(currentValue: Float) {
                    engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(ambient = currentValue))
                    engine.eventBus.post(MaterialChangedEvent(material))
                }
            }
            ambientInput.setValue(material.materialInfo.ambient)
            run {
                val ambientSliderInput = object : SliderInput("Ambient/Emmissive", WebSlider.HORIZONTAL, 0, 100, (material.materialInfo.ambient * 100).toInt()) {

                    override fun onValueChange(value: Int, delta: Int) {
                        ambientInput.setValue(value.toFloat() / 100f)
                        engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(ambient = value.toFloat() / 100f))
                        engine.eventBus.post(MaterialChangedEvent(material))
                    }
                }

                val groupPanelAmbient = GroupPanel(4, WebLabel("Ambient"), ambientInput, ambientSliderInput)
                webComponentPanel.addElement(groupPanelAmbient)
            }
        }
        run {
            val transparencyInput = object : LimitedWebFormattedTextField(0f, 1f) {
                override fun onChange(currentValue: Float) {
                    engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(transparency = currentValue))
                    engine.eventBus.post(MaterialChangedEvent(material))
                }
            }
            transparencyInput.setValue(material.materialInfo.transparency)
            run {
                val transparencySliderInput = object : SliderInput("Transparency", WebSlider.HORIZONTAL, 0, 100, (material.materialInfo.transparency * 100).toInt()) {

                    override fun onValueChange(value: Int, delta: Int) {
                        transparencyInput.setValue(value.toFloat() / 100f)
                        engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(transparency = value.toFloat() / 100f))
                        engine.eventBus.post(MaterialChangedEvent(material))
                    }
                }

                val groupPanelTransparency = GroupPanel(4, WebLabel("Transparency"), transparencyInput, transparencySliderInput)
                webComponentPanel.addElement(groupPanelTransparency)
            }
        }
        run {
            val parallaxScaleInput = object : LimitedWebFormattedTextField(0f, 1f) {
                override fun onChange(currentValue: Float) {
                    engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(parallaxScale = currentValue))
                    engine.eventBus.post(MaterialChangedEvent(material))
                }
            }
            parallaxScaleInput.setValue(material.materialInfo.parallaxScale)
            run {
                val parallaxScaleSliderInput = object : SliderInput("Parallax Scale", WebSlider.HORIZONTAL, 0, 100, (material.materialInfo.parallaxScale * 100).toInt()) {

                    override fun onValueChange(value: Int, delta: Int) {
                        parallaxScaleInput.setValue(value.toFloat() / 100f)
                        engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(parallaxScale = value.toFloat() / 100f))
                        engine.eventBus.post(MaterialChangedEvent(material))
                    }
                }

                val groupPanelParallaxScale = GroupPanel(4, WebLabel("Parallax Scale"), parallaxScaleInput, parallaxScaleSliderInput)
                webComponentPanel.addElement(groupPanelParallaxScale)
            }
        }
        run {
            val parallaxBiasInput = object : LimitedWebFormattedTextField(0f, 1f) {
                override fun onChange(currentValue: Float) {
                    engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(parallaxBias = currentValue))
                    engine.eventBus.post(MaterialChangedEvent(material))
                }
            }
            parallaxBiasInput.setValue(material.materialInfo.parallaxBias)
            run {
                val parallaxBiasSliderInput = object : SliderInput("Parallax Bias", WebSlider.HORIZONTAL, 0, 100, (material.materialInfo.parallaxScale * 100).toInt()) {

                    override fun onValueChange(value: Int, delta: Int) {
                        parallaxBiasInput.setValue(value.toFloat() / 100f)
                        engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(parallaxBias = value.toFloat() / 100f))
                        engine.eventBus.post(MaterialChangedEvent(material))
                    }
                }

                val groupPanelParallaxBias = GroupPanel(4, WebLabel("Parallax Bias"), parallaxBiasInput, parallaxBiasSliderInput)
                webComponentPanel.addElement(groupPanelParallaxBias)
            }
        }

        run {
            val values = EnumSet.allOf(SimpleMaterial.MaterialType::class.java).toTypedArray()
            val materialTypeInput = WebComboBox(values, values.indexOf(material.materialInfo.materialType))
            materialTypeInput.addActionListener { e ->
                val selected = materialTypeInput.selectedItem as SimpleMaterial.MaterialType
                engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(materialType = selected))
                engine.eventBus.post(MaterialChangedEvent(material))
            }
            val materialTypePanel = GroupPanel(4, WebLabel("Material Type"), materialTypeInput)
            webComponentPanel.addElement(materialTypePanel)
        }
        run {
            val values = EnumSet.allOf(SimpleMaterial.TransparencyType::class.java).toTypedArray()
            val transparencyTypeInput = WebComboBox(values, values.indexOf(material.materialInfo.transparencyType))
            transparencyTypeInput.addActionListener { e ->
                val selected = transparencyTypeInput.selectedItem as SimpleMaterial.TransparencyType
                engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(transparencyType = selected))
                engine.eventBus.post(MaterialChangedEvent(material))
            }
            val materialTypePanel = GroupPanel(4, WebLabel("Transparency Type"), transparencyTypeInput)
            webComponentPanel.addElement(materialTypePanel)
        }
        run {
            val values = EnumSet.allOf(SimpleMaterial.ENVIRONMENTMAP_TYPE::class.java).toTypedArray()
            val environmentMapInput = WebComboBox(values, values.indexOf(material.materialInfo.environmentMapType))
            environmentMapInput.addActionListener { e ->
                val selected = environmentMapInput.selectedItem as SimpleMaterial.ENVIRONMENTMAP_TYPE
                engine.scene.materialManager.changeMaterial(material.materialInfo.copyXXX(environmentMapType = selected))
            }
            val groupPanelEnvironmentMapType = GroupPanel(4, WebLabel("Environment map type"), environmentMapInput)
            webComponentPanel.addElement(groupPanelEnvironmentMapType)
        }

        panels.add(webComponentPanel)
    }

    private fun copyShaderIfNotPresent(chosenFile: File, shaderFileInWorkDir: File) {
        if (!shaderFileInWorkDir.exists()) {
            try {
                FileUtils.copyFile(chosenFile, shaderFileInWorkDir)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    private fun addMaterialInitCommand(material: SimpleMaterial) {
        init(material)
        val result = engine.gpuContext.calculate {
            InitMaterialCommand(material, engine.scene.materialManager).execute()
        }
        if (result.isSuccessful) {
            showNotification(NotificationIcon.plus, "SimpleMaterial changed")

            init(result.material)
            engine.eventBus.post(MaterialChangedEvent(material))
        } else {
            showNotification(NotificationIcon.error, "Not able to change materials")
        }
    }

    private fun showNotification(icon: NotificationIcon, text: String) {
        val notificationPopup = WebNotificationPopup()
        notificationPopup.setIcon(icon)
        notificationPopup.displayTime = 2000
        notificationPopup.content = WebLabel(text)
        NotificationManager.showNotification(notificationPopup)
    }
}
