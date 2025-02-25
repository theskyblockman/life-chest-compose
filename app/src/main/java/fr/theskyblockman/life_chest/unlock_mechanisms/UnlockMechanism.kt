package fr.theskyblockman.life_chest.unlock_mechanisms

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.theskyblockman.life_chest.vault.Vault
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

    open fun enabled(context: Context): Boolean {
        return true
    }

    @Composable
    abstract fun Opener(
        vaultId: String,
        additionalUnlockData: Map<String, ByteArray>,
        snackbarHostState: SnackbarHostState,
        onDismissRequest: () -> Unit,
        onKeyIssued: (key: SecretKeySpec) -> Boolean
    )

    abstract fun deleter(vault: Vault)

    abstract val id: String

    @get:Composable
    abstract val displayName: String

    abstract val supportsExport: Boolean
}