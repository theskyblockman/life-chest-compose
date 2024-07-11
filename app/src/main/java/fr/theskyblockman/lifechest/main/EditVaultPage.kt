package fr.theskyblockman.lifechest.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.VaultsViewModel
import fr.theskyblockman.lifechest.vault.Vault

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVaultPage(
    navController: NavController,
    vault: Vault,
    vaultsViewModel: VaultsViewModel = viewModel()
) {
    var editingName by remember {
        mutableStateOf(vault.name)
    }
    var editingEncryptionLevel by remember {
        mutableStateOf(vault.encryptionLevel)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.edit_chest, vault.name)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.go_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Button(contentPadding = PaddingValues(start = 16.dp, end = 24.dp), enabled =
            ((editingName.isNotBlank() &&
                    editingName != vault.name) ||
                    editingEncryptionLevel != vault.encryptionLevel),
                onClick = {
                    vault.name = editingName
                    vault.encryptionLevel = editingEncryptionLevel

                    vault.writeConfig()

                    vaultsViewModel.updateLoadedVaults()
                    navController.navigateUp()

                }) {
                Icon(
                    painter = painterResource(R.drawable.outline_edit_24),
                    contentDescription = stringResource(id = R.string.edit),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = stringResource(id = R.string.edit_chest, vault.name))
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState), Arrangement.Top
        ) {
            OutlinedTextField(
                value = editingName,
                onValueChange = {
                    editingName = it
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text(text = stringResource(R.string.vault_name)) },
                singleLine = true
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Slider(
                    value = editingEncryptionLevel.toFloat(),
                    onValueChange = {
                        editingEncryptionLevel = it.toInt().toByte()
                    },
                    steps = 1,
                    valueRange = 0f..2f,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .fillMaxWidth(.75f)
                )
                Text(
                    text = "Stage ${editingEncryptionLevel + 1}",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }


            Text(
                text = when (editingEncryptionLevel.toInt()) {
                    0x00 -> stringResource(id = R.string.stage_1_encryption_level)
                    0x01 -> stringResource(id = R.string.stage_2_encryption_level)
                    0x02 -> stringResource(id = R.string.stage_3_encryption_level)
                    else -> throw IllegalStateException()
                }, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}