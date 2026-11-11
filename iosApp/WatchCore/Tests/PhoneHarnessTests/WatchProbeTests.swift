import Foundation
import XCTest
@testable import PhoneHarness

/// The rules of the iPhone's DEBUG size probe (plan 4 Task 5B), which measures where
/// WatchConnectivity first refuses a payload: the request read from its `taqwa://watch/probe` URL,
/// and the search that halves the gap between the two bounds until it is at most a step. The
/// harness that sends each size (`WatchHarness.swift`) is plumbing whose evidence is the
/// simulators' logs; what it sends, in what order, and what it reports is decided here.
final class WatchProbeTests: XCTestCase {

    // MARK: - The request

    func testTheRequestIsReadFromTheProbeURL() throws {
        let context = try XCTUnwrap(WatchProbeRequest(url("taqwa://watch/probe?via=context&from=60000&to=300000&step=4096")))
        XCTAssertEqual(context, WatchProbeRequest(via: .context, from: 60_000, to: 300_000, step: 4_096, fill: .random))
        let userInfo = try XCTUnwrap(WatchProbeRequest(url("taqwa://watch/probe?via=userInfo&from=1000&to=2000&step=10")))
        XCTAssertEqual(userInfo, WatchProbeRequest(via: .userInfo, from: 1_000, to: 2_000, step: 10, fill: .random))
    }

    func testTheBoundsDefaultToThePlansOwn() throws {
        let request = try XCTUnwrap(WatchProbeRequest(url("taqwa://watch/probe?via=context")))
        XCTAssertEqual(request, WatchProbeRequest(via: .context, from: 60_000, to: 300_000, step: 4_096, fill: .random))
    }

    func testOnlyAWellFormedProbeIsARequest() {
        for text in [
            "taqwa://watch/probe", // no via
            "taqwa://watch/probe?via=message", // no such via
            "taqwa://watch/probe?via=Context", // vias are matched exactly
            "taqwa://watch/probe?via=context&from=300000&to=60000", // the bounds the wrong way round
            "taqwa://watch/probe?via=context&from=60000&to=60000", // no gap
            "taqwa://watch/probe?via=context&from=0", // nothing to send
            "taqwa://watch/probe?via=context&from=-1",
            "taqwa://watch/probe?via=context&step=0",
            "taqwa://watch/probe?via=context&step=-4096",
            "taqwa://watch/probe?via=context&from=6e4", // whole numbers only
            "taqwa://watch/probe?via=context&to=lots",
            "taqwa://watch/other?via=context", // another command
            "taqwa://recite/probe?via=context", // the recitation harness's host
            "https://watch/probe?via=context", // another scheme
        ] {
            XCTAssertNil(WatchProbeRequest(url(text)), text)
        }
    }

    // MARK: - The fill

    func testTheFillIsRandomUnlessZerosAreAskedForByName() throws {
        // Zeros measure nothing (WatchConnectivity checks 300,000 of them as 1,200 bytes), so a URL
        // that names no fill, as plan 5's ship gate and the plan's own URLs are written, sends
        // bytes no compressor shrinks. Zeros are still there, by name.
        for text in [
            "taqwa://watch/probe?via=context&from=60000&to=300000&step=4096",
            "taqwa://watch/probe?via=userInfo&from=60000&to=300000&step=4096",
            "taqwa://watch/probe?via=context",
            "taqwa://watch/probe?via=context&fill=random",
        ] {
            XCTAssertEqual(try XCTUnwrap(WatchProbeRequest(url(text)), text).fill, .random, text)
        }
        let zero = try XCTUnwrap(WatchProbeRequest(url("taqwa://watch/probe?via=userInfo&fill=zero&from=1000&to=2000&step=10")))
        XCTAssertEqual(zero, WatchProbeRequest(via: .userInfo, from: 1_000, to: 2_000, step: 10, fill: .zero))
        for text in [
            "taqwa://watch/probe?via=context&fill=json", // no such fill
            "taqwa://watch/probe?via=context&fill=Zero", // fills are matched exactly
            "taqwa://watch/probe?via=context&fill=", // a fill named, but none given
        ] {
            XCTAssertNil(WatchProbeRequest(url(text)), text)
        }
    }

    func testZerosAreZeros() {
        let data = WatchProbeFill.zero.data(count: 4_096)
        XCTAssertEqual(data.count, 4_096)
        XCTAssertTrue(data.allSatisfy { $0 == 0 })
    }

    func testRandomBytesAreTheSameEachTimeAndDoNotCompress() throws {
        // WatchConnectivity compresses what it carries (a 21,034-byte feed is kept as a 6,499-byte
        // context on the simulator, and 300,000 zeros were taken), so only bytes no compressor
        // can shrink measure the limit a feed meets at its worst.
        let data = WatchProbeFill.random.data(count: 65_536)
        XCTAssertEqual(data.count, 65_536)
        XCTAssertEqual(data, WatchProbeFill.random.data(count: 65_536))
        XCTAssertEqual(WatchProbeFill.random.data(count: 1_000), data.prefix(1_000))
        for algorithm in [NSData.CompressionAlgorithm.zlib, .lzfse, .lz4, .lzma] {
            let packed = try (data as NSData).compressed(using: algorithm) as Data
            XCTAssertGreaterThanOrEqual(packed.count, data.count * 99 / 100, "\(algorithm.rawValue)")
        }
    }

    func testTheLineNamesItsFillAndAZeroLineIsNoThreshold() {
        // Plan 5's gate holds a line's largest accepted against 61,024. A zero run's "300000 none"
        // is WatchConnectivity taking 1,200 compressed bytes, so its line ends `unmeasured` and
        // never reads as a threshold; a random line is the plan's form after its fill.
        var search = WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096)
        search.record(60_000, accepted: true)
        search.record(300_000, accepted: true)
        XCTAssertEqual(search.line(.context, fill: .random), "TaqwaWatch probe context random 300000 none")
        XCTAssertEqual(search.line(.context, fill: .zero), "TaqwaWatch probe context zero 300000 none unmeasured")
        var stopped = WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096)
        stopped.record(60_000, accepted: true)
        XCTAssertEqual(stopped.line(.userInfo, fill: .zero), "TaqwaWatch probe userInfo zero 60000 none incomplete unmeasured")
        XCTAssertEqual(stopped.line(.userInfo, fill: .random), "TaqwaWatch probe userInfo random 60000 none incomplete")
    }

    // MARK: - The search

    func testTheSearchTriesFromThenToThenHalvesTheGap() {
        let (tried, search) = run(WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096), limit: 65_000)
        XCTAssertEqual(tried, [60_000, 300_000, 180_000, 120_000, 90_000, 75_000, 67_500, 63_750])
        XCTAssertEqual(search.largestAccepted, 63_750)
        XCTAssertEqual(search.firstRefused, 67_500)
        XCTAssertTrue(search.isDone)
        XCTAssertEqual(search.line(.userInfo, fill: .random), "TaqwaWatch probe userInfo random 63750 67500")
    }

    func testTheSearchEndsWithinAStepOfAnyLimitBetweenTheBounds() {
        // A step of 4,096 over 240,000 bytes: from, to, and six halvings at most; a step of 10
        // over 1,000: from, to, and seven.
        for (from, to, step, most) in [(60_000, 300_000, 4_096, 8), (1_000, 2_000, 10, 9)] {
            for limit in stride(from: from, to: to, by: max(1, (to - from) / 241)) {
                let (tried, search) = run(WatchProbeSearch(from: from, to: to, step: step), limit: limit)
                let accepted = search.largestAccepted ?? -1
                let refused = search.firstRefused ?? -1
                let about = "\(from)...\(to) by \(step), limit \(limit)"
                XCTAssertTrue(search.isDone, about)
                XCTAssertLessThanOrEqual(accepted, limit, about)
                XCTAssertGreaterThan(refused, limit, about)
                XCTAssertLessThanOrEqual(refused - accepted, step, about)
                XCTAssertLessThanOrEqual(tried.count, most, about)
                XCTAssertEqual(Set(tried).count, tried.count, "a size sent twice, \(about)")
            }
        }
    }

    func testAGapOfExactlyAStepIsDone() {
        let (tried, search) = run(WatchProbeSearch(from: 4_096, to: 8_192, step: 4_096), limit: 5_000)
        XCTAssertEqual(tried, [4_096, 8_192])
        XCTAssertTrue(search.isDone)
        XCTAssertEqual(search.line(.context, fill: .random), "TaqwaWatch probe context random 4096 8192")
    }

    func testTheBoundsKeepTheLargestAcceptedAndTheSmallestRefused() {
        // Whatever order the answers are recorded in.
        var search = WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096)
        search.record(70_000, accepted: true)
        search.record(65_000, accepted: true)
        search.record(90_000, accepted: false)
        search.record(120_000, accepted: false)
        XCTAssertEqual(search.largestAccepted, 70_000)
        XCTAssertEqual(search.firstRefused, 90_000)
        XCTAssertEqual(search.next, 80_000)
    }

    func testAFirstSizeRefusedEndsTheSearchWithNothingAccepted() {
        let (tried, search) = run(WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096), limit: 59_999)
        XCTAssertEqual(tried, [60_000])
        XCTAssertNil(search.largestAccepted)
        XCTAssertEqual(search.firstRefused, 60_000)
        XCTAssertEqual(search.line(.context, fill: .random), "TaqwaWatch probe context random none 60000")
    }

    func testALastSizeAcceptedEndsTheSearchWithNothingRefused() {
        let (tried, search) = run(WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096), limit: 300_000)
        XCTAssertEqual(tried, [60_000, 300_000])
        XCTAssertEqual(search.largestAccepted, 300_000)
        XCTAssertNil(search.firstRefused)
        XCTAssertEqual(search.line(.context, fill: .random), "TaqwaWatch probe context random 300000 none")
    }

    func testASearchStoppedBeforeItsEndSaysSo() {
        // A userInfo transfer that is never answered stops the search where it stands: its line is
        // what was known, and never reads as a measured threshold.
        var search = WatchProbeSearch(from: 60_000, to: 300_000, step: 4_096)
        XCTAssertEqual(search.line(.userInfo, fill: .random), "TaqwaWatch probe userInfo random none none incomplete")
        search.record(60_000, accepted: true)
        search.record(300_000, accepted: false)
        XCTAssertFalse(search.isDone)
        XCTAssertEqual(search.next, 180_000)
        XCTAssertEqual(search.line(.userInfo, fill: .random), "TaqwaWatch probe userInfo random 60000 300000 incomplete")
    }

    // MARK: - Helpers

    private func url(_ text: String) -> URL {
        URL(string: text)!
    }

    /// Runs a search against a channel that accepts every size up to `limit`, as WatchConnectivity
    /// refuses a payload past its own, and gives the sizes in the order they were sent.
    private func run(_ start: WatchProbeSearch, limit: Int) -> ([Int], WatchProbeSearch) {
        var search = start
        var tried: [Int] = []
        while let size = search.next, tried.count < 100 {
            tried.append(size)
            search.record(size, accepted: size <= limit)
        }
        return (tried, search)
    }
}
