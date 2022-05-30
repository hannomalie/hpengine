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
import de.hanno.hpengine.engine.graphics.*
import de.hanno.hpengine.engine.graphics.imgui.dsl.Window
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledPipeline
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
import org.lwjgl.opengl.GL11

class ImGuiEditor(
    private val window: GlfwWindow,
    private val gpuContext: GpuContext<OpenGl>,
    private val textureManager: TextureManager,
    private val finalOutput: FinalOutput,
    private val debugOutput: DebugOutput,
    private val config: ConfigImpl,
    private val sharedDepthBuffer: SharedDepthBuffer,
    private val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    private val renderExtensions: List<DeferredRenderExtension<OpenGl>>
) : RenderSystem {
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
    val renderTargetTextures: List<Texture> get() = gpuContext.registeredRenderTargets.flatMap { it.textures } + textureManager.texturesForDebugOutput.values
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

                        ImGui.image(finalOutput.texture2D.id, screenWidth, screenHeight)
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
                                    is MaterialComponent -> {
                                        selectOrUnselect(MaterialSelection(component.material))
                                    }
                                    is NameComponent -> selectOrUnselect(
                                        NameSelection(
                                            entityIndex,
                                            component.name
                                        )
                                    )
                                    is TransformComponent -> selectOrUnselect(
                                        TransformSelection(
                                            entityIndex,
                                            component
                                        )
                                    )
                                    is OceanWaterComponent -> selectOrUnselect(OceanWaterSelection(component))
                                }
                            }
                        }
                    }
                }
                treeNode("Materials") {
                    artemisWorld.getSystem(MaterialManager::class.java)?.materials?.sortedBy { it.name }
                        ?.forEach { material ->
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
                        null -> {
                            tab("Entity") { }
                        }
                        is MeshSelection -> {
                            tab("Entity") { }
                        }
                        is ModelComponentSelection -> {
                            tab("Entity") {
                                artemisWorld.getSystem(ModelSystem::class.java)[selection.modelComponent.modelComponentDescription]?.let {
                                    if (ImGui.checkbox("Invert Y Texture Coord", it.isInvertTexCoordY)) {
                                        it.isInvertTexCoordY = !it.isInvertTexCoordY
                                    }
                                    val material = it.meshes.first().material
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
                            tab("Entity") {
                                val oceanWater = selection.oceanWater
                                val colors = floatArrayOf(
                                    oceanWater.albedo.x,
                                    oceanWater.albedo.y,
                                    oceanWater.albedo.z
                                )
                                if (ImGui.colorPicker3("Albedo", colors)) {
                                    oceanWater.albedo.x = colors[0]
                                    oceanWater.albedo.y = colors[1]
                                    oceanWater.albedo.z = colors[2]
                                }
                                floatInput("Amplitude", oceanWater.amplitude, min = 0.1f, max = 5f) { floatArray ->
                                    oceanWater.amplitude = floatArray[0]
                                }
                                floatInput("Windspeed", oceanWater.windspeed, min = 0.0f, max = 250f) { floatArray ->
                                    oceanWater.windspeed = floatArray[0]
                                }
                                float2Input(
                                    "Direction",
                                    oceanWater.direction.x,
                                    oceanWater.direction.y,
                                    min = 0.0f,
                                    max = 1.0f
                                ) { floatArray ->
                                    oceanWater.direction.x = floatArray[0]
                                    oceanWater.direction.y = floatArray[1]
                                }
                                floatInput(
                                    "Wave Height",
                                    oceanWater.waveHeight,
                                    min = 0.0f,
                                    max = 10.0f
                                ) { floatArray ->
                                    oceanWater.waveHeight = floatArray[0]
                                }
                                checkBox("Choppy", oceanWater.choppy) { boolean ->
                                    oceanWater.choppy = boolean
                                }
                                floatInput("Choppiness", oceanWater.choppiness, min = 0.0f, max = 1.0f) { floatArray ->
                                    oceanWater.choppiness = floatArray[0]
                                }
                                floatInput("Time Factor", oceanWater.timeFactor, min = 0.0f, max = 10f) { floatArray ->
                                    oceanWater.timeFactor = floatArray[0]
                                }
                            }
                        }
                        is ReflectionProbeSelection -> {
                            tab("Entity") { }
                        }
                        is TransformSelection -> tab("Entity") {
                            if(ImGui.button("Reset transform")) {
                                selection.transform.transform.identity()
                            } else Unit
                        }
                    }!!

                    tab("Output") {

                        if (ImGui.beginCombo("Mipmap Level", debugOutput.mipmapLevel.toString())) {
                            repeat(10) {
                                val selected = debugOutput.mipmapLevel == it
                                if (ImGui.selectable(it.toString(), selected)) {
                                    debugOutput.mipmapLevel = it
                                }
                                if (selected) {
                                    ImGui.setItemDefaultFocus()
                                }
                            }
                            ImGui.endCombo()
                        }
                        var counter = 0
                        text("Select output")
                        if(ImGui.radioButton("Default", output, -1)) {
                            debugOutput.texture2D = null
                        }
                        gpuContext.registeredRenderTargets.forEach { target ->
                            target.renderedTextures.forEachIndexed { textureIndex, texture ->
                                if (ImGui.radioButton(target.name + "[$textureIndex]", output, counter)) {
                                    (currentOutputTexture as? Texture2D)?.let {
                                        debugOutput.texture2D = it
                                    }
                                }
                                counter++
                            }
                        }
                        textureManager.texturesForDebugOutput.forEach { (name, texture) ->
                            if (ImGui.radioButton(name, output, counter)) {
                                (currentOutputTexture as? Texture2D)?.let {
                                    debugOutput.texture2D = it
                                }
                            }
                            counter++
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
                        if (ImGui.checkbox("Draw indirect", config.performance.isIndirectRendering)) {
                            config.performance.isIndirectRendering = !config.performance.isIndirectRendering
                        }
                        if (ImGui.checkbox(
                                "Force singlethreaded rendering",
                                config.debug.forceSingleThreadedRendering
                            )
                        ) {
                            config.debug.forceSingleThreadedRendering = !config.debug.forceSingleThreadedRendering
                        }
                        if (ImGui.checkbox("Use cpu frustum culling", config.debug.isUseCpuFrustumCulling)) {
                            config.debug.isUseCpuFrustumCulling = !config.debug.isUseCpuFrustumCulling
                        }
                        if (ImGui.checkbox("Use gpu frustum culling", config.debug.isUseGpuFrustumCulling)) {
                            config.debug.isUseGpuFrustumCulling = !config.debug.isUseGpuFrustumCulling
                        }
                        if (ImGui.checkbox("Use gpu occlusion culling", config.debug.isUseGpuOcclusionCulling)) {
                            config.debug.isUseGpuOcclusionCulling = !config.debug.isUseGpuOcclusionCulling
                        }
                        if (ImGui.button("Freeze culling")) {
                            config.debug.freezeCulling = !config.debug.freezeCulling
                        }
                        if (ImGui.checkbox("Print pipeline output", config.debug.isPrintPipelineDebugOutput)) {
                            config.debug.isPrintPipelineDebugOutput = !config.debug.isPrintPipelineDebugOutput
                        }
                        if (ImGui.checkbox("Editor", config.debug.isEditorOverlay)) {
                            config.debug.isEditorOverlay = !config.debug.isEditorOverlay
                        }
                    }
                    tab("Render") {
                        val renderManager = artemisWorld.getSystem(RenderManager::class.java)!!
                        val renderMode = renderManager.renderMode
                        if (ImGui.button("Use ${if (renderMode is RenderMode.Normal) "Single Step" else "Normal"}")) {
                            renderManager.renderMode = when (renderMode) {
                                RenderMode.Normal -> RenderMode.SingleFrame()
                                is RenderMode.SingleFrame -> RenderMode.Normal
                            }
                        }
                        if (renderMode is RenderMode.SingleFrame) {
                            if (ImGui.button("Step")) {
                                renderMode.frameRequested.getAndSet(true)
                            }
                        }

                        renderManager.renderSystems
                            .firstIsInstanceOrNull<ExtensibleDeferredRenderer>()?.apply {
                                val currentReadState = renderStateManager.renderState.currentReadState
                                var counter = 0
                                when (val indirectPipeline = currentReadState[indirectPipeline]) {
                                    is GPUCulledPipeline -> {
                                        GL11.glFinish()
                                        val commandOrganization = indirectPipeline.commandOrganizationStatic
                                        val batchCount = commandOrganization.filteredRenderBatches.size
                                        ImGui.text("Input batches: $batchCount")
                                        val drawCount = commandOrganization.drawCountsCompacted.buffer.getInt(0)
                                        ImGui.text("Draw commands: $drawCount")
                                        commandOrganization.commandBuffer.typedBuffer.forEach(batchCount) {
                                            if(counter < drawCount) {
                                                val entityOffset = commandOrganization.entityOffsetBuffersCulled[counter].value
                                                ImGui.text("$entityOffset - ")
                                                ImGui.sameLine()
                                                ImGui.text(it.print())
                                            } else {
                                                ImGui.text("_ - ")
                                                ImGui.sameLine()
                                                ImGui.text(it.print())
                                            }
                                            counter++
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    private fun Window.materialGrid(material: Material) {
        text(material.name)
        val colors = floatArrayOf(
            material.diffuse.x,
            material.diffuse.y,
            material.diffuse.z
        )
        if (ImGui.colorPicker3("Albedo", colors)) {
            material.diffuse.x = colors[0]
            material.diffuse.y = colors[1]
            material.diffuse.z = colors[2]
        }
        floatInput("Roughness", material.roughness) { floatArray -> material.roughness = floatArray[0] }
        floatInput("Metallic", material.metallic) { floatArray -> material.metallic = floatArray[0] }
        floatInput("Ambient", material.ambient) { floatArray -> material.ambient = floatArray[0] }
        floatInput("Transparency", material.transparency) { floatArray ->
            material.transparency = floatArray[0]
        }
        floatInput("ParallaxScale", material.parallaxScale) { floatArray ->
            material.parallaxScale = floatArray[0]
        }
        floatInput("ParallaxBias", material.parallaxBias) { floatArray ->
            material.parallaxBias = floatArray[0]
        }
        floatInput("UVScaleX", material.uvScale.x, 0.01f, 10f) { floatArray ->
            material.uvScale.x = floatArray[0]
        }
        floatInput("UVScaleY", material.uvScale.y, 0.01f, 10f) { floatArray ->
            material.uvScale.y = floatArray[0]
        }
        floatInput("LODFactor", material.lodFactor, 1f, 100f) { floatArray -> material.lodFactor = floatArray[0] }
        if (ImGui.checkbox("WorldSpaceTexCoords", material.useWorldSpaceXZAsTexCoords)) {
            material.useWorldSpaceXZAsTexCoords = !material.useWorldSpaceXZAsTexCoords
        }
        if (ImGui.beginCombo("Type", material.materialType.toString())) {
            Material.MaterialType.values().forEach { type ->
                val selected = material.materialType == type
                if (ImGui.selectable(type.toString(), selected)) {
                    material.materialType = type
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.beginCombo("TransparencyType", material.transparencyType.toString())) {
            Material.TransparencyType.values().forEach { type ->
                val selected = material.transparencyType == type
                if (ImGui.selectable(type.toString(), selected)) {
                    material.transparencyType = type
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.checkbox("BackFaceCulling", material.cullBackFaces)) {
            material.cullBackFaces = !material.cullBackFaces
        }
        if (ImGui.checkbox("DepthTest", material.depthTest)) {
            material.depthTest = !material.depthTest
        }
        if (ImGui.beginCombo("EnvironmentMapType", material.environmentMapType.toString())) {
            Material.ENVIRONMENTMAP_TYPE.values().forEach { type ->
                val selected = material.environmentMapType == type
                if (ImGui.selectable(type.toString(), selected)) {
                    material.environmentMapType = type
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.checkbox("CastShadows", material.isShadowCasting)) {
            material.isShadowCasting = !material.isShadowCasting
        }
        textureSelection(material)
    }

    private fun floatInput(
        label: String,
        initial: Float,
        min: Float = 0.001f,
        max: Float = 1.0f,
        onChange: (FloatArray) -> Unit
    ) {
        val floatArray = floatArrayOf(initial)
        if (ImGui.sliderFloat(label, floatArray, min, max)) {
            onChange(floatArray)
        }
    }

    private fun float2Input(
        label: String,
        initial0: Float,
        initial1: Float,
        min: Float = 0.001f,
        max: Float = 1.0f,
        onChange: (FloatArray) -> Unit
    ) {
        val floatArray = floatArrayOf(initial0, initial1)
        if (ImGui.sliderFloat2(label, floatArray, min, max)) {
            onChange(floatArray)
        }
    }

    private fun float3Input(
        label: String,
        initial0: Float,
        initial1: Float,
        initial2: Float,
        min: Float = 0.001f,
        max: Float = 1.0f,
        onChange: (FloatArray) -> Unit
    ) {
        val floatArray = floatArrayOf(initial0, initial1, initial2)
        if (ImGui.sliderFloat3(label, floatArray, min, max)) {
            onChange(floatArray)
        }
    }

    private fun intInput(label: String, initial: Int, min: Int = 0, max: Int = 100, onChange: (IntArray) -> Unit) {
        val intArray = intArrayOf(initial)
        if (ImGui.sliderInt(label, intArray, min, max)) {
            onChange(intArray)
        }
    }

    private fun textureSelection(material: Material) {
        ImGui.text("Textures")
        val all2DTextures = artemisWorld.getSystem(TextureManager::class.java)
            .textures.filterValues { it is FileBasedTexture2D } + textureManager.texturesForDebugOutput

        val all2DTexturesNames = all2DTextures.keys.toTypedArray()

        Material.MAP.values().forEach { type ->
            material.maps[type].let { currentTexture ->
                val currentIndex = all2DTextures.values.indexOf(currentTexture)
                val is2DTexture = currentIndex > -1
                val previewValue = when {
                    is2DTexture -> all2DTexturesNames[currentIndex]
                    currentTexture != null -> "Other"
                    else -> "None"
                }
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