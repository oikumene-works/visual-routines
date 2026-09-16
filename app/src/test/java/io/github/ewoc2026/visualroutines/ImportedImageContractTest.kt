package io.github.ewoc2026.visualroutines

import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ImportedImageContractTest {
    private val image = EditorStepImage(RoutineImageSource.IMPORTED, UUID.randomUUID().toString())

    @Test fun importedImageNeedsExplicitRoleAndInformativeDescription() {
        val step = EditorStep(instruction = "Take the cloth.", image = image)
        assertFalse(step.canSave())
        assertFalse(step.copy(image = image.copy(semanticRole = RoutineImageSemanticRole.INFORMATIVE)).canSave())
        assertFalse(step.copy(image = image.copy(semanticRole = RoutineImageSemanticRole.INFORMATIVE, descriptionOverride = "  ")).canSave())
        assertTrue(step.copy(image = image.copy(semanticRole = RoutineImageSemanticRole.INFORMATIVE, descriptionOverride = "Blue cloth.")).canSave())
        assertTrue(step.copy(image = image.copy(semanticRole = RoutineImageSemanticRole.REDUNDANT)).canSave())
    }

    @Test fun unclassifiedImageAndUnfinishedDescriptionSurviveEditorRestoration() {
        for (draftImage in listOf(image, image.copy(semanticRole = RoutineImageSemanticRole.INFORMATIVE, descriptionOverride = ""))) {
            val steps = listOf(EditorStep(instruction = "Take the cloth.", image = draftImage))
            assertEquals(steps, decodeEditorSteps(encodeEditorSteps(steps)))
        }
    }

    @Test fun importedReferenceRoundTripsWithoutRequiringTheAssetToExist() {
        val file = Files.createTempDirectory("vr-import-contract").resolve("routines.json").toFile()
        val repository = JsonRoutineRepository(JsonRoutineStore(file))
        val id = UUID.randomUUID().toString()
        val reference = RoutineStepImage(RoutineImageSource.IMPORTED, image.assetId, RoutineImageSemanticRole.INFORMATIVE, "Blue cloth.")
        repository.createRoutine(id, "Clean", listOf(RoutineStepContent("Take the cloth.", image = reference)))
        assertEquals(reference, repository.loadCatalog().routines.first().steps.first().image)
        assertTrue(file.readText().contains("\"formatVersion\": 3"))
    }

    @Test fun versionTwoMigratesWithoutChangingBundledReferencesOrDescription() {
        val file = Files.createTempDirectory("vr-v2-migration").resolve("routines.json").toFile()
        val repository = JsonRoutineRepository(JsonRoutineStore(file))
        val reference = RoutineStepImage(RoutineImageSource.BUNDLED, "unknown-bundled-image", RoutineImageSemanticRole.INFORMATIVE, "A cloth.")
        val id = UUID.randomUUID().toString()
        val catalog = repository.createRoutine(id, "Clean", listOf(RoutineStepContent("Take the cloth.", image = reference)))
        file.writeText(file.readText().replace("\"formatVersion\": 3", "\"formatVersion\": 2"))
        assertEquals(catalog, JsonRoutineRepository(JsonRoutineStore(file)).loadCatalog())
        assertTrue(file.readText().contains("\"formatVersion\": 3"))
    }

    @Test fun versionTwoCannotSmuggleAnImportedSourceIntoItsBundledSchema() {
        val file = Files.createTempDirectory("vr-v2-invalid").resolve("routines.json").toFile()
        val repository = JsonRoutineRepository(JsonRoutineStore(file))
        val reference = RoutineStepImage(RoutineImageSource.IMPORTED, image.assetId, RoutineImageSemanticRole.REDUNDANT)
        repository.createRoutine(UUID.randomUUID().toString(), "Clean", listOf(RoutineStepContent("Take cloth.", image = reference)))
        val bytes = file.readText().replace("\"formatVersion\": 3", "\"formatVersion\": 2")
        file.writeText(bytes)
        assertNotNull(repository.loadCatalog().userRoutineLoadError)
        assertEquals(bytes, file.readText())
    }
}
