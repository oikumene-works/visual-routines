package io.github.ewoc2026.visualroutines

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the real typed preferences and fresh store/state owners, without a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedPreferencesSettingsStoreTest {
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private val preferences get() = application.getSharedPreferences("home_settings", Context.MODE_PRIVATE)
    private fun store() = SharedPreferencesSettingsStore(application)
    private fun freshState() = VisualRoutinesState(
        SharedPreferencesSessionStore(application), application.routineRepository, store(),
    )

    @Test fun aFreshReadUsesDefaultsWithoutCreatingPreferences() {
        assertEquals(HomeSettings(), store().load())
        assertEquals(HomeSettings(), freshState().homeSettings)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun oldTwoKeySettingsKeepTheirValuesWithoutWritingNewKeysOnRead() {
        for (examples in listOf(false, true)) for (editing in listOf(false, true)) {
            assertTrue(preferences.edit().clear()
                .putBoolean("show_example_routines", examples)
                .putBoolean("allow_routine_editing", editing).commit())
            val before = preferences.all.toMap()
            assertEquals(HomeSettings(examples, editing), freshState().homeSettings)
            assertEquals(before, preferences.all)
        }
    }

    @Test fun allSixPairsRoundTripWithStableTokensAndExistingSettings() {
        val modes = listOf("system" to AppearanceMode.System, "light" to AppearanceMode.Light, "dark" to AppearanceMode.Dark)
        val styles = listOf("standard" to DarkThemeStyle.Standard, "black" to DarkThemeStyle.Black)
        assertTrue(preferences.edit().putString("unrelated_future_key", "keep").commit())
        for ((modeToken, mode) in modes) for ((styleToken, style) in styles) {
            val settings = HomeSettings(false, false, mode, style)
            store().save(settings)
            assertEquals(modeToken, preferences.getString("appearance_mode", null))
            assertEquals(styleToken, preferences.getString("dark_theme_style", null))
            assertEquals(settings, store().load())
            assertEquals(settings, freshState().homeSettings)
            assertEquals("keep", preferences.getString("unrelated_future_key", null))
        }
    }

    @Test fun invalidFieldsDefaultIndependentlyAndReadsNeverRewriteThem() {
        val modes = listOf(null, "system", "light", "dark", "future", "", "DARK", true, 42, setOf("dark"))
        val styles = listOf(null, "standard", "black", "future", "", "BLACK", false, 42L, setOf("black"))
        for (mode in modes) for (style in styles) {
            val edit = preferences.edit().clear()
                .putBoolean("show_example_routines", false)
                .putBoolean("allow_routine_editing", false)
            edit.putFixture("appearance_mode", mode)
            edit.putFixture("dark_theme_style", style)
            assertTrue(edit.commit())
            val before = preferences.all.toMap()
            val expected = HomeSettings(
                false, false,
                when (mode) { "light" -> AppearanceMode.Light; "dark" -> AppearanceMode.Dark; else -> AppearanceMode.System },
                if (style == "black") DarkThemeStyle.Black else DarkThemeStyle.Standard,
            )
            assertEquals("mode=$mode style=$style", expected, store().load())
            assertEquals(expected, freshState().homeSettings)
            assertEquals(before, preferences.all)
        }
    }

    @Test fun independentUpdatesRetainStyleAndBothExistingSettings() {
        val state = freshState()
        state.updateShowExampleRoutines(false)
        state.updateDarkThemeStyle(DarkThemeStyle.Black)
        state.updateAppearanceMode(AppearanceMode.Light)
        val light = preferences.all.toMap()
        state.updateDarkThemeStyle(DarkThemeStyle.Standard)
        assertEquals(light, preferences.all)
        state.updateAllowRoutineEditing(false)
        assertEquals(HomeSettings(false, false, AppearanceMode.Light, DarkThemeStyle.Black), store().load())
        state.updateAppearanceMode(AppearanceMode.Dark)
        state.updateShowExampleRoutines(true)
        state.updateAllowRoutineEditing(true)
        assertEquals(HomeSettings(true, true, AppearanceMode.Dark, DarkThemeStyle.Black), freshState().homeSettings)
        state.updateAppearanceMode(AppearanceMode.System)
        state.updateDarkThemeStyle(DarkThemeStyle.Standard)
        assertEquals(HomeSettings(), freshState().homeSettings)
    }

    @Test fun appearanceUpdatesPreserveCatalogImportedImageProgressAndEditorScreen() {
        val draftId = UUID.randomUUID().toString()
        val routineId = UUID.randomUUID().toString()
        val source = File(application.cacheDir, "appearance-source.png")
        Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            source.outputStream().use { assertTrue(compress(Bitmap.CompressFormat.PNG, 100, it)) }
            recycle()
        }
        val images = application.importedImages
        images.beginDraft(draftId, emptySet())
        val imageId = images.importImage(draftId) { source.inputStream() }
        application.routineRepository.createRoutine(routineId, "Saved routine", listOf(
            RoutineStepContent("First step.", image = RoutineStepImage(
                RoutineImageSource.IMPORTED, imageId, RoutineImageSemanticRole.INFORMATIVE, "Blue cloth.",
            )),
            RoutineStepContent("Second step."),
        ))
        images.finishDraft(draftId)
        val state = freshState()
        state.openHomeWithPausedSession(routineId, 2)
        val progress = state.activeSession
        val progressPreferences = application.getSharedPreferences("active_routine", Context.MODE_PRIVATE).all.toMap()
        val catalog = JsonRoutineStore.appPrivateFile(application.filesDir)
        val catalogBytes = catalog.readBytes()
        val image = File(application.filesDir, "${ImportedImageStore.DIRECTORY_NAME}/$imageId.png")
        val imageBytes = image.readBytes()
        state.openCreateRoutine(CreateRoutineDraft("Unsaved draft", listOf(RoutineStepContent("Keep this note.", note = "Notes."))))
        val editor = state.screen
        for (mode in AppearanceMode.entries) {
            state.updateAppearanceMode(mode)
            state.updateDarkThemeStyle(DarkThemeStyle.Black)
            assertEquals(editor, state.screen)
            assertEquals(progress, state.activeSession)
            assertEquals(progress, freshState().activeSession)
            assertEquals(progressPreferences, application.getSharedPreferences("active_routine", Context.MODE_PRIVATE).all)
            assertArrayEquals(catalogBytes, catalog.readBytes())
            assertArrayEquals(imageBytes, image.readBytes())
        }
    }

    private fun SharedPreferences.Editor.putFixture(key: String, value: Any?) {
        when (value) {
            null -> remove(key)
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> error("Unsupported fixture")
        }
    }
}
