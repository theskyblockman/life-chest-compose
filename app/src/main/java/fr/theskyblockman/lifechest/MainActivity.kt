package fr.theskyblockman.lifechest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.material.color.DynamicColors
import fr.theskyblockman.lifechest.main.EditVaultPage
import fr.theskyblockman.lifechest.main.MainPage
import fr.theskyblockman.lifechest.main.NewVaultPage
import fr.theskyblockman.lifechest.onboarding.OnboardingActivity
import fr.theskyblockman.lifechest.ui.theme.AppTheme
import fr.theskyblockman.lifechest.vault.Vault
import fr.theskyblockman.lifechest.vault.Vault.Companion.vaultsRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vaultsRoot = applicationInfo.dataDir

        if (!File(vaultsRoot, "vaults").exists()) {
            val intent = Intent(
                this,
                OnboardingActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        }

        DynamicColors.applyToActivitiesIfAvailable(application)

        val vaultsViewModel: VaultsViewModel by viewModels()
        vaultsViewModel.updateLoadedVaults()

        setContent {
            MainActivityNav(context = this, vaultsViewModel = vaultsViewModel)
        }
    }
}

@Composable
fun MainActivityNav(context: Context?, vaultsViewModel: VaultsViewModel = viewModel()) {
    val navController = rememberNavController()

    AppTheme {
        NavHost(navController, startDestination = "/") {
            composable("/") {
                MainPage(navController, vaultsViewModel)
            }

            composable("/new", enterTransition = {
                slideIn(animationSpec = tween(
                    300, easing = LinearEasing
                ), initialOffset = {
                    IntOffset(
                        (it.width * 1.5).toInt(),
                        (it.height * 1.5).toInt()
                    )
                }) + scaleIn(animationSpec = tween(300, easing = LinearEasing))
            }) {
                NewVaultPage(navController, context, vaultsViewModel)
            }

            composable("/edit/{vaultId}", arguments = listOf(
                navArgument("vaultId") { type = NavType.StringType }
            )) {
                val vaultId = it.arguments?.getString("vaultId") ?: throw IllegalArgumentException()
                val vault = Vault.loadVault(vaultId) ?: throw IllegalArgumentException()
                EditVaultPage(
                    navController,
                    vault,
                    vaultsViewModel
                )
            }
        }
    }
}

class VaultsViewModel : ViewModel() {
    private val _loadedVaults = MutableStateFlow(listOf<Vault>())
    val loadedVaults = _loadedVaults.asStateFlow()

    fun updateLoadedVaults() {
        _loadedVaults.update {
            Vault.loadVaults()
        }
    }
}