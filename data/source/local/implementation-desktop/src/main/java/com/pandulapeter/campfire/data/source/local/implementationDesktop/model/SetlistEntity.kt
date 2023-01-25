package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class SetlistEntity : RealmObject {

    @PrimaryKey var id: String = ""
    var title: String = ""
    var songIds: String = ""
    var priority: Int = -1
}