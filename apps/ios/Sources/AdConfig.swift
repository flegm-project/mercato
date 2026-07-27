import Foundation

/// AdMob identifiers for the iOS app.
///
/// Real production IDs ship in release; Googles official sample IDs are used
/// in debug so development never generates invalid traffic on the real units.
/// The account and unit IDs were created for Mercato (see docs/MONETIZATION.md).
enum AdConfig {
    #if DEBUG
    /// Google sample application ID (iOS).
    static let applicationID = "ca-app-pub-3940256099942544~1458002511"
    static let banner        = "ca-app-pub-3940256099942544/2934735716"
    static let sponsor       = "ca-app-pub-3940256099942544/2934735716"
    static let interstitial  = "ca-app-pub-3940256099942544/4411468910"
    static let rectangle     = "ca-app-pub-3940256099942544/2934735716"
    #else
    /// Production application ID (iOS), Mercato AdMob account.
    static let applicationID = "ca-app-pub-5435447054359850~6829263654"
    static let banner        = "ca-app-pub-5435447054359850/2890018645"
    static let sponsor       = "ca-app-pub-5435447054359850/7734196921"
    static let interstitial  = "ca-app-pub-5435447054359850/1444386673"
    static let rectangle     = "ca-app-pub-5435447054359850/2534107062"
    #endif
}
