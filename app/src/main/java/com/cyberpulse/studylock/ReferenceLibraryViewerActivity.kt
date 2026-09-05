package com.cyberpulse.studylock

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class ReferenceLibraryViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val orange = Color.rgb(255, 122, 31)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 42, 36, 48)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "StudyLock Libraries"
            textSize = 28f
            setTextColor(Color.rgb(33, 23, 15))
        })
        root.addView(TextView(this).apply {
            text = "Downloaded reference libraries that the Offline AI Tutor can search without internet."
            textSize = 14f
            setTextColor(Color.rgb(110, 93, 81))
            setPadding(0, 8, 0, 24)
        })

        addSourceButton(root, "Cyber Pulse Info", "https://cyber-pulse-info.netlify.app", orange)
        addSourceButton(root, "Cyber Learn Projects", "https://cyber-learn-projects.netlify.app", orange)

        root.addView(Button(this).apply {
            text = "Import downloaded library"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("studylock://import-library")))
            }
        }, matchWidth())

        val libraries = CustomReferenceLibraryStore.list(applicationContext)
        root.addView(TextView(this).apply {
            text = if (libraries.isEmpty()) "No custom libraries imported yet" else "Installed custom libraries (${libraries.size})"
            textSize = 18f
            setTextColor(Color.rgb(33, 23, 15))
            setPadding(0, 30, 0, 12)
        })

        if (libraries.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Download a StudyLock-compatible .db/.sqlite file, or a ZIP containing compatible databases, from either reference website. Then tap Import downloaded library."
                textSize = 14f
                setTextColor(Color.rgb(110, 93, 81))
            })
        } else {
            libraries.forEach { library ->
                root.addView(TextView(this).apply {
                    text = "${library.displayName}\n${library.entries} reference entries • ${formatBytes(library.file.length())}"
                    textSize = 15f
                    setTextColor(Color.rgb(33, 23, 15))
                    setPadding(24, 20, 24, 20)
                    setBackgroundColor(Color.rgb(255, 246, 238))
                }, matchWidth())
            }

            root.addView(Button(this).apply {
                text = "Remove all custom libraries"
                setOnClickListener {
                    AlertDialog.Builder(this@ReferenceLibraryViewerActivity)
                        .setTitle("Remove custom libraries?")
                        .setMessage("The built-in StudyLock starter library will stay installed.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            val ok = CustomReferenceLibraryStore.removeAll(applicationContext)
                            Toast.makeText(
                                this@ReferenceLibraryViewerActivity,
                                if (ok) "Custom libraries removed." else "Some libraries could not be removed.",
                                Toast.LENGTH_LONG
                            ).show()
                            render()
                        }
                        .show()
                }
            }, matchWidth())
        }

        root.addView(Button(this).apply {
            text = "Back to StudyLock"
            setOnClickListener { finish() }
        }, matchWidth())

        setContentView(scroll)
    }

    private fun addSourceButton(parent: LinearLayout, label: String, url: String, orange: Int) {
        parent.addView(Button(this).apply {
            text = "Open $label"
            setTextColor(Color.WHITE)
            setBackgroundColor(orange)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }, matchWidth())
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 8, 0, 8) }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb < 1.0) "${(bytes / 1024L).coerceAtLeast(1)} KB" else "${"%.1f".format(mb)} MB"
    }
}
