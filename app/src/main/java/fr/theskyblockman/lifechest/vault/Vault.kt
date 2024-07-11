package fr.theskyblockman.lifechest.vault

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.explorer.SortMethod
import fr.theskyblockman.lifechest.unlock_mechanisms.UnlockMechanism
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.crypto.spec.SecretKeySpec


data class Policy(
    var name: String = "",
    var encryptionLevel: Byte = 2,
    var unlockMechanismType: String = "password",
    var versionCode: Long,
    var creator: (suspend (vault: Vault) -> SecretKeySpec?)? = null
)

@Serializable
data class Vault(
    var name: String,
    var id: String,
    var creationDate: Instant,
    var fileListID: String,
    var encryptionLevel: Byte,
    var unlockMechanismType: String,
    var additionalUnlockData: MutableMap<String, ByteArray>,
    var versionCode: Long,
    var sortMethod: SortMethod
) {
    companion object {
        const val ASK_TO_CLOSE_CHANNEL_ID = "fr.theskyblockman.lifechest/ask_to_close_chest"

        lateinit var vaultsRoot: String

        suspend fun createFromPolicy(policy: Policy): Vault? {
            val newVault = Vault(
                name = policy.name,
                id = Crypto.createID(),
                creationDate = Clock.System.now(),
                fileListID = Crypto.createID(),
                additionalUnlockData = mutableMapOf(),
                unlockMechanismType = policy.unlockMechanismType,
                encryptionLevel = policy.encryptionLevel,
                versionCode = policy.versionCode,
                sortMethod = SortMethod.Name
            )

            val createdKey = policy.creator!!(newVault) ?: return null

            return withContext(Dispatchers.IO) {
                newVault.key = createdKey
                newVault.fileTree = DirectoryNode(children = mutableListOf(), id = Crypto.createID(), name = newVault.name)
                newVault.vaultDirectory.mkdirs()

                newVault.fileList.createNewFile()
                newVault.writeFileTree()

                newVault.configFile.createNewFile()
                newVault.writeConfig()

                Crypto.createWitness(newVault)
                return@withContext newVault
            }
        }

        private fun fromConfig(file: File): Vault {
            if(!file.exists()) {
                throw FileSystemException(file, reason = "The config file does not exist")
            }

            return Json.decodeFromString(file.readText())
        }

        fun loadVaults(): List<Vault> {
            val vaultsDirectory = File(vaultsRoot, "vaults")
            if(vaultsDirectory.mkdir()) {
                return emptyList()
            }

            val vaultFiles = vaultsDirectory.listFiles() ?: return emptyList()

            val vaults = mutableListOf<Vault>()

            for(vaultFile in vaultFiles) {
                try {
                    vaults.add(fromConfig(File(vaultFile, ".config.json")))
                } catch (e: Exception) {
                    Log.w("VaultLoader", "Failed to load vault ${vaultFile.name}")
                }
            }

            return vaults
        }

        fun loadVault(id: String): Vault? {
            val vaultsDirectory = File(vaultsRoot, "vaults")
            val vaultFile = File(vaultsDirectory, id)

            try {
                return fromConfig(File(vaultFile, ".config.json"))
            } catch (e: Exception) {
                Log.w("VaultLoader", "Failed to load vault ${vaultFile.name}")
            }

            return null
        }
    }

    @Transient
    var key: SecretKeySpec? = null

    @Transient
    var fileTree: DirectoryNode? = null

    val vaultDirectory: File
        get() {
            return File("$vaultsRoot${File.separator}vaults", id)
        }

    val fileList: File
        get() {
            return File(vaultDirectory, ".$fileListID")
        }

    @Transient
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
        classDiscriminator = "#class"
    }

    fun loadFileTree(): DirectoryNode {
        fileTree = json.decodeFromString(
            Crypto.Decrypt.bytesToString(
                fileList.readBytes(),
                key!!
            )
        )
        return fileTree!!
    }

    fun writeFileTree() {
        fileList.writeBytes(
            Crypto.Encrypt.stringToBytes(
                json.encodeToString(fileTree!!),
                key!!
            )
        )
    }

    val configFile: File
        get() {
            return File(vaultDirectory, ".config.json")
        }

    fun writeConfig() {
        configFile.writeText(
            Json.encodeToString(this)
        )
    }

    val witnessFile: File
        get() {
            return File(vaultDirectory, ".witness")
        }

    val currentMechanism: UnlockMechanism
        get() {
            return UnlockMechanism.mechanisms.first { it.id == unlockMechanismType }
        }


    /**
     * Test if the creator is valid for this vault
     *
     * If the result is true, this object's [key] will be set.
     * @param key The creator to test
     * @return True if the creator is valid, false otherwise
     */
    fun testKey(key: SecretKeySpec): Boolean {
        if (!witnessFile.exists()) {
            throw FileSystemException(witnessFile, reason = "The witness file does not exist")
        }

        if (Crypto.Decrypt.witness(this, key).contentEquals(id.toByteArray())) {
            this.key = key
            return true
        }

        return false
    }

    fun delete() {
        currentMechanism.deleter(this)
        if(!vaultDirectory.deleteRecursively() || vaultDirectory.exists()) {
            throw FileSystemException(vaultDirectory, reason = "Failed to delete vault directory")
        }
    }

    fun onSetBackground(context: Context) {
        Log.d("Vault", "Vault on set background with encryption level $encryptionLevel")
        when(encryptionLevel.toInt()) {
            0x00 -> {
                return
            }
            0x01 -> {
                // Create an explicit intent for an Activity in your app.
                val intent = Intent("fr.theskyblockman.close_chest")
                val pendingIntent: PendingIntent = PendingIntentCompat.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT, false)!!

                val builder = NotificationCompat.Builder(context, ASK_TO_CLOSE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle(context.getString(R.string.close_your_chest))
                    .setContentText(context.getString(R.string.click_to_close_chest))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)

                with(NotificationManagerCompat.from(context)) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@with
                    }
                    // notificationId is a unique int for each notification that you must define.
                    notify(0, builder.build())
                }
            }
            0x02 -> {
                (context as Activity).finish()
            }
            else -> {
                throw FileSystemException(vaultDirectory, reason = "Invalid encryption level")
            }
        }
    }

    fun onBroughtBack(context: Context) {
        when(encryptionLevel.toInt()) {
            0x00, 0x02 -> {
                return
            }
            0x01 -> {
                NotificationManagerCompat.from(context).cancel(0)
            }
            else -> {
                throw FileSystemException(vaultDirectory, reason = "Invalid encryption level")
            }
        }
    }
}