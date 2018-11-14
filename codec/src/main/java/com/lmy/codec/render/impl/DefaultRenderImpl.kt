/*
 * Copyright (c) 2018-present, lmyooyo@gmail.com.
 *
 * This source code is licensed under the GPL license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.lmy.codec.render.impl

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import com.lmy.codec.egl.CameraEglSurface
import com.lmy.codec.egl.ScreenEglSurface
import com.lmy.codec.entity.CodecContext
import com.lmy.codec.helper.FpsMeasurer
import com.lmy.codec.helper.PixelsReader
import com.lmy.codec.pipeline.Pipeline
import com.lmy.codec.render.Render
import com.lmy.codec.texture.impl.filter.BaseFilter
import com.lmy.codec.texture.impl.filter.NormalFilter
import com.lmy.codec.util.debug_i


/**
 * Created by lmyooyo@gmail.com on 2018/3/27.
 */
class DefaultRenderImpl(var context: CodecContext,
                        var cameraSurface: CameraEglSurface,
                        private var pipeline: Pipeline,
                        private var filter: BaseFilter? = null)
    : Render, FpsMeasurer.OnUpdateListener {

    private val filterLock = Any()
    private val outputFrameBuffer = IntArray(1)
    private val outputFrameBufferTexture = IntArray(1)
    private var transformMatrix: FloatArray = FloatArray(16)
    private var screenTexture: SurfaceTexture? = null
    private var screenSurface: ScreenEglSurface? = null
    private var reader: PixelsReader? = null
    private var width: Int = 0
    private var height: Int = 0
    private val videoMeasurer: FpsMeasurer = FpsMeasurer.create().apply {
        onUpdateListener = this@DefaultRenderImpl
    }
    private val renderMeasurer: FpsMeasurer = FpsMeasurer.create().apply {
        onUpdateListener = this@DefaultRenderImpl
    }

    fun init() {
        this.width = context.video.width
        this.height = context.video.height
        initReader()
        initFilter(if (null != filter) filter!! else NormalFilter())
    }

    private fun initFilter(f: BaseFilter) {
        synchronized(filterLock) {
            cameraSurface.makeCurrent()
            filter?.release()
            filter = f
            filter!!.width = this.width
            filter!!.height = this.height
            debug_i("Camera texture: ${cameraSurface.getFrameBuffer()[0]}," +
                    " ${cameraSurface.getFrameBufferTexture()[0]}")
            filter!!.textureId = cameraSurface.getFrameBufferTexture()
            filter!!.init()
            outputFrameBuffer[0] = filter!!.frameBuffer[0]
            outputFrameBufferTexture[0] = filter!!.frameBufferTexture[0]
        }
        initScreen()
    }

    private fun initReader() {
//        reader?.stop()
//        reader = PixelsReader.create(Resources.instance.isSupportPBO(), this.width, this.height)
//        reader?.start()
    }

    private fun initScreen() {
        if (null == screenSurface && null != screenTexture) {
            screenSurface = ScreenEglSurface.create(screenTexture!!, getFrameBufferTexture(),
                    cameraSurface.getEglContext())
        }
        screenSurface?.updateInputTexture(getFrameBufferTexture())
        screenSurface?.makeCurrent()
        screenSurface?.clear()
        screenSurface?.updateLocation(context)
    }

    override fun draw() {
        videoMeasurer.end()
        videoMeasurer.start()
        renderMeasurer.start()
        drawCamera()
        drawFilter()
		//wang test
        GLES20.glFinish()
        if (null == screenSurface) return
        screenSurface?.makeCurrent()
        GLES20.glViewport(0, 0, context.viewSize.width, context.viewSize.height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        screenSurface?.draw(transformMatrix)
        screenSurface?.swapBuffers()
        renderMeasurer.end()
    }

    private fun drawFilter() {
        synchronized(filterLock) {
            cameraSurface.makeCurrent()
            GLES20.glViewport(0, 0, this.width, this.height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glClearColor(0.3f, 0.3f, 0.3f, 0f)
            filter?.draw(null)
//            ++count
//            if (0 == count % 60) {
//                reader?.readPixels(filter!!.frameBuffer[0])
//                reader?.shoot("${Environment.getExternalStorageDirectory().path}/temp.jpg")
//                reader?.recycleBuffer()
//            }
        }
    }

    private fun drawCamera() {
        cameraSurface.makeCurrent()
        cameraSurface.updateTexImage()
        cameraSurface.getTransformMatrix(transformMatrix)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.3f, 0.3f, 0.3f, 0f)
        cameraSurface.draw(transformMatrix)
    }

    override fun start(texture: SurfaceTexture?, width: Int, height: Int) {
        updateScreenTexture(texture)
        context.viewSize.width = width
        context.viewSize.height = height
        pipeline?.queueEvent(Runnable { init() })
    }

    override fun updateSize(width: Int, height: Int) {
        if (width == this.width && this.height == height) return
        this.width = width
        this.height = height
        pipeline?.queueEvent(Runnable {
            synchronized(filterLock) {
                cameraSurface.makeCurrent()
                initReader()
                cameraSurface.updateLocation(context)
                filter?.updateFrameBuffer(this.width, this.height)
                initScreen()
            }
        })
    }

    override fun stop() {
        pipeline?.queueEvent(Runnable {
            cameraSurface.makeCurrent()
            screenSurface?.release()
            screenSurface = null
            debug_i("release")
        })
    }

    override fun release() {
        reader?.stop()
        stop()
    }

    override fun onFrameAvailable() {
        pipeline?.queueEvent(Runnable { draw() })
    }

    fun updateScreenTexture(texture: SurfaceTexture?) {
        screenTexture = texture
    }

    override fun post(runnable: Runnable) {
        pipeline?.queueEvent(runnable)
    }

    override fun setFilter(filter: BaseFilter) {
        pipeline?.queueEvent(Runnable {
            initFilter(filter)
        })
    }

    override fun getFilter(): BaseFilter? {
        synchronized(filterLock) {
            return filter
        }
    }

    override fun getFrameBuffer(): IntArray {
        synchronized(filterLock) {
            return outputFrameBuffer
        }

    }

    override fun getFrameBufferTexture(): IntArray {
        synchronized(filterLock) {
            return outputFrameBufferTexture
        }
    }

    override fun onUpdate(measurer: FpsMeasurer, fps: Float) {
        if (measurer == videoMeasurer) {
            debug_i("Video fps $fps")
        } else {
            debug_i("Render fps $fps")
        }
    }
}