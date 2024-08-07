package fr.theskyblockman.life_chest.explorer

import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.vault.TreeNode

enum class SortMethod(val displayName: Int) {
    ImportDate(R.string.import_date),
    ImportDateReverse(R.string.import_date_reverse),
    Size(R.string.size),
    SizeReverse(R.string.size_reverse),
    Name(R.string.name),
    NameReverse(R.string.name_reverse),
    Random(R.string.random);

    fun sortItems(items: List<TreeNode>): List<TreeNode> {
        return when(this) {
            ImportDate -> items.sortedBy { it.creationDate }
            ImportDateReverse -> items.sortedByDescending { it.creationDate }
            Size -> items.sortedBy { it.size }
            SizeReverse -> items.sortedByDescending { it.size }
            Name -> items.sortedBy { it.name }
            NameReverse -> items.sortedByDescending { it.name }
            Random -> items.shuffled()
        }
    }
}