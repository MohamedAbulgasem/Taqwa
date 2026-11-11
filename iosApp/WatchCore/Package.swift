// swift-tools-version: 6.0
//
// The Apple Watch's core: a Swift port of `:watchcore` (the feed, its decoder and the rules every
// watch surface applies to it), with no Kotlin in it (plan 4, the Lead's call 1). It is a package
// for one reason: `swift test --package-path iosApp/WatchCore` runs it on the Mac against the JSON
// vectors Kotlin writes in `watchcore/vectors/`, in seconds and with no simulator. The Xcode
// targets compile its sources directly and do not depend on the package.
//
// `WatchViews` serves the tests alone: by symbolic links in `Tests/WatchViews/`, it compiles
// WatchCore's sources together with the few view files from `iosApp/WatchShared/` and
// `iosApp/TaqwaWatch/` that the Mac can build, in one module, as each Xcode target compiles them.
// `WatchViewsTests` then draws them off-device (ImageRenderer, an NSHostingView): what only the
// simulator's shots held before (Ruling 64 item 3). The files themselves stay where the targets
// find them; a package cannot reach outside its own folder but through a link.
//
// `PhoneHarness` is the same kind of link, to the iPhone's side: `iosApp/iosApp/WatchProbe.swift`,
// the rules of the DEBUG size probe (plan 4 Task 5B) that `WatchHarness.swift` runs over
// WatchConnectivity on the paired simulators; and `iosApp/iosApp/WatchKeyFile.swift`, the watch
// key's file, read, written and marked excluded from backup (plan 4 Task 11, Ruling 69 item 3).
// `PhoneHarnessTests` holds them on the Mac.
import PackageDescription

let package = Package(
    name: "WatchCore",
    platforms: [.macOS(.v14), .watchOS(.v10)],
    products: [
        .library(name: "WatchCore", targets: ["WatchCore"]),
    ],
    targets: [
        .target(name: "WatchCore"),
        .testTarget(name: "WatchCoreTests", dependencies: ["WatchCore"]),
        .target(name: "WatchViews", path: "Tests/WatchViews"),
        .testTarget(name: "WatchViewsTests", dependencies: ["WatchViews"]),
        .target(name: "PhoneHarness", path: "Tests/PhoneHarness"),
        .testTarget(name: "PhoneHarnessTests", dependencies: ["PhoneHarness"]),
    ],
    swiftLanguageModes: [.v5]
)
