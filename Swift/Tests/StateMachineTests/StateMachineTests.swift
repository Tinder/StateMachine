//
//  Copyright (c) 2019, Match Group, LLC
//  BSD License, see LICENSE file for details
//

import Nimble
@testable import StateMachine
import XCTest

final class StateMachineTests: XCTestCase, StateMachineBuilder {

    enum State: StateMachineHashable {

        case stateOne, stateTwo
    }

    enum Event: StateMachineHashable {

        case eventOne, eventTwo
    }

    enum SideEffect {

        case commandOne, commandTwo, commandThree
    }

    typealias TestStateMachine = StateMachine<State, Event, SideEffect>
    typealias ValidTransition = TestStateMachine.Transition.Valid
    typealias InvalidTransition = TestStateMachine.Transition.Invalid

    static func testStateMachine(withInitialState _state: State) -> TestStateMachine {
        TestStateMachine {
            initialState(_state)
            state(.stateOne) {
                on(.eventOne) {
                    dontTransition(emit: .commandOne)
                }
                on(.eventTwo) {
                    transition(to: .stateTwo, emit: .commandTwo)
                }
            }
            state(.stateTwo) {
                on(.eventTwo) {
                    dontTransition(emit: .commandThree)
                }
            }
        }
    }

    func givenState(is state: State) -> TestStateMachine {
        let stateMachine: TestStateMachine = Self.testStateMachine(withInitialState: state)
        expect(stateMachine.state).to(equal(state))
        return stateMachine
    }

    func testDontTransition() throws {

        // Given
        let stateMachine: TestStateMachine = givenState(is: .stateOne)

        // When
        let transition: ValidTransition = try stateMachine.transition(.eventOne)

        // Then
        expect(stateMachine.state).to(equal(.stateOne))
        expect(transition).to(equal(ValidTransition(fromState: .stateOne,
                                                    event: .eventOne,
                                                    toState: .stateOne,
                                                    sideEffect: .commandOne)))
    }

    func testTransition() throws {

        // Given
        let stateMachine: TestStateMachine = givenState(is: .stateOne)

        // When
        let transition: ValidTransition = try stateMachine.transition(.eventTwo)

        // Then
        expect(stateMachine.state).to(equal(.stateTwo))
        expect(transition).to(equal(ValidTransition(fromState: .stateOne,
                                                    event: .eventTwo,
                                                    toState: .stateTwo,
                                                    sideEffect: .commandTwo)))
    }

    func testInvalidTransition() throws {

        // Given
        let stateMachine: TestStateMachine = givenState(is: .stateTwo)

        // When
        let transition: () throws -> ValidTransition = {
            try stateMachine.transition(.eventOne)
        }

        // Then
        expect(transition).to(throwError { error in
            expect(error).to(beAKindOf(InvalidTransition.self))
        })
    }

    func testObservation() throws {

        var results: [Result<ValidTransition, InvalidTransition>] = []

        // Given
        let stateMachine: TestStateMachine = givenState(is: .stateOne)
            .startObserving(self) {
                results.append($0.mapError { $0 as! InvalidTransition })
            }

        // When
        try stateMachine.transition(.eventOne)
        try stateMachine.transition(.eventTwo)
        let transition: () throws -> ValidTransition = {
            try stateMachine.transition(.eventOne)
        }

        // Then
        expect(transition).to(throwError { error in
            expect(error).to(beAKindOf(InvalidTransition.self))
        })

        // When
        try stateMachine.transition(.eventTwo)

        // Then
        expect(results).to(equal([
            .success(ValidTransition(fromState: .stateOne,
                                     event: .eventOne,
                                     toState: .stateOne,
                                     sideEffect: .commandOne)),
            .success(ValidTransition(fromState: .stateOne,
                                     event: .eventTwo,
                                     toState: .stateTwo,
                                     sideEffect: .commandTwo)),
            .failure(InvalidTransition()),
            .success(ValidTransition(fromState: .stateTwo,
                                     event: .eventTwo,
                                     toState: .stateTwo,
                                     sideEffect: .commandThree))
        ]))
    }

    func testStopObservation() throws {

        var transitionCount: Int = 0

        // Given
        let stateMachine: TestStateMachine = givenState(is: .stateOne)
            .startObserving(self) { _ in
                transitionCount += 1
            }

        // When
        try stateMachine.transition(.eventOne)
        try stateMachine.transition(.eventOne)

        // Then
        expect(transitionCount).to(equal(2))

        // When
        stateMachine.stopObserving(self)
        try stateMachine.transition(.eventOne)
        try stateMachine.transition(.eventOne)

        // Then
        expect(transitionCount).to(equal(2))
    }

    func testRecursionDetectedError() throws {

        var error: TestStateMachine.StateMachineError? = nil

        // Given
        let stateMachine: TestStateMachine = givenState(is: .stateOne)

        stateMachine.startObserving(self) { [unowned stateMachine] _ in
            do {
                try stateMachine.transition(.eventOne)
            } catch let e as TestStateMachine.StateMachineError {
                error = e
            } catch {}
        }

        // When
        try stateMachine.transition(.eventOne)

        // Then
        expect(error).to(equal(.recursionDetected))
    }
}

final class Logger {

    private(set) var messages: [String] = []

    func log(_ message: String) {
        messages.append(message)
    }
}

func log(_ expectedMessages: String...) -> Matcher<Logger> {
    let expectedString: String = stringify(expectedMessages.joined(separator: "\\n"))
    return Matcher {
        let actualMessages: [String]? = try $0.evaluate()?.messages
        let actualString: String = stringify(actualMessages?.joined(separator: "\\n"))
        let message: ExpectationMessage = .expectedCustomValueTo("log <\(expectedString)>",
                                                                 actual: "<\(actualString)>")
        return MatcherResult(bool: actualMessages == expectedMessages, message: message)
    }
}
