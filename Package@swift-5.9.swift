// swift-tools-version:5.9

import PackageDescription
import CompilerPluginSupport

let package = Package(
    name: "StateMachine",
    platforms: [
        .macOS(.v10_15),
        .iOS(.v13),
        .tvOS(.v13),
        .watchOS(.v5),
    ],
    products: [
        .library(
            name: "StateMachine",
            targets: ["StateMachine"]),
    ],
    dependencies: [
        .package(
            url: "https://github.com/apple/swift-syntax.git",
            from: "509.1.0"),
        .package(
            url: "https://github.com/Quick/Nimble.git",
            from: "13.2.0"),
    ],
    targets: [
        .target(
            name: "StateMachine",
            dependencies: ["StateMachineMacros"],
            path: "Swift/Sources/StateMachine"),
        .macro(
            name: "StateMachineMacros",
            dependencies: [
                .product(name: "SwiftSyntaxMacros", package: "swift-syntax"),
                .product(name: "SwiftCompilerPlugin", package: "swift-syntax"),
            ],
            path: "Swift/Sources/StateMachineMacros"),
        .testTarget(
            name: "StateMachineTests",
            dependencies: [
                "StateMachine",
                "StateMachineMacros",
                .product(name: "SwiftSyntaxMacros", package: "swift-syntax"),
                .product(name: "SwiftSyntaxMacrosTestSupport", package: "swift-syntax"),
                "Nimble",
            ],
            path: "Swift/Tests/StateMachineTests"),
    ]
)
