package paige.navic.domain.manager

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.domain.parser.LogLine
import paige.navic.domain.parser.LogLineParser
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.time.Duration.Companion.milliseconds

private const val LOG_SIZE = 500
private const val BATCH_DELAY_MS = 100L

actual class LogManager {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var readerJob: Job? = null

	private val _logs = mutableStateListOf<LogLine>()
	actual val logs: List<LogLine> = _logs

	actual fun startStreaming() {
		stopStreaming()
		_logs.clear()
		readerJob = scope.launch {
			var process: Process? = null
			var reader: BufferedReader? = null
			try {
				process = Runtime.getRuntime().exec(arrayOf("logcat", "--format=tag"))
				reader = InputStreamReader(process.inputStream).buffered()

				val batch = mutableListOf<LogLine>()
				var lastEmitTime = System.currentTimeMillis()

				while (isActive) {
					if (reader.ready()) {
						val nextLine = reader.readLine() ?: break
						batch.add(LogLineParser.parseString(nextLine))
					} else {
						delay(10.milliseconds)
					}

					val currentTime = System.currentTimeMillis()
					if (batch.isNotEmpty() && (batch.size >= 50 || currentTime - lastEmitTime >= BATCH_DELAY_MS)) {
						val itemsToEmit = batch.toList()
						batch.clear()
						withContext(Dispatchers.Main) {
							updateLogs(itemsToEmit)
						}
						lastEmitTime = currentTime
					}
				}
			} catch (e: Exception) {
				e.printStackTrace()
			} finally {
				withContext(NonCancellable) {
					reader?.close()
					process?.destroy()
				}
			}
		}
	}

	private fun updateLogs(newLines: List<LogLine>) {
		if (newLines.isEmpty()) return
		Snapshot.withMutableSnapshot {
			val itemsToAdd = if (newLines.size > LOG_SIZE) newLines.takeLast(LOG_SIZE) else newLines
			val toRemove = (_logs.size + itemsToAdd.size - LOG_SIZE).coerceAtLeast(0)
			if (toRemove > 0) {
				val countToRemove = toRemove.coerceAtMost(_logs.size)
				repeat(countToRemove) {
					_logs.removeAt(0)
				}
			}
			_logs.addAll(itemsToAdd)
		}
	}

	actual fun stopStreaming() {
		readerJob?.cancel()
		readerJob = null
	}

	actual fun clearLogs() {
		scope.launch(Dispatchers.IO) {
			try {
				Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
			} catch (e: Exception) {
				e.printStackTrace()
			}
			withContext(Dispatchers.Main) {
				_logs.clear()
			}
		}
	}
}
