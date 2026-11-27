package com.bidyoke

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Offline polyphonic MIDI renderer for Bidyoke's bundled KAR songs.
 * It uses a lightweight built-in tone synthesizer, so no network or
 * separately installed SoundFont is needed for these demo tracks.
 */
class MidiKaraokePlayer(
    private val onState: (Boolean, String) -> Unit
) {
    @Volatile private var running = false
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var worker: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun play(file: File) {
        stop()
        running = true
        worker = Thread({
            var track: AudioTrack? = null
            try {
                val events = KarMidiParser.parse(file.readBytes())
                val rate = 44100
                val minBuffer = AudioTrack.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                track.play()

                data class Voice(val hz: Double, val volume: Double, var phase: Double = 0.0)
                val voices = LinkedHashMap<Int, Voice>()
                val buffer = ShortArray(512)
                var eventIndex = 0
                var frame = 0L
                val endFrame = ((events.lastOrNull()?.timeUs ?: 0L) + 1_000_000L) * rate / 1_000_000L

                while (running && frame < endFrame) {
                    val elapsedUs = frame * 1_000_000L / rate
                    while (eventIndex < events.size && events[eventIndex].timeUs <= elapsedUs) {
                        val event = events[eventIndex++]
                        when (event.kind) {
                            1 -> {
                                if (event.velocity > 0) {
                                    val hz = 440.0 * Math.pow(2.0, (event.note - 69) / 12.0)
                                    voices[event.note] = Voice(hz, event.velocity / 127.0 * 0.22)
                                } else voices.remove(event.note)
                            }
                            2 -> voices.remove(event.note)
                            3 -> mainHandler.post { if (running) onState(true, event.text) }
                        }
                    }

                    val count = minOf(buffer.size.toLong(), endFrame - frame).toInt()
                    for (i in 0 until count) {
                        var sample = 0.0
                        for (voice in voices.values) {
                            sample += sin(voice.phase) * voice.volume
                            voice.phase += 2.0 * PI * voice.hz / rate
                            if (voice.phase >= 2.0 * PI) voice.phase -= 2.0 * PI
                        }
                        buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                    }
                    if (count > 0) track.write(buffer, 0, count)
                    frame += count
                }
                if (running) track.stop()
            } catch (_: Exception) {
                // The activity returns to standby if a media file is invalid.
            } finally {
                try { track?.release() } catch (_: Exception) { }
                audioTrack = null
                running = false
                mainHandler.post { onState(false, "") }
            }
        }, "Bidyoke-MIDI").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { audioTrack?.pause(); audioTrack?.flush() } catch (_: Exception) { }
        worker?.interrupt()
        worker = null
    }

    fun release() = stop()
}

private data class KarEvent(
    val tick: Long,
    val kind: Int,
    val note: Int = 0,
    val velocity: Int = 0,
    val text: String = "",
    val tempo: Int = 500000,
    var timeUs: Long = 0L
)

private object KarMidiParser {
    fun parse(data: ByteArray): List<KarEvent> {
        var pos = 0
        fun byte(): Int {
            require(pos < data.size) { "Unexpected end of MIDI file" }
            return data[pos++].toInt() and 0xff
        }
        fun int32(): Int = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()
        fun text(n: Int): String = buildString { repeat(n) { append(byte().toChar()) } }
        fun variableLength(): Long {
            var value = 0L
            var current: Int
            do {
                current = byte()
                value = (value shl 7) or (current and 0x7f).toLong()
            } while ((current and 0x80) != 0)
            return value
        }

        require(text(4) == "MThd") { "Not a Standard MIDI/KAR file" }
        val headerLength = int32()
        byte(); byte() // format
        val trackCount = (byte() shl 8) or byte()
        val division = (byte() shl 8) or byte()
        if (headerLength > 6) pos += headerLength - 6
        require(division > 0 && (division and 0x8000) == 0) { "Unsupported MIDI time division" }

        val result = mutableListOf<KarEvent>()
        repeat(trackCount) {
            if (pos + 8 > data.size) return@repeat
            val chunkType = text(4)
            val chunkLength = int32()
            val end = (pos + chunkLength).coerceAtMost(data.size)
            if (chunkType != "MTrk") {
                pos = end
                return@repeat
            }
            var tick = 0L
            var runningStatus = 0
            while (pos < end) {
                tick += variableLength()
                var status = byte()
                if (status < 0x80) {
                    pos--
                    status = runningStatus
                } else if (status < 0xf0) {
                    runningStatus = status
                }

                when (status) {
                    0xff -> {
                        val type = byte()
                        val size = variableLength().toInt().coerceAtMost(end - pos)
                        val payload = data.copyOfRange(pos, pos + size)
                        pos += size
                        when (type) {
                            0x51 -> if (payload.size == 3) {
                                val tempo = ((payload[0].toInt() and 0xff) shl 16) or
                                    ((payload[1].toInt() and 0xff) shl 8) or
                                    (payload[2].toInt() and 0xff)
                                result += KarEvent(tick, 0, tempo = tempo)
                            }
                            0x05, 0x01 -> {
                                val lyric = payload.toString(Charsets.UTF_8)
                                    .replace("\\", "").replace("/", " ").replace("@", "").trim()
                                if (lyric.isNotEmpty()) result += KarEvent(tick, 3, text = lyric)
                            }
                        }
                        if (type == 0x2f) break
                    }
                    0xf0, 0xf7 -> {
                        val size = variableLength().toInt()
                        pos = (pos + size).coerceAtMost(end)
                    }
                    else -> {
                        val command = status and 0xf0
                        val note = byte()
                        val velocity = if (command == 0xc0 || command == 0xd0) 0 else byte()
                        when (command) {
                            0x90 -> result += KarEvent(tick, if (velocity == 0) 2 else 1, note, velocity)
                            0x80 -> result += KarEvent(tick, 2, note)
                        }
                    }
                }
            }
            pos = end
        }

        val sorted = result.sortedWith(compareBy<KarEvent> { it.tick }.thenBy { if (it.kind == 0) 0 else 1 })
        var previousTick = 0L
        var elapsedMicros = 0L
        var tempo = 500000
        for (event in sorted) {
            elapsedMicros += (event.tick - previousTick) * tempo / division
            event.timeUs = elapsedMicros
            previousTick = event.tick
            if (event.kind == 0) tempo = event.tempo
        }
        return sorted.filter { it.kind != 0 }
    }
}
