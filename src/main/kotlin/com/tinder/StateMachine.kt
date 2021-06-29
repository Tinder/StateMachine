package com.tinder

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class StateMachine<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> private constructor(
    private val graph: Graph<STATE, EVENT, SIDE_EFFECT>,
    state: StateMachine<STATE, EVENT, SIDE_EFFECT>.StateWithHistory? = null
) {
    val state: StateWithHistory = state ?: StateWithHistory(graph.initialState, emptyMap())

    inner class StateWithHistory internal constructor(
        val state: STATE,
        val history: Map<out KClass<out STATE>, STATE>
    ) {
        fun update(newState: STATE): StateWithHistory {
            val (entered, exited) = graph.getEnteredAndExitedStates(state, newState)

            val newHistoryEntries = exited
                .filter {
                    it.stateClass.isAbstract || it.stateClass.isSealed
                }
                .map {
                    it.stateClass to state
                }
                .toMap()
            val obsoleteHistoryEntries = entered
                .filter {
                    it.stateClass.isAbstract || it.stateClass.isSealed
                }
                .map {
                    it.stateClass
                }
            return StateWithHistory(
                newState,
                history - obsoleteHistoryEntries + newHistoryEntries
            )
        }
    }

    fun transition(event: EVENT): Pair<StateMachine<STATE, EVENT, SIDE_EFFECT>, Transition<STATE, EVENT, SIDE_EFFECT>> {
        val (newState, transition) = getTransition(event).let { tmp ->
            when (tmp) {
                is Transition.Valid -> {
                    val (entered, exited) = graph.getEnteredAndExitedStates(state.state, tmp.toState)
                    val exitSideEffects = exited.flatMap {
                        it.onExitListeners.flatMap { it(state.state, event, tmp.toState) }
                    }
                    val enteredSideEffects = entered.flatMap {
                        it.onEnterListeners.flatMap { it(tmp.toState, event, state.state) }
                    }
                    val newState = state.update(tmp.toState)
                    newState to tmp.copy(sideEffects = exitSideEffects + tmp.sideEffects + enteredSideEffects)
                }
                is Transition.Invalid -> null to tmp
            }
        }
        transition.notifyOnTransition()
        return (newState?.let { StateMachine(graph, newState) } ?: this) to transition
    }

    fun with(init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit): StateMachine<STATE, EVENT, SIDE_EFFECT> {
        return create(graph, init)
    }

    private fun getTransition(
        event: EVENT
    ): Transition<STATE, EVENT, SIDE_EFFECT> {
        val matchingFrom = graph.getDefinitions(state.state)
        matchingFrom.forEach {
            for ((eventMatcher, createTransitionTo) in it.transitions) {
                if (eventMatcher.matches(event)) {
                    val (toState, sideEffects) = when (val transition = createTransitionTo(state.state, event)) {
                        is Graph.State.TransitionTo.TransitionToState -> transition
                        is Graph.State.TransitionTo.TransitionToGroup -> getTransitionToGroup(
                            it,
                            transition
                        )
                    }
                    return Transition.Valid(
                        state.state,
                        event,
                        toState,
                        sideEffects
                    )
                }
            }
        }

        return Transition.Invalid(state.state, event)
    }

    private fun getTransitionToGroup(
        matchedState: Graph.State<STATE, EVENT, SIDE_EFFECT>,
        transition: Graph.State.TransitionTo.TransitionToGroup<out STATE, out SIDE_EFFECT>
    ): Graph.State.TransitionTo.TransitionToState<STATE, SIDE_EFFECT> {
        val historyEntry = state.history[transition.toGroup]
            ?: matchedState.initialState?.invoke()
            ?: error("no history entry and no initial state for ${matchedState.stateClass} defined")
        return Graph.State.TransitionTo.TransitionToState(historyEntry, transition.sideEffects)
    }

    private fun Transition<STATE, EVENT, SIDE_EFFECT>.notifyOnTransition() {
        graph.onTransitionListeners.forEach { it(this) }
    }

    sealed class Transition<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> {
        abstract val fromState: STATE
        abstract val event: EVENT

        data class Valid<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT,
            val toState: STATE,
            val sideEffects: Iterable<SIDE_EFFECT> = emptyList()
        ) : Transition<STATE, EVENT, SIDE_EFFECT>() {
            constructor(fromState: STATE, event: EVENT, toState: STATE, vararg sideEffects: SIDE_EFFECT)
                    : this(fromState, event, toState, sideEffects.toList())
        }

        data class Invalid<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT
        ) : Transition<STATE, EVENT, SIDE_EFFECT>()
    }

    data class Graph<STATE : Any, EVENT : Any, SIDE_EFFECT : Any>(
        val initialState: STATE,
        val stateDefinitions: Map<KClass<out STATE>, State<STATE, EVENT, SIDE_EFFECT>>,
        val onTransitionListeners: List<(Transition<STATE, EVENT, SIDE_EFFECT>) -> Unit>
    ) {
        fun getDefinitions(state: STATE): List<State<STATE, EVENT, SIDE_EFFECT>> = stateDefinitions
            .filter { it.key.isInstance(state) }
            .map { it.value }

        class State<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> internal constructor(val stateClass: KClass<out STATE>) {
            val onEnterListeners = mutableListOf<(STATE, EVENT, STATE) -> Iterable<SIDE_EFFECT>>()
            val onExitListeners = mutableListOf<(STATE, EVENT, STATE) -> Iterable<SIDE_EFFECT>>()
            val transitions =
                linkedMapOf<Matcher<EVENT, EVENT>, (STATE, EVENT) -> TransitionTo<out STATE, out SIDE_EFFECT>>()
            var initialState: (() -> STATE)? = null

            fun isSubStateOf(other: State<STATE, *, *>) = stateClass.isSubclassOf(other.stateClass)
            fun isState(stateClass: KClass<out STATE>) = this.stateClass == stateClass

            sealed class TransitionTo<STATE : Any, SIDE_EFFECT : Any> {
                data class TransitionToState<STATE : Any, SIDE_EFFECT : Any>(
                    val toState: STATE,
                    val sideEffects: Iterable<SIDE_EFFECT>
                ) : TransitionTo<STATE, SIDE_EFFECT>()

                data class TransitionToGroup<STATE : Any, SIDE_EFFECT : Any>(
                    val toGroup: KClass<STATE>,
                    val sideEffects: Iterable<SIDE_EFFECT>
                ) : TransitionTo<STATE, SIDE_EFFECT>() {
                    init {
                        require(toGroup.isAbstract || toGroup.isSealed)
                    }
                }
            }
        }

        fun getEnteredAndExitedStates(
            from: STATE,
            to: STATE
        ): Pair<List<State<STATE, EVENT, SIDE_EFFECT>>, List<State<STATE, EVENT, SIDE_EFFECT>>> {
            val matchingFrom = getDefinitions(from)
            val matchingTo = getDefinitions(to)

            val stateHierarchyComparer = Comparator<State<STATE, *, *>> { a, b -> if (a.isSubStateOf(b)) -1 else 1 }
            val matchingFromSorted = matchingFrom.sortedWith(stateHierarchyComparer)
            val matchingToSorted = matchingTo.sortedWith(stateHierarchyComparer.reversed())

            if (matchingFrom == matchingTo) {
                return matchingFromSorted.take(1) to matchingFromSorted.take(1)
            }
            val exited = matchingFromSorted - matchingToSorted
            val entered = matchingToSorted - matchingFromSorted

            return entered to exited
        }
    }

    class Matcher<T : Any, out R : T> private constructor(
        private val clazz: KClass<R>,
        private val predicates: MutableList<(T) -> Boolean> = mutableListOf({ it -> clazz.isInstance(it) })
    ) {

        fun where(predicate: R.() -> Boolean): Matcher<T, R> = apply {
            predicates.add {
                @Suppress("UNCHECKED_CAST")
                (it as R).predicate()
            }
        }

        fun matches(value: T) = predicates.all { it(value) }

        companion object {
            fun <T : Any, R : T> any(clazz: KClass<R>): Matcher<T, R> = Matcher(clazz)

            inline fun <T : Any, reified R : T> any(): Matcher<T, R> = any(R::class)

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
            stateClass: KClass<S>,
            init: StateDefinitionBuilder<S>.() -> Unit
        ) {
            stateDefinitions[stateClass] = StateDefinitionBuilder(stateClass).apply(init).build()
        }

        inline fun <reified S : STATE> state(noinline init: StateDefinitionBuilder<S>.() -> Unit) {
            state(S::class, init)
        }

        fun onTransition(listener: (Transition<STATE, EVENT, SIDE_EFFECT>) -> Unit) {
            onTransitionListeners.add(listener)
        }

        fun build(): Graph<STATE, EVENT, SIDE_EFFECT> {
            return Graph(requireNotNull(initialState), stateDefinitions.toMap(), onTransitionListeners.toList())
        }

        inner class StateDefinitionBuilder<S : STATE>(stateClass: KClass<S>) {
            abstract inner class EventHandlerBuilder(val state: S, val cause: EVENT) {
                internal val sideEffects = mutableListOf<SIDE_EFFECT>()

                fun emit(vararg sideEffect: SIDE_EFFECT) {
                    sideEffects.addAll(sideEffect)
                }
            }

            inner class OnEnterEventHandlerBuilder(state: S, cause: EVENT, val previousState: STATE) :
                EventHandlerBuilder(state, cause)

            inner class OnExitEventHandlerBuilder(state: S, cause: EVENT, val newState: STATE) :
                EventHandlerBuilder(state, cause)

            inner class TransitionBuilder<E : EVENT>(val currentState: S, val event: E) {
                fun transitionTo(state: STATE, vararg sideEffect: SIDE_EFFECT) =
                    Graph.State.TransitionTo.TransitionToState(state, sideEffect.asIterable())

                inline fun <reified S : STATE> transitionTo(vararg sideEffect: SIDE_EFFECT) =
                    Graph.State.TransitionTo.TransitionToGroup(S::class, sideEffect.asIterable())

                fun dontTransition(vararg sideEffect: SIDE_EFFECT) = transitionTo(currentState, *sideEffect)
            }

            private val stateDefinition = Graph.State<STATE, EVENT, SIDE_EFFECT>(stateClass)

            inline fun <reified E : EVENT> any(): Matcher<EVENT, E> = Matcher.any()

            inline fun <reified R : EVENT> eq(value: R): Matcher<EVENT, R> = Matcher.eq(value)

            fun <E : EVENT> on(
                eventMatcher: Matcher<EVENT, E>,
                createTransitionTo: TransitionBuilder<E>.() -> Graph.State.TransitionTo<out STATE, out SIDE_EFFECT>
            ) {
                stateDefinition.transitions[eventMatcher] = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    createTransitionTo(TransitionBuilder(state as S, event as E))
                }
            }

            inline fun <reified E : EVENT> on(
                noinline createTransitionTo: TransitionBuilder<E>.() -> Graph.State.TransitionTo<out STATE, out SIDE_EFFECT>
            ) {
                return on(any(), createTransitionTo)
            }

            inline fun <reified E : EVENT> on(
                event: E,
                noinline createTransitionTo: TransitionBuilder<E>.() -> Graph.State.TransitionTo<out STATE, out SIDE_EFFECT>
            ) {
                return on(eq(event), createTransitionTo)
            }

            fun onEnter(listener: OnEnterEventHandlerBuilder.() -> Unit) = with(stateDefinition) {
                onEnterListeners.add { state, cause, fromState ->
                    @Suppress("UNCHECKED_CAST")
                    OnEnterEventHandlerBuilder(state as S, cause, fromState)
                        .apply(listener)
                        .sideEffects
                }
            }

            fun onExit(listener: OnExitEventHandlerBuilder.() -> Unit) = with(stateDefinition) {
                onExitListeners.add { state, cause, toState ->
                    @Suppress("UNCHECKED_CAST")
                    OnExitEventHandlerBuilder(state as S, cause, toState)
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
