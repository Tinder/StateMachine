// swift-tools-version:5.2

import PackageDescription

let package = Package(
    name: "StateMachine",
    products: [
        .library(
            name: "StateMachine",
            targets: ["StateMachine"]),
    ],
    dependencies: [
        .package(url: "https://github.com/Quick/Nimble.git",
                 from: "9.0.0-rc.3"),
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
