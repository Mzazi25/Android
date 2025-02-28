/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.savedsites.impl.sync

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.DATA_CHANGE
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SavedSitesSyncDataObserver @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val syncEngine: SyncEngine,
    private val deviceSyncState: DeviceSyncState,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private var initialised: Boolean = false

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        if (deviceSyncState.isUserSignedInOnDevice()) {
            Timber.d("Sync-Feature: Listening to changes in Saved Sites")
            coroutineScope.launch(dispatcherProvider.io()) {
                savedSitesRepository.lastModified().collect {
                    if (initialised) {
                        Timber.d("Sync-Feature: Changes to Saved Sites detected, triggering sync")
                        syncEngine.triggerSync(DATA_CHANGE)
                    } else {
                        Timber.d("Sync-Feature: Changes to Saved Sites initialised")
                        initialised = true
                    }
                }
            }
        }
    }
}
