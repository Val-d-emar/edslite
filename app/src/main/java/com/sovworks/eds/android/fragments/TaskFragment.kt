package com.sovworks.eds.android.fragments

import android.app.Activity
import android.app.Fragment
import android.os.AsyncTask
import android.os.AsyncTask.Status.FINISHED
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.fragments.TaskFragment.EventType.Added
import com.sovworks.eds.android.fragments.TaskFragment.EventType.Removed
import com.sovworks.eds.android.helpers.ProgressReporter
import com.sovworks.eds.settings.GlobalConfig
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException

abstract class TaskFragment : Fragment() {
    enum class EventType {
        Added,
        Removed
    }

    interface EventListener {
        fun onEvent(eventType: EventType?, tf: TaskFragment?)
    }

    interface TaskCallbacks {
        fun onPrepare(args: Bundle?)

        fun onUpdateUI(state: Any?)

        fun onResumeUI(args: Bundle?)

        fun onSuspendUI(args: Bundle?)

        fun onCompleted(args: Bundle?, result: Result?)
    }

    interface CallbacksProvider {
        fun getCallbacks(fragmentTag: String?): TaskCallbacks?
    }

    class Result {
        @JvmOverloads
        constructor(result: Any? = null) {
            _result = result
            error = null
            isCancelled = false
        }

        constructor(error: Throwable?, isCancelled: Boolean) {
            _result = null
            this.error = error
            this.isCancelled = isCancelled
        }

        @get:Throws(Throwable::class)
        val result: Any?
            get() {
                if (error != null) throw error
                return _result
            }

        val isCancelled: Boolean
        val error: Throwable?
        private val _result: Any?
    }

    /**
     * Hold a reference to the parent Activity so we can report the task's
     * current progress and results. The Android framework will pass us a
     * reference to the newly created Activity after each configuration change.
     */
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (_callbacks == null) initCallbacks()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (_callbacks == null) initCallbacks()
    }

    /**
     * This method will only be called once when the retained Fragment is first
     * created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        onEvent(
            Added,
            this
        )
        super.onCreate(savedInstanceState)
        // Retain this fragment across configuration changes.
        retainInstance = true
        Logger.debug(
            String.format(
                "TaskFragment %s has been created. Args=%s",
                this@TaskFragment,
                arguments
            )
        )
        initTask(activity)
        _task.execute()
    }

    override fun onResume() {
        super.onResume()
        if (_callbacks != null) _callbacks!!.onResumeUI(arguments)
    }

    override fun onPause() {
        if (_callbacks != null) _callbacks!!.onSuspendUI(arguments)
        super.onPause()
    }

    override fun onDetach() {
        super.onDetach()
        _callbacks = null
    }

    @Synchronized
    fun cancel() {
        _task.cancel(false)
    }

    interface TaskState {
        @JvmField
        val isTaskCancelled: Boolean
        fun updateUI(state: Any?)
        fun setResult(result: Any?)
    }

    class TaskStateProgressReporter(private val _ts: TaskState) : ProgressReporter {
        override fun setText(text: CharSequence) {
            _ts.updateUI(text)
        }

        override fun setProgress(progress: Int) {
            _ts.updateUI(progress)
        }

        override fun isCancelled(): Boolean {
            return _ts.isTaskCancelled
        }
    }

    protected open fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
        if (activity is CallbacksProvider) return (activity as CallbacksProvider).getCallbacks(tag)
        if (activity is TaskCallbacks) return activity
        return null
    }

    protected open fun initTask(activity: Activity?) {
    }

    @Throws(Throwable::class)
    protected abstract fun doWork(state: TaskState?)

    protected open fun detachTask() {
        val fm = fragmentManager
        if (fm != null) {
            fm.beginTransaction().remove(this@TaskFragment).commitAllowingStateLoss()
            Logger.debug(
                String.format(
                    "TaskFragment %s has been removed from the fragment manager",
                    this
                )
            )
            onEvent(
                Removed,
                this
            )
        }
    }

    private var _callbacks: TaskCallbacks? = null

    private fun initCallbacks() {
        _callbacks = getTaskCallbacks(activity)
        if (_callbacks != null) {
            if (_task.status != FINISHED) _callbacks!!.onPrepare(arguments)
            else try {
                _callbacks!!.onCompleted(arguments, _task.get())
            } catch (ignored: Exception) {
            }
        }
    }

    private val _task: AsyncTask<Void, Any, Result> = object : AsyncTask<Void?, Any?, Result>() {
        override fun doInBackground(vararg ignore: Void): Result {
            Logger.debug(
                String.format(
                    "TaskFragment %s: background job started",
                    this@TaskFragment
                )
            )
            try {
                val ts = TaskStateImpl()
                doWork(ts)
                return Result(ts.workResult)
            } catch (e: Throwable) {
                return Result(e, false)
            }
        }

        override fun onProgressUpdate(vararg state: Any) {
            if (_callbacks != null) _callbacks!!.onUpdateUI(state[0])
        }

        override fun onCancelled() {
            Logger.debug(
                String.format(
                    "TaskFragment %s has been cancelled",
                    this@TaskFragment
                )
            )
            try {
                if (_callbacks != null) {
                    _callbacks!!.onSuspendUI(arguments)
                    _callbacks!!.onCompleted(arguments, Result(CancellationException(), true))
                }
            } catch (e: Throwable) {
                Logger.showAndLog(activity, e)
            } finally {
                detachTask()
            }
        }

        override fun onPostExecute(result: Result) {
            Logger.debug(
                String.format(
                    "TaskFragment %s completed",
                    this@TaskFragment
                )
            )
            try {
                if (_callbacks != null) {
                    _callbacks!!.onSuspendUI(arguments)
                    _callbacks!!.onCompleted(arguments, result)
                }
            } catch (e: Throwable) {
                Logger.showAndLog(activity, e)
            } finally {
                detachTask()
            }
        }

        inner class TaskStateImpl : TaskState {
            var workResult: Any? = null

            override fun updateUI(state: Any?) {
                if (!isTaskCancelled) publishProgress(state)
            }

            override val isTaskCancelled: Boolean
                get() = isCancelled

            override fun setResult(result: Any?) {
                workResult = result
            }
        }
    }

    companion object {
        const val ARG_HOST_FRAGMENT: String = "com.sovworks.eds.android.HOST_FRAGMENT_TAG"

        @Synchronized
        fun addEventListener(listener: EventListener) {
            if (GlobalConfig.isDebug()) _eventListeners!!.add(WeakReference(listener))
        }


        @JvmStatic
        @Synchronized
        fun onEvent(eventType: EventType?, tf: TaskFragment?) {
            if (GlobalConfig.isDebug()) {
                for (wr in _eventListeners!!) {
                    val el = wr.get()
                    el?.onEvent(eventType, tf)
                }
            }
        }

        private var _eventListeners: MutableList<WeakReference<EventListener>>? = null

        init {
            if (GlobalConfig.isDebug()) {
                _eventListeners = ArrayList()
            }
        }
    }
}
