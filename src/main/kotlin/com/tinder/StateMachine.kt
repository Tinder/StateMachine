package com.tinder

import java.util.concurrent.atomic.AtomicReference

class StateMachine<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> private constructor(
    private val graph: Graph<STATE, EVENT, SIDE_EFFECT>
) {

    private val stateRef = AtomicReference<STATE>(graph.initialState)

    val state: STATE
        get() = stateRef.get()

    fun transition(event: EVENT): Transition<STATE, EVENT, SIDE_EFFECT> {
        val transition = synchronized(this) {
            val fromState = stateRef.get()
            val transition = fromState.getTransition(event)
            if (transition is Transition.Valid) {
                stateRef.set(transition.toState)
            }
            transition
        }
        return when (transition) {
            is Transition.Valid -> {
                val sideEffects = with(transition) {
                    with(fromState) {
                        notifyOnExit(event)
                    } + sideEffects + with(toState) {
                        notifyOnEnter(event)
                    }
                }
                transition.copy(sideEffects = sideEffects)
            }
            is Transition.Invalid -> transition
        }.also {
            it.notifyOnTransition()
        }
    }

    fun with(init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit): StateMachine<STATE, EVENT, SIDE_EFFECT> {
        return create(graph.copy(initialState = state), init)
    }

    private fun STATE.getTransition(event: EVENT): Transition<STATE, EVENT, SIDE_EFFECT> {
        for ((eventMatcher, createTransitionTo) in getDefinition().transitions) {
            if (eventMatcher.matches(event)) {
                val (toState, sideEffects) = createTransitionTo(this, event)
                return Transition.Valid(this, event, toState, sideEffects)
            }
        }
        return Transition.Invalid(this, event)
    }

    private fun STATE.getDefinition() = graph.stateDefinitions
        .filter { it.key.matches(this) }
        .map { it.value }
        .firstOrNull() ?: error("Missing definition for state ${this.javaClass.simpleName}!")

    private fun STATE.notifyOnEnter(cause: EVENT): Iterable<SIDE_EFFECT> =
            getDefinition().onEnterListeners.flatMap { it(this, cause) }

    private fun STATE.notifyOnExit(cause: EVENT): Iterable<SIDE_EFFECT> =
            getDefinition().onExitListeners.flatMap { it(this, cause) }

    private fun Transition<STATE, EVENT, SIDE_EFFECT>.notifyOnTransition() {
        graph.onTransitionListeners.forEach { it(this) }
    }

    @Suppress("UNUSED")
    sealed class Transition<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> {
        abstract val fromState: STATE
        abstract val event: EVENT

        data class Valid<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT,
            val toState: STATE,
            val sideEffects: Iterable<SIDE_EFFECT> = emptyList()
        ) : Transition<STATE, EVENT, SIDE_EFFECT>() {
            internal constructor(
                    fromState: STATE,
                    event: EVENT,
                    toState: STATE,
                    sideEffect: SIDE_EFFECT
            ) : this(fromState, event, toState, listOf(sideEffect))
        }

        data class Invalid<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT
        ) : Transition<STATE, EVENT, SIDE_EFFECT>()
    }

    data class Graph<STATE : Any, EVENT : Any, SIDE_EFFECT : Any>(
        val initialState: STATE,
        val stateDefinitions: Map<Matcher<STATE, STATE>, State<STATE, EVENT, SIDE_EFFECT>>,
        val onTransitionListeners: List<(Transition<STATE, EVENT, SIDE_EFFECT>) -> Unit>
    ) {

        class State<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> internal constructor() {
            val onEnterListeners = mutableListOf<(STATE, EVENT) -> Iterable<SIDE_EFFECT>>()
            val onExitListeners = mutableListOf<(STATE, EVENT) -> Iterable<SIDE_EFFECT>>()
            val transitions = linkedMapOf<Matcher<EVENT, EVENT>, (STATE, EVENT) -> TransitionTo<STATE, SIDE_EFFECT>>()

            data class TransitionTo<out STATE : Any, out SIDE_EFFECT : Any> internal constructor(
                val toState: STATE,
                val sideEffects: Iterable<SIDE_EFFECT>
            )
        }
    }

    class Matcher<T : Any, out R : T> private constructor(private val clazz: Class<R>) {

        private val predicates = mutableListOf<(T) -> Boolean>({ clazz.isInstance(it) })

        fun where(predicate: R.() -> Boolean): Matcher<T, R> = apply {
            predicates.add {
                @Suppress("UNCHECKED_CAST")
                (it as R).predicate()
            }
        }

        fun matches(value: T) = predicates.all { it(value) }

        companion object {
            fun <T : Any, R : T> any(clazz: Class<R>): Matcher<T, R> = Matcher(clazz)

            inline fun <T : Any, reified R : T> any(): Matcher<T, R> = any(R::class.java)

            inline fun <T : Any, reified R : T> eq(value: R): Matcher<T, R> = any<T, R>().where { this == value }
        }
    }

    class GraphBuilder<STATE : Any, EVENT : Any, SIDE_EFFECT : Any>(
        graph: Graph<STATE, EVENT, SIDE_EFFECT>? = null
    ) {
        private var initialState = graph?.initialState
        private val stateDefinitions = LinkedHashMap(graph?.stateDefinitions ?: emptyMap())
        private val onTransitionListeners = ArrayList(graph?.onTransitionListeners ?: emptyList())

        fun initialState(initialState: STATE) {
            this.initialState = initialState
        }

        fun <S : STATE> state(
            stateMatcher: Matcher<STATE, S>,
            init: StateDefinitionBuilder<S>.() -> Unit
        ) {
            stateDefinitions[stateMatcher] = StateDefinitionBuilder<S>().apply(init).build()
        }

        inline fun <reified S : STATE> state(noinline init: StateDefinitionBuilder<S>.() -> Unit) {
            state(Matcher.any(), init)
        }

        inline fun <reified S : STATE> state(state: S, noinline init: StateDefinitionBuilder<S>.() -> Unit) {
            state(Matcher.eq<STATE, S>(state), init)
        }

        fun onTransition(listener: (Transition<STATE, EVENT, SIDE_EFFECT>) -> Unit) {
            onTransitionListeners.add(listener)
        }

        fun build(): Graph<STATE, EVENT, SIDE_EFFECT> {
            return Graph(requireNotNull(initialState), stateDefinitions.toMap(), onTransitionListeners.toList())
        }

        inner class StateDefinitionBuilder<S : STATE> {
            inner class EventHandlerBuilder(val state: S, val cause: EVENT) {
                internal val sideEffects = mutableListOf<SIDE_EFFECT>()

                fun emit(vararg sideEffect: SIDE_EFFECT) {
                    sideEffects.addAll(sideEffect)
                }
            }

            inner class TransitionBuilder<E: EVENT>(val currentState: S, val event: E) {
                fun transitionTo(state: STATE, sideEffect: SIDE_EFFECT) = transitionTo(state, listOf(sideEffect))
                fun transitionTo(state: STATE, sideEffects: Iterable<SIDE_EFFECT> = emptyList()) =
                        Graph.State.TransitionTo(state, sideEffects)

                fun dontTransition(sideEffects: Iterable<SIDE_EFFECT> = emptyList()) = transitionTo(currentState, sideEffects)
                fun dontTransition(sideEffects: SIDE_EFFECT) = transitionTo(currentState, listOf(sideEffects))
            }

            private val stateDefinition = Graph.State<STATE, EVENT, SIDE_EFFECT>()

            inline fun <reified E : EVENT> any(): Matcher<EVENT, E> = Matcher.any()

            inline fun <reified R : EVENT> eq(value: R): Matcher<EVENT, R> = Matcher.eq(value)

            fun <E : EVENT> on(
                eventMatcher: Matcher<EVENT, E>,
                createTransitionTo: TransitionBuilder<E>.() -> Graph.State.TransitionTo<STATE, SIDE_EFFECT>
            ) {
                stateDefinition.transitions[eventMatcher] = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    createTransitionTo(TransitionBuilder(state as S, event as E))
                }
            }

            inline fun <reified E : EVENT> on(
                noinline createTransitionTo: TransitionBuilder<E>.() -> Graph.State.TransitionTo<STATE, SIDE_EFFECT>
            ) {
                return on(any(), createTransitionTo)
            }

            inline fun <reified E : EVENT> on(
                event: E,
                noinline createTransitionTo: TransitionBuilder<E>.() -> Graph.State.TransitionTo<STATE, SIDE_EFFECT>
            ) {
                return on(eq(event), createTransitionTo)
            }

            fun onEnter(listener: EventHandlerBuilder.() -> Unit) = with(stateDefinition) {
                onEnterListeners.add { state, cause ->
                    @Suppress("UNCHECKED_CAST")
                    EventHandlerBuilder(state as S, cause)
                            .apply(listener)
                            .sideEffects
                }
            }

            fun onExit(listener: EventHandlerBuilder.() -> Unit) = with(stateDefinition) {
                onExitListeners.add { state, cause ->
                    @Suppress("UNCHECKED_CAST")
                    EventHandlerBuilder(state as S, cause)
                            .apply(listener)
                            .sideEffects
                }
            }

            fun build() = stateDefinition
        }
    }

    companion object {
        fun <STATE : Any, EVENT : Any, SIDE_EFFECT : Any> create(
            init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit
        ): StateMachine<STATE, EVENT, SIDE_EFFECT> {
            return create(null, init)
        }

        private fun <STATE : Any, EVENT : Any, SIDE_EFFECT : Any> create(
            graph: Graph<STATE, EVENT, SIDE_EFFECT>?,
            init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit
        ): StateMachine<STATE, EVENT, SIDE_EFFECT> {
            return StateMachine(GraphBuilder(graph).apply(init).build())
        }
    }
}
