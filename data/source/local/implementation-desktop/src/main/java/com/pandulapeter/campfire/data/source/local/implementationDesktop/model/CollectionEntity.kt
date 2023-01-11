package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class CollectionEntity : RealmObject {

    @PrimaryKey var id: String = ""
    var title: String = ""
    var description: String = ""
    var thumbnailUrl: String = ""
    var songIds: String = ""
    var isPublic: Boolean = false
    var databaseUrl: String = ""
}