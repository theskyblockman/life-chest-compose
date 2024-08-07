package fr.theskyblockman.life_chest.transactions

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import fr.theskyblockman.life_chest.transactions.LcefOuterClass.FileMetadata
import fr.theskyblockman.life_chest.transactions.LcefOuterClass.Lcef
import fr.theskyblockman.life_chest.vault.Crypto
import fr.theskyblockman.life_chest.vault.FileNode
import fr.theskyblockman.life_chest.vault.Vault
import kotlinx.datetime.Instant
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object LcefManager {
    const val MIME_TYPE = "application/vnd.fr.theskyblockman.life_chest.lcef"

    private const val LCEF_VERSION: Byte = 0x00

    val LCEF_MAGIC = byteArrayOf(0x4C, 0x43, 0x45, 0x46) // LCEF

    private fun fileNodeToLcef(vault: Vault, node: FileNode): Lcef {
        val encryptedFileNameIv = Crypto.createIV(Cipher.getInstance(Crypto.DEFAULT_CIPHER_NAME))
        return Lcef.newBuilder()
            .setKeyHash(Crypto.createHash(vault.key!!.encoded).toByteString())
            .setIv(node.attachedFile.iv.toByteString())
            .setUnlockMethod(vault.unlockMechanismType)
            .putAllAdditionalUnlockData(vault.additionalUnlockData.mapValues { it.value.toByteString() })
            .setEncryptedFileNameIv(encryptedFileNameIv.iv.toByteString())
            .setEncryptedFileName(
                Crypto.Encrypt.stringToBytes(node.name, vault.key!!, encryptedFileNameIv)
                    .toByteString()
            )
            .setFileMetadata(
                // We intentionally don't give the name
                FileMetadata.newBuilder()
                    .setLastModified(node.importDate.toEpochMilliseconds())
                    .setMimeType(node.type)
                    .setSize(node.size)
                    .setCreationDate(node.creationDate.toEpochMilliseconds())
            )
            .setFileID(node.id)
            .setVaultID(vault.id)
            .setThumbnail(
                if (node.attachedThumbnail == null)
                    ByteString.EMPTY
                else
                    node.attachedThumbnail.attachedEncryptedFile.readBytes().toByteString()
            )
            .setThumbnailIv(
                node.attachedThumbnail?.iv?.toByteString() ?: ByteString.EMPTY
            )
            .build()
    }

    fun createLcefHeader(vault: Vault, node: FileNode): ByteArray {
        val lcef = fileNodeToLcef(vault, node)

        return byteArrayOf(
            // LCEF magic
            *LCEF_MAGIC,
            // Version of LCEF
            LCEF_VERSION,
            // Size of metadata
            *ByteBuffer.allocate(Int.SIZE_BYTES).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(lcef.serializedSize)
            }.array(),
            // Metadata
            *lcef.toByteArray()
        )
    }

    fun isLcef(file: InputStream): Boolean {
        val currentMagic = ByteArray(LCEF_MAGIC.size)
        file.read(currentMagic)
        val isLcef = currentMagic.contentEquals(LCEF_MAGIC)
        return isLcef
    }

    /**
     * This method is **not** responsible to close [inputStream]
     * It reads until the start of the encrypted content of the file
     */
    fun readLcefHeader(inputStream: InputStream): Lcef? {
        try {
            val magic = ByteArray(LCEF_MAGIC.size)
            inputStream.read(magic)
            if (!magic.contentEquals(LCEF_MAGIC)) {
                Log.w("LcefParser", "Invalid LCEF magic with LCEF mime type")
                return null
            }

            val version = inputStream.read()

            Log.d("LcefParser", "Starting to parse LCEF file version $version")

            val metadataSize = ByteBuffer.allocate(Int.SIZE_BYTES).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                inputStream.read(array())
            }.int

            val metadata = ByteArray(metadataSize)

            if (inputStream.read(metadata) != metadataSize) {
                Log.e("LcefParser", "EOF while reading metadata")
                return null
            }

            Log.d("LcefParser", "Read successfully LCEF headers (metadata size: $metadataSize)")

            return Lcef.parseFrom(metadata)
        } catch (e: Exception) {
            Log.e("LcefParser", "Failed to parse LCEF file", e)
            return null
        }
    }

    fun importLcefFiles(
        context: Context,
        vault: Vault,
        outLocation: String,
        files: Map<Uri, SecretKeySpec>
    ) {
        for (file in files) {
            context.contentResolver.openInputStream(file.key)!!.use { fis ->
                val lcef = readLcefHeader(fis) ?: return

                val metadata = lcef.fileMetadata
                    .toBuilder()
                    .setName(
                        Crypto.Decrypt.bytesToString(
                            lcef.encryptedFileName.toByteArray(),
                            file.value,
                            IvParameterSpec(lcef.encryptedFileNameIv.toByteArray())
                        )
                    )
                    .build()

                val thumbnail = if (lcef.thumbnail.toByteArray().isEmpty()) {
                    null
                } else {
                    Crypto.Encrypt.inputStream(
                        Crypto.Decrypt.inputStreamToInputStream(
                            ByteArrayInputStream(lcef.thumbnail.toByteArray()),
                            file.value,
                            lcef.thumbnailIv.toByteArray()
                        ),
                        Instant.fromEpochMilliseconds(metadata.lastModified),
                        vault
                    )
                }

                if (metadata == null) {
                    return
                }

                val encryptedFile = Crypto.Encrypt.inputStream(
                    Crypto.Decrypt.inputStreamToInputStream(
                        fis,
                        file.value,
                        lcef.iv.toByteArray()
                    ),
                    Instant.fromEpochMilliseconds(metadata.lastModified),
                    vault
                )

                vault.fileTree!!.goTo(outLocation)!!.children.add(
                    FileNode(
                        encryptedFile,
                        thumbnail,
                        name = metadata.name,
                        type = metadata.mimeType ?: "*/*",
                        size = metadata.size,
                        creationDate = Instant.fromEpochMilliseconds(metadata.creationDate)
                    )
                )
            }
        }
    }
}