package com.tinder

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions
import org.junit.Test

class StateMachineTest {
    private val defaultOnExitListener =
        mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default>.EventHandlerBuilder.() -> Unit>()
    private val defaultOnEnterListener =
        mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default>.EventHandlerBuilder.() -> Unit>()

    private val lockedOnExitListener =
        mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default.Locked>.EventHandlerBuilder.() -> Unit>()
    private val lockedOnEnterListener =
        mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Default.Locked>.EventHandlerBuilder.() -> Unit>()

    private val brokenOnExitListener =
        mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Broken>.EventHandlerBuilder.() -> Unit>()
    private val brokenOnEnterListener =
        mock<StateMachine.GraphBuilder<State, Event, Command>.StateDefinitionBuilder<State.Broken>.EventHandlerBuilder.() -> Unit>()

    private val stateMachine = StateMachine.create<State, Event, Command> {
        initialState(State.Default.Locked(credit = 0))
        state<State.Default> {
            onEnter(defaultOnEnterListener)
            onEnter {
                emit(Command.PlayJingle)
            }
            onExit(defaultOnExitListener)
            on<Event.MachineDidFail> {
                transitionTo(State.Broken, Command.OrderRepair)
            }
        }
        state<State.Default.Locked> {
            onEnter(lockedOnEnterListener)
            onEnter {
                emit(Command.SetDisplay("Please insert coin"))
            }
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
            onEnter {
                emit(Command.SetDisplay("Please step in"))
            }
            on<Event.AdmitPerson> {
                transitionTo(State.Default.Locked(credit = 0), Command.CloseDoors)
            }
        }
        state<State.Broken> {
            onEnter(brokenOnEnterListener)
            onEnter {
                emit(Command.SetRotatingRedLightStatus(true))
                emit(Command.SetDisplay("Technician informed"))
            }
            onExit(brokenOnExitListener)
            onExit {
                emit(Command.SetRotatingRedLightStatus(false))
            }
            on<Event.MachineRepairDidComplete> {
                transitionTo<State.Default>()
            }
        }
    }

    @Test
    fun initialState_shouldBeLocked() {
        // Then
        Assertions.assertThat(stateMachine.state.state).isEqualTo(State.Default.Locked(credit = 0))
    }

    @Test
    fun givenStateIsLocked_whenInsertCoin_andCreditLessThanFairPrice_shouldTransitionToLockedState() {
        // When
        val (newState, transition) = stateMachine.transition(Event.InsertCoin(10))

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Locked(credit = 10))
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid<State, Event, Command>(
                State.Default.Locked(credit = 0),
                Event.InsertCoin(10),
                State.Default.Locked(credit = 10),
                Command.SetDisplay("Please insert coin")
            )
        )
    }

    @Test
    fun givenStateIsLocked_whenInsertCoin_andCreditEqualsFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
        // Given
        val stateMachine = givenStateIs(State.Default.Locked(credit = 35))

        // When
        val (newState, transition) = stateMachine.transition(Event.InsertCoin(15))

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Unlocked)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(
                State.Default.Locked(credit = 35),
                Event.InsertCoin(15),
                State.Default.Unlocked,
                Command.OpenDoors,
                Command.SetDisplay("Please step in")
            )
        )
    }

    @Test
    fun givenStateIsLocked_whenInsertCoin_andCreditMoreThanFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
        // Given
        val stateMachine = givenStateIs(State.Default.Locked(credit = 35))

        // When
        val (newState, transition) = stateMachine.transition(Event.InsertCoin(20))

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Unlocked)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(
                State.Default.Locked(credit = 35),
                Event.InsertCoin(20),
                State.Default.Unlocked,
                Command.OpenDoors,
                Command.SetDisplay("Please step in")
            )
        )
    }

    @Test
    fun givenStateIsLocked_whenAdmitPerson_shouldTransitionToLockedStateAndSoundAlarm() {
        // Given
        val stateMachine = givenStateIs(State.Default.Locked(credit = 35))

        clearInvocations(lockedOnEnterListener, lockedOnExitListener)

        // When
        val (newState, transition) = stateMachine.transition(Event.AdmitPerson)

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Locked(credit = 35))
        inOrder(lockedOnEnterListener, lockedOnExitListener) {
            verify(lockedOnExitListener).invoke(any())
            verify(lockedOnEnterListener).invoke(any())
        }
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(
                State.Default.Locked(credit = 35),
                Event.AdmitPerson,
                State.Default.Locked(credit = 35),
                Command.SoundAlarm,
                Command.SetDisplay("Please insert coin")
            )
        )
    }

    @Test
    fun givenStateIsLocked_whenMachineDidFail_shouldTransitionToBrokenStateAndOrderRepair() {
        // Given
        val stateMachine = givenStateIs(State.Default.Locked(credit = 15))

        // When
        val (newState, transitionToBroken) = stateMachine.transition(Event.MachineDidFail)

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Broken)
        Assertions.assertThat(transitionToBroken).isEqualTo(
            StateMachine.Transition.Valid(
                State.Default.Locked(credit = 15),
                Event.MachineDidFail,
                State.Broken,
                Command.OrderRepair,
                Command.SetRotatingRedLightStatus(true),
                Command.SetDisplay("Technician informed")
            )
        )
    }

    @Test
    fun givenStateIsUnlocked_whenAdmitPerson_shouldTransitionToLockedStateAndCloseDoors() {
        // Given
        val stateMachine = givenStateIs(State.Default.Unlocked)

        // When
        val (newState, transition) = stateMachine.transition(Event.AdmitPerson)

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Locked(credit = 0))
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(
                State.Default.Unlocked,
                Event.AdmitPerson,
                State.Default.Locked(credit = 0),
                Command.CloseDoors,
                Command.SetDisplay("Please insert coin")
            )
        )
    }

    @Test
    fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState() {
        // Given
        val stateMachine = givenStateIs(State.Default.Locked(credit = 15))
            .transition(Event.MachineDidFail).first

        // When
        val (newState, transition) = stateMachine.transition(Event.MachineRepairDidComplete)

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Locked(credit = 15))
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid<State, Event, Command>(
                State.Broken,
                Event.MachineRepairDidComplete,
                State.Default.Locked(credit = 15),
                Command.SetRotatingRedLightStatus(false),
                Command.PlayJingle,
                Command.SetDisplay("Please insert coin")
            )
        )
    }

    @Test
    fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToUnlocked() {
        // Given
        val stateMachine = givenStateIs(State.Default.Locked(credit = 15))
            .transition(Event.InsertCoin(15)).first
            .transition(Event.InsertCoin(20)).first
            .transition(Event.MachineDidFail).first

        // When
        val (newState, transition) = stateMachine.transition(Event.MachineRepairDidComplete)

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Default.Unlocked)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid<State, Event, Command>(
                State.Broken,
                Event.MachineRepairDidComplete,
                State.Default.Unlocked,
                Command.SetRotatingRedLightStatus(false),
                Command.PlayJingle,
                Command.SetDisplay("Please step in")
            )
        )
    }

    @Test
    fun givenStateIsBroken_whenMachineRepairDidComplete_shouldErrorIfNoHistory() {
        // Given
        Assertions.assertThatThrownBy {
            givenStateIs(State.Broken).transition(Event.MachineRepairDidComplete)
        }
            .hasMessage("no history entry and no initial state for class com.tinder.StateMachineTest\$Companion\$State\$Broken defined")
    }

    @Test
    fun listenersShouldBeCalledInOrder() {
        val stateMachine = givenStateIs(State.Default.Locked(credit = 15))

        // When
        val (newState, _) = stateMachine.transition(Event.MachineDidFail)

        // Then
        Assertions.assertThat(newState.state.state).isEqualTo(State.Broken)
        inOrder(lockedOnExitListener, defaultOnExitListener, brokenOnEnterListener) {
            verify(lockedOnExitListener).invoke(any())
            verify(defaultOnExitListener).invoke(any())
            verify(brokenOnEnterListener).invoke(any())
        }
    }

    private fun givenStateIs(state: State): StateMachine<State, Event, Command> {
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
            object PlayJingle : Command()
            data class SetRotatingRedLightStatus(val on: Boolean) : Command()
            data class SetDisplay(val text: String) : Command()
        }
    }
}
