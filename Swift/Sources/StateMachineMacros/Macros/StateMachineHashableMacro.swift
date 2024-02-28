//
//  Copyright (c) 2024, Match Group, LLC
//  BSD License, see LICENSE file for details
//

import SwiftSyntax
import SwiftSyntaxMacros

public struct StateMachineHashableMacro: ExtensionMacro {

    public static func expansion(
        of node: AttributeSyntax,
        attachedTo declaration: some DeclGroupSyntax,
        providingExtensionsOf type: some TypeSyntaxProtocol,
        conformingTo protocols: [TypeSyntax],
        in context: some MacroExpansionContext
    ) throws -> [ExtensionDeclSyntax] {

        guard let enumDecl: EnumDeclSyntax = declaration.as(EnumDeclSyntax.self)
        else { throw StateMachineHashableMacroError.typeMustBeEnum }

        let elements: [EnumCaseElementSyntax] = enumDecl
            .memberBlock
            .members
            .compactMap { $0.as(MemberBlockItemSyntax.self) }
            .map(\.decl)
            .compactMap { $0.as(EnumCaseDeclSyntax.self) }
            .flatMap(\.elements)

        guard !elements.isEmpty
        else { throw StateMachineHashableMacroError.enumMustHaveCases }

        let enumCases: [String] = elements
            .map(\.name.text)
            .map { "case \($0)" }

        let hashableIdentifierCases: [String] = elements
            .map(\.name.text)
            .map { "case .\($0):\nreturn .\($0)" }

        var associatedValueCases: [String] = []
        for element: EnumCaseElementSyntax in elements {
            if let parameters: EnumCaseParameterListSyntax = element.parameterClause?.parameters, !parameters.isEmpty {
                if parameters.count > 1 {
                    let associatedValues: String = (1...parameters.count)
                        .map { "value\($0)" }
                        .joined(separator: ", ")
                    let `case`: String = """
                        case let .\(element.name.text)(\(associatedValues)):
                        return (\(associatedValues))
                        """
                    associatedValueCases.append(`case`)
                } else {
                    let `case`: String = """
                        case let .\(element.name.text)(value):
                        return (value)
                        """
                    associatedValueCases.append(`case`)
                }
            } else {
                let `case`: String = """
                    case .\(element.name.text):
                    return ()
                    """
                associatedValueCases.append(`case`)
            }
        }

        let decl: DeclSyntax = """
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

        return decl.as(ExtensionDeclSyntax.self).flatMap { [$0] } ?? []
    }
}
