# Support files

`Products.storekit` describes the single in-app purchase for **local testing
only**. It lets the remove-ads flow be built and exercised in the simulator
without an App Store Connect entry, which needs a paid Apple Developer Program
membership.

Point the scheme at it in Xcode: Edit Scheme, Run, Options, StoreKit
Configuration. `scripts/gen-ios-project.sh` wires it automatically.

When the real product exists in App Store Connect, keep the same product id
(`com.flegm.mercato.removeads`) and set the scheme back to None so the app talks to
the real store.
