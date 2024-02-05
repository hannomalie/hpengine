package de.hanno.hpengine.graphics.editor

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.rendertarget.RenderTargetImpl
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.output.DebugOutput
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import imgui.ImGui
import imgui.type.ImInt
import org.koin.core.annotation.Single

@Single(binds = [OutputSelection::class, BaseSystem::class])
class OutputSelection(
    private val graphicsApi: GraphicsApi,
    internal val window: Window,
    internal val textureManager: TextureManagerBaseSystem,
    internal val config: Config,
    internal val programManager: ProgramManager,
    internal val debugOutput: DebugOutput,
): BaseSystem() {

    val output = ImInt(-1)

    val textureOrNull get() = debugOutput.texture2D?.let { textureRenderTarget.textures.first().id }

    private val textureRenderTarget = RenderTarget2D(
        graphicsApi,
        RenderTargetImpl(
            graphicsApi,
            graphicsApi.FrameBuffer(null),
            1920,
            1080,
            listOf(
                ColorAttachmentDefinition("Color", InternalTextureFormat.RGB8, TextureFilterConfig(MinFilter.LINEAR))
            ).toTextures(graphicsApi, config.width, config.height),
            "Dummy Texture Target",
        )
    )
    private val simpleTextureRenderer = SimpleTextureRenderer(
        graphicsApi,
        config,
        null,
        programManager,
        window.frontBuffer
    )

    data class TextureOutputSelection(val identifier: String, val texture: Texture2D)

    val textureOutputOptions: List<TextureOutputSelection>
        get() {
            return graphicsApi.registeredRenderTargets.flatMap { target ->
                val texture2DSelections = target.textures.filterIsInstance<Texture2D>().mapIndexed { index, texture ->
                    TextureOutputSelection(target.name + "[$index]", texture)
                }

                val depthTexturesSelections = (target.frameBuffer.depthBuffer?.texture as? Texture2D)?.let {
                    TextureOutputSelection(
                        target.name + "[depth]",
                        it
                    )
                }

                val cubeMapFaceViews = if (target is CubeMapArrayRenderTarget) target.cubeMapFaceViews else emptyList()
                val cubeMapFaceViewSelections = cubeMapFaceViews.mapIndexed { index, it ->
                    TextureOutputSelection(
                        target.name + "[$index]",
                        it
                    )
                }

                texture2DSelections + depthTexturesSelections + cubeMapFaceViewSelections

            }.filterNotNull() +
                    textureManager.texturesForDebugOutput.filterValues { it is Texture2D }
                        .map { TextureOutputSelection(it.key, it.value as Texture2D) } +
                    textureManager.textures.filterValues { it is Texture2D }
                        .map { TextureOutputSelection(it.key, it.value as Texture2D) }
        }

    override fun processSystem() { }
    fun draw() {
        debugOutput.texture2D?.let {
            textureRenderTarget.use(false)
            simpleTextureRenderer.drawToQuad(it, mipMapLevel = debugOutput.mipmapLevel)
        }
    }

    fun renderSelection() {
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
        de.hanno.hpengine.graphics.imgui.dsl.Window.text("Select output")
        if(ImGui.radioButton("Default", output, -1)) {
            debugOutput.texture2D = null
        }
        textureOutputOptions.forEachIndexed { index, option ->
            if (ImGui.radioButton(option.identifier, output, index)) {
                debugOutput.texture2D = option.texture
            }
        }
    }
}