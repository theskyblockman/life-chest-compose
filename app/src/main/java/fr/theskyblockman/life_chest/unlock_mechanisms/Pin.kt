package fr.theskyblockman.life_chest.unlock_mechanisms

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.vault.Crypto
import fr.theskyblockman.life_chest.vault.Vault
import javax.crypto.spec.SecretKeySpec

class Pin : UnlockMechanism() {
    private fun validate(context: Context, pin: String): String? {
        if (pin.length != 4) {
            return context.getString(R.string.pin_code_must_be_4_digits_long)
        }

        if (listOf(
                "1234",
                "4321",
                "1111",
                "2222",
                "3333",
                "4444",
                "5555",
                "6666",
                "7777",
                "8888",
                "9999",
                "0000",
            ).contains(pin)
        ) {
            return context.getString(R.string.please_use_a_stronger_pin_code)
        }

        return null
    }

    @Composable
    override fun creator(modifier: Modifier): suspend (vault: Vault) -> SecretKeySpec? {
        var currentValue by remember { mutableStateOf("") }
        var currentError: String? by remember { mutableStateOf(null) }
        val context = LocalContext.current

        OutlinedTextField(
            modifier = modifier,
            value = currentValue,
            onValueChange = { currentValue = it },
            label = { Text(stringResource(R.string.pin_code)) },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = currentError != null,
            supportingText = {
                if (currentError != null) {
                    Text(currentError!!)
                }
            }
        )

        return {
            currentError = null
            currentError = validate(context, currentValue)

            var key: SecretKeySpec? = SecretKeySpec(
                Crypto.createHash(currentValue.toByteArray()),
                Crypto.DEFAULT_CIPHER_NAME
            )

            if (currentError != null) {
                key = null
            }

            key
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Opener(
        vaultId: String,
        additionalUnlockData: Map<String, ByteArray>,
        snackbarHostState: SnackbarHostState,
        onDismissRequest: () -> Unit,
        onKeyIssued: (key: SecretKeySpec) -> Boolean
    ) {
        var currentValue by remember { mutableStateOf("") }
        var lastResult: Boolean? by remember {
            mutableStateOf(null)
        }

        BasicAlertDialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    Modifier
                        .padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.enter_your_pin_code),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                    )

                    val focusRequester = remember { FocusRequester() }

                    OutlinedTextField(
                        value = currentValue,
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .focusRequester(focusRequester),
                        onValueChange = { currentValue = it },
                        label = { Text(stringResource(R.string.pin_code)) },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = lastResult == false,
                        supportingText = {
                            if (lastResult == false) {
                                Text(stringResource(id = R.string.wrong_credentials))
                            }
                        }
                    )

                    LaunchedEffect(null) {
                        focusRequester.requestFocus()
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                lastResult =
                                    onKeyIssued(
                                        SecretKeySpec(
                                            Crypto.createHash(
                                                currentValue.toByteArray()
                                            ), Crypto.DEFAULT_CIPHER_NAME
                                        )
                                    )
                            },
                        ) {
                            Text(stringResource(R.string.confirm))
                        }

                    }
                }
            }
        }
    }

    override fun deleter(vault: Vault) {}

    override val id: String
        get() = "pin"

    override val displayName: String
        @Composable
        get() = stringResource(id = R.string.pin_code)

    override val supportsExport: Boolean
        get() = true
}