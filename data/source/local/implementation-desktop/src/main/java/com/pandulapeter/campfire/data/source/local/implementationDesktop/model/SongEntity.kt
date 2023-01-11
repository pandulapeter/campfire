package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class SongEntity : RealmObject {

    @PrimaryKey var id: String = ""
    var url: String = ""
    var title: String = ""
    var artist: String = ""
    var key: String = ""
    var isExplicit: Boolean = false
    var hasChords: Boolean = false
    var isPublic: Boolean = false
    var databaseUrl: String = ""
}