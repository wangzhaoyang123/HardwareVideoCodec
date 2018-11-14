package com.lmy.codec.x264

import android.media.MediaCodec
import android.media.MediaFormat
import com.lmy.codec.entity.Egl
import com.lmy.codec.entity.RecycleQueue
import com.lmy.codec.helper.Libyuv
import java.nio.ByteBuffer

/**
 * Created by lmyooyo@gmail.com on 2018/6/7.
 */
class CacheX264Encoder(private val codec: X264Encoder,
                       val maxCache: Int = 5,
                       private var cache: Cache? = null,
                       var onSampleListener: OnSampleListener? = null,
                       private var running: Boolean = true) : X264, Runnable {

    private var mEncodeThread = Thread(this).apply { name = "CacheX264Encoder" }

    init {
        var size = codec.getWidth() * codec.getHeight() * Egl.COLOR_CHANNELS
        if (Libyuv.COLOR_I420 == codec.colorFormat) {
            size = codec.getWidth() * codec.getHeight() * 3 / 2
        }
        cache = Cache(size, maxCache)
        mEncodeThread.start()
    }

    override fun start() {
        codec.start()
    }

    fun encode(buffer: ByteBuffer) {
        val data = cache?.pollCache() ?: return
        buffer.rewind()
        buffer.get(data)
        cache?.offer(data)
    }

    override fun encode(src: ByteArray): MediaCodec.BufferInfo? {
        val data = cache?.pollCache() ?: return null
        System.arraycopy(src, 0, data, 0, data.size)
        cache?.offer(data)
        return null

    }

    override fun stop() {
        if (!running) return
        running = false
        mEncodeThread.interrupt()
    }

    override fun release() {
        stop()
    }

    override fun run() {
        while (running) {
            var data: ByteArray?
            try {
                data = cache!!.take()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                break
            }
            val bufferInfo = codec.encode(data) ?: continue
            cache!!.recycle(data)
            if (X264Encoder.BUFFER_FLAG_CODEC_CONFIG == bufferInfo.flags) {
                onSampleListener?.onFormatChanged(codec.getOutFormat())
            } else {
                onSampleListener?.onSample(bufferInfo, codec.getOutBuffer())
            }
        }
        codec.release()
        cache?.release()
    }

    override fun getWidth(): Int = codec.getWidth()

    override fun getHeight(): Int = codec.getHeight()
    override fun setLevel(level: Int) {
        codec.setLevel(level)
    }

    override fun setProfile(profile: String) {
        codec.setProfile(profile)
    }

    class Cache(private var size: Int,
                capacity: Int) : RecycleQueue<ByteArray>(capacity) {
        init {
            ready()
        }

        override fun newCacheEntry(): ByteArray {
            return ByteArray(size)
        }
    }

    override fun post(event: Runnable): X264 {
        event.run()
        return this
    }

    interface OnSampleListener {
        fun onFormatChanged(format: MediaFormat)
        fun onSample(info: MediaCodec.BufferInfo, data: ByteBuffer)
    }
}