package com.tinder

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.then
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
internal class StateMachineTest {

    class MatterStateMachine {

        private val logger = mock<Logger>()
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
            assertThat(stateMachine.state).isEqualTo(State.Solid)
        }

        @Test
        fun givenStateIsSolid_onMelted_shouldTransitionToLiquidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Solid)

            // When
            val transition = stateMachine.transition(Event.OnMelted)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Liquid)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Solid, Event.OnMelted, State.Liquid, SideEffect.LogMelted)
            )
            then(logger).should().log(ON_MELTED_MESSAGE)
        }

        @Test
        fun givenStateIsLiquid_onFroze_shouldTransitionToSolidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Liquid)

            // When
            val transition = stateMachine.transition(Event.OnFrozen)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Solid)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Liquid, Event.OnFrozen, State.Solid, SideEffect.LogFrozen)
            )
            then(logger).should().log(ON_FROZEN_MESSAGE)
        }

        @Test
        fun givenStateIsLiquid_onVaporized_shouldTransitionToGasStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Liquid)

            // When
            val transition = stateMachine.transition(Event.OnVaporized)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Gas)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Liquid, Event.OnVaporized, State.Gas, SideEffect.LogVaporized)
            )
            then(logger).should().log(ON_VAPORIZED_MESSAGE)
        }

        @Test
        fun givenStateIsGas_onCondensed_shouldTransitionToLiquidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Gas)

            // When
            val transition = stateMachine.transition(Event.OnCondensed)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Liquid)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Gas, Event.OnCondensed, State.Liquid, SideEffect.LogCondensed)
            )
            then(logger).should().log(ON_CONDENSED_MESSAGE)
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

            interface Logger {
                fun log(message: String)
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
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 0))
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditLessThanFairPrice_shouldTransitionToLockedState() {
            // When
            val transition = stateMachine.transition(Event.InsertCoin(10))

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 10))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 0),
                    Event.InsertCoin(10),
                    State.Locked(credit = 10),
                    null
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditEqualsFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.InsertCoin(15))

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Unlocked)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.InsertCoin(15),
                    State.Unlocked,
                    Command.OpenDoors
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditMoreThanFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.InsertCoin(20))

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Unlocked)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.InsertCoin(20),
                    State.Unlocked,
                    Command.OpenDoors
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenAdmitPerson_shouldTransitionToLockedStateAndSoundAlarm() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.AdmitPerson)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 35))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.AdmitPerson,
                    State.Locked(credit = 35),
                    Command.SoundAlarm
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenMachineDidFail_shouldTransitionToBrokenStateAndOrderRepair() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 15))

            // When
            val transitionToBroken = stateMachine.transition(Event.MachineDidFail)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Broken(oldState = State.Locked(credit = 15)))
            assertThat(transitionToBroken).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 15),
                    Event.MachineDidFail,
                    State.Broken(oldState = State.Locked(credit = 15)),
                    Command.OrderRepair
                )
            )
        }

        @Test
        fun givenStateIsUnlocked_whenAdmitPerson_shouldTransitionToLockedStateAndCloseDoors() {
            // Given
            val stateMachine = givenStateIs(State.Unlocked)

            // When
            val transition = stateMachine.transition(Event.AdmitPerson)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 0))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Unlocked,
                    Event.AdmitPerson,
                    State.Locked(credit = 0),
                    Command.CloseDoors
                )
            )
        }

        @Test
        fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState() {
            // Given
            val stateMachine = givenStateIs(State.Broken(oldState = State.Locked(credit = 15)))

            // When
            val transition = stateMachine.transition(Event.MachineRepairDidComplete)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 15))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Broken(oldState = State.Locked(credit = 15)),
                    Event.MachineRepairDidComplete,
                    State.Locked(credit = 15),
                    null
                )
            )
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

    @RunWith(Enclosed::class)
    class ObjectStateMachine {

        class WithInitialState {

            private val onTransitionListener1 = mock<(StateMachine.Transition<State, Event, SideEffect>) -> Unit>()
            private val onTransitionListener2 = mock<(StateMachine.Transition<State, Event, SideEffect>) -> Unit>()
            private val onStateAExitListener1 = mock<State.(Event) -> Unit>()
            private val onStateAExitListener2 = mock<State.(Event) -> Unit>()
            private val onStateCEnterListener1 = mock<State.(Event) -> Unit>()
            private val onStateCEnterListener2 = mock<State.(Event) -> Unit>()
            private val stateMachine = StateMachine.create<State, Event, SideEffect> {
                initialState(State.A)
                state<State.A> {
                    onExit(onStateAExitListener1)
                    onExit(onStateAExitListener2)
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
                    onEnter(onStateCEnterListener1)
                    onEnter(onStateCEnterListener2)
                }
                onTransition(onTransitionListener1)
                onTransition(onTransitionListener2)
            }

            @Test
            fun state_shouldReturnInitialState() {
                // When
                val state = stateMachine.state

                // Then
                assertThat(state).isEqualTo(State.A)
            }

            @Test
            fun transition_givenValidEvent_shouldReturnTransition() {
                // When
                val transitionFromStateAToStateB = stateMachine.transition(Event.E1)

                // Then
                assertThat(transitionFromStateAToStateB).isEqualTo(
                    StateMachine.Transition.Valid(State.A, Event.E1, State.B, null)
                )

                // When
                val transitionFromStateBToStateC = stateMachine.transition(Event.E3)

                // Then
                assertThat(transitionFromStateBToStateC).isEqualTo(
                    StateMachine.Transition.Valid(State.B, Event.E3, State.C, SideEffect.SE1)
                )
            }

            @Test
            fun transition_givenValidEvent_shouldCreateAndSetNewState() {
                // When
                stateMachine.transition(Event.E1)

                // Then
                assertThat(stateMachine.state).isEqualTo(State.B)

                // When
                stateMachine.transition(Event.E3)

                // Then
                assertThat(stateMachine.state).isEqualTo(State.C)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnStateChangeListener() {
                // When
                stateMachine.transition(Event.E1)

                // Then
                then(onTransitionListener1).should().invoke(
                    StateMachine.Transition.Valid(State.A, Event.E1, State.B, null)
                )

                // When
                stateMachine.transition(Event.E3)

                // Then
                then(onTransitionListener2).should()
                    .invoke(StateMachine.Transition.Valid(State.B, Event.E3, State.C, SideEffect.SE1))

                // When
                stateMachine.transition(Event.E4)

                // Then
                then(onTransitionListener2).should()
                    .invoke(StateMachine.Transition.Valid(State.C, Event.E4, State.C, null))
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnEnterListeners() {
                // When
                stateMachine.transition(Event.E2)

                // Then
                then(onStateCEnterListener1).should().invoke(State.C, Event.E2)
                then(onStateCEnterListener2).should().invoke(State.C, Event.E2)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnExitListeners() {
                // When
                stateMachine.transition(Event.E2)

                // Then
                then(onStateAExitListener1).should().invoke(State.A, Event.E2)
                then(onStateAExitListener2).should().invoke(State.A, Event.E2)
            }

            @Test
            fun transition_givenInvalidEvent_shouldReturnInvalidTransition() {
                // When
                val fromState = stateMachine.state
                val transition = stateMachine.transition(Event.E3)

                // Then
                assertThat(transition).isEqualTo(
                    StateMachine.Transition.Invalid<State, Event, SideEffect>(State.A, Event.E3)
                )
                assertThat(stateMachine.state).isEqualTo(fromState)
            }

            @Test
            fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
                // Then
                assertThatIllegalStateException()
                    .isThrownBy {
                        stateMachine.transition(Event.E4)
                    }
            }
        }

        class WithoutInitialState {

            @Test
            fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
                // Then
                assertThatIllegalArgumentException().isThrownBy {
                    StateMachine.create<State, Event, SideEffect> {}
                }
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

    @RunWith(Enclosed::class)
    class ConstantStateMachine {

        class WithInitialState {

            private val onTransitionListener1 = mock<(StateMachine.Transition<String, Int, String>) -> Unit>()
            private val onTransitionListener2 = mock<(StateMachine.Transition<String, Int, String>) -> Unit>()
            private val onStateCEnterListener1 = mock<String.(Int) -> Unit>()
            private val onStateCEnterListener2 = mock<String.(Int) -> Unit>()
            private val onStateAExitListener1 = mock<String.(Int) -> Unit>()
            private val onStateAExitListener2 = mock<String.(Int) -> Unit>()
            private val stateMachine = StateMachine.create<String, Int, String> {
                initialState(STATE_A)
                state(STATE_A) {
                    onExit(onStateAExitListener1)
                    onExit(onStateAExitListener2)
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
                    onEnter(onStateCEnterListener1)
                    onEnter(onStateCEnterListener2)
                }
                onTransition(onTransitionListener1)
                onTransition(onTransitionListener2)
            }

            @Test
            fun state_shouldReturnInitialState() {
                // When
                val state = stateMachine.state

                // Then
                assertThat(state).isEqualTo(STATE_A)
            }

            @Test
            fun transition_givenValidEvent_shouldReturnTrue() {
                // When
                val transitionFromStateAToStateB = stateMachine.transition(EVENT_1)

                // Then
                assertThat(transitionFromStateAToStateB).isEqualTo(
                    StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B, null)
                )

                // When
                val transitionFromStateBToStateC = stateMachine.transition(EVENT_3)

                // Then
                assertThat(transitionFromStateBToStateC).isEqualTo(
                    StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C, SIDE_EFFECT_1)
                )
            }

            @Test
            fun transition_givenValidEvent_shouldCreateAndSetNewState() {
                // When
                stateMachine.transition(EVENT_1)

                // Then
                assertThat(stateMachine.state).isEqualTo(STATE_B)

                // When
                stateMachine.transition(EVENT_3)

                // Then
                assertThat(stateMachine.state).isEqualTo(STATE_C)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnStateChangeListener() {
                // When
                stateMachine.transition(EVENT_1)

                // Then
                then(onTransitionListener1).should().invoke(
                    StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B, null)
                )

                // When
                stateMachine.transition(EVENT_3)

                // Then
                then(onTransitionListener2).should().invoke(
                    StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C, SIDE_EFFECT_1)
                )
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnEnterListeners() {
                // When
                stateMachine.transition(EVENT_2)

                // Then
                then(onStateCEnterListener1).should().invoke(STATE_C, EVENT_2)
                then(onStateCEnterListener2).should().invoke(STATE_C, EVENT_2)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnExitListeners() {
                // When
                stateMachine.transition(EVENT_2)

                // Then
                then(onStateAExitListener1).should().invoke(STATE_A, EVENT_2)
                then(onStateAExitListener2).should().invoke(STATE_A, EVENT_2)
            }

            @Test
            fun transition_givenInvalidEvent_shouldReturnInvalidTransition() {
                // When
                val fromState = stateMachine.state
                val transition = stateMachine.transition(EVENT_3)

                // Then
                assertThat(transition).isEqualTo(
                    StateMachine.Transition.Invalid<String, Int, String>(STATE_A, EVENT_3)
                )
                assertThat(stateMachine.state).isEqualTo(fromState)
            }

            @Test
            fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
                // Then
                assertThatIllegalStateException()
                    .isThrownBy {
                        stateMachine.transition(EVENT_4)
                    }
            }
        }

        class WithoutInitialState {

            @Test
            fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
                // Then
                assertThatIllegalArgumentException().isThrownBy {
                    StateMachine.create<String, Int, String> {}
                }
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
                assertThatIllegalStateException()
                    .isThrownBy { stateMachine.transition(EVENT_1) }
                    .withMessage("Missing definition for state ${STATE_B.javaClass.simpleName}!")
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

}
