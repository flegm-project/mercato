import StoreKit
import SwiftUI

/// The only purchase in the app: removing ads, once, for good.
/// See docs/MONETIZATION.md. There is no shop and no consumable.
enum StoreProduct {
    static let removeAds = "com.mercato.removeads"
}

/// Owns the remove-ads entitlement and the StoreKit plumbing behind it.
///
/// The entitlement is the single source of truth for whether any ad slot is
/// allowed to fill, so it is read from StoreKit rather than mirrored into
/// local storage: a receipt that was refunded or restored on another device
/// must be reflected, and a flag we wrote ourselves would not be.
@MainActor
final class Store: ObservableObject {
    @Published private(set) var adsRemoved = false
    @Published private(set) var product: Product?
    @Published private(set) var purchasing = false
    /// Set when a restore found nothing, so the UI can say so.
    @Published var restoreFoundNothing = false

    private var updates: Task<Void, Never>?

    init() {
        // A transaction can arrive at any time: finished on another device,
        // approved after Ask to Buy, or refunded. Listening for the lifetime of
        // the app is what keeps the entitlement honest.
        updates = Task { [weak self] in
            for await update in Transaction.updates {
                guard let self else { return }
                if case .verified(let transaction) = update {
                    await transaction.finish()
                    await self.refreshEntitlement()
                }
            }
        }
        Task {
            await loadProduct()
            await refreshEntitlement()
        }
    }

    deinit { updates?.cancel() }

    func loadProduct() async {
        product = try? await Product.products(for: [StoreProduct.removeAds]).first
    }

    /// Reads the entitlement from StoreKit itself.
    func refreshEntitlement() async {
        var owned = false
        for await entitlement in Transaction.currentEntitlements {
            if case .verified(let transaction) = entitlement,
               transaction.productID == StoreProduct.removeAds,
               transaction.revocationDate == nil {
                owned = true
            }
        }
        adsRemoved = owned
    }

    func purchase() async {
        guard let product, !purchasing else { return }
        purchasing = true
        defer { purchasing = false }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                if case .verified(let transaction) = verification {
                    await transaction.finish()
                    await refreshEntitlement()
                }
            case .userCancelled, .pending:
                break
            @unknown default:
                break
            }
        } catch {
            // A failed purchase leaves the entitlement untouched; the store
            // surfaces its own error to the player.
        }
    }

    /// Required by the App Store, and the way a reinstall or a new device gets
    /// the purchase back.
    func restore() async {
        try? await AppStore.sync()
        await refreshEntitlement()
        restoreFoundNothing = !adsRemoved
    }

    /// Display price in the store's own currency, once the product has loaded.
    var displayPrice: String? {
        #if DEBUG
        // QA: render the purchasable row in the simulator, where no StoreKit
        // product loads. Never compiled into release.
        if CommandLine.arguments.contains("-MercatoFakePrice") { return "4,99 \u{20AC}" }
        #endif
        return product?.displayPrice
    }
}
