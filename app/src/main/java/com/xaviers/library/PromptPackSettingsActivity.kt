package com.xaviers.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.xaviers.library.databinding.ActivityPromptPackSettingsBinding

class PromptPackSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromptPackSettingsBinding
    private lateinit var repository: PromptPackRepository
    private lateinit var storage: PromptPackStorage

    private val promptPackPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        importAsset(PromptPackAsset.PROMPT_PACK, uri)
    }

    private val visualDictionaryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        importAsset(PromptPackAsset.VISUAL_DICTIONARY, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptPackSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PromptPackRepository.get(this)
        storage = PromptPackStorage(this)

        binding.importPromptPackButton.setOnClickListener {
            promptPackPicker.launch(JSON_MIME_TYPES)
        }
        binding.openPromptPackDownloadButton.setOnClickListener {
            openPublicDownload(PUBLIC_PROMPT_PACK_URL)
        }
        binding.importVisualDictionaryButton.setOnClickListener {
            visualDictionaryPicker.launch(JSON_MIME_TYPES)
        }

        refreshStatus(repository.reload())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(repository.reload())
    }

    private fun importAsset(asset: PromptPackAsset, uri: Uri?) {
        if (uri == null) return

        runCatching {
            storage.importDocument(asset, uri)
        }.onSuccess { result ->
            val status = repository.reload()
            refreshStatus(status)
            val validationMessage = when (asset) {
                PromptPackAsset.PROMPT_PACK -> status.promptPack.validationMessage
                PromptPackAsset.VISUAL_DICTIONARY -> status.visualDictionary.validationMessage
            }
            Toast.makeText(
                this,
                getString(R.string.prompt_pack_import_success, result.displayName, validationMessage),
                Toast.LENGTH_LONG
            ).show()
        }.onFailure { throwable ->
            Toast.makeText(
                this,
                getString(R.string.prompt_pack_import_failed, throwable.message ?: "Unknown import error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun refreshStatus(status: PromptPackRuntimeStatus) {
        bindFileStatus(
            sourceView = binding.promptPackSourceValue,
            lastImportView = binding.promptPackLastImportValue,
            validationView = binding.promptPackValidationValue,
            fileStatus = status.promptPack
        )
        bindFileStatus(
            sourceView = binding.visualDictionarySourceValue,
            lastImportView = binding.visualDictionaryLastImportValue,
            validationView = binding.visualDictionaryValidationValue,
            fileStatus = status.visualDictionary
        )
        binding.referenceNote.text = getString(R.string.prompt_pack_reference_note)
    }

    private fun bindFileStatus(
        sourceView: android.widget.TextView,
        lastImportView: android.widget.TextView,
        validationView: android.widget.TextView,
        fileStatus: PromptPackFileStatus
    ) {
        sourceView.text = fileStatus.sourceLabel
        lastImportView.text = fileStatus.lastImportedFileName ?: getString(R.string.prompt_pack_not_imported)
        validationView.text = fileStatus.validationMessage
    }

    private fun openPublicDownload(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(Intent.createChooser(intent, getString(R.string.prompt_pack_open_download_button)))
        }.onFailure { throwable ->
            Toast.makeText(
                this,
                getString(R.string.prompt_pack_open_failed, throwable.message ?: "unknown error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private val JSON_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")
        private const val PUBLIC_PROMPT_PACK_URL =
            "https://raw.githubusercontent.com/faded201/Xaviers-library/main/prompt_pack.json"
    }
}
