package com.nikolaspaci.app.llamallmlocal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.os.Build;
import android.util.Log;
class MainActivity : AppCompatActivity() {

    private var sessionPtr: Long = 0
    private var modelPath: String? = null
    private lateinit var modelPathTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cachedFileAdapter: CachedFileAdapter
    private lateinit var initButton: Button

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                copyFileInBackground(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("ABI", Build.SUPPORTED_ABIS[0]);
        // Initialize Views
        modelPathTextView = findViewById(R.id.modelPathTextView)
        progressBar = findViewById(R.id.progressBar)
        initButton = findViewById(R.id.initButton)
        val browseButton: Button = findViewById(R.id.browseButton)
        val promptEditText: EditText = findViewById(R.id.promptEditText)
        val predictButton: Button = findViewById(R.id.predictButton)
        val resultTextView: TextView = findViewById(R.id.resultTextView)
        val cachedFilesRecyclerView: RecyclerView = findViewById(R.id.cachedFilesRecyclerView)

        // Setup RecyclerView
        cachedFileAdapter = CachedFileAdapter(emptyList()) { file ->
            modelPath = file.absolutePath
            modelPathTextView.text = "Selected: ${file.name}"
            Toast.makeText(this, "${file.name} selected. Initializing...", Toast.LENGTH_SHORT).show()
            initButton.performClick() // Automatically click the init button
        }
        cachedFilesRecyclerView.layoutManager = LinearLayoutManager(this)
        cachedFilesRecyclerView.adapter = cachedFileAdapter

        // Load initial cached files
        loadCachedFiles()

        // Set Click Listeners
        browseButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // Allow all file types, you can restrict to ".gguf" if needed
            }
            filePickerLauncher.launch(intent)
        }

        initButton.setOnClickListener {
            if (!modelPath.isNullOrEmpty()) {
                if (sessionPtr != 0L) {
                    LlamaApi.free(sessionPtr)
                }
                sessionPtr = LlamaApi.init(modelPath!!)
                if (sessionPtr != 0L) {
                    Toast.makeText(this, "Model Initialized Successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to initialize model", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please select a model file first", Toast.LENGTH_SHORT).show()
            }
        }

        predictButton.setOnClickListener {
            val prompt = promptEditText.text.toString()
            if (sessionPtr != 0L && prompt.isNotEmpty()) {
                resultTextView.text = "Thinking..."
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        LlamaApi.predict(sessionPtr, prompt)
                    }
                    resultTextView.text = result
                    progressBar.visibility = View.GONE
                }
            } else {
                Toast.makeText(this, "Model not initialized or prompt is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCachedFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val files = cacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
                ?: emptyList()
            withContext(Dispatchers.Main) {
                cachedFileAdapter.updateData(files)
            }
        }
    }

    private fun copyFileInBackground(uri: Uri) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            modelPathTextView.text = "Copying file..."
            val resultPath = withContext(Dispatchers.IO) {
                copyFileToCache(uri)
            }
            progressBar.visibility = View.GONE
            modelPath = resultPath
            modelPathTextView.text = modelPath?.let { File(it).name } ?: "Failed to copy file."
            if (modelPath != null) {
                Toast.makeText(this@MainActivity, "File copied successfully!", Toast.LENGTH_SHORT).show()
                loadCachedFiles() // Refresh the list
            }
        }
    }

    private fun copyFileToCache(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = getFileName(uri) ?: "model.gguf"
            val outputFile = File(cacheDir, fileName)
            val outputStream = FileOutputStream(outputFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }

    override fun onDestroy() {
        super.onDestroy()
        if (sessionPtr != 0L) {
            LlamaApi.free(sessionPtr)
        }
    }
}