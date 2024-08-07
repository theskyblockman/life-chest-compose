package fr.theskyblockman.life_chest.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.theskyblockman.life_chest.BuildConfig
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.VaultsViewModel
import fr.theskyblockman.life_chest.explorer.ExplorerActivity
import fr.theskyblockman.life_chest.onboarding.OnboardingActivity
import fr.theskyblockman.life_chest.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(
    navController: NavController,
    vaultsViewModel: VaultsViewModel = viewModel()
) {
    var currentVault: Vault? by remember {
        mutableStateOf(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val activity = context as Activity

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

                    intent.putExtras(vaultBundle)

                    Log.d("VaultManagement", "Vault ${currentVault!!.name} unlocked")

                    currentVault = null
                    activity.startActivity(intent)

                } else {
                    Log.d("VaultManagement", "Vault ${currentVault!!.name} still locked")
                }

                result
            })
    }

    var deleteAllDialogVisible by remember {
        mutableStateOf(false)
    }

    Scaffold(modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },

        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) }, actions = {
                var expanded by remember {
                    mutableStateOf(false)
                }
                IconButton(onClick = {
                    expanded = true
                }) {
                    var aboutDialogVisible by remember {
                        mutableStateOf(false)
                    }
                    if (aboutDialogVisible) {
                        AboutDialog(activity) {
                            aboutDialogVisible = false
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.delete_all)) },
                            onClick = {
                                deleteAllDialogVisible = true
                                expanded = false
                            })

                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.about)) },
                            onClick = {
                                expanded = false
                                aboutDialogVisible = true
                            })

                        if (BuildConfig.DEBUG) {
                            DropdownMenuItem(text = { Text(text = "Show onboarding") }, onClick = {
                                val intent = Intent(
                                    activity,
                                    OnboardingActivity::class.java
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                activity.startActivity(intent)
                            })
                        }
                    }

                    Icon(
                        painter = painterResource(R.drawable.outline_more_vert_24),
                        contentDescription = stringResource(R.string.more)
                    )
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(text = {
                Text(text = stringResource(id = R.string.create_new_chest))
            }, icon = {
                Icon(
                    painter = painterResource(R.drawable.outline_add_24),
                    contentDescription = stringResource(id = R.string.create_new_chest)
                )
            }, onClick = { navController.navigate("/new") })
        }
    ) { innerPadding ->
        val loadedVaults by vaultsViewModel.loadedVaults.collectAsStateWithLifecycle()

        if (deleteAllDialogVisible) {
            DeleteAllDialog(vaults = loadedVaults, onDismissRequest = {
                deleteAllDialogVisible = false
            }, onConfirm = {
                for (vault in loadedVaults) {
                    vault.delete()
                }
                vaultsViewModel.updateLoadedVaults()
                deleteAllDialogVisible = false
            })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            if (loadedVaults.isEmpty()) Arrangement.Center else Arrangement.Top
        ) {
            if (loadedVaults.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.no_vaults),
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
            }



            for (vault in loadedVaults) {
                VaultListItem(
                    vault = vault,
                    snackbarHostState = snackbarHostState,
                    navController = navController,
                    scope = scope,
                    readOnly = false,
                    activated = true,
                    reloadVaults = { vaultsViewModel.updateLoadedVaults() }) {
                    currentVault = vault
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAllDialog(vaults: List<Vault>, onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.delete_all),
                    style = MaterialTheme.typography.headlineSmall
                )
                Box(modifier = Modifier.padding(bottom = 16.dp))
                Text(stringResource(R.string.are_you_sure_to_delete_vaults, vaults.size))
                Box(modifier = Modifier.padding(bottom = 24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        onDismissRequest()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm()
                        }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteDialog(vault: Vault, onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.delete),
                    style = MaterialTheme.typography.headlineSmall
                )
                Box(modifier = Modifier.padding(bottom = 16.dp))
                Text(stringResource(R.string.are_you_sure_to_delete_vault, vault.name))
                Box(modifier = Modifier.padding(bottom = 24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        onDismissRequest()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm()
                        }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
fun VaultListItem(
    vault: Vault,
    snackbarHostState: SnackbarHostState,
    navController: NavController? = null,
    scope: CoroutineScope,
    readOnly: Boolean = false,
    activated: Boolean = true,
    reloadVaults: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var deletionDialogExpanded by remember { mutableStateOf(false) }

    if (deletionDialogExpanded) {
        DeleteDialog(vault, {
            deletionDialogExpanded = false
        }) {
            scope.launch {
                try {
                    vault.delete()
                    reloadVaults()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.vault_delete_success)
                    )
                } catch (e: Exception) {
                    reloadVaults()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.vault_delete_fail)
                    )
                }
            }
        }
    }

    Surface(onClick, enabled = activated) {
        ListItem(headlineContent = { Text(text = vault.name) }, trailingContent = {
            if (readOnly) return@ListItem

            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
            ) {
                IconButton(
                    onClick = {
                        expanded = true
                    }) {
                    Icon(
                        painter = painterResource(R.drawable.outline_more_vert_24),
                        contentDescription = stringResource(R.string.more)
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = R.string.edit)) },
                        onClick = {
                            navController?.navigate("/edit/${vault.id}")
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = R.string.delete)) },
                        onClick = {
                            expanded = false
                            deletionDialogExpanded = true
                        }
                    )
                }
            }
        })
    }
}