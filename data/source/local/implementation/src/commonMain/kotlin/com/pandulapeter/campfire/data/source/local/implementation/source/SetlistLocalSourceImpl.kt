package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.SETLIST_EXTENSION
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.setlistFileName
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.serialization.json.Json

internal class SetlistLocalSourceImpl(
    private val fileStorage: FileStorage
) : SetlistLocalSource {

    override suspend fun loadSetlists(): List<Setlist> = fileStorage.list(StorageDirectory.SETLISTS)
        .filter { it.name.endsWith(SETLIST_EXTENSION, ignoreCase = true) }
        .mapNotNull { file ->
            try {
                fileStorage.readText(StorageDirectory.SETLISTS, file.name)
                    ?.let { json.decodeFromString<SetlistDocument>(it).toModel(file.name) }
            } catch (exception: Exception) {
                // Left on disk rather than deleted: a setlist the user hand-edited into invalid JSON is theirs to fix.
                println("Could not read the setlist \"${file.name}\": ${exception.message}")
                null
            }
        }

    override suspend fun createSetlist(title: String, priority: Int): Setlist {
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, setlistFileName(title))
        val setlist = Setlist(fileName = fileName, title = title, priority = priority, entries = emptyList())
        saveSetlist(setlist)
        return setlist
    }

    override suspend fun saveSetlist(setlist: Setlist) = fileStorage.writeText(
        directory = StorageDirectory.SETLISTS,
        name = setlist.fileName,
        text = json.encodeToString(setlist.toDocument())
    )

    override suspend fun deleteSetlist(fileName: String) = fileStorage.delete(StorageDirectory.SETLISTS, fileName)

    private companion object {
        // Pretty printed because these files are meant to survive an export and be readable (and editable) outside the app.
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
