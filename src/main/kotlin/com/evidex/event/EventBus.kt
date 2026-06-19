package com.evidex.event

import java.util.concurrent.ConcurrentLinkedQueue

class EventBus {
    private val listeners = mutableMapOf<Class<*>, MutableList<(Any) -> Unit>>()
    private val pending = ConcurrentLinkedQueue<Pair<Class<*>, Any>>()

    fun <T : Any> on(eventClass: Class<T>, listener: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        listeners.getOrPut(eventClass) { mutableListOf() }.add { listener(it as T) }
    }

    inline fun <reified T : Any> on(noinline listener: (T) -> Unit) {
        on(T::class.java, listener)
    }

    fun emit(event: Any) {
        pending.add(event.javaClass to event)
    }

    fun flush() {
        while (true) {
            val entry = pending.poll() ?: break
            val (cls, event) = entry
            listeners[cls]?.forEach { it(event) }
        }
    }

    fun clear() {
        listeners.clear()
        pending.clear()
    }
}
