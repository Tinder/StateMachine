//
//  Copyright (c) 2019, Match Group, LLC
//  BSD License, see LICENSE file for details
//

public enum StateMachineHashableMacroError: Error, CustomStringConvertible {

    case typeMustBeEnumeration
    case enumerationMustHaveCases
    case invalidExtension

    public var description: String {
        switch self {
        case .typeMustBeEnumeration:
            return "Type Must Be Enumeration"
        case .enumerationMustHaveCases:
            return "Enumeration Must Have Cases"
        case .invalidExtension:
            return "Invalid Extension"
        }
    }
}
