# StateMachine

[![CircleCI](https://circleci.com/gh/Tinder/StateMachine.svg?style=svg)](https://circleci.com/gh/Tinder/StateMachine)

A state machine library in Kotlin and Swift.

`StateMachine` is used in [Scarlet](https://github.com/Tinder/Scarlet)

## Usage

In this example, we create a `StateMachine` from the following state diagram.

![State Diagram](./example/activity-diagram.png)

### Kotlin

Define states, events and side effects:

~~~kotlin
sealed class State {
    object Solid : State()
    object Liquid : State()
    object Gas : State()
}

sealed class Event {
    object OnMelted : Event()
    object OnFroze : Event()
    object OnVaporized : Event()
    object OnCondensed : Event()
}

sealed class SideEffect {
    object LogMelted : SideEffect()
    object LogFrozen : SideEffect()
    object LogVaporized : SideEffect()
    object LogCondensed : SideEffect()
}
~~~

Initialize state machine and declare state transitions:

~~~kotlin
val stateMachine = StateMachine.create<State, Event, SideEffect> {
    initialState(State.Solid)
    state<State.Solid> {
        on<Event.OnMelted> {
            transitionTo(State.Liquid, SideEffect.LogMelted)
        }
    }
    state<State.Liquid> {
        on<Event.OnFroze> {
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
~~~

Perform state transitions:

~~~kotlin
assertThat(stateMachine.state).isEqualTo(Solid)

// When
val transition = stateMachine.transition(OnMelted)

// Then
assertThat(stateMachine.state).isEqualTo(Liquid)
assertThat(transition).isEqualTo(
    StateMachine.Transition.Valid(Solid, OnMelted, Liquid, LogMelted)
)
then(logger).should().log(ON_MELTED_MESSAGE)
~~~

### Swift

Inherit `StateMachineBuilder` to gain access to the DSL builder methods:

```swift
class MyExample: StateMachineBuilder {

    ... state machine implementation ...
}
```

Define states, events and side effects:

```swift
enum State: StateMachineHashable {
    case solid, liquid, gas
}

enum Event: StateMachineHashable {
    case melt, freeze, vaporize, condense
}

enum SideEffect {
    case logMelted, logFrozen, logVaporized, logCondensed
}
```

Initialize state machine and declare state transitions:

```swift
let stateMachine = MatterStateMachine {
    initialState(.solid)
    state(.solid) {
        on(.melt) {
            transition(to: .liquid, emit: .logMelted)
        }
    }
    state(.liquid) {
        on(.freeze) {
            transition(to: .solid, emit: .logFrozen)
        }
        on(.vaporize) {
            transition(to: .gas, emit: .logVaporized)
        }
    }
    state(.gas) {
        on(.condense) {
            transition(to: .liquid, emit: .logCondensed)
        }
    }
    onTransition {
        guard case let .success(transition) = $0, let sideEffect = transition.sideEffect else { return }
        switch sideEffect {
        case .logMelted: logger.log(Message.melted)
        case .logFrozen: logger.log(Message.frozen)
        case .logVaporized: logger.log(Message.vaporized)
        case .logCondensed: logger.log(Message.condensed)
        }
    }
}
```

Perform state transitions:

```swift
expect(stateMachine.state).to(equal(.solid))

// When
let transition = try stateMachine.transition(.melt)

// Then
expect(stateMachine.state).to(equal(.liquid))
expect(transition).to(equal(
    StateMachine.Transition.Valid(fromState: .solid, event: .melt, toState: .liquid, sideEffect: .logMelted))
)
expect(logger).to(log(Message.melted))
```

## Examples

### Matter State Machine

- [Kotlin](https://github.com/Tinder/StateMachine/blob/9ba046313703f37db749466b4a3504caaea2888c/src/test/kotlin/com/tinder/StateMachineTest.kt#L18-L47)
- [Swift](https://github.com/Tinder/StateMachine/blob/9ba046313703f37db749466b4a3504caaea2888c/Swift/Tests/StateMachineTests/StateMachine_Matter_Tests.swift#L40-L69)

### Turnstile State Machine

- [Kotlin](https://github.com/Tinder/StateMachine/blob/9ba046313703f37db749466b4a3504caaea2888c/src/test/kotlin/com/tinder/StateMachineTest.kt#L157-L185)
- [Swift](https://github.com/Tinder/StateMachine/blob/9ba046313703f37db749466b4a3504caaea2888c/Swift/Tests/StateMachineTests/StateMachine_Turnstile_Tests.swift#L36-L64)

NOTE: Due to Swift using enumerations (as opposed to sealed classes in Kotlin), 
any Swift enumeration taking advantage of associated values will require [additional boilerplate implementation](https://github.com/Tinder/StateMachine/blob/9ba046313703f37db749466b4a3504caaea2888c/Swift/Tests/StateMachineTests/StateMachine_Turnstile_Tests.swift#L198-L260).

## Kotlin Download

`StateMachine` is available in Maven Central.

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

### Maven

```xml
<dependency>
    <groupId>com.tinder.statemachine</groupId>
    <artifactId>statemachine</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.tinder.statemachine:statemachine:0.2.0'
```

## Swift Installation

### Swift Package Manager

```
dependencies: [
    .package(url: "https://github.com/Tinder/StateMachine.git", from: "0.0.1")
]
```

### Cocoapods

```
pod 'StateMachine', :git => 'https://github.com/Tinder/StateMachine.git'
```

## Visualization

Thanks to @nvinayshetty, you can visualize your state machines right in the IDE using the [State Arts](https://github.com/nvinayshetty/StateArts) Intellij [plugin](https://plugins.jetbrains.com/plugin/12193-state-art).

## License
~~~
Copyright (c) 2018, Match Group, LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Match Group, LLC nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL MATCH GROUP, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
~~~

[latest-jar]: https://tinder.com/
[snap]: https://oss.sonatype.org/content/repositories/snapshots/
