//
//  Created by Christopher Fuller on 12/21/19.
//  Copyright © 2019 Tinder. All rights reserved.
//

open class StateMachine<State: StateMachineHashable, Event: StateMachineHashable, SideEffect> {

    public typealias FunctionBuilder = StateMachineTypes.StateMachineFunctionBuilder

    public typealias Definition = StateMachineTypes.StateMachineDefinition<State, Event, SideEffect>
    public typealias InitialState = StateMachineTypes.InitialState<State>
    public typealias Component = StateMachineTypes.Component<State, Event, SideEffect>

    private typealias States = [State.HashableIdentifier: Events]
    private typealias Events = [Event.HashableIdentifier: Action.Factory]

    private typealias EventHandler = StateMachineTypes.EventHandler<State, Event, SideEffect>
    private typealias Action = StateMachineTypes.Action<State, Event, SideEffect>

    public enum Transition {

        public typealias Result = Swift.Result<Valid, Error>
        public typealias Callback = (_ result: Result) -> Void

        public struct Valid: CustomDebugStringConvertible {

            public let fromState: State
            public let event: Event
            public let toState: State
            public let sideEffect: SideEffect?

            public var debugDescription: String {
                if let sideEffect = sideEffect {
                    return "fromState: \(fromState), event: \(event), toState: \(toState), sideEffect: \(sideEffect)"
                } else {
                    return "fromState: \(fromState), event: \(event), toState: \(toState), sideEffect: nil"
                }
            }
        }

        public struct Invalid: Error, Equatable {}
    }

    public private(set) var state: State

    private let states: States
    private var observers: [(object: () -> AnyObject?, callback: Transition.Callback)] = []

    public init(@FunctionBuilder build: () -> Definition) {
        let definition: Definition = build()
        state = definition.initialState.state
        states = definition.states.reduce(into: States()) {
            $0[$1.state] = $1.events.reduce(into: Events()) {
                $0[$1.event] = $1.action
            }
        }
        observers = definition.callbacks.map { [unowned self] in
            (object: { self }, callback: $0)
        }
    }

    @discardableResult
    public func startObserving(_ observer: AnyObject?, callback: @escaping Transition.Callback) -> Self {
        guard let observer = observer else { return self }
        observers.append((object: { [weak observer] in observer }, callback: callback))
        return self
    }

    public func stopObserving(_ observers: AnyObject?...) {
        stopObserving(observers)
    }

    public func stopObserving(_ observers: [AnyObject?]) {
        self.observers.removeAll {
            guard let object = $0.object() else { return true }
            return observers.contains { $0 === object }
        }
    }

    @discardableResult
    public func transition(_ event: Event) throws -> Transition.Valid {
        let result: Transition.Result
        defer { notify(result) }
        do {
            let stateIdentifier: State.HashableIdentifier = state.hashableIdentifier
            let eventIdentifier: Event.HashableIdentifier = event.hashableIdentifier
            let factory: Action.Factory? = states[stateIdentifier]?[eventIdentifier]
            if let action: Action = try factory?(state, event) {
                let transition: Transition.Valid =
                    .init(fromState: state,
                          event: event,
                          toState: action.toState ?? state,
                          sideEffect: action.sideEffect)
                if let toState = action.toState {
                    state = toState
                }
                result = .success(transition)
            } else {
                result = .failure(Transition.Invalid())
            }
        } catch {
            result = .failure(error)
            throw error
        }
        return try result.get()
    }

    private func notify(_ result: Transition.Result) {
        var observers: [(object: () -> AnyObject?, callback: Transition.Callback)] = []
        for observer in self.observers {
            guard observer.object() != nil else { continue }
            observers.append(observer)
            observer.callback(result)
        }
        self.observers = observers
    }
}

extension StateMachine.Transition.Valid: Equatable where State: Equatable, Event: Equatable, SideEffect: Equatable {}

public protocol StateMachineBuilder {

    associatedtype State: StateMachineHashable
    associatedtype Event: StateMachineHashable

    associatedtype SideEffect

    typealias InitialState = StateMachineTypes.InitialState<State>
    typealias Component = StateMachineTypes.Component<State, Event, SideEffect>

    typealias StateFunctionBuilder = StateMachineTypes.StateFunctionBuilder

    typealias EventHandler = StateMachineTypes.EventHandler<State, Event, SideEffect>
    typealias Action = StateMachineTypes.Action<State, Event, SideEffect>
}

extension StateMachineBuilder {

    public static func initialState(
        _ state: State
    ) -> InitialState {
        InitialState(state: state)
    }

    public static func state(
        _ state: State.HashableIdentifier
    ) -> Component {
        .state(state: state, events: [])
    }

    public static func state(
        _ state: State.HashableIdentifier,
        @StateFunctionBuilder build: () -> [EventHandler]
    ) -> Component {
        .state(state: state, events: build())
    }

    public static func on(
        _ event: Event.HashableIdentifier,
        perform: @escaping (State, Event) throws -> Action
    ) -> [EventHandler] {
        [EventHandler(event: event, action: perform)]
    }

    public static func on(
        _ event: Event.HashableIdentifier,
        perform: @escaping (State) throws -> Action
    ) -> [EventHandler] {
        [EventHandler(event: event) { state, _ in try perform(state) }]
    }

    public static func on(
        _ event: Event.HashableIdentifier,
        perform: @escaping () throws -> Action
    ) -> [EventHandler] {
        [EventHandler(event: event) { _, _ in try perform() }]
    }

    public static func transition(
        to state: State,
        emit sideEffect: SideEffect? = nil
    ) -> Action {
        Action(toState: state, sideEffect: sideEffect)
    }

    public static func dontTransition(
        emit sideEffect: SideEffect? = nil
    ) -> Action {
        Action(toState: nil, sideEffect: sideEffect)
    }

    public static func onTransition(
        _ callback: @escaping StateMachine<State, Event, SideEffect>.Transition.Callback
    ) -> Component {
        .callback(callback: callback)
    }
}

public enum StateMachineTypes {

    @_functionBuilder
    public struct StateMachineFunctionBuilder {

        public static func buildBlock<State, Event, SideEffect>(
            _ initialState: InitialState<State>,
            _ components: Component<State, Event, SideEffect>...
        ) -> StateMachineDefinition<State, Event, SideEffect> {
            StateMachineDefinition(initialState: initialState, components: components)
        }
    }

    public struct StateMachineDefinition<State: StateMachineHashable, Event: StateMachineHashable, SideEffect> {

        fileprivate let initialState: InitialState<State>
        fileprivate let components: [Component<State, Event, SideEffect>]

        fileprivate typealias States = [(state: State.HashableIdentifier, events: [EventHandler<State, Event, SideEffect>])]
        fileprivate typealias Callbacks = [StateMachine<State, Event, SideEffect>.Transition.Callback]

        fileprivate var states: States {
            components.compactMap {
                guard case let .state(state, events) = $0 else { return nil }
                return (state: state, events: events)
            }
        }

        fileprivate var callbacks: Callbacks {
            components.compactMap {
                guard case let .callback(callback) = $0 else { return nil }
                return callback
            }
        }
    }

    public struct InitialState<State> {

        fileprivate let state: State
    }

    public enum Component<State: StateMachineHashable, Event: StateMachineHashable, SideEffect> {

        case state(state: State.HashableIdentifier, events: [EventHandler<State, Event, SideEffect>])
        case callback(callback: StateMachine<State, Event, SideEffect>.Transition.Callback)
    }

    @_functionBuilder
    public struct StateFunctionBuilder {

        public static func buildBlock<State, Event, SideEffect>(
            _ events: [EventHandler<State, Event, SideEffect>]...
        ) -> [EventHandler<State, Event, SideEffect>] {
            events.flatMap { $0 }
        }
    }

    public struct EventHandler<State: StateMachineHashable, Event: StateMachineHashable, SideEffect> {

        fileprivate let event: Event.HashableIdentifier
        fileprivate let action: Action<State, Event, SideEffect>.Factory
    }

    public struct Action<State: StateMachineHashable, Event: StateMachineHashable, SideEffect> {

        fileprivate typealias Factory = (State, Event) throws -> Self

        fileprivate let toState: State?
        fileprivate let sideEffect: SideEffect?
    }

    public struct IncorrectTypeError: Error, CustomDebugStringConvertible {

        public var debugDescription: String {
            "Incorrect Type: Expected <\(expectedType)> but encountered <\(encounteredType)>"
        }

        public let expectedType: Any.Type
        public let encounteredType: Any.Type
    }
}

@dynamicMemberLookup
public protocol StateMachineHashable {

    associatedtype HashableIdentifier: Hashable

    typealias IncorrectTypeError = StateMachineTypes.IncorrectTypeError

    var hashableIdentifier: HashableIdentifier { get }
    var associatedValue: Any { get }

    // TODO: [CF] Return T (instead of closure) once Swift supports throwing subscript
    subscript<T>(dynamicMember member: String) -> () throws -> T { get }
}

extension StateMachineHashable where Self: Hashable {

    public var hashableIdentifier: Self { self }
}

extension StateMachineHashable {

    public var associatedValue: Any { () }

    public subscript<T>(dynamicMember member: String) -> () throws -> T {
        { [associatedValue] in
            guard let value = associatedValue as? T else {
                throw IncorrectTypeError(expectedType: T.self,
                                         encounteredType: type(of: associatedValue))
            }
            return value
        }
    }
}
