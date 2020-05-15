package com.tinder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class StateMachineTest {

    class MatterStateMachine {

        private val logger = LoggerMock()

        private val stateMachine = StateMachine.create<State, Event, SideEffect> {
            initialState(State.Solid)
            state<State.Solid> {
                on<Event.OnMelted> {
                    transitionTo(State.Liquid, SideEffect.LogMelted)
                }
            }
            state<State.Liquid> {
                on<Event.OnFrozen> {
                    transitionTo(State.Solid, SideEffect.LogFrozen)
                }
                on<Event.OnVaporized> {
                    transitionTo(State.Gas, SideEffect.LogVaporized)
                }
            }
            state<State.Gas> {
                on<Event.OnCondensed> {
                    transitionTo(State.Liquid, SideEffect.LogCondensed)
                }
            }
            onTransition {
                val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
                when (validTransition.sideEffect) {
                    SideEffect.LogMelted -> logger.log(ON_MELTED_MESSAGE)
                    SideEffect.LogFrozen -> logger.log(ON_FROZEN_MESSAGE)
                    SideEffect.LogVaporized -> logger.log(ON_VAPORIZED_MESSAGE)
                    SideEffect.LogCondensed -> logger.log(ON_CONDENSED_MESSAGE)
                }
            }
        }

        @Test
        fun initialState_shouldBeSolid() {
            // Then
            assertEquals(State.Solid, stateMachine.state)
        }

        @Test
        fun givenStateIsSolid_onMelted_shouldTransitionToLiquidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Solid)

            // When
            val transition = stateMachine.transition(Event.OnMelted)

            // Then
            assertEquals(State.Liquid, stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(State.Solid, Event.OnMelted, State.Liquid, SideEffect.LogMelted),
                    transition)
            assertEquals(ON_MELTED_MESSAGE, logger.lastMessage)
        }

        @Test
        fun givenStateIsLiquid_onFroze_shouldTransitionToSolidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Liquid)

            // When
            val transition = stateMachine.transition(Event.OnFrozen)

            // Then
            assertEquals(State.Solid, stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(State.Liquid, Event.OnFrozen, State.Solid, SideEffect.LogFrozen),
                    transition)
            assertEquals(ON_FROZEN_MESSAGE, logger.lastMessage)
        }

        @Test
        fun givenStateIsLiquid_onVaporized_shouldTransitionToGasStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Liquid)

            // When
            val transition = stateMachine.transition(Event.OnVaporized)

            // Then
            assertEquals(State.Gas, stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(State.Liquid, Event.OnVaporized, State.Gas, SideEffect.LogVaporized),
                    transition)
            assertEquals(ON_VAPORIZED_MESSAGE, logger.lastMessage)
        }

        @Test
        fun givenStateIsGas_onCondensed_shouldTransitionToLiquidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Gas)

            // When
            val transition = stateMachine.transition(Event.OnCondensed)

            // Then
            assertEquals(State.Liquid, stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(State.Gas, Event.OnCondensed, State.Liquid, SideEffect.LogCondensed),
                    transition)
            assertEquals(ON_CONDENSED_MESSAGE, logger.lastMessage)
        }

        private fun givenStateIs(state: State): StateMachine<State, Event, SideEffect> {
            return stateMachine.with { initialState(state) }
        }

        companion object {
            const val ON_MELTED_MESSAGE = "I melted"
            const val ON_FROZEN_MESSAGE = "I froze"
            const val ON_VAPORIZED_MESSAGE = "I vaporized"
            const val ON_CONDENSED_MESSAGE = "I condensed"

            sealed class State {
                object Solid : State()
                object Liquid : State()
                object Gas : State()
            }

            sealed class Event {
                object OnMelted : Event()
                object OnFrozen : Event()
                object OnVaporized : Event()
                object OnCondensed : Event()
            }

            sealed class SideEffect {
                object LogMelted : SideEffect()
                object LogFrozen : SideEffect()
                object LogVaporized : SideEffect()
                object LogCondensed : SideEffect()
            }
            //Utility class to mock logger due to Kotlin Multiplatform limitations
            class LoggerMock {
                lateinit var lastMessage: String;
                fun log(message: String) {
                    lastMessage = message
                }
            }
        }
    }

    class TurnstileStateMachine {

        private val stateMachine = StateMachine.create<State, Event, Command> {
            initialState(State.Locked(credit = 0))
            state<State.Locked> {
                on<Event.InsertCoin> {
                    val newCredit = credit + it.value
                    if (newCredit >= FARE_PRICE) {
                        transitionTo(State.Unlocked, Command.OpenDoors)
                    } else {
                        transitionTo(State.Locked(newCredit))
                    }
                }
                on<Event.AdmitPerson> {
                    dontTransition(Command.SoundAlarm)
                }
                on<Event.MachineDidFail> {
                    transitionTo(State.Broken(this), Command.OrderRepair)
                }
            }
            state<State.Unlocked> {
                on<Event.AdmitPerson> {
                    transitionTo(State.Locked(credit = 0), Command.CloseDoors)
                }
            }
            state<State.Broken> {
                on<Event.MachineRepairDidComplete> {
                    transitionTo(oldState)
                }
            }
        }

        @Test
        fun initialState_shouldBeLocked() {
            // Then
            assertEquals(State.Locked(credit = 0), stateMachine.state)
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditLessThanFairPrice_shouldTransitionToLockedState() {
            // When
            val transition = stateMachine.transition(Event.InsertCoin(10))

            // Then
            assertEquals(State.Locked(credit = 10), stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Locked(credit = 0),
                    Event.InsertCoin(10),
                    State.Locked(credit = 10),
                    null),
                    transition)
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditEqualsFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.InsertCoin(15))

            // Then
            assertEquals(State.Unlocked, stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.InsertCoin(15),
                    State.Unlocked,
                    Command.OpenDoors),
                    transition)
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditMoreThanFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.InsertCoin(20))

            // Then
            assertEquals(State.Unlocked, stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.InsertCoin(20),
                    State.Unlocked,
                    Command.OpenDoors),
                    transition)
        }

        @Test
        fun givenStateIsLocked_whenAdmitPerson_shouldTransitionToLockedStateAndSoundAlarm() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.AdmitPerson)

            // Then
            assertEquals(State.Locked(credit = 35), stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.AdmitPerson,
                    State.Locked(credit = 35),
                    Command.SoundAlarm),
                    transition)
        }

        @Test
        fun givenStateIsLocked_whenMachineDidFail_shouldTransitionToBrokenStateAndOrderRepair() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 15))

            // When
            val transitionToBroken = stateMachine.transition(Event.MachineDidFail)

            // Then
            assertEquals(State.Broken(oldState = State.Locked(credit = 15)), stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Locked(credit = 15),
                    Event.MachineDidFail,
                    State.Broken(oldState = State.Locked(credit = 15)),
                    Command.OrderRepair),
                    transitionToBroken)
        }

        @Test
        fun givenStateIsUnlocked_whenAdmitPerson_shouldTransitionToLockedStateAndCloseDoors() {
            // Given
            val stateMachine = givenStateIs(State.Unlocked)

            // When
            val transition = stateMachine.transition(Event.AdmitPerson)

            // Then
            assertEquals(State.Locked(credit = 0), stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Unlocked,
                    Event.AdmitPerson,
                    State.Locked(credit = 0),
                    Command.CloseDoors),
                    transition)
        }

        @Test
        fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState() {
            // Given
            val stateMachine = givenStateIs(State.Broken(oldState = State.Locked(credit = 15)))

            // When
            val transition = stateMachine.transition(Event.MachineRepairDidComplete)

            // Then
            assertEquals(State.Locked(credit = 15), stateMachine.state)
            assertEquals(StateMachine.Transition.Valid(
                    State.Broken(oldState = State.Locked(credit = 15)),
                    Event.MachineRepairDidComplete,
                    State.Locked(credit = 15),
                    null),
                    transition)
        }

        private fun givenStateIs(state: State): StateMachine<State, Event, Command> {
            return stateMachine.with { initialState(state) }
        }

        companion object {
            private const val FARE_PRICE = 50

            sealed class State {
                data class Locked(val credit: Int) : State()
                object Unlocked : State()
                data class Broken(val oldState: State) : State()
            }

            sealed class Event {
                data class InsertCoin(val value: Int) : Event()
                object AdmitPerson : Event()
                object MachineDidFail : Event()
                object MachineRepairDidComplete : Event()
            }

            sealed class Command {
                object SoundAlarm : Command()
                object CloseDoors : Command()
                object OpenDoors : Command()
                object OrderRepair : Command()
            }
        }
    }

    class ObjectStateMachine {

        class WithInitialState {

            private val onTransitionListener1 = TransitionListenerMock<(StateMachine.Transition<State, Event, SideEffect>)>()
            private val onTransitionListener2 = TransitionListenerMock<(StateMachine.Transition<State, Event, SideEffect>)>()
            private val onStateAExitListener1 = StateListenerMock<State, Event>()
            private val onStateAExitListener2 = StateListenerMock<State, Event>()
            private val onStateCEnterListener1 = StateListenerMock<State, Event>()
            private val onStateCEnterListener2 = StateListenerMock<State, Event>()
            private val stateMachine = StateMachine.create<State, Event, SideEffect> {
                initialState(State.A)
                state<State.A> {
                    onExit(onStateAExitListener1::invoke)
                    onExit(onStateAExitListener2::invoke)
                    on<Event.E1> {
                        transitionTo(State.B)
                    }
                    on<Event.E2> {
                        transitionTo(State.C)
                    }
                    on<Event.E4> {
                        transitionTo(State.D)
                    }
                }
                state<State.B> {
                    on<Event.E3> {
                        transitionTo(State.C, SideEffect.SE1)
                    }
                }
                state<State.C> {
                    on<Event.E4> {
                        dontTransition()
                    }
                    onEnter(onStateCEnterListener1::invoke)
                    onEnter(onStateCEnterListener2::invoke)
                }
                onTransition(onTransitionListener1::invoke)
                onTransition(onTransitionListener2::invoke)
            }

            @Test
            fun state_shouldReturnInitialState() {
                // When
                val state = stateMachine.state

                // Then
                assertEquals(State.A, state)
            }

            @Test
            fun transition_givenValidEvent_shouldReturnTransition() {
                // When
                val transitionFromStateAToStateB = stateMachine.transition(Event.E1)

                // Then
                assertEquals(StateMachine.Transition.Valid(State.A, Event.E1, State.B, null),
                        transitionFromStateAToStateB)

                // When
                val transitionFromStateBToStateC = stateMachine.transition(Event.E3)

                // Then
                assertEquals(StateMachine.Transition.Valid(State.B, Event.E3, State.C, SideEffect.SE1),
                        transitionFromStateBToStateC)
            }

            @Test
            fun transition_givenValidEvent_shouldCreateAndSetNewState() {
                // When
                stateMachine.transition(Event.E1)

                // Then
                assertEquals(State.B, stateMachine.state)

                // When
                stateMachine.transition(Event.E3)

                // Then
                assertEquals(State.C, stateMachine.state)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnStateChangeListener() {
                // When
                stateMachine.transition(Event.E1)

                // Then
                assertEquals(StateMachine.Transition.Valid(State.A, Event.E1, State.B, null),
                        onTransitionListener1.callers.last())

                // When
                stateMachine.transition(Event.E3)

                // Then
                assertEquals(StateMachine.Transition.Valid(State.B, Event.E3, State.C, SideEffect.SE1),
                        onTransitionListener2.callers.last())

                // When
                stateMachine.transition(Event.E4)

                // Then
                assertEquals(StateMachine.Transition.Valid(State.C, Event.E4, State.C, null),
                        onTransitionListener2.callers.last())
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnEnterListeners() {
                // When
                stateMachine.transition(Event.E2)

                // Then
                assertEquals(listOf(State.C, Event.E2), onStateCEnterListener1.callers.last())
                assertEquals(listOf(State.C, Event.E2), onStateCEnterListener2.callers.last())
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnExitListeners() {
                // When
                stateMachine.transition(Event.E2)

                // Then
                assertEquals(listOf(State.A, Event.E2), onStateAExitListener1.callers.last())
                assertEquals(listOf(State.A, Event.E2), onStateAExitListener2.callers.last())
            }

            @Test
            fun transition_givenInvalidEvent_shouldReturnInvalidTransition() {
                // When
                val fromState = stateMachine.state
                val transition = stateMachine.transition(Event.E3)

                // Then
                assertEquals(StateMachine.Transition.Invalid<State, Event, SideEffect>(State.A, Event.E3), transition)

                assertEquals(fromState, stateMachine.state)
            }

            @Test
            fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
                // Then
                assertFailsWith<IllegalStateException> { stateMachine.transition(Event.E4) }
            }
        }

        class WithoutInitialState {

            @Test
            fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
                // Then
                assertFailsWith<IllegalArgumentException> { StateMachine.create<State, Event, SideEffect> {} }
            }
        }

        private companion object {
            private sealed class State {
                object A : State()
                object B : State()
                object C : State()
                object D : State()
            }

            private sealed class Event {
                object E1 : Event()
                object E2 : Event()
                object E3 : Event()
                object E4 : Event()
            }

            private sealed class SideEffect {
                object SE1 : SideEffect()
                object SE2 : SideEffect()
                object SE3 : SideEffect()
            }
        }
    }

    class ConstantStateMachine {

        class WithInitialState {

            private val onTransitionListener1 = TransitionListenerMock<(StateMachine.Transition<String, Int, String>)>();
            private val onTransitionListener2 = TransitionListenerMock<(StateMachine.Transition<String, Int, String>)>()
            private val onStateCEnterListener1 = StateListenerMock<String, Int>()
            private val onStateCEnterListener2 = StateListenerMock<String, Int>()
            private val onStateAExitListener1 = StateListenerMock<String, Int>()
            private val onStateAExitListener2 = StateListenerMock<String, Int>()
            private val stateMachine = StateMachine.create<String, Int, String> {
                initialState(STATE_A)
                state(STATE_A) {
                    onExit(onStateAExitListener1::invoke)
                    onExit(onStateAExitListener2::invoke)
                    on(EVENT_1) {
                        transitionTo(STATE_B)
                    }
                    on(EVENT_2) {
                        transitionTo(STATE_C)
                    }
                    on(EVENT_4) {
                        transitionTo(STATE_D)
                    }
                }
                state(STATE_B) {
                    on(EVENT_3) {
                        transitionTo(STATE_C, SIDE_EFFECT_1)
                    }
                }
                state(STATE_C) {
                    onEnter(onStateCEnterListener1::invoke)
                    onEnter(onStateCEnterListener2::invoke)
                }
                onTransition(onTransitionListener1::invoke)
                onTransition(onTransitionListener2::invoke)
            }

            @Test
            fun state_shouldReturnInitialState() {
                // When
                val state = stateMachine.state

                // Then
                assertEquals(STATE_A, state)
            }

            @Test
            fun transition_givenValidEvent_shouldReturnTrue() {
                // When
                val transitionFromStateAToStateB = stateMachine.transition(EVENT_1)

                // Then
                assertEquals(StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B, null),
                        transitionFromStateAToStateB)

                // When
                val transitionFromStateBToStateC = stateMachine.transition(EVENT_3)

                // Then
                assertEquals(StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C, SIDE_EFFECT_1),
                        transitionFromStateBToStateC)
            }

            @Test
            fun transition_givenValidEvent_shouldCreateAndSetNewState() {
                // When
                stateMachine.transition(EVENT_1)

                // Then
                assertEquals(STATE_B, stateMachine.state)

                // When
                stateMachine.transition(EVENT_3)

                // Then
                assertEquals(STATE_C, stateMachine.state)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnStateChangeListener() {
                // When
                stateMachine.transition(EVENT_1)

                // Then
                assertEquals(StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B, null), onTransitionListener1.callers.last())

                // When
                stateMachine.transition(EVENT_3)

                // Then
                assertEquals(StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C, SIDE_EFFECT_1), onTransitionListener2.callers.last())
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnEnterListeners() {
                // When
                stateMachine.transition(EVENT_2)

                // Then
                assertEquals(listOf(STATE_C, EVENT_2), onStateCEnterListener1.callers.last())
                assertEquals(listOf(STATE_C, EVENT_2), onStateCEnterListener2.callers.last())
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnExitListeners() {
                // When
                stateMachine.transition(EVENT_2)

                // Then
                assertEquals(listOf(STATE_A, EVENT_2), onStateAExitListener1.callers.last())
                assertEquals(listOf(STATE_A, EVENT_2), onStateAExitListener2.callers.last())
            }

            @Test
            fun transition_givenInvalidEvent_shouldReturnInvalidTransition() {
                // When
                val fromState = stateMachine.state
                val transition = stateMachine.transition(EVENT_3)

                // Then
                assertEquals(StateMachine.Transition.Invalid<String, Int, String>(STATE_A, EVENT_3),
                        transition)
                assertEquals(fromState, stateMachine.state)
            }

            @Test
            fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
                // Then
                assertFailsWith<IllegalStateException> { stateMachine.transition(EVENT_4) }
            }
        }

        class WithoutInitialState {

            @Test
            fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
                // Then
                assertFailsWith<IllegalArgumentException> { StateMachine.create<String, Int, String> {} }
            }
        }

        class WithMissingStateDefinition {

            private val stateMachine = StateMachine.create<String, Int, Nothing> {
                initialState(STATE_A)
                state(STATE_A) {
                    on(EVENT_1) {
                        transitionTo(STATE_B)
                    }
                }
                // Missing STATE_B definition.
            }

            @Test
            fun transition_givenMissingDestinationStateDefinition_shouldThrowIllegalStateExceptionWithStateName() {
                // Then
                val exception = assertFailsWith<IllegalStateException> { stateMachine.transition(EVENT_1) }
                assertEquals("Missing definition for state ${STATE_B::class.simpleName}!", exception.message)
            }
        }

        private companion object {
            private const val STATE_A = "a"
            private const val STATE_B = "b"
            private const val STATE_C = "c"
            private const val STATE_D = "d"

            private const val EVENT_1 = 1
            private const val EVENT_2 = 2
            private const val EVENT_3 = 3
            private const val EVENT_4 = 4

            private const val SIDE_EFFECT_1 = "alpha"
        }
    }
    //Utility classes to mock listeners due to Kotlin Multiplatform limitations
    private class TransitionListenerMock<T> {
        val callers = mutableListOf<T>()
        fun invoke(p1: T) {
            callers.add(p1);
        }
    }

    private class StateListenerMock<T, K> {
        val callers = mutableListOf<List<*>>()
        fun invoke(p1: T, p2: K) {
            callers.add(listOf(p1, p2));
        }
    }
}
