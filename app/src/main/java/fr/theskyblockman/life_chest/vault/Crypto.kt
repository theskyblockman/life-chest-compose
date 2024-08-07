package fr.theskyblockman.life_chest.vault

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min
import kotlin.random.Random


object Crypto {
    // In most cases, this is used
    const val DEFAULT_CIPHER_NAME = "AES/CTR/NoPadding"
    const val HASH_CIPHER_NAME = "SHA-256"

    fun createIV(cipher: Cipher): IvParameterSpec {
        val randomSecureRandom = SecureRandom.getInstance("SHA1PRNG")
        val rawIv = ByteArray(cipher.blockSize)
        randomSecureRandom.nextBytes(rawIv)

        return IvParameterSpec(rawIv)
    }

    fun createHash(data: ByteArray): ByteArray {
        val cipher = MessageDigest.getInstance(HASH_CIPHER_NAME)
        return cipher.digest(data)
    }

    fun createID(): String {
        // To keep the sme behavior as the original implementation, we generate only valid hex numbers
        val chars = ('0'..'9') + ('a'..'f')
        val randomString = CharArray(32) {
            chars[Random.nextInt(chars.size)]
        }
        return randomString.concatToString()
    }

    object Encrypt {
        fun inputStream(
            inputStream: InputStream,
            creationDate: Instant = Clock.System.now(),
            vault: Vault
        ): EncryptedFile {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)
            val iv = createIV(cipher)
            assert(vault.key != null) {
                "Vault is still locked"
            }
            cipher.init(Cipher.ENCRYPT_MODE, vault.key, iv)
            val fileID = createID()

            val outFile = File(vault.vaultDirectory, fileID)
            outFile.createNewFile()
            inputStream.use {
                val writer = outFile.outputStream()
                writer.use {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) {
                            break
                        }
                        writer.write(cipher.update(buffer, 0, bytesRead))
                    }
                    writer.write(cipher.doFinal())
                }
            }

            return EncryptedFile(
                id = fileID,
                vaultID = vault.id,
                iv = iv.iv,
                creationDate = creationDate,
                importDate = Clock.System.now()
            )
        }

        fun inputStreamToInputStream(
            inputStream: InputStream,
            key: SecretKeySpec,
            iv: ByteArray
        ): CipherInputStream {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)

            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

            return CipherInputStream(inputStream, cipher)
        }

        fun stringToBytes(
            string: String,
            key: SecretKeySpec,
            iv: IvParameterSpec
        ): ByteArray {
            return bytes(string.toByteArray(), key, iv)
        }

        private fun bytes(
            bytes: ByteArray,
            key: SecretKeySpec,
            iv: IvParameterSpec
        ): ByteArray {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)

            return cipher.doFinal(bytes)
        }
    }

    object Decrypt {
        private fun fileToStream(file: EncryptedFile, vault: Vault): Sequence<ByteArray> {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)

            assert(vault.key != null) {
                "Vault is still locked"
            }
            cipher.init(Cipher.DECRYPT_MODE, vault.key, IvParameterSpec(file.iv))
            val inFile = file.attachedEncryptedFile
            val inputStream = inFile.inputStream()

            return sequence {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                do {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        yield(decrypted)
                    }
                } while (bytesRead != -1)
                val final = cipher.doFinal()
                if (final.isNotEmpty()) {
                    yield(final)
                }
                inputStream.close()
            }
        }

        fun file(
            file: EncryptedFile,
            vault: Vault
        ): ByteArray {
            return fileToStream(file, vault).toList().reduce(ByteArray::plus)
        }

        fun inputStreamToInputStream(
            inputStream: InputStream,
            key: SecretKeySpec,
            iv: ByteArray
        ): CipherInputStream {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)

            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

            return CipherInputStream(inputStream, cipher)
        }

        fun fileToInputStream(
            file: EncryptedFile,
            vault: Vault,
        ): CipherInputStream {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)

            cipher.init(Cipher.DECRYPT_MODE, vault.key!!, IvParameterSpec(file.iv))

            return CipherInputStream(file.attachedEncryptedFile.inputStream(), cipher)
        }

        fun bytesToString(
            bytes: ByteArray,
            key: SecretKeySpec,
            iv: IvParameterSpec
        ): String {
            return bytes(bytes, key, iv).decodeToString()
        }

        fun bytes(
            bytes: ByteArray,
            key: SecretKeySpec,
            iv: IvParameterSpec
        ): ByteArray {
            val cipher = Cipher.getInstance(DEFAULT_CIPHER_NAME)
            cipher.init(Cipher.DECRYPT_MODE, key, iv)

            return cipher.doFinal(bytes)
        }

        object FileProgressively {
            fun getInputStreamForFile(vault: Vault, file: EncryptedFile): RandomAccessFile {
                return RandomAccessFile(File(vault.vaultDirectory, file.id), "r")
            }

            // The IV is a counter?
            private fun calculateAdjustedIV(initialIV: ByteArray, blockIndex: Long): ByteArray {
                val initialCounter = BigInteger(1, initialIV)
                val adjustedCounter = initialCounter + BigInteger.valueOf(blockIndex)
                return adjustedCounter.toByteArray().takeLast(16).toByteArray()
            }

            fun readWith(
                cipher: Cipher,
                key: SecretKeySpec,
                iv: ByteArray,
                offset: Long = 0L,
                size: Long,
                data: ByteArray,
                randomAccessFile: RandomAccessFile
            ): Long {
                randomAccessFile.seek(offset)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    IvParameterSpec(calculateAdjustedIV(iv, offset / 16))
                )
                var totalBytesRead = 0L
                val encrypted = ByteArray(DEFAULT_BUFFER_SIZE)
                while (totalBytesRead < size) {
                    val blockSize = min((size - totalBytesRead).toInt(), DEFAULT_BUFFER_SIZE)

                    val bytesRead = randomAccessFile.read(encrypted, 0, blockSize)
                    if (bytesRead == -1) {
                        break
                    }

                    totalBytesRead += cipher.update(
                        encrypted,
                        0,
                        bytesRead,
                        data,
                        totalBytesRead.toInt()
                    )
                }

                return totalBytesRead
            }
        }
    }
}