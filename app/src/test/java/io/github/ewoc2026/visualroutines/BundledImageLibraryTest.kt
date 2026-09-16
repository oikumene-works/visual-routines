package io.github.ewoc2026.visualroutines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BundledImageLibraryTest {
    @Test
    fun libraryContainsFourUniqueStableAssets() {
        assertEquals(
            listOf(
                "bed-linen-remove-sheet",
                "bed-linen-remove-duvet-cover",
                "bed-linen-insert-duvet-cover",
                "bed-linen-finish-with-bedspread",
            ),
            BundledImageLibrary.all.map(BundledImageAsset::assetId),
        )
        assertEquals(
            BundledImageLibrary.all.size,
            BundledImageLibrary.all.map(BundledImageAsset::drawableResourceId).distinct().size,
        )
    }

    @Test
    fun resolverReturnsAssetForBundledReference() {
        val image = RoutineStepImage(
            source = RoutineImageSource.BUNDLED,
            assetId = "bed-linen-remove-sheet",
            semanticRole = RoutineImageSemanticRole.REDUNDANT,
        )

        val asset = BundledImageLibrary.resolve(image)

        assertNotNull(asset)
        assertEquals("bed-linen-remove-sheet", asset?.assetId)
    }

    @Test
    fun resolverReturnsNullForAbsentOrUnknownReference() {
        assertNull(BundledImageLibrary.resolve(null))
        assertNull(BundledImageLibrary.resolve("unknown-bundled-image"))
        assertNull(
            BundledImageLibrary.resolve(
                RoutineStepImage(
                    source = RoutineImageSource.BUNDLED,
                    assetId = "unknown-bundled-image",
                    semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                ),
            ),
        )
    }
}
