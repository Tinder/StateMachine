//
//  Created by Christopher Fuller on 12/21/19.
//  Copyright © 2019 Tinder. All rights reserved.
//

import Nimble
@testable import StateMachine
import XCTest

final class StateMachine_Matter_Tests: XCTestCase, StateMachineBuilder {

    @StateMachineHashable
    enum State {

        case solid, liquid, gas
    }

    @StateMachineHashable
    enum Event {

        case melt, freeze, vaporize, condense
    }

    enum SideEffect {

        case logMelted, logFrozen, logVaporized, logCondensed
    }

    typealias MatterStateMachine = StateMachine<State, Event, SideEffect>
    typealias ValidTransition = MatterStateMachine.Transition.Valid
    typealias InvalidTransition = MatterStateMachine.Transition.Invalid

    enum Message {

        static let melted: String = "I melted"
        static let frozen: String = "I froze"
        static let vaporized: String = "I vaporized"
        static let condensed: String = "I condensed"
    }

    static func matterStateMachine(withInitialState _state: State, logger: Logger) -> MatterStateMachine {
        MatterStateMachine {
            initialState(_state)
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
    }

    var logger: Logger!

    override func setUp() {
        super.setUp()
        logger = .init()
    }

    override func tearDown() {
        logger = nil
        super.tearDown()
    }

    func givenState(is state: State) -> MatterStateMachine {
        let stateMachine: MatterStateMachine = Self.matterStateMachine(withInitialState: state, logger: logger)
        expect(stateMachine.state).to(equal(state))
        return stateMachine
    }

    func test_givenStateIsSolid_whenMelted_shouldTransitionToLiquidState() throws {

        // Given
        let stateMachine: MatterStateMachine = givenState(is: .solid)

        // When
        let transition: ValidTransition = try stateMachine.transition(.melt)

        // Then
        expect(stateMachine.state).to(equal(.liquid))
        expect(transition).to(equal(ValidTransition(fromState: .solid,
                                                    event: .melt,
                                                    toState: .liquid,
                                                    sideEffect: .logMelted)))
        expect(self.logger).to(log(Message.melted))
    }

    func test_givenStateIsSolid_whenFrozen_shouldThrowInvalidTransitionError() throws {

        // Given
        let stateMachine: MatterStateMachine = givenState(is: .solid)

        // When
        let transition: () throws -> ValidTransition = {
            try stateMachine.transition(.freeze)
        }

        // Then
        expect(transition).to(throwError { error in
            expect(error).to(beAKindOf(InvalidTransition.self))
        })
    }

    func test_givenStateIsLiquid_whenFrozen_shouldTransitionToSolidState() throws {

        // Given
        let stateMachine: MatterStateMachine = givenState(is: .liquid)

        // When
        let transition: ValidTransition = try stateMachine.transition(.freeze)

        // Then
        expect(stateMachine.state).to(equal(.solid))
        expect(transition).to(equal(ValidTransition(fromState: .liquid,
                                                    event: .freeze,
                                                    toState: .solid,
                                                    sideEffect: .logFrozen)))
        expect(self.logger).to(log(Message.frozen))
    }

    func test_givenStateIsLiquid_whenVaporized_shouldTransitionToGasState() throws {

        // Given
        let stateMachine: MatterStateMachine = givenState(is: .liquid)

        // When
        let transition: ValidTransition = try stateMachine.transition(.vaporize)

        // Then
        expect(stateMachine.state).to(equal(.gas))
        expect(transition).to(equal(ValidTransition(fromState: .liquid,
                                                    event: .vaporize,
                                                    toState: .gas,
                                                    sideEffect: .logVaporized)))
        expect(self.logger).to(log(Message.vaporized))
    }

    func test_givenStateIsGas_whenCondensed_shouldTransitionToLiquidState() throws {

        // Given
        let stateMachine: MatterStateMachine = givenState(is: .gas)

        // When
        let transition: ValidTransition = try stateMachine.transition(.condense)

        // Then
        expect(stateMachine.state).to(equal(.liquid))
        expect(transition).to(equal(ValidTransition(fromState: .gas,
                                                    event: .condense,
                                                    toState: .liquid,
                                                    sideEffect: .logCondensed)))
        expect(self.logger).to(log(Message.condensed))
    }
}
