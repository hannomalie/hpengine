package de.hanno.hpengine.ressources

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

internal class IncludeRegexTest {

    @Test
    fun `include filename is extracted correctly`() {
        val code = """
            int foo = 1;
            //include(foo.glsl)
            int bar = 2;
        """.trimIndent()

        val result = includeRegex.find(code.lines()[1])!!

        result.groupValues[1] shouldBe "foo.glsl"
    }

    @Test
    fun `complex include filename is extracted correctly`() {
        val code = """
            int foo = 1;
            //include(bar/foo.glsl)
            int bar = 2;
        """.trimIndent()

        val result = includeRegex.find(code.lines()[1])!!

        result.groupValues[1] shouldBe "bar/foo.glsl"
    }

    @Test
    fun `windows include filename is extracted correctly`() {
        val code = """
            int foo = 1;
            //include(bar\foo.glsl)
            int bar = 2;
        """.trimIndent()

        val result = includeRegex.find(code.lines()[1])!!

        result.groupValues[1] shouldBe "bar\\foo.glsl"
    }


    @Test
    fun `include files are parsed from text`() {
        val includes = """
            int foo = 1;
            //include(bar\foo.glsl)
            //include(baz/foo.glsl)
            //include(foo.glsl)
            int bar = 2;
        """.extractIncludeFiles()

        includes.shouldContainExactly(
            File("bar\\foo.glsl"),
            File("baz/foo.glsl"),
            File("foo.glsl"),
        )
    }


}