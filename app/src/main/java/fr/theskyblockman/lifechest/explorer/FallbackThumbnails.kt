package fr.theskyblockman.lifechest.explorer

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import fr.theskyblockman.lifechest.R

@OptIn(UnstableApi::class)
@Composable
fun thumbnailFromMimeType(mimeType: String): Painter {
    return when {
        mimeType == "directory" -> painterResource(R.drawable.outline_folder_24)
        MimeTypes.isImage(mimeType) -> return painterResource(R.drawable.outline_image_24)
        MimeTypes.isVideo(mimeType) -> return painterResource(R.drawable.outline_movie_24)
        MimeTypes.isAudio(mimeType) -> return painterResource(R.drawable.outline_audio_file_24)
        MimeTypes.isText(mimeType) -> return painterResource(R.drawable.outline_description_24)
        else -> {
            when (mimeType.split("/").last()) {
                "pdf" -> return painterResource(R.drawable.outline_description_24)
                "gzip",
                "zip",
                "x-7z-compressed",
                "vnd.rar",
                "application/vnd.android.package-archive",
                "x-bzip",
                "x-bzip2" -> return painterResource(R.drawable.outline_folder_zip_24)

                else -> return painterResource(R.drawable.outline_question_mark_24)
            }
        }
    }
}