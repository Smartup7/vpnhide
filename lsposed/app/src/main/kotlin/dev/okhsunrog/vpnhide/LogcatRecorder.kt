package dev.okhsunrog.vpnhide

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records an unfiltered `logcat -b all` stream to a file, driven by a
 * single start/stop lifecycle. Used by the Diagnostics screen to capture
 * everything happening during an issue reproduction — our own VpnHide
 * tags, system_server, radio, crashes, anything.
 *
 * Requires root (READ_LOGS is not granted to third-party apps); we spawn
 * `logcat` under `su` so the subprocess inherits CAP_SYSLOG.
 *
 * Only one recording may be active at a time. The process is intentionally
 * spawned via `exec logcat ...` so `Process.destroy()` kills logcat, not
 * just the wrapping shell.
 */
internal object LogcatRecorder {
    private const val TAG = "VpnHide-Logcat"

    sealed interface State {
        data class Stopped(
            val lastFile: File?,
        ) : State

        data class Recording(
            val file: File,
            val startMs: Long,
            val sizeBytes: Long,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Stopped(null))
    val state: StateFlow<State> = _state

    private var process: Process? = null
    private var scope: CoroutineScope? = null

    /**
     * Start recording. No-op if already recording.
     *
     * Output is a plain-text logcat capture at `-v threadtime` format,
     * all buffers (`main system crash events radio`), written to a file
     * in the app's cache directory. Returns the target file immediately;
     * the process runs in the background until [stop] is called.
     */
    fun start(context: Context): File? {
        if (_state.value is State.Recording) return (_state.value as State.Recording).file

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "vpnhide_logcat_$ts.log")
        try {
            file.createNewFile()
        } catch (t: Throwable) {
            Log.w(TAG, "failed to create output file: ${t.message}")
            return null
        }

        val proc =
            try {
                // `exec logcat` ensures the PID we hold is the logcat
                // process itself — su destroy() would otherwise kill
                // only the shell wrapper.
                ProcessBuilder("su", "-c", "exec logcat -b all -v threadtime")
                    .redirectErrorStream(true)
                    .start()
            } catch (t: Throwable) {
                Log.w(TAG, "failed to spawn logcat via su: ${t.message}")
                return null
            }

        process = proc
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        val startMs = System.currentTimeMillis()
        _state.value = State.Recording(file, startMs, 0L)

        // Pipe stdout → file
        newScope.launch {
            try {
                proc.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(8 * 1024)
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n > 0) out.write(buf, 0, n)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "pipe error: ${t.message}")
            }
        }

        // Periodic size refresh so the UI shows growing recording size
        newScope.launch {
            while (isActive) {
                delay(500)
                val current = _state.value
                if (current is State.Recording) {
                    _state.value = current.copy(sizeBytes = file.length())
                }
            }
        }

        return file
    }

    /**
     * Stop recording. Returns the captured file, or null if nothing was
     * recording. Safe to call multiple times.
     */
    suspend fun stop(): File? {
        val current = _state.value as? State.Recording ?: return null

        process?.destroy()
        process = null
        scope?.cancel()
        scope = null

        // Give the pipe a moment to flush, then publish final state.
        withContext(Dispatchers.IO) {
            // small grace period for the piping coroutine to finish writing
            delay(120)
        }
        _state.value = State.Stopped(current.file)
        return current.file
    }
}
