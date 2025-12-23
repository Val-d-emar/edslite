package com.sovworks.eds.util

class ConditionWaiter(
    private val _condition: com.sovworks.eds.util.ConditionWaiter.ICondition,
    private val _syncer: Any
) :
    Thread() {
    interface ICondition {
        val isTrue: Boolean
    }

    fun setTimeout(timeout: Int) {
        _timeout = timeout
    }

    fun setSleepTimeout(sleepTimeout: Int) {
        _sleepTimeout = sleepTimeout
    }

    fun setNumRetries(numRetries: Int) {
        _numRetries = numRetries
    }

    override fun run() {
        result = false
        var sleepTime = 0
        var retr = 0
        try {
            do {
                result = _condition.isTrue()
                if (!result) {
                    try {
                        sleep(_sleepTimeout.toLong())
                    } catch (e: InterruptedException) {
                    }
                    sleepTime += _sleepTimeout
                    retr++
                }
            } while (!_fin && !result && (_timeout == 0 || (_timeout > 0 && sleepTime < _timeout))
                && (_numRetries == 0 || (_numRetries > 0 && retr < _numRetries))
            )
        } finally {
            synchronized(_syncer) {
                (_syncer as Object).notify()
            }
        }
    }

    fun fin() {
        _fin = true
    }

    private var _timeout = 5000
    private var _sleepTimeout = 200
    private var _numRetries = 0
    private var _fin = false
    var result: Boolean = false
        private set

    companion object {
        @JvmOverloads
        fun waitFor(
            condition: com.sovworks.eds.util.ConditionWaiter.ICondition?,
            timeout: Int = 5000
        ): Boolean {
            val syncer = Any()
            val waiter = ConditionWaiter(
                condition!!, syncer
            )
            waiter.setTimeout(timeout)

            synchronized(syncer) {
                waiter.start()
                try {
                    (syncer as Object).wait()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            return waiter.getResult()
        }

        fun waitFor(
            condition: com.sovworks.eds.util.ConditionWaiter.ICondition?,
            numRetries: Int,
            sleepTimeout: Int
        ): Boolean {
            val syncer = Any()
            val waiter = ConditionWaiter(
                condition!!, syncer
            )
            waiter.setTimeout(0)
            waiter.setSleepTimeout(sleepTimeout)
            waiter.setNumRetries(numRetries)

            synchronized(syncer) {
                waiter.start()
                try {
                    (syncer as Object).wait()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            return waiter.getResult()
        }
    }
}
