package com.tinder

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions
import org.junit.Test

class NestedStateMachineTest {
    private val defaultOnExitListener = mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default>.EventHandlerBuilder.() -> Unit>()
    private val defaultOnEnterListener = mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default>.EventHandlerBuilder.() -> Unit>()

    private val lockedOnExitListener = mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default.Locked>.EventHandlerBuilder.() -> Unit>()
    private val lockedOnEnterListener = mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default.Locked>.EventHandlerBuilder.() -> Unit>()

    private val stateMachine = StateMachine.create<State, Event, Command> {
        initialState(State.Default.Locked(credit = 0))
        state<State.Default> {
            onEnter(defaultOnEnterListener)
            onExit(defaultOnExitListener)
            on<Event.MachineDidFail> {
                transitionTo(State.Broken, Command.OrderRepair)
            }
        }
        state<State.Default.Locked> {
            onEnter(lockedOnEnterListener)
            onExit(lockedOnExitListener)
            on<Event.InsertCoin> {
                val newCredit = currentState.credit + event.value
                if (newCredit >= FARE_PRICE) {
                    transitionTo(State.Default.Unlocked, Command.OpenDoors)
                } else {
                    transitionTo(State.Default.Locked(newCredit))
                }
            }
            on<Event.AdmitPerson> {
                dontTransition(Command.SoundAlarm)
            }
        }
        state<State.Default.Unlocked> {
            on<Event.AdmitPerson> {
                transitionTo(State.Default.Locked(credit = 0), Command.CloseDoors)
            }
        }
        state<State.Broken> {
            on<Event.MachineRepairDidComplete> {
                transitionTo<State.Default>()
            }
        }
    }

    @Test
    fun initialState_shouldBeLocked() {
        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Locked(credit = 0))
    }

    @Test
    fun givenStateIsLocked_whenInsertCoin_andCreditLessThanFairPrice_shouldTransitionToLockedState() {
        // When
        val transition = stateMachine.transition(Companion.Event.InsertCoin(10))

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Locked(credit = 10))
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid<Companion.State, Companion.Event, Companion.Command>(
//                        Companion.State.Default.Locked(credit = 0),
//                        Companion.Event.InsertCoin(10),
//                        Companion.State.Default.Locked(credit = 10)
//                )
//        )
    }

    @Test
    fun givenStateIsLocked_whenInsertCoin_andCreditEqualsFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 35))

        // When
        val transition = stateMachine.transition(Companion.Event.InsertCoin(15))

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Unlocked)
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid(
//                        Companion.State.Default.Locked(credit = 35),
//                        Companion.Event.InsertCoin(15),
//                        Companion.State.Default.Unlocked,
//                        Companion.Command.OpenDoors
//                )
//        )
    }

    @Test
    fun givenStateIsLocked_whenInsertCoin_andCreditMoreThanFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 35))

        // When
        val transition = stateMachine.transition(Companion.Event.InsertCoin(20))

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Unlocked)
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid(
//                        Companion.State.Default.Locked(credit = 35),
//                        Companion.Event.InsertCoin(20),
//                        Companion.State.Default.Unlocked,
//                        Companion.Command.OpenDoors
//                )
//        )
    }

    @Test
    fun givenStateIsLocked_whenAdmitPerson_shouldTransitionToLockedStateAndSoundAlarm() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 35))

        clearInvocations(lockedOnEnterListener, lockedOnExitListener)

        // When
        val transition = stateMachine.transition(Companion.Event.AdmitPerson)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Locked(credit = 35))
        inOrder(lockedOnEnterListener, lockedOnExitListener) {
            verify(lockedOnExitListener).invoke(any())
            verify(lockedOnEnterListener).invoke(any())
        }
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid(
//                        Companion.State.Default.Locked(credit = 35),
//                        Companion.Event.AdmitPerson,
//                        Companion.State.Default.Locked(credit = 35),
//                        Companion.Command.SoundAlarm
//                )
//        )
    }

    @Test
    fun givenStateIsLocked_whenMachineDidFail_shouldTransitionToBrokenStateAndOrderRepair() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 15))

        // When
        val transitionToBroken = stateMachine.transition(Companion.Event.MachineDidFail)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Broken)
//        Assertions.assertThat(transitionToBroken).isEqualTo(
//                StateMachine.Transition.Valid(
//                        Companion.State.Default.Locked(credit = 15),
//                        Companion.Event.MachineDidFail,
//                        Companion.State.Broken,
//                        Companion.Command.OrderRepair
//                )
//        )
    }

    @Test
    fun givenStateIsUnlocked_whenAdmitPerson_shouldTransitionToLockedStateAndCloseDoors() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Unlocked)

        // When
        val transition = stateMachine.transition(Companion.Event.AdmitPerson)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Locked(credit = 0))
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid(
//                        Companion.State.Default.Unlocked,
//                        Companion.Event.AdmitPerson,
//                        Companion.State.Default.Locked(credit = 0),
//                        Companion.Command.CloseDoors
//                )
//        )
    }

    @Test
    fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 15))
        stateMachine.transition(Event.MachineDidFail)

        // When
        val transition = stateMachine.transition(Companion.Event.MachineRepairDidComplete)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Locked(credit = 15))
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid<State, Event, Command>(
//                        Companion.State.Broken,
//                        Companion.Event.MachineRepairDidComplete,
//                        Companion.State.Default.Locked(credit = 15)
//                )
//        )
    }

    @Test
    fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState2() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 15))
        stateMachine.transition(Event.InsertCoin(15))
        stateMachine.transition(Event.InsertCoin(20))
        stateMachine.transition(Event.MachineDidFail)

        // When
        val transition = stateMachine.transition(Companion.Event.MachineRepairDidComplete)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Unlocked)
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid<State, Event, Command>(
//                        Companion.State.Broken,
//                        Companion.Event.MachineRepairDidComplete,
//                        Companion.State.Default.Locked(credit = 15)
//                )
//        )
    }

    @Test
    fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState3() {
        // Given
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 15))
        stateMachine.transition(Event.InsertCoin(15))
        stateMachine.transition(Event.InsertCoin(20))
        stateMachine.transition(Event.MachineDidFail)
        stateMachine.transition(Companion.Event.MachineRepairDidComplete)
        stateMachine.transition(Event.MachineDidFail)

        // When
        stateMachine.transition(Companion.Event.MachineRepairDidComplete)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Default.Unlocked)
//        Assertions.assertThat(transition).isEqualTo(
//                StateMachine.Transition.Valid<State, Event, Command>(
//                        Companion.State.Broken,
//                        Companion.Event.MachineRepairDidComplete,
//                        Companion.State.Default.Locked(credit = 15)
//                )
//        )
    }

    @Test
    fun onExitShouldBeCalledInOrder() {
        val stateMachine = givenStateIs(Companion.State.Default.Locked(credit = 15))

        // When
        stateMachine.transition(Event.MachineDidFail)

        // Then
        Assertions.assertThat(stateMachine.state.currentState).isEqualTo(Companion.State.Broken)
        inOrder(lockedOnExitListener, defaultOnExitListener) {
            verify(lockedOnExitListener).invoke(any())
            verify(defaultOnExitListener).invoke(any())
        }
    }

    private fun givenStateIs(state: Companion.State): StateMachine<Companion.State, Companion.Event, Companion.Command> {
        return stateMachine.with { initialState(state) }
    }

    companion object {
        private const val FARE_PRICE = 50

        sealed class State {
            sealed class Default : State() {
                data class Locked(val credit: Int) : Default()
                object Unlocked : Default()
            }
            object Broken : State()
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
