package com.alexmherrmann.utils.log4j2cloudwatchappender

import com.amazonaws.services.logs.AWSLogsClient
import com.amazonaws.services.logs.model.InputLogEvent
import com.amazonaws.services.logs.model.PutLogEventsRequest
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

//private class RingBufferWithEvictNotifications<E>(
//        size: Int = 150,
//        evictedDealer: (E) -> Unit
//) {
//    private val buffer = ArrayList<E>(size)
//
//    fun offer()
//
//}

/**
 * An interface so we can test the internals
 */
sealed interface CloudwatchPutter {
    fun push(groupName: String, streamName: String, toPush: MutableList<InputLogEvent>)
}

/**
 * Actually pushed to cloudwatch logs
 */
class RealPutter(private val client: AWSLogsClient) : CloudwatchPutter {
    override fun push(groupName: String, streamName: String, toPush: MutableList<InputLogEvent>) {
        client.putLogEvents(PutLogEventsRequest(groupName, streamName, toPush))
    }
}


/**
 * Just test the pushing
 */
class TestingPusher(): CloudwatchPutter {
    val pushed = mutableListOf<InputLogEvent>()

    override fun push(groupName: String, streamName: String, toPush: MutableList<InputLogEvent>) {
        pushed.addAll(toPush)
    }

}


/**
 * An appender that lets you push to aws cloudwatch logs!
 *
 * It's asynchronous and tries to buffer as many as possible in one batch without breaking aws logs' limits
 */
class CloudwatchAppender(
        val putter: CloudwatchPutter,
        _name: String,
        filter: Filter?,
        ignoreExceptions: Boolean,
        _layout: Layout<out java.io.Serializable>,
        properties: Array<out Property>?)
    : AbstractAppender(_name, filter, _layout, ignoreExceptions, properties) {


    private val streamName = UUID.randomUUID()
    private val buffer = ArrayBlockingQueue<LogEvent>(120)

    init {
        val executor = Executors.newSingleThreadScheduledExecutor()

        // Schedule
        executor.scheduleWithFixedDelay({
            // Use this to keep track of however many bytes we've collected so far
            var totalSize = 0
            val logEventsToPut = buffer.stream().map { event ->
                InputLogEvent().apply {
                    timestamp = event.timeMillis
                    message = String(layout.toByteArray(event))
                }
            }.takeWhile {
                // This is just a guess on the message
                totalSize += it.message.length + 2048
                // Limits on cloudwatch logs events
                totalSize < 750_000
            }.collect(Collectors.toList())


            putter.push(
                    "sherpas-test",
                    streamName.toString(),
                    logEventsToPut)
        }, 150, 215, TimeUnit.MILLISECONDS)
    }

    override fun append(event: LogEvent) {
        // Only push when the filter allows it or the filter is null
        when (filter?.filter(event)) {
            null,
            Filter.Result.ACCEPT -> {
                buffer.offer(event)
            }
            Filter.Result.NEUTRAL,
            Filter.Result.DENY -> Unit
        }
    }

}