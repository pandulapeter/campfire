package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class DatabaseEntity : RealmObject {

    @PrimaryKey var url: String = ""
    var name: String = ""
    var isEnabled: Boolean = false
    var priority: Int = -1
    var isAddedByUser: Boolean = false
}