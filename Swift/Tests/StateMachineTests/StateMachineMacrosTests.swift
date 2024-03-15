//
//  Copyright (c) 2019, Match Group, LLC
//  BSD License, see LICENSE file for details
//

#if canImport(StateMachineMacros)

import StateMachineMacros
import SwiftSyntaxMacros
import SwiftSyntaxMacrosTestSupport
import XCTest

final class StateMachineMacrosTests: XCTestCase {

    private let macros: [String: Macro.Type] = ["StateMachineHashable": StateMachineHashableMacro.self]

    func testTypeMustBeEnumDiagnostic() {
        assertMacroExpansion(
          """
          @StateMachineHashable
          struct Example {}
          """,
          expandedSource: """
          struct Example {}
          """,
          diagnostics: [DiagnosticSpec(message: "Type Must Be Enumeration", line: 1, column: 1)],
          macros: macros
        )
    }

    func testEnumMustHaveCasesDiagnostic() {
        assertMacroExpansion(
          """
          @StateMachineHashable
          enum Example {}
          """,
          expandedSource: """
          enum Example {}
          """,
          diagnostics: [DiagnosticSpec(message: "Enumeration Must Have Cases", line: 1, column: 1)],
          macros: macros
        )
    }

    func testStateMachineHashableMacro() {
        assertMacroExpansion(
          """
          @StateMachineHashable
          enum Example {

              case case0, case1(Any), case2(Any, Any)
          }
          """,
          expandedSource: """
          enum Example {

              case case0, case1(Any), case2(Any, Any)
          }

          extension Example: StateMachineHashable {

              enum HashableIdentifier {

                  case case0
                  case case1
                  case case2
              }

              var hashableIdentifier: HashableIdentifier {
                  switch self {
                  case .case0:
                      return .case0
                  case .case1:
                      return .case1
                  case .case2:
                      return .case2
                  }
              }

              var associatedValue: Any {
                  switch self {
                  case .case0:
                      return ()
                  case let .case1(value):
                      return (value)
                  case let .case2(value1, value2):
                      return (value1, value2)
                  }
              }
          }
          """,
          macros: macros
        )
    }
}

#endif
