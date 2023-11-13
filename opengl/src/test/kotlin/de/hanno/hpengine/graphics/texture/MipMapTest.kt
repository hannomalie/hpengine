package de.hanno.hpengine.graphics.texture

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MipMapTest {
    @Test
    fun `mip map count is calculated`() {
        calculateMipMapCount(16) shouldBe 5
    }

    @Test
    fun `mip map count is calculated for dimension`() {
        val mipMapCount = getMipMapCountForDimension(16, 16, 0)
        mipMapCount shouldBe 5
    }

    @Test
    fun `mip map sizes are calculated for simple case`() {
        val mipMapSizes = calculateMipMapSizes(9, 9)

        mipMapSizes[0] shouldBe TextureDimension2D(9, 9)
        mipMapSizes[1] shouldBe TextureDimension2D(4, 4)
        mipMapSizes[2] shouldBe TextureDimension2D(2, 2)
        mipMapSizes[3] shouldBe TextureDimension2D(1, 1)
        mipMapSizes shouldHaveSize 4
        mipMapSizes.size shouldBe getMipMapCountForDimension(9, 9, 0)
    }

    @Test
    fun `mip map sizes are calculated for larger width dimension`() {
        val mipMapSizes = calculateMipMapSizes(9, 1)

        mipMapSizes[0] shouldBe TextureDimension2D(9, 1)
        mipMapSizes[1] shouldBe TextureDimension2D(4, 1)
        mipMapSizes[2] shouldBe TextureDimension2D(2, 1)
        mipMapSizes[3] shouldBe TextureDimension2D(1, 1)
        mipMapSizes shouldHaveSize 4
        mipMapSizes.size shouldBe getMipMapCountForDimension(9, 1, 0)
    }
}