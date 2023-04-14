import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

//fun main() {
//    PriorityExecutor().apply {
//        submitAction(1000) {
//            println(1000)
//        }
//        repeat(1000) {
//            val random = Random.nextInt(0, 1000)
//            submitAction(1000) {
//                delay(random.toLong())
//                println(random)
//            }
//        }
//    }
//    Thread.sleep(10000000)
//}

class PriorityExecutor {

    val priorityDispatcher = PriorityDispatcher()

    class PriorityDispatcher : CoroutineDispatcher() {

        private val priorityExecutor = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, PriorityBlockingQueue(100000)
        ) { it -> Thread(it) }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val priority = context[CoroutinePriority]?.priority ?: 0
            val executable = RunWithPriorityFuture(RunWithPriority(block, priority))
            priorityExecutor.execute(executable)
        }

    }

    internal data class CoroutinePriority(
        val priority: Int
    ) : AbstractCoroutineContextElement(CoroutinePriority) {
        companion object Key : CoroutineContext.Key<CoroutinePriority>

        override fun toString(): String = "CoroutinePriority($priority)"
    }

    internal class RunWithPriority(val runnable: Runnable, val priority: Int) : Runnable {

        override fun run() {
            runnable.run()
        }
    }

    internal class RunWithPriorityFuture(private val runWithPriority: RunWithPriority) :
        FutureTask<RunWithPriorityFuture>(runWithPriority.runnable, null), Comparable<RunWithPriorityFuture> {

        override fun compareTo(other: RunWithPriorityFuture): Int {
            return other.runWithPriority.priority - runWithPriority.priority
        }

    }

    fun <T> submitAction(priority: Int, runnable: suspend () -> T): Deferred<T> {
        return CoroutineScope(priorityDispatcher).async(CoroutinePriority(priority)) {
            runnable.invoke()
        }
    }

    suspend fun <T> launch(
        priority: Int,
        runnable: suspend () -> T
    ) = withContext(priorityDispatcher + CoroutinePriority(priority)) {
        runnable.invoke()
    }
}