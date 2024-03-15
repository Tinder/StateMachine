//
//  Copyright (c) 2024, Match Group, LLC
//  BSD License, see LICENSE file for details
//

public enum StateMachineHashableMacroError: Error, CustomStringConvertible {

    case typeMustBeEnum
    case enumMustHaveCases
    case invalidExtension

    public var description: String {
        switch self {
        case .typeMustBeEnum:
            return "Type Must Be Enum"
        case .enumMustHaveCases:
            return "Enum Must Have Cases"
        case .invalidExtension:
            return "Invalid Extension"
        }
    }
}
