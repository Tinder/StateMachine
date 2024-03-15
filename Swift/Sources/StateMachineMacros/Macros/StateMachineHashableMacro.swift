//
//  Copyright (c) 2019, Match Group, LLC
//  BSD License, see LICENSE file for details
//

import SwiftSyntax
import SwiftSyntaxBuilder
import SwiftSyntaxMacros

public struct StateMachineHashableMacro: ExtensionMacro {

    public static func expansion(
        of node: AttributeSyntax,
        attachedTo declaration: some DeclGroupSyntax,
        providingExtensionsOf type: some TypeSyntaxProtocol,
        conformingTo protocols: [TypeSyntax],
        in context: some MacroExpansionContext
    ) throws -> [ExtensionDeclSyntax] {

        guard let enumDecl: EnumDeclSyntax = .init(declaration)
        else { throw StateMachineHashableMacroError.typeMustBeEnumeration }

        let elements: [EnumCaseElementSyntax] = enumDecl
            .memberBlock
            .members
            .compactMap(MemberBlockItemSyntax.init)
            .map(\.decl)
            .compactMap(EnumCaseDeclSyntax.init)
            .flatMap(\.elements)

        guard !elements.isEmpty
        else { throw StateMachineHashableMacroError.enumerationMustHaveCases }

        let enumCases: [String] = elements
            .map(\.name.text)
            .map { "case \($0)" }

        let hashableIdentifierCases: [String] = elements
            .map(\.name.text)
            .map { "case .\($0):\nreturn .\($0)" }

        let associatedValueCases: [String] = elements.map { element in
            if let parameters: EnumCaseParameterListSyntax = element.parameterClause?.parameters, !parameters.isEmpty {
                if parameters.count > 1 {
                    let associatedValues: String = (1...parameters.count)
                        .map { "value\($0)" }
                        .joined(separator: ", ")
                    return """
                                case let .\(element.name.text)(\(associatedValues)):
                                    return (\(associatedValues))
                        """
                } else {
                    return """
                                case let .\(element.name.text)(value):
                                    return (value)
                        """
                }
            } else {
                return """
                            case .\(element.name.text):
                                return ()
                    """
            }
        }

        let node: SyntaxNodeString = """
            extension \(type): StateMachineHashable {

                enum HashableIdentifier {

                    \(raw: enumCases.joined(separator: "\n"))
                }

                var hashableIdentifier: HashableIdentifier {
                    switch self {
            \(raw: hashableIdentifierCases.joined(separator: "\n"))
                    }
                }

                var associatedValue: Any {
                    switch self {
            \(raw: associatedValueCases.joined(separator: "\n"))
                    }
                }
            }
            """

        return try [ExtensionDeclSyntax(node)]
    }
}
