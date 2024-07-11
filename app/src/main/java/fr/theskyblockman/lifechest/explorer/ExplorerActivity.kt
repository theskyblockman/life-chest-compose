package fr.theskyblockman.lifechest.explorer

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
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.NotificationManagerCompat
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.theskyblockman.lifechest.R
import fr.theskyblockman.lifechest.transactions.LcefManager
import fr.theskyblockman.lifechest.transactions.LcefOuterClass.FileMetadata
import fr.theskyblockman.lifechest.ui.theme.AppTheme
import fr.theskyblockman.lifechest.vault.Crypto
import fr.theskyblockman.lifechest.vault.DirectoryNode
import fr.theskyblockman.lifechest.vault.EncryptedFile
import fr.theskyblockman.lifechest.vault.FileNode
import fr.theskyblockman.lifechest.vault.TreeNode
import fr.theskyblockman.lifechest.vault.Vault
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
import java.io.IOException
import java.io.InputStream
import javax.crypto.spec.SecretKeySpec

class ExplorerActivity : FragmentActivity() {
    companion object {
        lateinit var vault: Vault
        val fileListTransactions: MutableMap<String, List<String>> = mutableMapOf()
        val lcefUrisTransactions: MutableMap<String, List<Uri>> = mutableMapOf()
        var bypassChestClosure = false
    }

    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private var importResultCompleter: CompletableDeferred<List<Uri>>? = null
    var currentNodeToExport: TreeNode? = null
    lateinit var exporter: ActivityResultLauncher<String>

    @Suppress("SameParameterValue")
    private fun createNotificationChannel(channelId: String) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system.
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

        vault = Json.decodeFromString(passedData.getString("vault")!!)

        vault.key =
            SecretKeySpec(passedData.getByteArray("key")!!, Crypto.DEFAULT_CIPHER_NAME)

        vault.loadFileTree()

        val contract = ActivityResultContracts.OpenMultipleDocuments()
        launcher = registerForActivityResult(contract) {
            importResultCompleter?.complete(it)
        }

        val filter = IntentFilter()
        filter.addAction("fr.theskyblockman.close_chest")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }

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
                                    vault,
                                    currentNodeToExport as FileNode
                                )
                            )
                            inputStream.copyTo(outputStream)
                        }
                    }
            }

        val toImport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            passedData.getParcelable("to-import", Uri::class.java)?.toString()
        } else {
            @Suppress("DEPRECATION")
            passedData.getParcelable<Uri?>("to-import")?.toString()
        }

        enableEdgeToEdge()
        setContent {
            ExplorerActivityNav(this, toImport)
        }
    }

    private val broadcastReceiver = CloseVault()

    private inner class CloseVault : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            NotificationManagerCompat.from(this@ExplorerActivity).cancel(0)
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

        if (!bypassChestClosure && !isFinishing) {
            vault.onSetBackground(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!bypassChestClosure) {
            vault.onBroughtBack(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationManagerCompat.from(this).cancel(0)
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
        onEnd: (newFileTree: DirectoryNode) -> Unit = {}
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
            val lastModified =
                queryResult.getLongOrNull(queryResult.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                    ?: System.currentTimeMillis()
            val mimeType =
                queryResult.getStringOrNull(
                    queryResult.getColumnIndex(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                )
            val name =
                queryResult.getString(
                    queryResult.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                )
            val size =
                queryResult.getLong(queryResult.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))

            val creationDate =
                queryResult.getLongOrNull(queryResult.getColumnIndex(MediaColumns.DATE_ADDED)) ?: 0L

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
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                thumbnail.recycle()
                ByteArrayInputStream(bos.toByteArray()).use { bis ->
                    return Crypto.Encrypt.inputStream(
                        bis,
                        Clock.System.now(),
                        outVault
                    )
                }
            }
        } catch (e: IOException) {
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
fun ExplorerActivityNav(activity: ExplorerActivity, defaultToImport: String?) {
    val navController = rememberNavController()
    var toImportUpdated: String? = remember {
        defaultToImport
    }

    AppTheme {
        NavHost(navController, startDestination = "explorer/{fileId}?toImport={toImport}") {
            // path should be URL encoded
            composable(
                "explorer/{fileId}?toImport={toImport}", arguments = listOf(navArgument(
                    "fileId"
                ) { type = NavType.StringType },
                    navArgument(
                        "toImport"
                    ) {
                        nullable = true
                        type = NavType.StringType
                        defaultValue = null
                    })
            ) { entry ->
                val rawFileId = entry.arguments?.getString("fileId")
                val fileId =
                    rawFileId ?: ExplorerActivity.vault.fileTree!!.id

                var rawToImport = entry.arguments?.getString("toImport")

                if (rawFileId == null) {
                    rawToImport = toImportUpdated
                    toImportUpdated = null
                }

                val toImport =
                    if (rawToImport == null) null else Uri.parse(rawToImport)

                Explorer(
                    navController,
                    currentFileId = fileId,
                    activity = activity,
                    toImport = toImport
                )
            }

            composable(
                "reader/{fileId}?transactionId={transactionId}",
                arguments = listOf(
                    navArgument("fileId") { type = NavType.StringType },
                    navArgument("transactionId") {
                        type = NavType.StringType
                        defaultValue = null
                        nullable = true
                    },
                )
            ) { entry ->
                val fileID = entry.arguments?.getString("fileId")!!
                val dir = ExplorerActivity.vault.fileTree!!.goToParentOf(fileID)!!
                val transactionId = entry.arguments?.getString("transactionId")

                val files: List<TreeNode> = if (transactionId != null) {
                    ExplorerActivity.fileListTransactions[transactionId]!!.map { curId ->
                        dir.children.first { it.id == curId }
                    }
                } else {
                    dir.children
                }

                FileReader(
                    files = files,
                    currentFileId = fileID,
                    activity = activity,
                    navController
                )
            }

            composable(
                "import-lcef/{location}/{transactionId}",
                arguments = listOf(
                    navArgument("location") {
                        type = NavType.StringType
                    },
                    navArgument("transactionId") {
                        type = NavType.StringType
                    },
                )
            ) { entry ->
                val location =
                    entry.arguments?.getString("location") ?: ExplorerActivity.vault.fileTree!!.id
                val transactionId = entry.arguments?.getString("transactionId")

                val files = ExplorerActivity.lcefUrisTransactions[transactionId] ?: emptyList()

                LcefUnlockPage(
                    navController = navController,
                    files = files,
                    location = location,
                    transactionId = transactionId
                )
            }
        }
    }
}