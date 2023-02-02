package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class RawSongDetailsEntity : RealmObject {

    @PrimaryKey var url: String = ""
    var rawData: String = ""
}