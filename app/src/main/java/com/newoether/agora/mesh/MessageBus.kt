package com.newoether.agora.mesh

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Message payload routed through the pub/sub [MessageBus].
 * Agents subscribe to topic salons without needing direct references to other agents.
 */
data class BusMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val topic: String,
    val senderId: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fingerprint: String = MeshProtocol.computeFingerprint(senderId, topic, payload, timestamp)
)

/**
 * Event-driven, non-blocking publish/subscribe message bus for Pi-Mesh.
 * Enables agents to publish and subscribe to topic salons (e.g. "general", "debat-contradictoire")
 * in a completely decoupled manner.
 */
class MessageBus {

    // Central shared flow with buffer overflow protection
    private val _busFlow = MutableSharedFlow<BusMessage>(
        replay = 10,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val busFlow: Flow<BusMessage> = _busFlow.asSharedFlow()

    // Topic subscriber counters for telemetry
    private val topicSubscribers = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Subscribes to a specific topic salon.
     * Returns a [Flow] that emits only messages addressed to this topic.
     */
    fun subscribe(topic: String): Flow<BusMessage> {
        topicSubscribers.computeIfAbsent(topic) { AtomicInteger(0) }.incrementAndGet()
        return _busFlow.filter { it.topic.equals(topic, ignoreCase = true) }
    }

    /**
     * Subscribes to all topics on the bus (used by coordinators, watchers, and UI monitors).
     */
    fun subscribeAll(): Flow<BusMessage> = _busFlow

    /**
     * Publishes a message to a specific topic salon.
     * Emits non-blockingly on the event-driven bus.
     */
    suspend fun publish(topic: String, senderId: String, payload: String): BusMessage {
        val message = BusMessage(
            topic = topic,
            senderId = senderId,
            payload = payload
        )
        _busFlow.emit(message)
        return message
    }

    /**
     * Try-publishes without suspending, suitable for non-coroutine contexts.
     */
    fun tryPublish(topic: String, senderId: String, payload: String): BusMessage {
        val message = BusMessage(
            topic = topic,
            senderId = senderId,
            payload = payload
        )
        _busFlow.tryEmit(message)
        return message
    }

    /**
     * Decrements the subscriber count when an agent leaves a topic salon.
     */
    fun unsubscribe(topic: String) {
        topicSubscribers[topic]?.let { counter ->
            if (counter.decrementAndGet() <= 0) {
                topicSubscribers.remove(topic)
            }
        }
    }

    /**
     * Returns the set of currently active topics with at least one subscriber or recent activity.
     */
    fun getActiveTopics(): Set<String> = topicSubscribers.keys.toSet()

    /**
     * Returns subscriber count for a given topic.
     */
    fun getSubscriberCount(topic: String): Int = topicSubscribers[topic]?.get() ?: 0

    companion object {
        val defaultBus = MessageBus()
    }
}
