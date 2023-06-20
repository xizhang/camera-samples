package com.example.android.camerax.video.effects

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.Executor

public class BackgroundVideoFrameProcessorListenerExecutor : Executor {
    val mHandler: Handler

    init {
        val mHandlerThread = HandlerThread("BackgroundVideoFrameProcessorListener")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
    }

    override fun execute(command: Runnable) {
        mHandler.post(command)
    }
}