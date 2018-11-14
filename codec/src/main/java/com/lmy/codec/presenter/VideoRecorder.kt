/*
 * Copyright (c) 2018-present, lmyooyo@gmail.com.
 *
 * This source code is licensed under the GPL license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.lmy.codec.presenter

import android.graphics.SurfaceTexture
import android.view.TextureView
import com.lmy.codec.encoder.Encoder
import com.lmy.codec.texture.impl.filter.BaseFilter
import com.lmy.codec.wrapper.CameraWrapper

/**
 * Created by lmyooyo@gmail.com on 2018/8/9.
 */
interface VideoRecorder : FilterSupport, SurfaceTexture.OnFrameAvailableListener {
    fun prepare()
    fun start()
    fun pause()
    fun stop()
    fun reset()
    fun prepared(): Boolean
    fun started(): Boolean
    fun setCameraIndex(index: CameraWrapper.CameraIndex)
    fun enableHardware(enable: Boolean)
    fun setOutputSize(width: Int, height: Int)
    fun setVideoBitrate(bitrate: Int)
    fun setFps(fps: Int)
    fun getWidth(): Int
    fun getHeight(): Int
    /**
     * File or rtmp url
     */
    fun setOutputUri(uri: String)

    fun setPreviewDisplay(view: TextureView)
    fun setOnStateListener(listener: OnStateListener)
    fun release()
    interface OnStateListener : Encoder.OnPreparedListener, Encoder.OnRecordListener {
        fun onStop()
        fun onError(error: Int, msg: String)
    }
}