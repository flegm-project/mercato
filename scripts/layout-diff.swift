import AppKit

// Measure the vertical layout of a screen capture and compare the two
// platforms band by band.
//
// The design-parity checker reads source and proves both apps declare the same
// values. It says nothing about where anything lands, which is where the two
// still differ: spacing, weighting and container heights are not in
// tokens.json, so nothing constrains them.
//
// This reads the pixels instead. Every row is classified by its dominant token
// colour, runs of the same class become bands, and the bands are compared in
// dp so a 3x iPhone capture and a 2.625x emulator capture line up.
//
// Usage: layout-diff <shots-dir> <route> [route ...]

struct Klass {
    let name: String
    let rgb: (Int, Int, Int)
}

// The token palette. A row is attributed to the nearest of these.
let palette: [Klass] = [
    Klass(name: "field", rgb: (0x1B, 0x2F, 0xE0)),
    Klass(name: "field-top", rgb: (0x2E, 0x48, 0xFF)),
    Klass(name: "field-deep", rgb: (0x12, 0x23, 0xA6)),
    Klass(name: "ivory", rgb: (0xFB, 0xFA, 0xF4)),
    Klass(name: "ink", rgb: (0x0B, 0x10, 0x30)),
    Klass(name: "deep", rgb: (0x0F, 0x1A, 0x66)),
    Klass(name: "yellow", rgb: (0xFF, 0xE2, 0x4A)),
    Klass(name: "mint", rgb: (0x2F, 0xE0, 0xA0)),
    Klass(name: "coral", rgb: (0xFF, 0x5C, 0x63)),
]

/// Only structural blocks are worth comparing: a card, a button, a banner, the
/// tab bar. Anything shorter is a divider or a line of text.
let minBandDp = 20.0
/// A row counts as solid only if most of the strip agrees. Without this a line
/// of ivory text over blue reads as an ivory band.
let solidShare = 0.75
/// The OS status bar and home indicator differ by construction.
let ignoreTopDp = 52.0
let ignoreBottomDp = 24.0
let screenDp = 874.0

func rows(_ path: String) -> [(String, Double)]? {
    guard let img = NSImage(contentsOfFile: path),
          let tiff = img.tiffRepresentation,
          let rep = NSBitmapImageRep(data: tiff) else { return nil }
    let w = rep.pixelsWide, h = rep.pixelsHigh
    var out: [(String, Double)] = []
    out.reserveCapacity(h)
    // Sample a horizontal strip either side of centre: the middle column alone
    // hits text as often as background.
    let xs = stride(from: w / 6, to: w * 5 / 6, by: max(1, w / 24)).map { $0 }
    for y in 0..<h {
        var tally: [String: Int] = [:]
        for x in xs {
            guard let c = rep.colorAt(x: x, y: y)?.usingColorSpace(.deviceRGB) else { continue }
            let r = Int(c.redComponent * 255), g = Int(c.greenComponent * 255), b = Int(c.blueComponent * 255)
            var best = ""; var bestD = Int.max
            for k in palette {
                let d = (r - k.rgb.0) * (r - k.rgb.0) + (g - k.rgb.1) * (g - k.rgb.1) + (b - k.rgb.2) * (b - k.rgb.2)
                if d < bestD { bestD = d; best = k.name }
            }
            tally[best, default: 0] += 1
        }
        let dom = tally.max { $0.value < $1.value }
        let name = (dom != nil && Double(dom!.value) / Double(xs.count) >= solidShare) ? dom!.key : "mixed"
        out.append((name, Double(y) / Double(h) * screenDp))
    }
    return out
}

struct Band { let klass: String; let start: Double; let end: Double
    var height: Double { end - start } }

func bands(_ rows: [(String, Double)]) -> [Band] {
    var out: [Band] = []
    var cur = rows[0].0, start = rows[0].1
    for (k, dp) in rows.dropFirst() {
        if k != cur {
            if dp - start >= minBandDp { out.append(Band(klass: cur, start: start, end: dp)) }
            cur = k; start = dp
        }
    }
    out.append(Band(klass: cur, start: start, end: screenDp))
    // The blue field is one surface whatever the gradient does to it.
    return out
        .map { Band(klass: $0.klass.hasPrefix("field") ? "field" : $0.klass, start: $0.start, end: $0.end) }
        .filter { $0.klass != "mixed" && $0.height >= minBandDp
                  && $0.start >= ignoreTopDp && $0.end <= screenDp - ignoreBottomDp }
}

/// Merge touching bands of the same class, which the gradient splits.
func merge(_ b: [Band]) -> [Band] {
    var out: [Band] = []
    for band in b {
        if let last = out.last, last.klass == band.klass, band.start - last.end < 8 {
            out[out.count - 1] = Band(klass: last.klass, start: last.start, end: band.end)
        } else { out.append(band) }
    }
    return out
}

let dir = CommandLine.arguments[1]
let routes = Array(CommandLine.arguments.dropFirst(2))
var totalDrift = 0

for route in routes {
    guard let ri = rows("\(dir)/ios/\(route).png"), let ra = rows("\(dir)/android/\(route).png") else {
        print("\(route): missing capture"); continue
    }
    let bi = merge(bands(ri)), ba = merge(bands(ra))
    print("\n\u{001B}[1m\(route)\u{001B}[0m")
    let n = max(bi.count, ba.count)
    if bi.count != ba.count {
        print("  \u{001B}[31mband count differs\u{001B}[0m  iOS \(bi.count), Android \(ba.count)")
        totalDrift += 1
    }
    print("  " + "class".padding(toLength: 10, withPad: " ", startingAt: 0) +
          "iOS start  h     Android start  h     Δstart  Δh")
    for i in 0..<n {
        let a = i < bi.count ? bi[i] : nil
        let b = i < ba.count ? ba[i] : nil
        func f(_ v: Double?) -> String {
            guard let v else { return "  -  " }
            return String(format: "%5.0f", v)
        }
        var flag = ""
        if let a, let b, a.klass == b.klass {
            let ds = b.start - a.start, dh = b.height - a.height
            if abs(ds) >= 8 || abs(dh) >= 8 { flag = "\u{001B}[31m  <-\u{001B}[0m"; totalDrift += 1 }
            print("  " + (a.klass).padding(toLength: 10, withPad: " ", startingAt: 0) +
                  f(a.start) + "  " + f(a.height) + "      " + f(b.start) + "  " + f(b.height) +
                  "   " + String(format: "%+5.0f", ds) + "  " + String(format: "%+5.0f", dh) + flag)
        } else {
            print("  " + ((a?.klass ?? b?.klass ?? "?") + " *").padding(toLength: 10, withPad: " ", startingAt: 0) +
                  f(a?.start) + "  " + f(a?.height) + "      " + f(b?.start) + "  " + f(b?.height) +
                  "        mismatch\u{001B}[31m  <-\u{001B}[0m")
            totalDrift += 1
        }
    }
}

print("\n\(totalDrift) layout difference(s) over \(routes.count) screens")
exit(totalDrift == 0 ? 0 : 1)
