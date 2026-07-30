import AVFoundation

/// The three cues, named after the moment rather than the sound.
enum Cue: String, CaseIterable {
    case correct, wrong, roundOver = "roundover"
}

/// Plays the game's cues, or does not, depending on the sound setting.
///
/// The players are built once and kept: `AVAudioPlayer` decodes on `prepare`,
/// and creating one per answer arrives after the card has already turned
/// green. Replaying is a seek to zero, which is free.
///
/// The session is `.ambient`, which is the category that says "this app makes
/// sounds but is not a music app": the ring/silent switch mutes it, the
/// player's own music keeps playing underneath, and nothing is interrupted.
/// `.playback` would stop their podcast to say "wrong answer".
final class Sounds {
    static let shared = Sounds()

    private var players: [Cue: AVAudioPlayer] = [:]

    private init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        for cue in Cue.allCases {
            guard let url = Bundle.main.url(forResource: cue.rawValue, withExtension: "wav"),
                  let player = try? AVAudioPlayer(contentsOf: url) else { continue }
            player.prepareToPlay()
            players[cue] = player
        }
    }

    /// Play `cue` unless the player has turned sound off. The preference is
    /// read at the moment of playing rather than held, so the switch in
    /// Settings takes effect on the next answer with no wiring between the two
    /// screens.
    func play(_ cue: Cue) {
        // `object(forKey:)` rather than `bool(forKey:)`: the latter reports
        // false for a key that was never written, which would ship the app
        // silent until someone toggled the switch twice.
        let on = UserDefaults.standard.object(forKey: "soundOn") as? Bool ?? true
        guard on, let player = players[cue] else { return }
        try? AVAudioSession.sharedInstance().setActive(true)
        player.currentTime = 0
        player.play()
    }
}
