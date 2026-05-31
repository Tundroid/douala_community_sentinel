package com.moleculesoft.dcs.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import kotlin.math.log10

class NoiseRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun start(cacheDir: File) {
        if (recorder != null) return

        val outFile = File(cacheDir, "noise_temp.3gp")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outFile.absolutePath)
            try {
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun getAmplitude(): Double {
        return recorder?.maxAmplitude?.toDouble() ?: 0.0
    }

    fun getDb(): Double {
        val amp = getAmplitude()
        return if (amp > 0) 20 * log10(amp / 2700.0) else 0.0
    }

    fun stop() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
    }
}
