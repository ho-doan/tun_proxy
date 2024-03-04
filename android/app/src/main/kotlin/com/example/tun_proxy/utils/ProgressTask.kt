package com.example.tun_proxy.utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

abstract class ProgressTask<Params, Progress, Result>() {
    @Volatile
    var status = Status.PENDING
        private set
    var isCancelled = false
        private set

    private inner class ProgressRunnable @SafeVarargs constructor(vararg params: Params) :
        Runnable {
        val params: Array<Params>
        private var result: Result? = null
        var handler = Handler(Looper.getMainLooper())

        init {
            this.params = params as Array<Params>
        }

        override fun run() {
            if (status != Status.PENDING) {
                when (status) {
                    Status.RUNNING -> throw IllegalStateException(
                        "Cannot execute task:"
                                + " the task is already running."
                    )

                    Status.FINISHED -> throw IllegalStateException(
                        ("Cannot execute task:"
                                + " the task has already been executed "
                                + "(a task can be executed only once)")
                    )

                    else -> {}
                }
            }
            status = Status.RUNNING
            try {
                onPreExecute()
                result = doInBackground(*params)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            handler.post(object : Runnable {
                override fun run() {
                    if (!isCancelled) {
                        onPostExecute(result)
                        status = Status.FINISHED
                    } else {
                        onCancelled()
                    }
                }
            })
        }
    }

    fun execute(vararg params: Params) {
        val executorService = Executors.newSingleThreadExecutor()
        executorService.submit(ProgressRunnable(*params))
    }

    protected fun onPreExecute() {}
    protected abstract fun doInBackground(vararg params: Params): Result
    protected fun onPostExecute(result: Result?) {}
    fun cancel(flag: Boolean) {
        isCancelled = flag
    }

    protected fun onCancelled() {}
    enum class Status {
        PENDING, RUNNING, FINISHED
    }
}