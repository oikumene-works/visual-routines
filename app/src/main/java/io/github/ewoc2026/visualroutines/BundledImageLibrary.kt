package io.github.ewoc2026.visualroutines

/**
 * One bundled image whose stable asset id is independent of Android resource
 * names and build-specific resource ids.
 */
internal data class BundledImageAsset(
    val assetId: String,
    val drawableResourceId: Int,
    val nameResourceId: Int,
    val defaultDescriptionResourceId: Int,
)

/**
 * The bounded offline image library. Unknown references intentionally resolve
 * to `null` so callers can keep the complete text-only step.
 */
internal object BundledImageLibrary {
    val all: List<BundledImageAsset> = listOf(
        BundledImageAsset(
            assetId = "bed-linen-remove-sheet",
            drawableResourceId = R.drawable.bed_linen_remove_sheet,
            nameResourceId = R.string.bundled_image_remove_sheet_name,
            defaultDescriptionResourceId = R.string.bundled_image_remove_sheet_description,
        ),
        BundledImageAsset(
            assetId = "bed-linen-remove-duvet-cover",
            drawableResourceId = R.drawable.bed_linen_remove_duvet_cover,
            nameResourceId = R.string.bundled_image_remove_duvet_cover_name,
            defaultDescriptionResourceId = R.string.bundled_image_remove_duvet_cover_description,
        ),
        BundledImageAsset(
            assetId = "bed-linen-insert-duvet-cover",
            drawableResourceId = R.drawable.bed_linen_insert_duvet_cover,
            nameResourceId = R.string.bundled_image_insert_duvet_cover_name,
            defaultDescriptionResourceId = R.string.bundled_image_insert_duvet_cover_description,
        ),
        BundledImageAsset(
            assetId = "bed-linen-finish-with-bedspread",
            drawableResourceId = R.drawable.bed_linen_finish_with_bedspread,
            nameResourceId = R.string.bundled_image_finish_with_bedspread_name,
            defaultDescriptionResourceId = R.string.bundled_image_finish_with_bedspread_description,
        ),
    )

    private val assetsById = all.associateBy(BundledImageAsset::assetId)

    fun resolve(image: RoutineStepImage?): BundledImageAsset? {
        return image
            ?.takeIf { it.source == RoutineImageSource.BUNDLED }
            ?.let { assetsById[it.assetId] }
    }

    fun resolve(assetId: String): BundledImageAsset? = assetsById[assetId]
}
