package com.nikolaspaci.app.llamallmlocal.di

import com.nikolaspaci.app.llamallmlocal.util.RemoteErrorLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridge so non-Hilt-aware call sites (e.g. Composables that don't host a
 * Hilt ViewModel) can pull the singleton RemoteErrorLogger out of the app
 * graph via [dagger.hilt.android.EntryPointAccessors.fromApplication].
 *
 * Going through Hilt is intentional: it forces [com.nikolaspaci.app.llamallmlocal.di.AppModule.provideFirebaseFirestore]
 * to run before the logger sees Firestore, which is what guarantees the
 * persistent-cache settings are applied before the first Firestore op.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemoteErrorLoggerEntryPoint {
    fun remoteErrorLogger(): RemoteErrorLogger
}
