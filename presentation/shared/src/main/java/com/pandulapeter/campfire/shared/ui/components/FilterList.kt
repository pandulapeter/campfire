package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Database

@Composable
internal fun FilterList(
    modifier: Modifier = Modifier,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit
) = LazyColumn(
    modifier = modifier
) {
    if (databases.isNotEmpty()) {
        item {
            Header(text = "Databases")
        }
        items(databases.size) {
            val database = databases[it]
            DatabaseItem(
                database = database,
                isChecked = database.isEnabled,
                onCheckedChanged = { onDatabaseEnabledChanged(database, it) }
            )
        }
        item {
            Header(text = "Filters")
        }
        val enabledDatabases = databases.filter { it.isEnabled }
        items(enabledDatabases.size) {
            val database = enabledDatabases[it]
            DatabaseItem(
                database = database,
                isChecked = !unselectedDatabaseUrls.contains(database.url),
                onCheckedChanged = { onDatabaseSelectedChanged(database, it) }
            )
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