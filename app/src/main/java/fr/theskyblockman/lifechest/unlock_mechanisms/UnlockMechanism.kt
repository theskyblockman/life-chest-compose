package fr.theskyblockman.lifechest.unlock_mechanisms

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.theskyblockman.lifechest.vault.Vault
import javax.crypto.spec.SecretKeySpec

abstract class UnlockMechanism {
    companion object {
        val mechanisms = listOf(
            Password(),
            Pin(),
            Biometric(),
        )
    }

    @Composable
    abstract fun creator(modifier: Modifier): suspend (vault: Vault) -> SecretKeySpec?

    @Composable
    abstract fun Opener(vaultId: String, additionalUnlockData: Map<String, ByteArray>, snackbarHostState: SnackbarHostState, onDismissRequest: () -> Unit, onKeyIssued: (key: SecretKeySpec) -> Boolean)

    abstract fun deleter(vault: Vault)

    abstract val id: String

    @get:Composable
    abstract val displayName: String

    abstract val supportsExport: Boolean
}