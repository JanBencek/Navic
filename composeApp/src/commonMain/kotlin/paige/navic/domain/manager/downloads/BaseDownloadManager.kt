package paige.navic.domain.manager.downloads

import paige.navic.domain.models.DomainSong

interface BaseDownloadManager {
	suspend fun downloadAudio(
		song: DomainSong,
		url: String,
		extension: String,
		headers: Map<String, String>,
		onProgress: suspend (Float) -> Unit
	): String
}
