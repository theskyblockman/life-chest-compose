package fr.theskyblockman.life_chest.explorer

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.DocumentsContract
import android.provider.MediaStore.MediaColumns
import android.util.Log
import android.util.Size
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.theskyblockman.life_chest.R
import fr.theskyblockman.life_chest.transactions.LcefManager
import fr.theskyblockman.life_chest.transactions.LcefOuterClass.FileMetadata
import fr.theskyblockman.life_chest.ui.theme.AppTheme
import fr.theskyblockman.life_chest.vault.Crypto
import fr.theskyblockman.life_chest.vault.DirectoryNode
import fr.theskyblockman.life_chest.vault.EncryptedContentProvider
import fr.theskyblockman.life_chest.vault.EncryptedFile
import fr.theskyblockman.life_chest.vault.FileNode
import fr.theskyblockman.life_chest.vault.TreeNode
import fr.theskyblockman.life_chest.vault.Vault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.crypto.spec.SecretKeySpec

class ExplorerViewModel : ViewModel() {
    private val vaultState: MutableStateFlow<Vault?> = MutableStateFlow(null)
    val vault = this.vaultState.asStateFlow()

    private val toImportState: MutableStateFlow<String?> = MutableStateFlow(null)
    val toImport = this.toImportState.asStateFlow()

    private val bypassChestClosureState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var bypassChestClosure = this.bypassChestClosureState.asStateFlow()

    fun setBypassChestClosure(bypass: Boolean) {
        bypassChestClosureState.update {
            bypass
        }
    }

    private val readerFilesState = MutableStateFlow<List<String>>(emptyList())
    val readerFiles = this.readerFilesState.asStateFlow()

    fun setReaderFiles(files: List<String>) {
        readerFilesState.update {
            files
        }
    }

    fun loadVault(rawVault: String, key: SecretKeySpec) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultState.update {
                Json.decodeFromString(rawVault)
            }
            vaultState.value!!.key = key
            EncryptedContentProvider.vault = vaultState.value!!
            currentSortMethod.update {
                vaultState.value!!.sortMethod
            }
        }
    }

    fun reloadVault() {
        if (vaultState.value == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val key = vaultState.value!!.key
            vaultState.update {
                Vault.loadVault(vaultState.value!!.id)
            }
            vaultState.value!!.key = key
            EncryptedContentProvider.vault = vaultState.value!!
            currentSortMethod.update {
                vaultState.value!!.sortMethod
            }
        }
    }

    suspend fun reloadFileTree() {
        if (vaultState.value == null) {
            Log.d("ExplorerViewModel", "Vault is null")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val newVault = vaultState.value!!
            newVault.loadFileTree()
            vaultState.update {
                newVault
            }
        }.join()
    }

    fun setToImport(toImport: String?) {
        toImportState.update {
            toImport
        }
    }

    private val currentSortMethod = MutableStateFlow(SortMethod.Name)
    val currentSortMethodState = this.currentSortMethod.asStateFlow()

    fun setSortMethod(method: SortMethod) {
        currentSortMethod.update {
            method
        }
    }

    fun setLcefUrisTransaction(urisTransaction: MutableList<Uri>?) {
        lcefUrisTransaction.update {
            urisTransaction
        }
    }

    private val lcefUrisTransaction: MutableStateFlow<MutableList<Uri>?> = MutableStateFlow(null)
    val lcefUrisTransactionState = this.lcefUrisTransaction.asStateFlow()
}

class ExplorerActivity : FragmentActivity() {
    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private var importResultCompleter: CompletableDeferred<List<Uri>>? = null
    var currentNodeToExport: TreeNode? = null
    lateinit var exporter: ActivityResultLauncher<String>
    private lateinit var explorerViewModel: ExplorerViewModel

    @Suppress("SameParameterValue")
    private fun createNotificationChannel(channelId: String) {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Vault.vaultsRoot = applicationInfo.dataDir

        createNotificationChannel(Vault.ASK_TO_CLOSE_CHANNEL_ID)

        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )

        val passedData = intent.extras

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (passedData == null) {
            finish()
            return
        }

        val contract = ActivityResultContracts.OpenMultipleDocuments()
        launcher = registerForActivityResult(contract) {
            importResultCompleter?.complete(it)
        }

        val filter = IntentFilter("fr.theskyblockman.life_chest.close_chest")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }

        val toImport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            passedData.getParcelable("to-import", Uri::class.java)?.toString()
        } else {
            @Suppress("DEPRECATION")
            passedData.getParcelable<Uri?>("to-import")?.toString()
        }

        val viewModel: ExplorerViewModel by viewModels()
        viewModel.loadVault(
            passedData.getString("vault")!!,
            SecretKeySpec(passedData.getByteArray("key")!!, Crypto.DEFAULT_CIPHER_NAME)
        )
        viewModel.setToImport(toImport)
        explorerViewModel = viewModel

        exporter =
            registerForActivityResult(ActivityResultContracts.CreateDocument(LcefManager.MIME_TYPE)) { uri ->
                uri ?: return@registerForActivityResult
                currentNodeToExport ?: return@registerForActivityResult
                if (currentNodeToExport !is FileNode) return@registerForActivityResult

                Vault.vaultsRoot = applicationInfo.dataDir
                (currentNodeToExport as FileNode).attachedFile.attachedEncryptedFile.inputStream()
                    .use { inputStream ->
                        contentResolver.openOutputStream(uri)!!.use { outputStream ->
                            outputStream.write(
                                LcefManager.createLcefHeader(
                                    viewModel.vault.value!!,
                                    currentNodeToExport as FileNode
                                )
                            )
                            inputStream.copyTo(outputStream)
                        }
                    }
            }

        enableEdgeToEdge()
        setContent {
            ExplorerActivityNav(this, viewModel)
        }
    }

    private val broadcastReceiver = CloseVault()

    private inner class CloseVault : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            NotificationManagerCompat.from(context ?: this@ExplorerActivity).cancel(0)
            finish()
        }
    }

    fun pickFiles(): CompletableDeferred<List<Uri>> {
        importResultCompleter = CompletableDeferred()
        launcher.launch(arrayOf("*/*"))
        return importResultCompleter!!
    }

    fun hideSystemUI() {
        actionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars())
                hide(WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    fun showSystemUI() {
        actionBar?.show()

        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                show(WindowInsets.Type.statusBars())
                show(WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (!explorerViewModel.bypassChestClosure.value && !isFinishing) {
            explorerViewModel.vault.value!!.onSetBackground(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!explorerViewModel.bypassChestClosure.value) {
            explorerViewModel.vault.value!!.onBroughtBack(this)
        } else {
            explorerViewModel.setBypassChestClosure(false)
        }
    }

    override fun onDestroy() {
        NotificationManagerCompat.from(this).cancel(0)
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }
}

data class FileImportsState(
    val filesPicked: Boolean = false,
    val fileAmount: Int? = null,
    val fileProcessed: Int? = null
)

class FileImportViewModel(private val application: Application) : AndroidViewModel(application) {
    val uiState = MutableStateFlow(FileImportsState())
    val uiStateFlow: StateFlow<FileImportsState> = uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun moveFiles(
        lastResultCompleter: CompletableDeferred<List<Uri>>,
        vault: Vault,
        currentPath: String,
        onLcefFiles: (List<Uri>) -> Unit = {},
        onEnd: suspend (newFileTree: DirectoryNode) -> Unit = {}
    ) {
        lastResultCompleter.invokeOnCompletion {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val result = lastResultCompleter.getCompleted()

                    if (result.isEmpty()) {
                        return@withContext
                    }

                    val lcefFiles = mutableListOf<Uri>()
                    val nonLcefFiles = mutableListOf<Uri>()

                    for (file in result) {
                        application.contentResolver.openInputStream(file)!!.use { fis ->
                            if (LcefManager.isLcef(fis)) {
                                lcefFiles += file
                            } else {
                                nonLcefFiles += file
                            }
                        }
                    }

                    uiState.update {
                        it.copy(
                            filesPicked = true,
                            fileAmount = nonLcefFiles.size,
                            fileProcessed = 0
                        )
                    }

                    onLcefFiles(lcefFiles)

                    for (file in nonLcefFiles) {
                        application.contentResolver.openInputStream(file)!!.use { fis ->
                            val metadata = gatherData(file)
                            val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                createThumbnail(file, vault)
                            } else {
                                null
                            }

                            moveFile(
                                fileInputStream = fis,
                                metadata = metadata,
                                thumbnail = thumbnail,
                                outVault = vault,
                                outPath = currentPath
                            )
                        }
                    }

                    vault.writeFileTree()

                    uiState.update {
                        FileImportsState()
                    }

                    onEnd(vault.fileTree!!)
                }

            }
        }
    }

    private fun gatherData(file: Uri): FileMetadata? {
        val queryResult = application.contentResolver.query(
            file, arrayOf(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                MediaColumns.DATE_ADDED,
            ), null, null
        ) ?: return null

        if (!queryResult.moveToFirst()) {
            return null
        }

        queryResult.use {
            val lastModifiedColumn =
                queryResult.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            var lastModified: Long = System.currentTimeMillis()
            if (lastModifiedColumn != -1) {
                lastModified =
                    queryResult.getLong(lastModifiedColumn)
            }

            val mimeTypeColumn =
                queryResult.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            val mimeType = if (mimeTypeColumn != -1) {
                queryResult.getString(
                    mimeTypeColumn
                )
            } else {
                application.contentResolver.getType(file) ?: "*/*"
            }

            val name =
                queryResult.getString(
                    queryResult.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                )

            val size =
                queryResult.getLong(queryResult.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))

            val creationDateColumn = queryResult.getColumnIndex(MediaColumns.DATE_ADDED)
            var creationDate = 0L
            if (creationDateColumn != -1) {
                creationDate = queryResult.getLong(creationDateColumn)
            }

            return FileMetadata.newBuilder()
                .setName(name)
                .setLastModified(lastModified)
                .setMimeType(mimeType)
                .setSize(size)
                .setCreationDate(creationDate)
                .build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun createThumbnail(file: Uri, outVault: Vault): EncryptedFile? {
        try {
            val thumbnail = application.contentResolver.loadThumbnail(
                file,
                Size(256, 256),
                null
            )
            ByteArrayOutputStream().use { bos ->
                thumbnail.compress(Bitmap.CompressFormat.PNG, 100, bos)
                thumbnail.recycle()
                ByteArrayInputStream(bos.toByteArray()).use { bis ->
                    return Crypto.Encrypt.inputStream(
                        bis,
                        Clock.System.now(),
                        outVault
                    )
                }
            }
        } catch (e: Exception) {
            Log.i(
                "FileImport.ThumbnailCreation",
                "Failed to create thumbnail, probably unsupported type"
            )
        }

        return null
    }

    private fun moveFile(
        fileInputStream: InputStream,
        metadata: FileMetadata?,
        thumbnail: EncryptedFile?,
        outVault: Vault,
        outPath: String
    ) {
        if (metadata == null) {
            uiState.update {
                it.copy(fileAmount = it.fileAmount!! - 1)
            }
            return
        }

        val encryptedFile = Crypto.Encrypt.inputStream(
            fileInputStream,
            Instant.fromEpochMilliseconds(metadata.lastModified),
            outVault
        )

        outVault.fileTree!!.goTo(outPath)!!.children.add(
            FileNode(
                encryptedFile,
                thumbnail,
                name = metadata.name,
                type = metadata.mimeType ?: "*/*",
                size = metadata.size,
                creationDate = Instant.fromEpochMilliseconds(metadata.creationDate)
            )
        )

        uiState.update {
            it.copy(fileProcessed = if (it.fileProcessed == null) null else it.fileProcessed + 1)
        }
    }
}

@Composable
fun ExplorerActivityNav(activity: ExplorerActivity, viewModel: ExplorerViewModel) {
    val navController = rememberNavController()
    val vault by viewModel.vault.collectAsState()

    AppTheme {
        NavHost(navController, startDestination = "explorer/{fileId}") {
            // path should be URL encoded
            composable(
                "explorer/{fileId}", arguments = listOf(navArgument(
                    "fileId"
                ) { type = NavType.StringType })
            ) { entry ->
                val rawFileId = entry.arguments?.getString("fileId")
                val fileId =
                    rawFileId ?: vault!!.fileTree?.id

                Log.d("Explorer", "File ID = $fileId")

                Explorer(
                    navController,
                    baseFileId = fileId,
                    activity = activity,
                    explorerViewModel = viewModel
                )
            }

            composable(
                "reader/{fileId}",
                arguments = listOf(
                    navArgument("fileId") { type = NavType.StringType }
                )
            ) { entry ->
                val fileID = entry.arguments?.getString("fileId")!!
                val dir = vault!!.fileTree!!.goToParentOf(fileID)!!
                val readerFiles by viewModel.readerFiles.collectAsState()
                val files = readerFiles.map { curId ->
                    dir.children.first { it.id == curId }
                }

                FileReader(
                    files = files,
                    currentFileId = fileID,
                    activity = activity,
                    explorerViewModel = viewModel,
                    navController = navController
                )
            }

            composable(
                "import-lcef/{location}",
                arguments = listOf(
                    navArgument("location") {
                        type = NavType.StringType
                    },
                )
            ) { entry ->
                val location =
                    entry.arguments?.getString("location") ?: vault!!.fileTree!!.id

                val files by viewModel.lcefUrisTransactionState.collectAsState()

                LcefUnlockPage(
                    navController = navController,
                    files = files ?: listOf(),
                    location = location,
                    explorerViewModel = viewModel
                )
            }
        }
    }
}