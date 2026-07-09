package paige.navic.domain.manager

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import paige.navic.domain.models.DomainSong
import java.io.File
import androidx.core.net.toUri
import paige.navic.domain.manager.downloads.BaseDownloadManager
import kotlin.time.Duration.Companion.milliseconds

class AndroidBaseDownloadManager(
	private val context: Context,
	private val storageManager: StorageManager
) : BaseDownloadManager {
	private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

	override suspend fun downloadAudio(
		song: DomainSong,
		url: String,
		extension: String,
		headers: Map<String, String>,
		onProgress: suspend (Float) -> Unit
	): String {
		val request = DownloadManager.Request(url.toUri())
			.setTitle(song.title)
			.setDescription(song.artistName)
			.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
			.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, "${song.id}.$extension")

		headers.forEach { (key, value) ->
			request.addRequestHeader(key, value)
		}

		val downloadId = downloadManager.enqueue(request)

		var downloading = true
		var finalPath = ""

		while (downloading) {
			val query = DownloadManager.Query().setFilterById(downloadId)
			val cursor: Cursor = downloadManager.query(query)

			if (cursor.moveToFirst()) {
				val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
				val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
				val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

				if (bytesTotal > 0) {
					onProgress(bytesDownloaded.toFloat() / bytesTotal.toFloat())
				}

				when (status) {
					DownloadManager.STATUS_SUCCESSFUL -> {
						val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
						val sourceFile = File(Uri.parse(localUri).path!!)
						
						finalPath = storageManager.getDownloadPath(song.id, extension)
						val destinationFile = File(finalPath)
						
						withContext(Dispatchers.IO) {
							sourceFile.copyTo(destinationFile, overwrite = true)
							sourceFile.delete()
						}
						
						downloading = false
					}
					DownloadManager.STATUS_FAILED -> {
						downloading = false
						throw Exception("Download failed with status: $status")
					}
				}
			}
			cursor.close()
			if (downloading) {
				delay(500.milliseconds)
			}
		}

		return finalPath
	}
}
