package com.macrotap.app

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import java.io.File

class AudioCapture(private val projection: MediaProjection, private val outFile: File) {
    private val rate = 44100
    @Volatile private var running = false
    private var thread: Thread? = null
    private lateinit var codec: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var track = -1
    private var started = false

    @SuppressLint("MissingPermission")
    fun start() {
        val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder()
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(maxOf(min * 2, 16384))
            .setAudioPlaybackCaptureConfig(cfg)
            .build()
        val mf = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2)
        mf.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        mf.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        mf.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(mf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        muxer = MediaMuxer(outFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        running = true
        thread = Thread { loop(rec) }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(5000)
    }

    private fun loop(rec: AudioRecord) {
        val buf = ByteArray(4096)
        var total = 0L
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    var idx = codec.dequeueInputBuffer(10000)
                    var tries = 0
                    while (idx < 0 && tries++ < 100) {
                        drain(false)
                        idx = codec.dequeueInputBuffer(10000)
                    }
                    if (idx >= 0) {
                        val ib = codec.getInputBuffer(idx)!!
                        ib.clear()
                        ib.put(buf, 0, n)
                        codec.queueInputBuffer(idx, 0, n, total * 1_000_000L / (rate * 4), 0)
                        total += n
                    }
                    drain(false)
                }
            }
            val idx = codec.dequeueInputBuffer(10000)
            if (idx >= 0) {
                codec.queueInputBuffer(
                    idx, 0, 0, total * 1_000_000L / (rate * 4), MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drain(true)
        } catch (e: Exception) {
        } finally {
            try { rec.stop() } catch (e: Exception) {}
            rec.release()
            try { codec.stop() } catch (e: Exception) {}
            codec.release()
            try { if (started) muxer.stop() } catch (e: Exception) {}
            muxer.release()
        }
    }

    private fun drain(waitEos: Boolean) {
        val info = MediaCodec.BufferInfo()
        var tries = 0
        while (true) {
            val oi = codec.dequeueOutputBuffer(info, if (waitEos) 10000L else 0L)
            if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
                started = true
            } else if (oi >= 0) {
                val ob = codec.getOutputBuffer(oi)!!
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                if (info.size > 0 && started) {
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    muxer.writeSampleData(track, ob, info)
                }
                codec.releaseOutputBuffer(oi, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
            } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!waitEos) return
                if (++tries > 100) return
            }
        }
    }
}
