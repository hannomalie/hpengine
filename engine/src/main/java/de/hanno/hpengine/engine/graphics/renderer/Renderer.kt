package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.function.Consumer

interface LineRenderer {
    val linePoints: MutableList<Vector3fc> // TODO: Remove completely
    fun MutableList<Vector3fc>.batchPointForLine(point: Vector3f)
    fun MutableList<Vector3fc>.batchLine(from: Vector3fc, to: Vector3fc)

    @JvmDefault
    fun drawLines(program: Program<Uniforms>) = drawLines(linePoints, program, 2f)
    fun drawLines(linePoints: List<Vector3fc>, program: Program<Uniforms>, lineWidth: Float): Int
    @JvmDefault
    fun drawAllLines(action: Consumer<Program<Uniforms>>) {
        drawAllLines(2f, action)
    }
    fun drawAllLines(lineWidth: Float, action: Consumer<Program<Uniforms>>)

}

fun MutableList<Vector3fc>.addLine(from: Vector3fc, to: Vector3fc) {
    add(from)
    add(to)
}

fun MutableList<Vector3fc>.batchPointForLine(point: Vector3f) {
    add(point)
}

fun MutableList<Vector3fc>.batchTriangle(a: Vector3f, b: Vector3f, c: Vector3f) {
    addLine(a, b)
    addLine(b, c)
    addLine(c, a)
}

fun MutableList<Vector3fc>.batchVector(vector: Vector3f, charWidth: Float) {
    batchString(String.format("%.2f", vector.x()), charWidth, charWidth * 0.2f, 0f, 2f * charWidth)
    batchString(String.format("%.2f", vector.y()), charWidth, charWidth * 0.2f, 0f, charWidth)
    batchString(String.format("%.2f", vector.z()), charWidth, charWidth * 0.2f, 0f, 0f)
}

fun MutableList<Vector3fc>.batchString(text: String, charWidth: Float) {
    batchString(text, charWidth, charWidth * 0.2f)
}

fun MutableList<Vector3fc>.batchString(text: String, charWidthIn: Float, gapIn: Float, x: Float = 0f) {
    batchString(text, charWidthIn, gapIn, x, 0f)
}

fun MutableList<Vector3fc>.batchString(text: String, charWidthIn: Float, gapIn: Float, x: Float, y: Float) {
    var x = x
    val charMaxWidth = charWidthIn + Math.round(charWidthIn * 0.25f)
    var gap = gapIn
    if (gap > charMaxWidth / 2f) {
        gap = charMaxWidth / 2f
    }
    val charWidth = charWidthIn - gap
    for (c in text.toUpperCase().toCharArray()) {
        if (c == 'A') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'B') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + 0.9f * charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + 0.9f * charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + 0.9f * charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + 0.9f * charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == 'C') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'D') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + 0.9f * charWidth, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y + 0.1f * charWidth, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + charWidth, y + 0.9f * charWidth, 0f), Vector3f(x + charWidth, y + 0.1f * charWidth, 0f))
            x += charMaxWidth
        } else if (c == 'E') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'F') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'G') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == 'H') {
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'I') {
            addLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        } else if (c == 'J') {
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth / 2, y, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == 'K') {
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'L') {
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'M') {
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth / 2, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 2, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'N') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'O') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'P') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == 'Q') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'R') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == 'S') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x + 0.1f * charWidth, y + charWidth / 2, 0f), Vector3f(x + 0.9f * charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + 0.1f * charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x + 0.9f * charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == 'T') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        } else if (c == 'U') {
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == 'V') {
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        } else if (c == 'W') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 4, y, 0f))
            addLine(Vector3f(x + charWidth / 4, y, 0f), Vector3f(x + charWidth / 2, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + 3f * charWidth / 4f, y, 0f))
            addLine(Vector3f(x + 3f * charWidth / 4f, y, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            x += charMaxWidth
        } else if (c == 'X') {
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == 'Y') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth / 2, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        } else if (c == 'Z') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == '0') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == '1') {
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth / 2, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth / 2, y + charWidth, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        } else if (c == '2') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == '3') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == '4') {
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == '5') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == '6') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth / 2, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == '7') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == '8') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y, 0f))
            x += charMaxWidth
        } else if (c == '9') {
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x + charWidth, y + charWidth, 0f))
            addLine(Vector3f(x, y + charWidth / 2, 0f), Vector3f(x + charWidth, y + charWidth / 2, 0f))
            addLine(Vector3f(x, y, 0f), Vector3f(x + charWidth, y, 0f))
            addLine(Vector3f(x, y + charWidth, 0f), Vector3f(x, y + charWidth / 2, 0f))
            addLine(Vector3f(x + charWidth, y + charWidth, 0f), Vector3f(x + charWidth, y, 0f))
            x += charMaxWidth
        } else if (c == '+') {
            addLine(Vector3f(x + charWidth / 2, y + 3f * charWidth / 4f, 0f), Vector3f(x + charWidth / 2, y + charWidth / 4f, 0f))
            addLine(Vector3f(x + charWidth / 4f, y + charWidth / 2, 0f), Vector3f(x + 3f * charWidth / 4f, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == '-') {
            addLine(Vector3f(x + charWidth / 4f, y + charWidth / 2, 0f), Vector3f(x + 3f * charWidth / 4f, y + charWidth / 2, 0f))
            x += charMaxWidth
        } else if (c == '.') {
            addLine(Vector3f(x + charWidth / 2, y + charWidth / 16f, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        } else if (c == ',') {
            addLine(Vector3f(x + charWidth / 2, y + charWidth / 4f, 0f), Vector3f(x + charWidth / 2, y, 0f))
            x += charMaxWidth
        }
    }
}

fun MutableList<Vector3fc>.addAABBLines(minWorld: Vector3fc, maxWorld: Vector3fc) {
    fun batchLine(from: Vector3fc, to: Vector3fc) {
        add(from)
        add(to)
    }

    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }


    run {
        val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
        val max = Vector3f(maxWorld.x(), maxWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(maxWorld.x(), maxWorld.y(), minWorld.z())
        val max = Vector3f(maxWorld.x(), minWorld.y(), minWorld.z())
        batchLine(min, max)
    }
    run {
        val min = Vector3f(minWorld.x(), maxWorld.y(), maxWorld.z())
        val max = Vector3f(minWorld.x(), minWorld.y(), maxWorld.z())
        batchLine(min, max)
    }
}
