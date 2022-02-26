//
//  Created by Christopher Fuller on 12/21/19.
//  Copyright © 2019 Tinder. All rights reserved.
//

import Nimble
@testable import StateMachine
import XCTest

final class StateMachine_Matter_Tests: XCTestCase, StateMachineBuilder {

    enum State: StateMachineHashable {

        case solid, liquid, gas
    }

    enum Event: StateMachineHashable {

        case melt, freeze, vaporize, condense
    }

    enum SideEffect {

        case logMelted, logFrozen, logVaporized, logCondensed
    }

    typealias MatterStateMachine = StateMachine<State, Event, SideEffect>
    typealias ValidTransition = MatterStateMachine.Transition.Valid
    typealias InvalidTransition = MatterStateMachine.Transition.Invalid

    enum Message: String {

        case melted = "I melted"
        case frozen = "I froze"
        case vaporized = "I vaporized"
        case condensed = "I condensed"
        case enteredSolid
        case exitedSolid
        case enteredLiquid
        case exitedLiquid
        case enteredGas
        case exitedGas
    }

    static func matterStateMachine(withInitialState _state: State, logger: Logger) -> MatterStateMachine {
        MatterStateMachine {
            initialState(_state)
            state(.solid) {
                onEnter { _ in
                    logger.log(Message.enteredSolid.rawValue)
                }
                onExit { _ in
                    logger.log(Message.exitedSolid.rawValue)
                }
                on(.melt) {
                    transition(to: .liquid, emit: .logMelted)
                }
            }
            state(.liquid) {
                onEnter { _ in
                    logger.log(Message.enteredLiquid.rawValue)
                }
                onExit { _ in
                    logger.log(Message.exitedLiquid.rawValue)
                }
                on(.freeze) {
                    transition(to: .solid, emit: .logFrozen)
                }
                on(.vaporize) {
                    transition(to: .gas, emit: .logVaporized)
                }
            }
            state(.gas) {
                onEnter { _ in
                    logger.log(Message.enteredGas.rawValue)
                }
                onExit { _ in
                    logger.log(Message.exitedGas.rawValue)
                }
                on(.condense) {
                    transition(to: .liquid, emit: .logCondensed)
                }
            }
            onTransition {
                guard case let .success(transition) = $0 else { return }
                transition.sideEffects.forEach { sideEffect in
                    switch sideEffect {
                    case .logMelted: logger.log(Message.melted.rawValue)
                    case .logFrozen: logger.log(Message.frozen.rawValue)
                    case .logVaporized: logger.log(Message.vaporized.rawValue)
                    case .logCondensed: logger.log(Message.condensed.rawValue)
                    }
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
                                                    sideEffects: [.logMelted])))
        expect(self.logger).to(log(Message.exitedSolid.rawValue, Message.enteredLiquid.rawValue, Message.melted.rawValue))
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
                                                    sideEffects: [.logFrozen])))
        expect(self.logger).to(log(Message.exitedLiquid.rawValue, Message.enteredSolid.rawValue, Message.frozen.rawValue))
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
                                                    sideEffects: [.logVaporized])))
        expect(self.logger).to(log(Message.exitedLiquid.rawValue, Message.enteredGas.rawValue, Message.vaporized.rawValue))
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
                                                    sideEffects: [.logCondensed])))
        expect(self.logger).to(log(Message.exitedGas.rawValue, Message.enteredLiquid.rawValue, Message.condensed.rawValue))
    }
}
