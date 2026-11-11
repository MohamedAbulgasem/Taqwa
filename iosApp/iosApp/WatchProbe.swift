#if DEBUG
import Foundation

/// The rules of the iPhone's DEBUG size probe (plan 4 Task 5B): what `WatchHarness` is asked to
/// measure, and the search that decides which size it sends next and what it reports. Pure, so
/// `swift test` holds them on the Mac (the WatchCore package's `PhoneHarness` target links this
/// file); `WatchHarness.swift` sends the sizes over WatchConnectivity on the paired simulators.
/// `#if DEBUG`, as the harness is, so none of it is in a shipping build.

/// The WatchConnectivity call a probe measures: `updateApplicationContext`, which answers each
/// size in the call, or `transferUserInfo`, which answers later in the session's delegate.
enum WatchProbeVia: String {
	case context
	case userInfo
}

/// What fills a probe's payload. WatchConnectivity compresses what it carries before it checks
/// the size (on the simulator a 21,034-byte feed is checked as 6,507 bytes, and 300,000 zeros as
/// 1,200), so zeros, the plan's `Data(count: n)`, never meet the limit. `random`, the default, is
/// bytes no compressor shrinks, the same each time (a SplitMix64 stream from a fixed seed): the
/// limit a feed meets at its very worst.
enum WatchProbeFill: String {
	case zero
	case random

	func data(count: Int) -> Data {
		switch self {
		case .zero:
			return Data(count: count)
		case .random:
			var state: UInt64 = 0x5441_5157_4157_4154
			var bytes = [UInt8](repeating: 0, count: count)
			var index = 0
			while index < count {
				state &+= 0x9E37_79B9_7F4A_7C15
				var mixed = state
				mixed = (mixed ^ (mixed >> 30)) &* 0xBF58_476D_1CE4_E5B9
				mixed = (mixed ^ (mixed >> 27)) &* 0x94D0_49BB_1331_11EB
				mixed ^= mixed >> 31
				for shift in stride(from: 0, to: 64, by: 8) where index < count {
					bytes[index] = UInt8(truncatingIfNeeded: mixed >> UInt64(shift))
					index += 1
				}
			}
			return Data(bytes)
		}
	}
}

/// `taqwa://watch/probe?via=context|userInfo&from=60000&to=300000&step=4096&fill=random|zero`: the
/// channel, the bounds the search starts from and the payload's fill. `from`, `to` and `step`
/// default to those, the plan's, and `fill` to `random`, so a URL that names no fill (the plan's,
/// and plan 5's ship gate on her pair) measures the limit rather than what zeros compress to; zeros
/// only by name (`fill=zero`). Anything else (another host or command, an unknown channel or fill,
/// bounds the wrong way round, no step, a number that is not a whole one) is no request.
struct WatchProbeRequest: Equatable {
	let via: WatchProbeVia
	let from: Int
	let to: Int
	let step: Int
	let fill: WatchProbeFill

	init(via: WatchProbeVia, from: Int, to: Int, step: Int, fill: WatchProbeFill) {
		self.via = via
		self.from = from
		self.to = to
		self.step = step
		self.fill = fill
	}

	init?(_ url: URL) {
		guard url.scheme == "taqwa", url.host == "watch",
			url.pathComponents.filter({ $0 != "/" }) == ["probe"] else { return nil }
		let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
		func value(_ name: String) -> String? { items.first { $0.name == name }?.value }
		func number(_ name: String, _ fallback: Int) -> Int? {
			guard let text = value(name) else { return fallback }
			return Int(text)
		}
		guard let via = value("via").flatMap(WatchProbeVia.init(rawValue:)),
			let fill = value("fill").map(WatchProbeFill.init(rawValue:)) ?? .random,
			let from = number("from", 60_000), let to = number("to", 300_000), let step = number("step", 4_096),
			from > 0, to > from, step > 0 else { return nil }
		self.init(via: via, from: from, to: to, step: step, fill: fill)
	}
}

/// The search between `from` and `to`: it sends `from` first and, once that is accepted, `to`;
/// while one is accepted and the other refused it sends the middle of the gap, until the gap is
/// at most `step`. So every size is sent once, and from 60,000 to 300,000 bytes by 4,096 it sends
/// eight at most (each accepted transfer is one more handed to the watch, which drops it).
/// `from` refused ends it with nothing accepted, and `to` accepted with nothing refused.
struct WatchProbeSearch {
	let from: Int
	let to: Int
	let step: Int
	/// The largest size accepted so far, and the smallest refused.
	private(set) var largestAccepted: Int?
	private(set) var firstRefused: Int?

	init(from: Int, to: Int, step: Int) {
		self.from = from
		self.to = to
		self.step = step
	}

	/// The size to send next, or nil once the search has ended.
	var next: Int? {
		guard let low = largestAccepted else { return firstRefused == nil ? from : nil }
		guard let high = firstRefused else { return low < to ? to : nil }
		return high - low > step ? low + (high - low) / 2 : nil
	}

	var isDone: Bool { next == nil }

	mutating func record(_ size: Int, accepted: Bool) {
		if accepted {
			largestAccepted = max(largestAccepted ?? size, size)
		} else {
			firstRefused = min(firstRefused ?? size, size)
		}
	}

	/// `TaqwaWatch probe <via> <fill> <largest accepted> <first refused>`, `none` for either not
	/// seen; then `incomplete` when the search stopped before its end (an unanswered transfer, a
	/// session that failed), and `unmeasured` after a zero fill, which WatchConnectivity compresses
	/// to almost nothing before it checks the size. So only a `random` line with neither word is a
	/// measured threshold: a partial answer or a zero run's "300000 none" never reads as one.
	func line(_ via: WatchProbeVia, fill: WatchProbeFill) -> String {
		let accepted = largestAccepted.map(String.init) ?? "none"
		let refused = firstRefused.map(String.init) ?? "none"
		return "TaqwaWatch probe \(via.rawValue) \(fill.rawValue) \(accepted) \(refused)"
			+ (isDone ? "" : " incomplete")
			+ (fill == .zero ? " unmeasured" : "")
	}
}
#endif
