package de.hanno.hpengine.engine.graphics.imgui

import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.clear
import de.hanno.hpengine.engine.component.artemis.*
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.extension.SharedDepthBuffer
import de.hanno.hpengine.engine.graphics.FinalOutput
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.imgui.dsl.Window
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.loadDemoScene
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import imgui.ImGui
import imgui.flag.*
import imgui.flag.ImGuiWindowFlags.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImInt
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.lwjgl.glfw.GLFW

class ImGuiEditor(
    private val window: GlfwWindow,
    private val gpuContext: GpuContext<OpenGl>,
    private val finalOutput: FinalOutput,
    private val config: ConfigImpl,
    private val sharedDepthBuffer: SharedDepthBuffer,
    private val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    private val renderExtensions: List<DeferredRenderExtension<OpenGl>>
) : RenderSystem {
    private val initialOutput = finalOutput.texture2D
    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    private val renderTarget = RenderTarget(
        gpuContext,
        FrameBuffer(gpuContext, sharedDepthBuffer.depthBuffer),
        name = "Final Image",
        width = finalOutput.texture2D.dimension.width,
        height = finalOutput.texture2D.dimension.height,
        textures = listOf(finalOutput.texture2D)
    )
    private var selection: Selection? = null
    private fun selectOrUnselect(newSelection: Selection) {
        selection = if (selection == newSelection) null else newSelection
    }

    val output = ImInt(-1)
    val renderTargetTextures: List<Texture> get() = gpuContext.registeredRenderTargets.flatMap { it.textures }
    val currentOutputTexture: Texture get() = renderTargetTextures[output.get()]

    override lateinit var artemisWorld: World

    init {
        window {
            ImGui.createContext()
            ImGui.getIO().apply {
//                addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                addConfigFlags(ImGuiConfigFlags.DockingEnable)
            }
        }
    }

    private val imGuiImplGlfw = window {
        ImGuiImplGlfw().apply {
            init(window.handle, true)
        }
    }
    private val imGuiImplGl3 = window {
        ImGuiImplGl3().apply {
            init(glslVersion)
        }
    }

    override fun renderEditor(result: DrawResult, renderState: RenderState) {
        if (!config.debug.isEditorOverlay) return

        renderTarget.use(gpuContext, false)
        if(output.get() > 0) {
            (currentOutputTexture as? Texture2D)?.let {
                renderTarget.setTargetTexture(it.id, 0)
                finalOutput.texture2D = it
            }
        } else {
            renderTarget.setTargetTexture(initialOutput.id, 0)
            finalOutput.texture2D = initialOutput
        }
        imGuiImplGlfw.newFrame()
        ImGui.getIO().setDisplaySize(renderTarget.width.toFloat(), renderTarget.height.toFloat())
        try {
            val screenWidth = ImGui.getIO().displaySizeX
            val screenHeight = ImGui.getIO().displaySizeY

            val leftPanelYOffset = screenHeight * 0.015f
            val leftPanelWidthPercentage = 0.1f
            val leftPanelWidth = screenWidth * leftPanelWidthPercentage

            val rightPanelWidthPercentage = 0.2f
            val rightPanelWidth = screenWidth * rightPanelWidthPercentage

            val midPanelHeight = screenHeight - leftPanelYOffset
            val midPanelWidth = screenWidth - leftPanelWidth - rightPanelWidth

            ImGui.newFrame()

            (selection as? EntitySelection)?.let { entitySelection ->
                artemisWorld.getEntity(entitySelection.entity).getComponent(TransformComponent::class.java)?.let {
                    showGizmo(
                        viewMatrixAsBuffer = renderState.camera.viewMatrixAsBuffer,
                        projectionMatrixAsBuffer = renderState.camera.projectionMatrixAsBuffer,
                        fovY = renderState.camera.fov,
                        near = renderState.camera.near,
                        far = renderState.camera.far,
                        editorCameraInputSystem = artemisWorld.getSystem(EditorCameraInputSystem::class.java),
                        windowWidth = screenWidth,
                        windowHeight = screenHeight,
                        panelWidth = midPanelWidth,
                        panelHeight = midPanelHeight,
                        windowPositionX = 0f,
                        windowPositionY = 0f,
                        panelPositionX = leftPanelWidth,
                        panelPositionY = leftPanelYOffset,
                        transform = it.transform,
                        viewMatrix = artemisWorld.getSystem(TagManager::class.java).getEntity(primaryCamera)
                            .getComponent(TransformComponent::class.java).transform
                    )
                }
            }

            if (output.get() != -1) {
                val windowFlags =
                    NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                            NoNavFocus or  // so turn off everything that would make it act like a window
                            NoDocking or
                            NoTitleBar or
                            NoResize or
                            NoMove or
                            NoCollapse or
                            NoBackground
                de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
                    ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
                    window("Main", windowFlags) {
                        ImGui.popStyleVar()

                        ImGui.image(currentOutputTexture.id, screenWidth, screenHeight)
                    }
                }
            }
            menu(screenWidth, screenHeight)

            leftPanel(renderState, leftPanelYOffset, leftPanelWidth, screenHeight)

            rightPanel(screenWidth, rightPanelWidthPercentage, rightPanelWidth, screenHeight)

//            ImGui.showDemoWindow(ImBoolean(true))
        } catch (it: Exception) {
            it.printStackTrace()
        } finally {
            ImGui.render()
            imGuiImplGl3.renderDrawData(ImGui.getDrawData())
            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                val backupWindowHandle = GLFW.glfwGetCurrentContext()
                ImGui.updatePlatformWindows()
                ImGui.renderPlatformWindowsDefault()
                GLFW.glfwMakeContextCurrent(backupWindowHandle)
            }
        }
    }

    private fun menu(screenWidth: Float, screenHeight: Float) {
        // https://github-wiki-see.page/m/JeffM2501/raylibExtras/wiki/Using-ImGui-Docking-Branch-with-rlImGui
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(screenWidth, screenHeight)
        val windowFlags =
            NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                    NoNavFocus or  // so turn off everything that would make it act like a window
                    NoDocking or
                    NoTitleBar or
                    NoResize or
                    NoMove or
                    NoCollapse or
                    MenuBar or
                    NoBackground
        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            window("Main", windowFlags) {
                ImGui.popStyleVar()

                menuBar {
                    menu("File") {
                        menuItem("New Scene") {
                            artemisWorld.clear()
                        }
                        menuItem("Load Demo") {
                            artemisWorld.loadDemoScene()
                        }
                    }
                }
            }
        }
    }

    private fun leftPanel(
        renderState: RenderState,
        leftPanelYOffset: Float,
        leftPanelWidth: Float,
        screenHeight: Float
    ) {
        ImGui.setNextWindowPos(0f, leftPanelYOffset)
        ImGui.setNextWindowSize(leftPanelWidth, screenHeight)
        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            window("Scene", NoCollapse or NoResize) {
                val componentsForEntity: Map<Int, Bag<Component>> = renderState.componentsForEntities
                componentsForEntity.forEach { (entityIndex, components) ->
                    treeNode(
                        components.firstIsInstanceOrNull<NameComponent>()?.name
                            ?: (artemisWorld.getSystem(TagManager::class.java).getTag(entityIndex)
                                ?: "Entity $entityIndex")
                    ) {
                        text("Entity") {
                            selectOrUnselect(SimpleEntitySelection(entityIndex, components.data.filterNotNull()))
                        }
                        components.forEach { component ->
                            text(component.javaClass.simpleName) {
                                when (component) {
                                    is ModelComponent -> {
                                        selectOrUnselect(ModelComponentSelection(entityIndex, component))
                                    }
                                    is NameComponent -> selectOrUnselect(
                                        NameSelection(
                                            entityIndex,
                                            component.name
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                treeNode("Materials") {
                    artemisWorld.getSystem(MaterialManager::class.java)?.materials?.forEach { material ->
                        text(material.name) {
                            selectOrUnselect(MaterialSelection(material))
                        }
                    }
                }
            }
        }
    }

    private fun rightPanel(
        screenWidth: Float,
        rightPanelWidthPercentage: Float,
        rightPanelWidth: Float,
        screenHeight: Float
    ) {
        ImGui.setNextWindowPos(screenWidth * (1.0f - rightPanelWidthPercentage), 0f)
        ImGui.setNextWindowSize(rightPanelWidth, screenHeight)
        ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            window("Right panel", NoCollapse or NoResize or NoTitleBar) {
                tabBar("Foo") {

                    when (val selection = selection) {
                        is MeshSelection -> {
                            tab("Entity") { }
                        }
                        is ModelComponentSelection -> {
                            tab("Entity") {
                                artemisWorld.getSystem(ModelSystem::class.java)[selection.modelComponent.modelComponentDescription]?.let {
                                    if (ImGui.checkbox("Invert Y Texture Coord", it.isInvertTexCoordY)) {
                                        it.isInvertTexCoordY = !it.isInvertTexCoordY
                                    }
                                    val material = it.material
                                    materialGrid(material)
                                }
                            }
                        }
                        is ModelSelection -> {
                            tab("Entity") { }
                        }
                        is NameSelection -> {
                            tab("Entity") { }
                        }
                        is SimpleEntitySelection -> tab("Entity") {
                            val system = artemisWorld.getSystem(InvisibleComponentSystem::class.java)
                            selection.components.firstIsInstanceOrNull<NameComponent>()?.run {
                                text("Name: $name")
                            }
                            checkBox("Visible", !system.invisibleComponentMapper.has(selection.entity)) { visible ->
                                system.invisibleComponentMapper.set(selection.entity, !visible)
                            }
                            selection.components.firstIsInstanceOrNull<TransformComponent>()?.run {
                                val position = transform.position
                                val positionArray = floatArrayOf(position.x, position.y, position.z)
                                ImGui.inputFloat3("Position", positionArray, "%.3f", ImGuiInputTextFlags.ReadOnly)
                            }
                        }
                        is GiVolumeSelection -> {
                            tab("Entity") { }
                        }
                        is MaterialSelection -> {
                            tab("Entity") {
                                materialGrid(selection.material)
                            }
                        }
                        Selection.None -> {
                            tab("Entity") { }
                        }
                        is OceanWaterSelection -> {
                            tab("Entity") { }
                        }
                        is ReflectionProbeSelection -> {
                            tab("Entity") { }
                        }
                        null -> {
                            tab("Entity") { }
                        }
                    }!!

                    tab("Output") {
                        var counter = 0
                        text("Select output")
                        ImGui.radioButton("Default", output, -1)
                        gpuContext.registeredRenderTargets.forEach { target ->
                            target.renderedTextures.forEachIndexed { textureIndex, texture ->
                                if(ImGui.radioButton(target.name + "[$textureIndex]", output, counter)) {
                                    (currentOutputTexture as? Texture2D)?.let {
                                        finalOutput.texture2D = it
                                    }
                                }
                                counter++
                            }
                        }
                    }
                    tab("RenderExtensions") {
                        deferredRenderExtensionConfig.run {
                            renderExtensions.forEach {
                                if (ImGui.checkbox(it.javaClass.simpleName, it.enabled)) {
                                    it.enabled = !it.enabled
                                }
                            }
                        }
                    }
                    tab("Config") {
                        if (ImGui.checkbox("Draw lines", config.debug.isDrawLines)) {
                            config.debug.isDrawLines = !config.debug.isDrawLines
                        }
                        if (ImGui.checkbox("Editor", config.debug.isEditorOverlay)) {
                            config.debug.isEditorOverlay = !config.debug.isEditorOverlay
                        }
                    }
                }
            }
        }
    }

    private fun Window.materialGrid(material: Material) {
        val materialInfo = material

        text(material.name)
        val colors = floatArrayOf(
            materialInfo.diffuse.x,
            materialInfo.diffuse.y,
            materialInfo.diffuse.z
        )
        if (ImGui.colorPicker3("Albedo", colors)) {
            materialInfo.diffuse.x = colors[0]
            materialInfo.diffuse.y = colors[1]
            materialInfo.diffuse.z = colors[2]
        }
        floatInput("Roughness", materialInfo.roughness) { floatArray -> materialInfo.roughness = floatArray[0] }
        floatInput("Metallic", materialInfo.metallic) { floatArray -> materialInfo.metallic = floatArray[0] }
        floatInput("Ambient", materialInfo.ambient) { floatArray -> materialInfo.ambient = floatArray[0] }
        floatInput("Transparency", materialInfo.transparency) { floatArray ->
            materialInfo.transparency = floatArray[0]
        }
        floatInput("ParallaxScale", materialInfo.parallaxScale) { floatArray ->
            materialInfo.parallaxScale = floatArray[0]
        }
        floatInput("ParallaxBias", materialInfo.parallaxBias) { floatArray ->
            materialInfo.parallaxBias = floatArray[0]
        }
        floatInput("UVScaleX", materialInfo.uvScale.x, 0.01f, 10f) { floatArray ->
            materialInfo.uvScale.x = floatArray[0]
        }
        floatInput("UVScaleY", materialInfo.uvScale.y, 0.01f, 10f) { floatArray ->
            materialInfo.uvScale.y = floatArray[0]
        }
        floatInput("LODFactor", materialInfo.lodFactor) { floatArray -> materialInfo.lodFactor = floatArray[0] }
        if (ImGui.checkbox("WorldSpaceTexCoords", materialInfo.useWorldSpaceXZAsTexCoords)) {
            materialInfo.useWorldSpaceXZAsTexCoords = !materialInfo.useWorldSpaceXZAsTexCoords
        }
        if (ImGui.beginCombo("Type", materialInfo.materialType.toString())) {
            Material.MaterialType.values().forEach { type ->
                val selected = materialInfo.materialType == type
                if (ImGui.selectable(type.toString(), selected)) {
                    materialInfo.materialType = type
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.beginCombo("TransparencyType", materialInfo.transparencyType.toString())) {
            Material.TransparencyType.values().forEach { type ->
                val selected = materialInfo.transparencyType == type
                if (ImGui.selectable(type.toString(), selected)) {
                    materialInfo.transparencyType = type
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.checkbox("BackFaceCulling", materialInfo.cullBackFaces)) {
            materialInfo.cullBackFaces = !materialInfo.cullBackFaces
        }
        if (ImGui.checkbox("DepthTest", materialInfo.depthTest)) {
            materialInfo.depthTest = !materialInfo.depthTest
        }
        if (ImGui.beginCombo("EnvironmentMapType", materialInfo.environmentMapType.toString())) {
            Material.ENVIRONMENTMAP_TYPE.values().forEach { type ->
                val selected = materialInfo.environmentMapType == type
                if (ImGui.selectable(type.toString(), selected)) {
                    materialInfo.environmentMapType = type
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.checkbox("CastShadows", materialInfo.isShadowCasting)) {
            materialInfo.isShadowCasting = !materialInfo.isShadowCasting
        }
        textureSelection(materialInfo)
    }

    private fun floatInput(label: String, initial: Float, min: Float = 0.001f, max: Float = 1.0f, onChange: (FloatArray) -> Unit) {
        val floatArray = floatArrayOf(initial)
        if (ImGui.sliderFloat(label, floatArray, min, max)) {
            onChange(floatArray)
        }
    }
    private fun float2Input(label: String, initial0: Float, initial1:Float, min: Float = 0.001f, max: Float = 1.0f, onChange: (FloatArray) -> Unit) {
        val floatArray = floatArrayOf(initial0, initial1)
        if (ImGui.sliderFloat(label, floatArray, min, max)) {
            onChange(floatArray)
        }
    }

    private fun textureSelection(material: Material) {
        ImGui.text("Textures")
        val all2DTextures =
            artemisWorld.getSystem(TextureManager::class.java).textures.filterValues { it is FileBasedTexture2D }
        val all2DTexturesNames = all2DTextures.keys.toTypedArray()

        Material.MAP.values().forEach { type ->
            material.maps[type].let { currentTexture ->
                val currentIndex = all2DTextures.values.indexOf(currentTexture)
                val is2DTexture = currentIndex > -1
                if(is2DTexture || currentTexture == null) {
                    val previewValue = if (currentTexture != null) all2DTexturesNames[currentIndex] else "None"
                    if (ImGui.beginCombo(type.name, previewValue)) {
                        if (ImGui.selectable("None", currentTexture == null)) {
                            material.maps.remove(type)
                        }
                        all2DTextures.forEach { (name, texture) ->
                            val selected = currentTexture == texture
                            if (ImGui.selectable(name, selected)) {
                                material.maps[type] = texture
                            }
                            if (selected) {
                                ImGui.setItemDefaultFocus()
                            }
                        }

                        ImGui.endCombo()
                    }
                }
            }
        }
    }
}