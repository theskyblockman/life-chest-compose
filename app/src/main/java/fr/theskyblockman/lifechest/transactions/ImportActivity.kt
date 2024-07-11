package fr.theskyblockman.lifechest.transactions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.explorer.ExplorerActivity
import fr.theskyblockman.lifechest.explorer.ExplorerActivity.Companion.vault
import fr.theskyblockman.lifechest.explorer.FileImportViewModel
import fr.theskyblockman.lifechest.explorer.FileImportsState
import fr.theskyblockman.lifechest.main.VaultListItem
import fr.theskyblockman.lifechest.transactions.LcefOuterClass.Lcef
import fr.theskyblockman.lifechest.ui.theme.AppTheme
import fr.theskyblockman.lifechest.unlock_mechanisms.UnlockMechanism
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImportActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Vault.vaultsRoot = applicationInfo.dataDir

        val fileToImport = intent.data

        if (fileToImport == null) {
            Toast.makeText(this, getString(R.string.no_file_to_import), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val queryResult = application.contentResolver.query(
            fileToImport, arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ), null, null
        )

        if (queryResult == null) {
            Toast.makeText(this, getString(R.string.failed_to_query_file), Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        queryResult.use {
            if (it.moveToFirst()) {
                val displayName =
                    it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))

                Log.d("ImportActivity", "Name of the file to import: $displayName")

                setContent {
                    AppTheme {
                        ImportActivityContent(fileToImport, displayName)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportActivityContent(file: Uri, fileName: String) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_the_vault_to_import_to)) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        var loadedVaults by remember { mutableStateOf(Vault.loadVaults()) }
        var currentVault: Vault? by remember {
            mutableStateOf(null)
        }
        val context = LocalContext.current

        if (currentVault != null) {
            currentVault!!.currentMechanism.Opener(
                currentVault!!.id,
                currentVault!!.additionalUnlockData,
                snackbarHostState = snackbarHostState,
                onDismissRequest = {
                    currentVault = null
                },
                { issuedKey ->
                    val result = currentVault!!.testKey(issuedKey)

                    if (result) {
                        val intent = Intent(
                            context,
                            ExplorerActivity::class.java
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        val vaultBundle = Bundle()
                        vaultBundle.putString("vault", Json.encodeToString(currentVault))
                        vaultBundle.putByteArray("key", currentVault!!.key!!.encoded)
                        vaultBundle.putParcelable("to-import", file)

                        intent.putExtras(vaultBundle)

                        Log.d("VaultManagement", "Vault ${currentVault!!.name} unlocked")

                        currentVault = null

                        (context as ImportActivity).startActivity(intent)
                    } else {
                        Log.d("VaultManagement", "Vault ${currentVault!!.name} still locked")
                    }

                    result
                })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement =
            if (loadedVaults.isEmpty()) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            val scope = rememberCoroutineScope()

            Column {
                for (vault in loadedVaults) {
                    VaultListItem(
                        vault = vault,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        context = context,
                        readOnly = true,
                        activated = true,
                        reloadVaults = { loadedVaults = Vault.loadVaults() }) {
                        currentVault = vault
                    }
                }
            }

            val fileNameWithoutExtension = fileName.replace("\\.\\w+$".toRegex(), "")
            Text(
                stringResource(R.string.importing_indicator, fileNameWithoutExtension),
                color = MaterialTheme.colorScheme.surfaceDim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun Importer(
    locationId: String,
    toImport: Uri,
    viewModel: FileImportViewModel,
    snackbarHostState: SnackbarHostState,
    activity: ExplorerActivity,
    onDismiss: () -> Unit
) {
    var lcef by remember { mutableStateOf<Lcef?>(null) }
    var headerSize: Long? by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        activity.contentResolver.openInputStream(toImport).use {
            it!!.use { inputStream ->
                val magic = ByteArray(4)
                if (inputStream.read(magic) != 4 || !magic.contentEquals(LcefManager.LCEF_MAGIC)) {
                    Log.e("ImportActivity", "Invalid LCEF file")
                    return@LaunchedEffect
                }

                when (val version = inputStream.read()) {
                    0x00 -> {
                        val rawMetadataSize = ByteArray(Int.SIZE_BYTES)
                        inputStream.read(rawMetadataSize)
                        val metadataSize = ByteBuffer.wrap(rawMetadataSize).apply {
                            order(ByteOrder.LITTLE_ENDIAN)
                        }.int
                        val metadata = ByteArray(metadataSize)
                        inputStream.read(metadata)
                        lcef = Lcef.parseFrom(metadata)
                        headerSize = 4L + 1L + Int.SIZE_BYTES.toLong() + metadataSize.toLong()
                    }

                    else -> {
                        Log.e("ImportActivity", "Unsupported LCEF version: $version")
                    }
                }
            }
        }
    }

    val context = LocalContext.current

    if (lcef != null) {
        for (unlockMechanism in UnlockMechanism.mechanisms) {
            if (unlockMechanism.id == lcef!!.unlockMethod) {
                unlockMechanism.Opener(lcef!!.vaultID, lcef!!.additionalUnlockDataMap.map {
                    it.key to it.value.toByteArray()
                }.toMap(), snackbarHostState, onDismiss) { key ->
                    if (lcef!!.keyHash.toByteArray()
                            .contentEquals(Crypto.createHash(key.encoded))
                    ) {
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                viewModel.apply {
                                    uiState.update {
                                        FileImportsState(
                                            filesPicked = true,
                                            fileAmount = 1,
                                            fileProcessed = null
                                        )
                                    }

                                    LcefManager.importLcefFiles(
                                        context,
                                        vault,
                                        locationId,
                                        mapOf(
                                            toImport to key
                                        )
                                    )

                                    vault.writeFileTree()

                                    uiState.update {
                                        FileImportsState(
                                            filesPicked = false,
                                            fileAmount = null,
                                            fileProcessed = null
                                        )
                                    }

                                    (context as Activity).finish()
                                }
                            }
                        }
                    }

                    return@Opener true
                }

                break
            }
        }
    }
}