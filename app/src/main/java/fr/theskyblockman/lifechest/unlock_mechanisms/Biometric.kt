package fr.theskyblockman.lifechest.unlock_mechanisms

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.Vault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Biometric : UnlockMechanism() {
    private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(keyName: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")

        // Before the keystore can be accessed, it must be loaded.
        keyStore.load(null)
        return keyStore.getKey(keyName, null) as SecretKey
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7
        )
    }

    @Composable
    override fun creator(modifier: Modifier): suspend (vault: Vault) -> SecretKeySpec? {
        Box(modifier = modifier)

        val context = LocalContext.current
        return { vault ->
            generateSecretKey(
                KeyGenParameterSpec.Builder(
                    vault.id,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(false)
                    .build()
            )

            val cipher = getCipher()
            val secretKey = getSecretKey(vault.id)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val value: CompletableDeferred<SecretKeySpec?> = CompletableDeferred()

            val prompt = BiometricPrompt(
                context as FragmentActivity,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val key = ByteArray(16)
                        SecureRandom().nextBytes(key)
                        val encryptedInfo = result.cryptoObject?.cipher?.doFinal(
                            key
                        )
                        if (encryptedInfo == null) {
                            value.complete(null)
                            return
                        }

                        vault.additionalUnlockData["encryptedKey"] = encryptedInfo
                        vault.additionalUnlockData["iv"] = cipher.iv
                        value.complete(SecretKeySpec(key, Crypto.DEFAULT_CIPHER_NAME))
                    }

                    override fun onAuthenticationFailed() {
                        value.complete(null)
                    }
                })

            val promptInfo = PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_login))
                .setSubtitle(context.getString(R.string.biometric_creation_subtitle))
                .setNegativeButtonText(context.getString(R.string.cancel))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfo.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                promptInfo
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText(context.getString(R.string.cancel))
            }

            prompt.authenticate(
                promptInfo
                    .build(),
                BiometricPrompt.CryptoObject(cipher)
            )

            value.await()
        }
    }

    @Composable
    override fun Opener(
        vaultId: String,
        additionalUnlockData: Map<String, ByteArray>,
        snackbarHostState: SnackbarHostState,
        onDismissRequest: () -> Unit,
        onKeyIssued: (key: SecretKeySpec) -> Boolean
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            val biometricPrompt = BiometricPrompt(
                context as FragmentActivity,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val encryptedKey = additionalUnlockData["encryptedKey"]
                        if (encryptedKey == null) {
                            onDismissRequest()
                            return
                        }

                        val decryptedInfo = result.cryptoObject?.cipher?.doFinal(
                            encryptedKey
                        )
                        if (decryptedInfo == null) {
                            onDismissRequest()
                        } else {
                            if (!onKeyIssued(
                                    SecretKeySpec(
                                        decryptedInfo,
                                        Crypto.DEFAULT_CIPHER_NAME
                                    )
                                )
                            ) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("The authentication didn't work, is your vault corrupted?")
                                }
                                onDismissRequest()
                            }
                        }
                    }

                    override fun onAuthenticationFailed() {
                        onDismissRequest()
                    }
                }
            )

            val cipher = getCipher()
            val secretKey = getSecretKey(vaultId)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(additionalUnlockData["iv"]))

            val promptInfo = PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_login))
                .setSubtitle(context.getString(R.string.biometric_login_subtitle))


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfo.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                promptInfo.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                promptInfo.setNegativeButtonText(context.getString(R.string.cancel))
            }

            biometricPrompt.authenticate(
                promptInfo
                    .build(),
                BiometricPrompt.CryptoObject(cipher),
            )
        }
    }

    override fun deleter(vault: Vault) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(vault.id)
    }

    override val id: String
        get() = "biometric"

    override val displayName: String
        @Composable
        get() = stringResource(id = R.string.biometric)

    override val supportsExport: Boolean
        get() = false
}