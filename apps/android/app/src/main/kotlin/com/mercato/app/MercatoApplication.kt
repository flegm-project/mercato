package com.mercato.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MercatoApplication : Application() {

    /** Process-wide services: the Rust core, preferences, the ads controller. */
    lateinit var graph: AppGraph
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this, scope)
        // The ads SDK initialises off the main thread; ad loads before the
        // callback simply queue up, so nothing needs to wait on it.
        scope.launch { MobileAds.initialize(this@MercatoApplication) }
        scope.launch {
            graph.warmUp()
            // The store is the truth for remove-ads; sync it once the core
            // is up (connect() also restores the entitlement).
            graph.billing.connect()
        }
    }
}
