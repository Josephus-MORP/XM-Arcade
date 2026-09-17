package com.xmarcade.app.core

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/** Shorts video pipeline: probe → skip-if-small → transcode → thumbnail.
 *  Parameters mirror Amethyst's LightCompressor setup (MEDIUM ×0.3 bitrate,
 *  auto-resize ladder, CBR, 1s I-frames, min-bitrate skip) and nostube's
 *  1080p / 30fps / AAC-96k upload recipes. Hardware codecs throughout;
 *  anything unexpected throws so the caller uploads the original file. */
object VideoPipe {
  data class VidInfo(
    val w: Int, val h: Int, val rotation: Int, val bitrate: Int,
    val vcodec: String, val acodec: String, val hasAudio: Boolean,
    val durationMs: Long, val fps: Int,
  )

  fun probe(f: File): VidInfo? {
    val r = MediaMetadataRetriever()
    return try {
      r.setDataSource(f.absolutePath)
      fun meta(k: Int): String = r.extractMetadata(k) ?: ""
      val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toIntOrNull() ?: 0
      val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toIntOrNull() ?: 0
      if (w <= 0 || h <= 0) return null
      var vcodec = ""; var acodec = ""; var fps = 0
      val ex = MediaExtractor()
      try {
        ex.setDataSource(f.absolutePath)
        for (i in 0 until ex.trackCount) {
          val mime = try { ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) } catch (_: Exception) { null } ?: ""
          if (mime.startsWith("video/") && vcodec.isEmpty()) {
            vcodec = mime
            fps = try { ex.getTrackFormat(i).getInteger(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 0 }
          }
          if (mime.startsWith("audio/") && acodec.isEmpty()) acodec = mime
        }
      } finally { try { ex.release() } catch (_: Exception) {} }
      VidInfo(w, h,
        meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toIntOrNull() ?: 0,
        meta(MediaMetadataRetriever.METADATA_KEY_BITRATE).toIntOrNull() ?: 0,
        vcodec, acodec, acodec.isNotEmpty(),
        meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0, fps)
    } catch (_: Exception) { null }
    finally { try { r.release() } catch (_: Exception) {} }
  }

  /** nostube "when to transcode" rules + LightCompressor min-bitrate skip. */
  fun needsTranscode(info: VidInfo, name: String): Boolean {
    val mp4 = name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".m4v")
    val codecOk = (info.vcodec == "video/avc" || info.vcodec == "video/hevc") &&
      (!info.hasAudio || info.acodec == "audio/mp4a-latm")
    if (!mp4 || !codecOk) return true // compat first: always normalize odd files
    if (info.bitrate in 1..1_999_999) return false // tiny file: not worth the battery
    if (minOf(info.w, info.h) > 1080) return true
    if (info.bitrate > 8_000_000) return true
    return false
  }

  /** LightCompressor auto-resize ladder + nostube 1080p short-side cap. Never upscales. */
  fun targetSize(w: Int, h: Int): Pair<Int, Int> {
    val mx = maxOf(w, h)
    val s = when {
      mx >= 1920 -> 0.5
      mx >= 1280 -> 0.75
      mx >= 960 -> 0.95
      else -> 0.9
    }
    var tw = (w * s).toInt(); var th = (h * s).toInt()
    val short = minOf(tw, th)
    if (short > 1080) {
      val k = 1080.0 / short
      tw = (tw * k).toInt(); th = (th * k).toInt()
    }
    if (tw >= w && th >= h) return w to h
    tw = maxOf(2, tw / 2 * 2); th = maxOf(2, th / 2 * 2)
    return tw to th
  }

  /** LightCompressor MEDIUM (×0.3), clamped to nostube's balanced-1080p ceiling. */
  fun targetBitrate(src: Int): Int =
    if (src <= 0) 2_500_000 else (src * 0.3).toInt().coerceIn(400_000, 6_750_000)

  private const val TIMEOUT_US = 10_000L

  private data class ASamp(val data: ByteArray, val pts: Long, val flags: Int)

  /** Decode → re-encode non-AAC audio to AAC-LC 96k, collecting samples in RAM. */
  private fun transcodeAudio(srcPath: String, aIdx: Int, aMime: String): Pair<MediaFormat, List<ASamp>> {
    val ex = MediaExtractor()
    ex.setDataSource(srcPath)
    ex.selectTrack(aIdx)
    val inFmt = ex.getTrackFormat(aIdx)
    val rate = try { inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 44100 }
    val srcCh = try { inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 2 }
    if (srcCh > 2) { ex.release(); throw IllegalArgumentException("surround audio not supported") }
    val ch = maxOf(1, srcCh)
    val dec = MediaCodec.createDecoderByType(aMime)
    dec.configure(inFmt, null, null, 0)
    val enc = MediaCodec.createEncoderByType("audio/mp4a-latm")
    enc.configure(MediaFormat.createAudioFormat("audio/mp4a-latm", rate, ch).apply {
      setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
    }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    dec.start(); enc.start()
    try {
      val out = mutableListOf<ASamp>()
      var outFmt: MediaFormat? = null
      var decEos = false; var encEos = false
      var pending: ByteArray? = null; var pendingOff = 0; var chunkPts = 0L; var pendingEos = false
      var eosSent = false
      val bytesPerSec = maxOf(1, rate * ch * 2)
      val di = MediaCodec.BufferInfo(); val ei = MediaCodec.BufferInfo()
      while (!encEos) {
        if (!decEos) {
          val idx = dec.dequeueInputBuffer(TIMEOUT_US)
          if (idx >= 0) {
            val buf = dec.getInputBuffer(idx)!!
            val n = ex.readSampleData(buf, 0)
            if (n < 0) { dec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); decEos = true }
            else { dec.queueInputBuffer(idx, 0, n, ex.sampleTime, ex.sampleFlags); ex.advance() }
          }
        }
        if (pending == null && !decEos) {
          val o = dec.dequeueOutputBuffer(di, TIMEOUT_US)
          when {
            o >= 0 -> {
              if (di.size > 0) {
                val buf = dec.getOutputBuffer(o)!!
                val chunk = ByteArray(di.size)
                buf.position(di.offset); buf.get(chunk)
                pending = chunk; pendingOff = 0; chunkPts = di.presentationTimeUs
              }
              if (di.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) pendingEos = true
              dec.releaseOutputBuffer(o, false)
            }
            o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
            o == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
          }
        } else if (pending == null && decEos) {
          // Drain any last decoder output after input EOS.
          val o = dec.dequeueOutputBuffer(di, TIMEOUT_US)
          if (o >= 0) {
            if (di.size > 0) {
              val buf = dec.getOutputBuffer(o)!!
              val chunk = ByteArray(di.size)
              buf.position(di.offset); buf.get(chunk)
              pending = chunk; pendingOff = 0; chunkPts = di.presentationTimeUs
            }
            if (di.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) pendingEos = true
            dec.releaseOutputBuffer(o, false)
          }
        }
        var fed = true
        while (fed) {
          val p = pending
          if (p == null) {
            if (pendingEos && !eosSent) {
              val idx = enc.dequeueInputBuffer(TIMEOUT_US)
              if (idx >= 0) {
                enc.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                eosSent = true
              }
            }
            fed = false
          } else {
            val idx = enc.dequeueInputBuffer(0)
            if (idx < 0) { fed = false }
            else {
              val buf = enc.getInputBuffer(idx)!!
              val n = minOf(buf.remaining(), p.size - pendingOff)
              if (n <= 0) { enc.queueInputBuffer(idx, 0, 0, 0, 0); fed = false }
              else {
                buf.put(p, pendingOff, n)
                pendingOff += n
                val last = pendingOff >= p.size
                val flags = if (last && pendingEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                enc.queueInputBuffer(idx, 0, n, chunkPts, flags)
                chunkPts += (n * 1_000_000L) / bytesPerSec
                if (last) { pending = null; if (pendingEos) eosSent = true }
              }
            }
          }
        }
        var drained = true
        while (drained) {
          val o = enc.dequeueOutputBuffer(ei, TIMEOUT_US)
          when {
            o >= 0 -> {
              if (ei.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && ei.size > 0) {
                val buf = enc.getOutputBuffer(o)!!
                val chunk = ByteArray(ei.size)
                buf.position(ei.offset); buf.get(chunk)
                out.add(ASamp(chunk, ei.presentationTimeUs, 0))
              }
              enc.releaseOutputBuffer(o, false)
              if (ei.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encEos = true
            }
            o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFmt = enc.outputFormat
            else -> drained = false
          }
        }
      }
      return (outFmt ?: throw RuntimeException("audio encoder produced no format")) to out
    } finally {
      try { dec.stop(); dec.release() } catch (_: Exception) {}
      try { enc.stop(); enc.release() } catch (_: Exception) {}
      try { ex.release() } catch (_: Exception) {}
    }
  }

  /** H.264 transcode with AAC passthrough (or re-encode). Throws → caller uploads original. */
  @Throws(Exception::class)
  fun transcode(src: File, dst: File, tw: Int, th: Int, bitrate: Int, fps: Int, rotation: Int) {
    if (dst.exists() && !dst.delete()) throw RuntimeException("stale temp file")
    val ex = MediaExtractor()
    ex.setDataSource(src.absolutePath)
    var vIdx = -1; var aIdx = -1; var vMime = ""; var aMime = ""
    for (i in 0 until ex.trackCount) {
      val m = try { ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) } catch (_: Exception) { null } ?: ""
      if (vIdx < 0 && m.startsWith("video/")) { vIdx = i; vMime = m }
      else if (aIdx < 0 && m.startsWith("audio/")) { aIdx = i; aMime = m }
    }
    if (vIdx < 0) { ex.release(); throw IllegalArgumentException("no video track") }
    var audioFmt: MediaFormat? = null
    var audioSamples: List<ASamp>? = null
    var audioCopy: MediaExtractor? = null
    if (aIdx >= 0) {
      if (aMime == "audio/mp4a-latm") {
        audioFmt = ex.getTrackFormat(aIdx)
        val c = MediaExtractor(); c.setDataSource(src.absolutePath); c.selectTrack(aIdx); audioCopy = c
      } else {
        val (f, s) = transcodeAudio(src.absolutePath, aIdx, aMime)
        audioFmt = f; audioSamples = s
      }
    }
    val mux = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    if (rotation == 90 || rotation == 180 || rotation == 270) try { mux.setOrientationHint(rotation) } catch (_: Exception) {}
    val vDec = MediaCodec.createDecoderByType(vMime)
    val vEnc = MediaCodec.createEncoderByType("video/avc")
    var surface: Surface? = null
    fun vFormat(highProfile: Boolean) = MediaFormat.createVideoFormat("video/avc", tw, th).apply {
      setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
      setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
      setInteger(MediaFormat.KEY_FRAME_RATE, if (fps > 0) minOf(fps, 30) else 30)
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
      if (highProfile) setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
    }
    try {
      try { vEnc.configure(vFormat(true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
      catch (_: Exception) { vEnc.configure(vFormat(false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
      surface = vEnc.createInputSurface()
      vDec.configure(ex.getTrackFormat(vIdx), surface, null, 0)
      ex.selectTrack(vIdx)
      vDec.start(); vEnc.start()
      var aTrack = -1
      if (audioFmt != null) aTrack = mux.addTrack(audioFmt)
      var vTrack = -1
      var muxStarted = false
      var decEos = false; var encEos = false; var eosSignaled = false
      val info = MediaCodec.BufferInfo()
      while (!encEos) {
        if (!decEos) {
          val idx = vDec.dequeueInputBuffer(TIMEOUT_US)
          if (idx >= 0) {
            val buf = vDec.getInputBuffer(idx)!!
            val n = ex.readSampleData(buf, 0)
            if (n < 0) { vDec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); decEos = true }
            else { vDec.queueInputBuffer(idx, 0, n, ex.sampleTime, ex.sampleFlags); ex.advance() }
          }
        }
        var dd = true
        while (dd) {
          val o = vDec.dequeueOutputBuffer(info, TIMEOUT_US)
          when {
            o >= 0 -> {
              val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
              vDec.releaseOutputBuffer(o, info.size > 0)
              if (eos) {
                decEos = true
                if (!eosSignaled) { vEnc.signalEndOfInputStream(); eosSignaled = true }
              }
            }
            o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
            else -> dd = false
          }
        }
        var ed = true
        while (ed) {
          val o = vEnc.dequeueOutputBuffer(info, TIMEOUT_US)
          when {
            o >= 0 -> {
              val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
              val cfg = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
              if (!cfg && info.size > 0 && muxStarted) {
                val buf = vEnc.getOutputBuffer(o)!!
                buf.position(info.offset); buf.limit(info.offset + info.size)
                mux.writeSampleData(vTrack, buf, info)
              }
              vEnc.releaseOutputBuffer(o, false)
              if (eos) encEos = true
            }
            o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              vTrack = mux.addTrack(vEnc.outputFormat)
              if (!muxStarted) { mux.start(); muxStarted = true }
            }
            else -> ed = false
          }
        }
      }
      if (muxStarted) {
        if (audioCopy != null && aTrack >= 0) {
          val buf = ByteBuffer.allocate(256 * 1024)
          val ai = MediaCodec.BufferInfo()
          while (true) {
            buf.clear()
            val n = audioCopy.readSampleData(buf, 0)
            if (n < 0) break
            ai.set(0, n, audioCopy.sampleTime, audioCopy.sampleFlags)
            mux.writeSampleData(aTrack, buf, ai)
            audioCopy.advance()
          }
        } else if (audioSamples != null && aTrack >= 0) {
          val ai = MediaCodec.BufferInfo()
          for (s in audioSamples) {
            ai.set(0, s.data.size, s.pts, s.flags)
            mux.writeSampleData(aTrack, ByteBuffer.wrap(s.data), ai)
          }
        }
      }
      mux.stop()
    } finally {
      try { vDec.stop(); vDec.release() } catch (_: Exception) {}
      try { vEnc.stop(); vEnc.release() } catch (_: Exception) {}
      try { surface?.release() } catch (_: Exception) {}
      try { ex.release() } catch (_: Exception) {}
      try { audioCopy?.release() } catch (_: Exception) {}
      try { mux.release() } catch (_: Exception) {}
    }
    if (!dst.exists() || dst.length() <= 0) throw RuntimeException("transcode produced no output")
  }

  /** WebP thumbnail from ~2.5s in, 360px wide. Null on any failure. */
  fun thumbnail(f: File): ByteArray? {
    val r = MediaMetadataRetriever()
    return try {
      r.setDataSource(f.absolutePath)
      val bmp = r.getFrameAtTime(2_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
      val w = 360
      val h = (bmp.height * 360.0 / bmp.width).toInt().coerceAtLeast(2)
      val small = Bitmap.createScaledBitmap(bmp, w, h, true)
      if (small !== bmp) try { bmp.recycle() } catch (_: Exception) {}
      val bos = ByteArrayOutputStream()
      small.compress(Bitmap.CompressFormat.WEBP, 80, bos)
      try { small.recycle() } catch (_: Exception) {}
      bos.toByteArray()
    } catch (_: Exception) { null }
    finally { try { r.release() } catch (_: Exception) {} }
  }
}
