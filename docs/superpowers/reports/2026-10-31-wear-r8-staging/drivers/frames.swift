// Frames of a screen recording, for the cold start (staging.sh cold), with AVFoundation alone:
//
//   swift frames.swift <in.mp4> <out dir> [step seconds, 0.1]
//
// It writes <out dir>/f<ms>.png every step, from the first frame to the end, each at its exact time.
import AVFoundation
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let args = CommandLine.arguments
guard args.count >= 3 else { print("usage: swift frames.swift <in.mp4> <out dir> [step]"); exit(2) }
let asset = AVURLAsset(url: URL(fileURLWithPath: args[1]))
let out = URL(fileURLWithPath: args[2], isDirectory: true)
try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
let step = args.count > 3 ? Double(args[3])! : 0.1
let generator = AVAssetImageGenerator(asset: asset)
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
let seconds = CMTimeGetSeconds(asset.duration)
var t = 0.0
while t < seconds {
    let time = CMTime(seconds: t, preferredTimescale: 600)
    if let image = try? generator.copyCGImage(at: time, actualTime: nil) {
        let url = out.appendingPathComponent(String(format: "f%04d.png", Int((t * 1000).rounded())))
        let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, image, nil)
        CGImageDestinationFinalize(dest)
    }
    t += step
}
print(String(format: "%.2f s recorded", seconds))
