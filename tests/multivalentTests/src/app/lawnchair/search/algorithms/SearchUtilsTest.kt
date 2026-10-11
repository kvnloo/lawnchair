/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.search.algorithms

import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import com.android.launcher3.model.data.AppInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SearchUtilsTest {

    private val hiddenApp = app("Private Journal", "example.privatejournal")
    private val visibleApp = app("Calendar", "example.calendar")
    private val hiddenApps = setOf(hiddenApp.toComponentKey().toString())

    @Test
    fun `never mode excludes a hidden app even with an exact query`() {
        assertThat(filtered(HiddenAppsInSearch.NEVER, "private journal"))
            .containsExactly(visibleApp)
    }

    @Test
    fun `exact-name mode excludes a hidden app on a partial query`() {
        assertThat(filtered(HiddenAppsInSearch.IF_NAME_TYPED, "private"))
            .containsExactly(visibleApp)
    }

    @Test
    fun `exact-name mode reveals a hidden app on a full query`() {
        assertThat(filtered(HiddenAppsInSearch.IF_NAME_TYPED, "private journal"))
            .containsExactly(hiddenApp, visibleApp)
            .inOrder()
    }

    @Test
    fun `always mode includes a hidden app without a query`() {
        assertThat(filtered(HiddenAppsInSearch.ALWAYS, ""))
            .containsExactly(hiddenApp, visibleApp)
            .inOrder()
    }

    @Test
    fun `unknown mode defaults to excluding hidden apps`() {
        assertThat(filtered("unrecognized", "private journal"))
            .containsExactly(visibleApp)
    }

    private fun filtered(mode: String, query: String): List<AppInfo> =
        sequenceOf(hiddenApp, visibleApp)
            .filterHiddenApps(query, hiddenApps, mode)
            .toList()

    private fun app(title: String, packageName: String) = AppInfo(
        ComponentName(packageName, "$" + "{packageName}.MainActivity"),
        title,
        Process.myUserHandle(),
        Intent(),
    )
}
