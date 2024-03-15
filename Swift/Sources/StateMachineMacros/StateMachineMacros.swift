//
//  Copyright (c) 2019, Match Group, LLC
//  BSD License, see LICENSE file for details
//

#if canImport(SwiftCompilerPlugin)

import SwiftCompilerPlugin
import SwiftSyntaxMacros

@main
internal struct StateMachineMacros: CompilerPlugin {

    internal let providingMacros: [Macro.Type] = [StateMachineHashableMacro.self]
}

#endif
