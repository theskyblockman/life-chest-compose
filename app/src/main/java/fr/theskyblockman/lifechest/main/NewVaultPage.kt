package fr.theskyblockman.lifechest.main

import android.content.Context
import android.os.Build
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.unlock_mechanisms.UnlockMechanism
import fr.theskyblockman.lifechest.vault.Policy
import fr.theskyblockman.lifechest.vault.Vault
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVaultPage(navController: NavController, context: Context?) {
    var currentPolicy by remember {
        mutableStateOf(
            Policy(
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context?.packageManager?.getPackageInfo(
                        context.packageName,
                        0
                    )?.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    context?.packageManager?.getPackageInfo(
                        context.packageName,
                        0
                    )?.versionCode?.toLong()
                } ?: 1,
            )
        )
    }

    var isVaultNameEmptyError by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.create_new_chest)) },
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
            val coroutineScope = rememberCoroutineScope()

            Button(contentPadding = PaddingValues(start = 16.dp, end = 24.dp), onClick = {
                if (currentPolicy.name.isEmpty()) {
                    isVaultNameEmptyError = true
                    return@Button
                }

                coroutineScope.launch {
                    val createdVault = Vault.createFromPolicy(
                        currentPolicy
                    )
                    if (createdVault == null) return@launch

                    navController.navigateUp()
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.create_new_chest),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = stringResource(id = R.string.create_new_chest))
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
                value = currentPolicy.name,
                onValueChange = {
                    currentPolicy = currentPolicy.copy(name = it)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text(text = stringResource(R.string.vault_name)) },
                isError = isVaultNameEmptyError,
                supportingText = {
                    if (isVaultNameEmptyError) {
                        Text(text = stringResource(R.string.vault_name_empty_error))
                    }
                },
                singleLine = true
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))

            val scrollSate = rememberScrollState()

            Row(
                modifier = Modifier
                    .horizontalScroll(scrollSate)
                    .padding(horizontal = 4.dp)
            ) {
                for (mechanism in UnlockMechanism.mechanisms) {
                    FilterChip(
                        selected = currentPolicy.unlockMechanismType == mechanism.id,
                        onClick = {
                            currentPolicy = currentPolicy.copy(unlockMechanismType = mechanism.id)
                        },
                        label = { Text(text = mechanism.displayName) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            currentPolicy =
                currentPolicy.copy(creator = UnlockMechanism.mechanisms.first { it.id == currentPolicy.unlockMechanismType }
                    .creator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp)
                            .padding(top = 6.dp)
                    ))

            HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Slider(
                    value = currentPolicy.encryptionLevel.toFloat(),
                    onValueChange = {
                        currentPolicy =
                            currentPolicy.copy(encryptionLevel = it.toInt().toByte())
                    },
                    steps = 1,
                    valueRange = 0f..2f,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .fillMaxWidth(.75f)
                )
                Text(
                    text = "Stage ${currentPolicy.encryptionLevel + 1}",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }


            Text(
                text = when (currentPolicy.encryptionLevel.toInt()) {
                    0 -> stringResource(id = R.string.stage_1_encryption_level)
                    1 -> stringResource(id = R.string.stage_2_encryption_level)
                    2 -> stringResource(id = R.string.stage_3_encryption_level)
                    else -> throw IllegalStateException()
                }, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}