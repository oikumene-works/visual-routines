package io.github.ewoc2026.visualroutines

import android.app.Application
import java.io.File

/** Owns the process-scoped storage and image lifetime boundary. */
class VisualRoutinesApplication : Application() {
    internal val importedImages: ImportedImageStore by lazy {
        ImportedImageStore(
            File(filesDir, ImportedImageStore.DIRECTORY_NAME),
            File(noBackupFilesDir, ImportedImageStore.DRAFT_DIRECTORY_NAME),
        )
    }
    internal val routineRepository: RoutineRepository by lazy {
        ImageRoutineRepository(
            JsonRoutineRepository(JsonRoutineStore(JsonRoutineStore.appPrivateFile(filesDir))),
            importedImages,
        )
    }
}
