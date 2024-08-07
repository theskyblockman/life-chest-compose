package fr.theskyblockman.life_chest.vault

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class EncryptedFile(
    var id: String,
    var vaultID: String,
    var iv: ByteArray,
    var creationDate: Instant,
    var importDate: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedFile

        if (id != other.id) return false
        if (vaultID != other.vaultID) return false
        if (!iv.contentEquals(other.iv)) return false
        if (creationDate != other.creationDate) return false
        if (importDate != other.importDate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vaultID.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + creationDate.hashCode()
        result = 31 * result + importDate.hashCode()
        return result
    }

    val attachedEncryptedFile: File
        get() = File(File(File(Vault.vaultsRoot, "vaults"), vaultID), id)
}