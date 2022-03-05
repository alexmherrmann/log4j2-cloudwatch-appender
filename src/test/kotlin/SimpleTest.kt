package com.alexmherrmann.utils.log4j2cloudwatchappender

import org.apache.logging.log4j.core.AbstractLogEvent
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.message.Message
import org.apache.logging.log4j.message.SimpleMessage
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Cheap way to log events
 */
class SimpleEvent(val msg: String) : AbstractLogEvent() {
    override fun getMessage(): Message {
        return SimpleMessage(msg)
    }
}

class SanityTesting {
    @Test
    fun simpleTestForOneMessage() {
        val putter = TestingPusher()
        val _layout = PatternLayout
                .newBuilder()
                .withPattern("%msg%n")
                .build();

        val log = CloudwatchAppender(putter, "simple", null, true, _layout, arrayOf())

        (0 until 8).forEach {
            log.append(SimpleEvent("Event $it"))
        }

        Awaitility
                .await()
                .atMost(800, TimeUnit.MILLISECONDS)
                .until { putter.pushed.size == 8 }


    }
}