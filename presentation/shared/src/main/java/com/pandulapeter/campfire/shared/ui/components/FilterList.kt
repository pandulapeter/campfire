package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Database

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilterList(
    modifier: Modifier = Modifier,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit
) = LazyColumn(
    modifier = modifier.fillMaxHeight()
) {
    if (databases.isNotEmpty()) {
        item(key = "header_databases") {
            Header(
                modifier = Modifier.animateItemPlacement(),
                text = "Databases"
            )
        }
        itemsIndexed(
            items = databases,
            key = { _, database -> "database_${database.url}" }
        ) { _, database ->
            DatabaseItem(
                modifier = Modifier.animateItemPlacement(),
                database = database,
                isChecked = database.isEnabled,
                onCheckedChanged = { onDatabaseEnabledChanged(database, it) }
            )
        }
        val enabledDatabases = databases.filter { it.isEnabled }
        if (enabledDatabases.isNotEmpty()) {
            item(key = "header_filters") {
                Header(
                    modifier = Modifier.animateItemPlacement(),
                    text = "Filters"
                )
            }
            itemsIndexed(
                items = enabledDatabases,
                key = { _, database -> "database_filter_${database.url}" }
            ) { _, database ->
                DatabaseItem(
                    modifier = Modifier.animateItemPlacement(),
                    database = database,
                    isChecked = !unselectedDatabaseUrls.contains(database.url),
                    onCheckedChanged = { onDatabaseSelectedChanged(database, it) }
                )
            }
        }
    }
}

@Composable
private fun DatabaseItem(
    modifier: Modifier = Modifier,
    database: Database,
    isChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) = Row(
    modifier = modifier.padding(horizontal = 8.dp).clickable { onCheckedChanged(!isChecked) },
) {
    Checkbox(
        modifier = Modifier.align(Alignment.CenterVertically),
        checked = isChecked,
        onCheckedChange = onCheckedChanged
    )
    Text(
        modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically),
        text = database.name
    )
}