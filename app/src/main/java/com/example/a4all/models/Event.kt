package com.example.a4all.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

data class Event(

    @get:PropertyName("Title")
    @set:PropertyName("Title")
    var title: String = "",

    @get:PropertyName("Description")
    @set:PropertyName("Description")
    var description: String = "",

    @get:PropertyName("bannerurl")
    @set:PropertyName("bannerurl")
    var bannerUrl: String = "",

    @get:PropertyName("Address")
    @set:PropertyName("Address")
    var address: String = "",

    @get:PropertyName("Category")
    @set:PropertyName("Category")
    var category: String = "",

    @get:PropertyName("Participants")
    @set:PropertyName("Participants")
    var participants: List<String> = emptyList(),

    @get:PropertyName("event_id")
    @set:PropertyName("event_id")
    var eventId: String = "",

    @get:PropertyName("geolocation")
    @set:PropertyName("geolocation")
    var geoLocation: GeoPoint? = null,

    @get:PropertyName("startTime")
    @set:PropertyName("startTime")
    var startTime: Timestamp? = null,

    @get:PropertyName("endTime")
    @set:PropertyName("endTime")
    var endTime: Timestamp? = null,

    @get:PropertyName("organiserId")
    @set:PropertyName("organiserId")
    var organiserId: String = ""   // 👈 Add this field
)
