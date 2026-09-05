package com.cyberpulse.studylock

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.concurrent.thread

class ReferenceLibraryImportActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            finish()
            return@registerForActivityResult
        }

        setStatus("Importing ${uris.size} downloaded ${if (uris.size == 1) "library" else "libraries"}…")
        thread(name = "StudyLockLibraryImport") {
            val results = uris.map { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                CustomReferenceLibraryStore.importUri(applicationContext, uri)
            }
            val imported = results.sumOf { it.imported }
            val failures = results.filterNot { it.success }
            runOnUiThread {
                val message = when {
                    imported > 0 && failures.isEmpty() ->
                        "Imported $imported StudyLock ${if (imported == 1) "library" else "libraries"}."
                    imported > 0 ->
                        "Imported $imported ${if (imported == 1) "library" else "libraries"}; ${failures.size} file(s) could not be imported."
                    else -> failures.firstOrNull()?.message ?: "No compatible StudyLock libraries were imported."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, ReferenceLibraryViewerActivity::class.java))
                finish()
            }
        }
    }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = "Choose downloaded StudyLock libraries to import.\n\nSupported formats: StudyLock SQLite databases and ZIP files containing compatible databases."
            textSize = 18f
            setTextColor(Color.rgb(33, 23, 15))
            gravity = Gravity.CENTER
        }
        root.addView(status)
        setContentView(root)

        if (savedInstanceState == null) {
            picker.launch(
                arrayOf(
                    "application/zip",
                    "application/octet-stream",
                    "application/x-sqlite3",
                    "application/vnd.sqlite3",
                    "application/x-sqlite"
                )
            )
        }
    }

    private fun setStatus(value: String) {
        status.text = value
    }
}
