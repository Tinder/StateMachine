//
//  Copyright (c) 2024, Match Group, LLC
//  BSD License, see LICENSE file for details
//

@attached(extension,
          conformances: StateMachineHashable,
          names: named(hashableIdentifier), named(HashableIdentifier), named(associatedValue))
public macro StateMachineHashable() = #externalMacro(module: "StateMachineMacros",
                                                     type: "StateMachineHashableMacro")
