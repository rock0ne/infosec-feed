// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "InfoSecFeedCore",
    platforms: [
        .macOS(.v13),
        .iOS(.v17),
    ],
    products: [
        .library(name: "InfoSecFeed", targets: ["InfoSecFeed"]),
    ],
    targets: [
        .target(
            name: "InfoSecFeed",
            path: "InfoSecFeed",
            exclude: [
                "Assets.xcassets",
                "ContentView.swift",
                "FeedStore.swift",
                "InfoSecFeedApp.swift",
                "PrivacyInfo.xcprivacy",
            ],
            sources: [
                "FeedRepository.swift",
                "Models.swift",
                "RSSFeedParser.swift",
                "Sources.swift",
            ]
        ),
        .testTarget(
            name: "InfoSecFeedTests",
            dependencies: ["InfoSecFeed"],
            path: "InfoSecFeedTests"
        ),
    ]
)
