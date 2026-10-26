package com.macrotap.app

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var audio: AudioCapture? = null
    private var videoFile: File? = null
    private var audioFile: File? = null
    private var recording = false
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toast(s: String) {
        main.post { Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            if (recording) stopRecording() else stopSelf()
            return START_NOT_STICKY
        }
        if (recording) return START_NOT_STICKY
        startAsForeground()
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            begin(code, data)
        } catch (e: Exception) {
            toast("Could not start recording: ${e.message}")
            recording = false
            try { projection?.stop() } catch (x: Exception) {}
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("macrotap", "MacroTap", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, RecorderService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "macrotap")
            .setContentTitle("MacroTap is recording the screen")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop", stopPi
                ).build()
            )
            .build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(code, data)
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (recording) stopRecording()
            }
        }, main)

        val dm = resources.displayMetrics
        val scale = minOf(1f, 720f / minOf(dm.widthPixels, dm.heightPixels))
        val w = ((dm.widthPixels * scale).toInt() / 2) * 2
        val h = ((dm.heightPixels * scale).toInt() / 2) * 2

        val vf = File(cacheDir, "v.mp4")
        val af = File(cacheDir, "a.m4a")
        vf.delete(); af.delete()
        videoFile = vf
        audioFile = af

        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(w, h)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(4_000_000)
        r.setOutputFile(vf.path)
        r.prepare()
        display = proj.createVirtualDisplay(
            "MacroTap", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
        r.start()
        recorder = r

        try {
            val a = AudioCapture(proj, af)
            a.start()
            audio = a
        } catch (e: Exception) {
            audio = null
            toast("Recording without audio: ${e.message}")
        }
        recording = true
    }

    private fun stopRecording() {
        recording = false
        try { recorder?.stop() } catch (e: Exception) {}
        recorder?.release()
        recorder = null
        audio?.stop()
        display?.release()
        display = null
        try { projection?.stop() } catch (e: Exception) {}
        projection = null
        val hadAudio = audio != null
        audio = null
        Thread {
            try {
                val v = videoFile!!
                val a = audioFile!!
                var finalFile = v
                if (hadAudio && a.exists() && a.length() > 0) {
                    try {
                        val m = File(cacheDir, "m.mp4")
                        m.delete()
                        merge(v, a, m)
                        finalFile = m
                    } catch (e: Exception) {
                        toast("Could not add audio, saving video only")
                    }
                }
                saveToGallery(finalFile)
                toast("Saved to Movies/MacroTap")
            } catch (e: Exception) {
                toast("Save failed: ${e.message}")
            }
            main.post { stopSelf() }
        }.start()
    }

    private fun merge(video: File, audioF: File, out: File) {
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEx = MediaExtractor()
        vEx.setDataSource(video.path)
        val aEx = MediaExtractor()
        aEx.setDataSource(audioF.path)
        fun pick(ex: MediaExtractor, prefix: String): Int {
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith(prefix)) return i
            }
            return -1
        }
        val vi = pick(vEx, "video/")
        val ai = pick(aEx, "audio/")
        if (vi < 0 || ai < 0) throw Exception("missing track")
        val vt = muxer.addTrack(vEx.getTrackFormat(vi))
        val at = muxer.addTrack(aEx.getTrackFormat(ai))
        vEx.selectTrack(vi)
        aEx.selectTrack(ai)
        muxer.start()
        fun copy(ex: MediaExtractor, t: Int) {
            val buf = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                info.set(0, n, ex.sampleTime, ex.sampleFlags)
                muxer.writeSampleData(t, buf, info)
                ex.advance()
            }
        }
        copy(vEx, vt)
        copy(aEx, at)
        muxer.stop()
        muxer.release()
        vEx.release()
        aEx.release()
    }

    private fun saveToGallery(file: File) {
        val name = "MacroTap_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val cv = ContentValues()
        cv.put(MediaStore.Video.Media.DISPLAY_NAME, name)
        cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MacroTap")
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)!!
        contentResolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }
}
