package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import org.joml.Vector3f
import java.util.function.Consumer

interface LineRenderer {
    fun batchPointForLine(point: Vector3f)
    fun batchLine(from: Vector3f, to: Vector3f)

    @JvmDefault
    fun drawLines(program: Program) = drawLines(program, 2f)
    fun drawLines(program: Program, lineWidth: Float): Int
    @JvmDefault
    fun drawAllLines(action: Consumer<Program>) {
        drawAllLines(2f, action)
    }
    fun drawAllLines(lineWidth: Float, action: Consumer<Program>)

    fun batchTriangle(a: Vector3f, b: Vector3f, c: Vector3f) {
        batchLine(a, b)
        batchLine(b, c)
        batchLine(c, a)
    }

    fun batchVector(vector: Vector3f, charWidth: Float) {
        batchString(String.format("%.2f", vector.x()), charWidth, charWidth * 0.2f, 0f, 2f * charWidth)
        batchString(String.format("%.2f", vector.y()), charWidth, charWidth * 0.2f, 0f, charWidth)
        batchString(String.format("%.2f", vector.z()), charWidth, charWidth * 0.2f, 0f, 0f)
    }

    fun batchString(text: String, charWidth: Float) {
        batchString(text, charWidth, charWidth * 0.2f)
    }

    fun batchString(text: String, charWidthIn: Float, gapIn: Float, x: Float = 0f) {
        batchString(text, charWidthIn, gapIn, x, 0f)
    }

    fun batchString(text: String, charWidthIn: Float, gapIn: Float, x: Float, y: Float) {
        var x = x
        val charMaxWidth = charWidthIn + Math.round(charWidthIn * 0.25f)
        var gap = gapIn
        if (gap > charMaxWidth / 2f) {
            gap = charMaxWidth / 2f
        }
        val charWidth = charWidthIn - gap
        for (c in text.toUpperCase().toCharArray()) {
            if (c == 'A') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'B') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + 0.9f * charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + 0.9f * charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + 0.9f * charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + 0.9f * charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == 'C') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'D') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + 0.9f * charWidth, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y + 0.1f * charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + charWidth, y + 0.9f * charWidth, 0f), Vector3f(x + charWidth, y + 0.1f * charWidth, 0f))
                x += charMaxWidth
            } else if (c == 'E') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'F') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'G') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == 'H') {
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'I') {
                batchLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            } else if (c == 'J') {
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth / 2, y, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == 'K') {
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'L') {
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'M') {
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth / 2, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 2, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'N') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'O') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'P') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == 'Q') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'R') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == 'S') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x + 0.1f * charWidth, y + charWidth / 2, 0f), Vector3f(x + 0.9f * charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + 0.1f * charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + 0.9f * charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == 'T') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            } else if (c == 'U') {
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == 'V') {
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            } else if (c == 'W') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 4, y, 0f))
                batchLine(Vector3f(x + charWidth / 4, y, 0f), Vector3f(x + charWidth / 2, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + 3f * charWidth / 4f, y, 0f))
                batchLine(Vector3f(x + 3f * charWidth / 4f, y, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                x += charMaxWidth
            } else if (c == 'X') {
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == 'Y') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 2, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            } else if (c == 'Z') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == '0') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == '1') {
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth / 2, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            } else if (c == '2') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == '3') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == '4') {
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == '5') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == '6') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == '7') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == '8') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
                x += charMaxWidth
            } else if (c == '9') {
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
                batchLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
                batchLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
                batchLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
                batchLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
                x += charMaxWidth
            } else if (c == '+') {
                batchLine(Vector3f(x + charWidth / 2, y + 3f * charWidth / 4f, 0f), Vector3f(x + charWidth / 2, y + charWidth / 4f, 0f))
                batchLine(Vector3f(x + charWidth / 4f, y + charWidth / 2, 0f), Vector3f(x + 3f * charWidth / 4f, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == '-') {
                batchLine(Vector3f(x + charWidth / 4f, y + charWidth / 2, 0f), Vector3f(x + 3f * charWidth / 4f, y + charWidth / 2, 0f))
                x += charMaxWidth
            } else if (c == '.') {
                batchLine(Vector3f(x + charWidth / 2, y + charWidth / 16f, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            } else if (c == ',') {
                batchLine(Vector3f(x + charWidth / 2, y + charWidth / 4f, 0f), Vector3f(x + charWidth / 2, y, 0f))
                x += charMaxWidth
            }
        }
    }
}
