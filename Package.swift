// swift-tools-version:5.4

import PackageDescription

let package = Package(
    name: "StateMachine",
    platforms: [
        .macOS(.v10_13),
        .iOS(.v11),
        .tvOS(.v11),
        .watchOS(.v5),
    ],
    products: [
        .library(
            name: "StateMachine",
            targets: ["StateMachine"]),
    ],
    dependencies: [
        .package(
            url: "https://github.com/Quick/Nimble.git",
            from: "9.2.0"),
    ],
    targets: [
        .target(
            name: "StateMachine",
            dependencies: [],
            path: "Swift/Sources/StateMachine"),
        .testTarget(
            name: "StateMachineTests",
            dependencies: ["StateMachine", "Nimble"],
            path: "Swift/Tests/StateMachineTests"),
    ]
)
