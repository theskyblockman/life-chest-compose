package fr.theskyblockman.lifechest.unlock_mechanisms

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
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.Vault
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

class Password : UnlockMechanism() {
    private fun validate(context: Context, password: String): String? {
        if (password.length < 10) {
            return context.getString(R.string.password_must_be_at_least_10_characters_long)
        }

        var lowerCase = false
        var upperCase = false
        var numberCase = false
        var otherCase = false

        for (char in password) {
            when {
                char.isLowerCase() -> lowerCase = true
                char.isUpperCase() -> upperCase = true
                char.isDigit() -> numberCase = true
                else -> otherCase = true
            }
        }

        if (!lowerCase) {
            return context.getString(R.string.password_must_contain_at_least_one_lowercase_letter)
        }

        if (!upperCase) {
            return context.getString(R.string.password_must_contain_at_least_one_uppercase_letter)
        }

        if (!numberCase) {
            return context.getString(R.string.password_must_contain_at_least_one_number)
        }

        if (!otherCase) {
            return context.getString(R.string.password_must_contain_at_least_one_other_character)
        }

        return null
    }

    @Composable
    override fun creator(modifier: Modifier): suspend (vault: Vault) -> SecretKeySpec? {
        val mg = MessageDigest.getInstance(Crypto.HASH_CIPHER_NAME)
        var currentValue by remember { mutableStateOf("") }
        var currentError: String? by remember { mutableStateOf(null) }
        val context = LocalContext.current

        OutlinedTextField(
            modifier = modifier,
            value = currentValue,
            onValueChange = { currentValue = it },
            label = { Text(stringResource(R.string.password)) },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
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
            var key: SecretKeySpec? = SecretKeySpec(
                mg.digest(currentValue.toByteArray()),
                Crypto.DEFAULT_CIPHER_NAME
            )

            currentError = validate(context, currentValue)

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
                        text = stringResource(R.string.enter_your_password),
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
                        label = { Text(stringResource(R.string.password)) },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
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
        get() = "password"

    override val displayName: String
        @Composable
        get() = stringResource(id = R.string.password)

    override val supportsExport: Boolean
        get() = true
}