package fr.theskyblockman.lifechest.explorer

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.transactions.LcefManager
import fr.theskyblockman.lifechest.transactions.LcefOuterClass.Lcef
import fr.theskyblockman.lifechest.unlock_mechanisms.UnlockMechanism
import fr.theskyblockman.lifechest.vault.Crypto
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LcefUnlockPage(
    navController: NavController,
    files: List<Uri>,
    location: String,
    explorerViewModel: ExplorerViewModel
) {
    val pageData = remember {
        mutableStateMapOf<String, Pair<Map<Lcef, Uri>, SecretKeySpec?>>()
    }
    val context = LocalContext.current

    LaunchedEffect(files) {
        for (file in files) {
            context.contentResolver.openInputStream(file)!!.use { fis ->
                val parsedLcef = LcefManager.readLcefHeader(fis)
                if (parsedLcef == null) {
                    Log.w("LcefUnlockPage", "Failed to parse LCEF but was given by a transaction")
                    return@use
                }

                if (pageData.containsKey(parsedLcef.vaultID)) {
                    pageData[parsedLcef.vaultID] = pageData[parsedLcef.vaultID]!!.copy(
                        first = pageData[parsedLcef.vaultID]!!.first + (parsedLcef to file)
                    )
                } else {
                    pageData[parsedLcef.vaultID] =
                        mapOf(parsedLcef to file) to (null as SecretKeySpec?)
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        explorerViewModel.setLcefUrisTransaction(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            explorerViewModel.setLcefUrisTransaction(null)
                            navController.navigateUp()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                title = { Text(stringResource(R.string.unlock_files)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Button(
                enabled = pageData.any {
                    it.value.second != null
                },
                onClick = {
                    val importableFiles = mutableMapOf<Uri, SecretKeySpec>()

                    for (vault in pageData) {
                        for (lcef in vault.value.first) {
                            if (vault.value.second != null) {
                                importableFiles[lcef.value] = vault.value.second!!
                            }
                        }
                    }

                    LcefManager.importLcefFiles(
                        context,
                        explorerViewModel.vault.value!!,
                        location,
                        importableFiles
                    )

                    explorerViewModel.vault.value!!.writeFileTree()
                    explorerViewModel.setLcefUrisTransaction(null)

                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.import_))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            var currentVault: Int? by remember { mutableStateOf(null) }
            for (vaultsDisplayed in (0 until pageData.size)) {
                val vaultToUnlock = pageData.toList()[vaultsDisplayed]

                if (currentVault == vaultsDisplayed) {
                    val sampleLcef = vaultToUnlock.second.first.entries.first().key

                    for (unlockMechanism in UnlockMechanism.mechanisms) {
                        if (unlockMechanism.id == sampleLcef.unlockMethod) {
                            unlockMechanism.Opener(
                                sampleLcef.vaultID,
                                sampleLcef.additionalUnlockDataMap.map {
                                    it.key to it.value.toByteArray()
                                }.toMap(),
                                snackbarHostState,
                                {
                                    currentVault = null
                                }) { key ->
                                if (sampleLcef.keyHash.toByteArray()
                                        .contentEquals(Crypto.createHash(key.encoded))
                                ) {
                                    pageData[sampleLcef.vaultID] =
                                        pageData[sampleLcef.vaultID]!!.copy(
                                            second = SecretKeySpec(
                                                key.encoded,
                                                Crypto.DEFAULT_CIPHER_NAME
                                            )
                                        )
                                    currentVault = null
                                }

                                return@Opener true
                            }

                            break
                        }
                    }
                }

                Surface(onClick = {
                    currentVault = vaultsDisplayed
                }) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.vault_import_title,
                                    vaultsDisplayed + 1,
                                    if (vaultToUnlock.second.second != null) stringResource(R.string.vault_import_title_unlocked) else stringResource(
                                        R.string.vault_import_title_locked
                                    )
                                )
                            )
                        },
                        supportingContent = {
                            val fileNames = mutableListOf<String>()
                            if (vaultToUnlock.second.second != null) {
                                for (file in vaultToUnlock.second.first) {
                                    fileNames.add(
                                        "- " + Crypto.Decrypt.bytesToString(
                                            file.key.encryptedFileName.toByteArray(),
                                            vaultToUnlock.second.second!!,
                                            IvParameterSpec(file.key.encryptedFileNameIv.toByteArray())
                                        )
                                    )
                                }
                            } else {
                                for (file in vaultToUnlock.second.first) {
                                    fileNames.add("- ${file.key.fileID}")
                                }
                            }

                            Text(fileNames.joinToString("\n"))
                        }
                    )
                }
            }
        }
    }
}

