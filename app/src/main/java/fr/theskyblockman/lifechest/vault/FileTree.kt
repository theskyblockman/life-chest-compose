package fr.theskyblockman.lifechest.vault

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = TreeNodeSerializer::class)
sealed class TreeNode {
    /**
     * All of the nodes who are lower in the tree than this node. Empty if this is not a directory.
     */
    abstract val children: MutableList<TreeNode>

    /**
     * The file attached to this node. null if it is a folder.
     */
    abstract val attachedFile: EncryptedFile?

    /**
     * A low-res picture shown for each file, optimized out if it is a folder.
     */
    abstract val attachedThumbnail: EncryptedFile?

    abstract fun goTo(fileId: String): TreeNode?

    abstract fun goToParentOf(fileId: String): DirectoryNode?

    abstract var name: String

    abstract val nodeType: String

    abstract val id: String

    abstract val type: String

    abstract val size: Long

    abstract fun delete()

    abstract val creationDate: Instant

    abstract val importDate: Instant

    abstract fun count(): Int
}

@Serializable
class FileNode(
    override val attachedFile: EncryptedFile,
    override val attachedThumbnail: EncryptedFile?,
    override val nodeType: String = "file",
    override var name: String,
    override val type: String,
    override val creationDate: Instant = Clock.System.now(),
    override val importDate: Instant = Clock.System.now(),
    override val size: Long
) : TreeNode() {
    override val children: MutableList<TreeNode>
        get() = mutableListOf()

    val vaultID: String
        get() = attachedFile.vaultID

    override fun goTo(fileId: String): TreeNode? {
        if (fileId == id) {
            return this
        }

        return null
    }

    override fun goToParentOf(fileId: String): DirectoryNode? = null

    override val id: String
        get() = attachedFile.id

    override fun delete() {
        attachedFile.attachedEncryptedFile.delete()
    }

    override fun count(): Int {
        return 1
    }
}

object TreeNodeSerializer : JsonContentPolymorphicSerializer<TreeNode>(TreeNode::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TreeNode> {
        return when (element.jsonObject["nodeType"]?.jsonPrimitive?.content) {
            "file" -> FileNode.serializer()
            "directory" -> DirectoryNode.serializer()
            else -> throw IllegalStateException("Unknown type: ${element.jsonObject["nodeType"]?.jsonPrimitive?.content}")
        }
    }
}

@Serializable
class DirectoryNode(
    override val children: MutableList<TreeNode>,
    override var name: String,
    override val nodeType: String = "directory",
    override val id: String,
    override val creationDate: Instant = Clock.System.now(),
    override val importDate: Instant = Clock.System.now(),
) : TreeNode() {
    override val attachedFile: EncryptedFile?
        get() = null

    override val attachedThumbnail: EncryptedFile?
        get() = null

    override val type: String
        get() = "directory"

    override val size: Long
        get() {
            var size = 0L

            for (child in children) {
                size += child.size
            }

            return size
        }

    fun listFiles(): List<FileNode> {
        val values = mutableListOf<FileNode>()

        for (child in children) {
            if (child is FileNode) {
                values.add(child)
            } else if (child is DirectoryNode) {
                values.addAll(child.listFiles())
            }
        }

        return values
    }

    override fun count(): Int {
        var amount = 0

        for (child in children) {
            amount += child.count()
        }

        return amount
    }

    /**
     * Returns the directory node if it exists and is a child of this directory
     */
    override fun goTo(fileId: String): TreeNode? {
        if (id == fileId) {
            return this
        }

        for (child in children) {
            val result = child.goTo(fileId)
            if (result != null) {
                return result
            }
        }

        return null
    }

    override fun goToParentOf(fileId: String): DirectoryNode? {
        for (child in children) {
            if (child.id == fileId) {
                return this
            }

            val result = child.goToParentOf(fileId)
            if (result != null) {
                return result
            }
        }

        return null
    }

    override fun delete() {
        for (child in children) {
            child.delete()
        }
    }
}