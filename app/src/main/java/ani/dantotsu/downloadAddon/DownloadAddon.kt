package ani.dantotsu.downloadAddon

import android.content.Context
import android.net.Uri
import android.util.Log
import ani.dantotsu.addons.download.DownloadAddonApiV2
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.SessionState

class DownloadAddon : DownloadAddonApiV2 {
    override fun cancelDownload(sessionId: Long) {
        FFmpegKit.cancel(sessionId)
    }

    override fun setDownloadPath(context: Context, uri: Uri): String {
        return FFmpegKitConfig.getSafParameterForWrite(
            context,
            uri
        )
    }

    override fun getReadPath(context: Context, uri: Uri): String {
        return FFmpegKitConfig.getSafParameter(
            context,
            uri,
            "r"
        )
    }

    override suspend fun executeFFProbe(
        videoUrl: String,
        headers: Map<String, String>,
        logCallback: (String) -> Unit
    ) {
        val headersStr = buildHeadersString(headers)
        val request = "${headersStr}-i $videoUrl -show_entries format=duration -v quiet -of csv=\"p=0\""
        FFprobeKit.executeAsync(
            request,
            {
                Log.d("FFmpegKit", it.allLogsAsString)
            }, {
                if (it.message.toDoubleOrNull() != null) {
                    logCallback(it.message)
                }
            })
    }

    override suspend fun executeFFMpeg(
        videoUrl: String,
        downloadPath: String,
        headers: Map<String, String>,
        subtitleUrls: List<Pair<String, String>>,
        audioUrls: List<Pair<String, String>>,
        statCallback: (Double) -> Unit
    ): Long {
        val headersStr = buildHeadersString(headers)

        val command = StringBuilder()
        // Input: video with headers
        command.append("${headersStr}-i \"$videoUrl\" ")

        // Input: subtitle streams
        for (sub in subtitleUrls) {
            command.append("${headersStr}-i \"${sub.first}\" ")
        }

        // Input: audio streams
        for (audio in audioUrls) {
            command.append("${headersStr}-i \"${audio.first}\" ")
        }

        // Map all inputs
        val totalInputs = 1 + subtitleUrls.size + audioUrls.size
        if (totalInputs > 1) {
            for (i in 0 until totalInputs) {
                command.append("-map $i ")
            }
        }

        // Codec settings
        command.append("-c copy ")

        // Subtitle codec (if subtitles present)
        if (subtitleUrls.isNotEmpty()) {
            command.append("-c:s srt ")
        }

        // Metadata for subtitle streams
        for ((index, sub) in subtitleUrls.withIndex()) {
            val streamIndex = index // subtitle stream index
            command.append("-metadata:s:s:$streamIndex language=\"${sub.second}\" ")
        }

        // Metadata for audio streams
        for ((index, audio) in audioUrls.withIndex()) {
            val streamIndex = index + 1 // skip original audio at index 0
            command.append("-metadata:s:a:$streamIndex language=\"${audio.second}\" ")
        }

        command.append("$downloadPath ")

        Log.d("FFmpegKit", "Command: $command")
        val exec = FFmpegKit.executeAsync(command.toString(),
            { session ->
                val state: SessionState = session.state
                val returnCode = session.returnCode
                Log.d("FFmpegKit",
                    java.lang.String.format(
                        "FFmpeg process exited with state %s and rc %s.%s",
                        state,
                        returnCode,
                        session.failStackTrace
                    )
                )

            }, {
                // CALLED WHEN SESSION PRINTS LOGS
                Log.d("FFmpegKit", it.message)
            }) {
            statCallback(it.time)
            Log.d("FFmpegKit", "Statistics: $it")
        }
        return exec.sessionId
    }

    override suspend fun customFFMpeg(
        command: String,
        videoUrls: List<String>,
        logCallback: (String) -> Unit
    ): Long {
        // command "1" = reconstruct container (copy streams to new file)
        val actualCommand = if (command == "1" && videoUrls.size >= 2) {
            "-i ${videoUrls[0]} -c copy ${videoUrls[1]}"
        } else {
            var cmd = command
            videoUrls.forEachIndexed { index, url ->
                cmd = cmd.replace("{$index}", url)
            }
            cmd
        }

        val exec = FFmpegKit.executeAsync(actualCommand,
            { session ->
                Log.d("FFmpegKit", "Custom FFmpeg exited: ${session.state} rc=${session.returnCode}")
            }, {
                logCallback(it.message)
            }) {
            Log.d("FFmpegKit", "Statistics: $it")
        }
        return exec.sessionId
    }

    override suspend fun customFFProbe(
        command: String,
        videoUrls: List<String>,
        logCallback: (String) -> Unit
    ) {
        var cmd = command
        videoUrls.forEachIndexed { index, url ->
            cmd = cmd.replace("{$index}", url)
        }
        FFprobeKit.executeAsync(cmd,
            {
                Log.d("FFmpegKit", it.allLogsAsString)
            }, {
                logCallback(it.message)
            })
    }

    override fun getState(sessionId: Long): String {
        FFmpegKitConfig.getFFmpegSessions().forEach {
            if (it.sessionId == sessionId) {
                return when (it.state) {
                    SessionState.COMPLETED -> "COMPLETED"
                    SessionState.FAILED -> "FAILED"
                    SessionState.RUNNING -> "RUNNING"
                    else -> "UNKNOWN"
                }
            }
        }
        return "UNKNOWN"
    }

    override fun getStackTrace(sessionId: Long): String? {
        FFmpegKitConfig.getFFmpegSessions().forEach {
            if (it.sessionId == sessionId) {
                return it.failStackTrace
            }
        }
        return null
    }

    override fun hadError(sessionId: Long): Boolean {
        FFmpegKitConfig.getFFmpegSessions().forEach {
            if (it.sessionId == sessionId) {
                return it.returnCode.isValueError
            }
        }
        return false
    }


    private fun buildHeadersString(headers: Map<String, String>): String {
        if (headers.isEmpty()) return ""
        val sb = StringBuilder("-headers \"")
        for ((key, value) in headers) {
            sb.append("$key: $value\r\n")
        }
        sb.append("\" ")
        return sb.toString()
    }
}