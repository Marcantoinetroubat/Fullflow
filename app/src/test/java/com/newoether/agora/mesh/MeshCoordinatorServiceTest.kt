package com.newoether.agora.mesh

import com.newoether.agora.mesh.service.AgentStatus
import com.newoether.agora.mesh.service.MeshCoordinatorService
import com.newoether.agora.mesh.service.ThreadSafeAgentRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MeshCoordinatorServiceTest {

    @Test
    fun testMessageBusPubSub() = runTest {
        val bus = MessageBus()
        val topic = "general-test"
        val sender = "test-agent"
        val payload = "hello world payload"

        val receivedMessages = mutableListOf<BusMessage>()
        val job = launch {
            bus.subscribe(topic).take(1).toList(receivedMessages)
        }

        // Yield to allow subscription to register
        this.testScheduler.advanceUntilIdle()

        bus.publish(topic, sender, payload)

        job.join()

        assertEquals(1, receivedMessages.size)
        assertEquals(topic, receivedMessages[0].topic)
        assertEquals(sender, receivedMessages[0].senderId)
        assertEquals(payload, receivedMessages[0].payload)
    }

    @Test
    fun testMessageBusSubscriptionCounters() {
        val bus = MessageBus()
        val topic = "debat"

        assertEquals(0, bus.getSubscriberCount(topic))

        val flow1 = bus.subscribe(topic)
        assertEquals(1, bus.getSubscriberCount(topic))

        val flow2 = bus.subscribe(topic)
        assertEquals(2, bus.getSubscriberCount(topic))

        bus.unsubscribe(topic)
        assertEquals(1, bus.getSubscriberCount(topic))

        bus.unsubscribe(topic)
        assertEquals(0, bus.getSubscriberCount(topic))
    }

    @Test
    fun testThreadSafeAgentRegistry() {
        val registry = ThreadSafeAgentRegistry()
        val agentId = "agent-test"

        registry.registerAgent(agentId, "Test Agent", "Testing")
        val agent = registry.getAgent(agentId)
        assertNotNull(agent)
        assertEquals(AgentStatus.IDLE, agent?.status)

        registry.updateStatus(agentId, AgentStatus.WORKING, "Running tests")
        assertEquals(AgentStatus.WORKING, registry.getAgent(agentId)?.status)
        assertEquals("Running tests", registry.getAgent(agentId)?.currentTask)

        registry.updateStatus(agentId, AgentStatus.IDLE, null)
        assertEquals(AgentStatus.IDLE, registry.getAgent(agentId)?.status)
        assertEquals(1, registry.getAgent(agentId)?.completedCount)
    }

    @Test
    fun testMeshCoordinatorServiceThreadSafeStatusUpdates() {
        val service = MeshCoordinatorService()
        val agentId = "agent-writer"

        // Initial status
        assertEquals(AgentStatus.IDLE, service.getAgentStatus(agentId))

        // Update status
        service.updateAgentStatus(agentId, AgentStatus.WORKING, "Drafting")
        assertEquals(AgentStatus.WORKING, service.getAgentStatus(agentId))

        // CAS transition - Success
        val casSuccess = service.compareAndSetAgentStatus(agentId, AgentStatus.WORKING, AgentStatus.IDLE, "Done")
        assertTrue(casSuccess)
        assertEquals(AgentStatus.IDLE, service.getAgentStatus(agentId))

        // CAS transition - Failure due to wrong expected state
        val casFailure = service.compareAndSetAgentStatus(agentId, AgentStatus.WORKING, AgentStatus.BLOCKED, "Failed")
        assertFalse(casFailure)
        assertEquals(AgentStatus.IDLE, service.getAgentStatus(agentId))
    }

    @Test
    fun testPrivacyPreservingMeshMessage() {
        val topic = "general-test"
        val sender = "agent-librarian"
        val metadata = mapOf("action" to "query", "db" to "local")

        val message = MeshMessage(
            topic = topic,
            senderId = sender,
            metadata = metadata
        )

        assertEquals(topic, message.topic)
        assertEquals(sender, message.senderId)
        assertEquals(metadata, message.metadata)
        assertNotNull(message.metadataHash)
    }

    @Test
    fun testMeshCoordinatorRouting() = runTest {
        val service = MeshCoordinatorService()

        val topic = "verification"
        val sender = "agent-auditor"
        val message = MeshMessage(topic = topic, senderId = sender, metadata = mapOf("source" to "test"))

        // Set sender status to IDLE initially
        service.updateAgentStatus(sender, AgentStatus.IDLE)
        assertEquals(AgentStatus.IDLE, service.getAgentStatus(sender))

        // Route message (non-blocking Channel submission and WORKING status transition)
        val success = service.routeMessage(message)
        assertTrue(success)

        // Verify sender transitioned to WORKING during routing
        assertEquals(AgentStatus.WORKING, service.getAgentStatus(sender))
    }
}
