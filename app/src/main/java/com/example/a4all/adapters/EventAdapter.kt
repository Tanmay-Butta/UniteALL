package com.example.a4all.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.a4all.R
import com.example.a4all.models.Event
import com.google.firebase.Timestamp
import kotlin.math.*

class EventAdapter(
    private val events: List<Event>,
    private val userLat: Double,
    private val userLng: Double,
    private val onItemClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bannerImage: ImageView = itemView.findViewById(R.id.eventBanner)
        val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        val eventDescription: TextView = itemView.findViewById(R.id.eventDescription)
        val eventDate: TextView = itemView.findViewById(R.id.eventDate)
        val eventDistance: TextView = itemView.findViewById(R.id.eventDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        Log.d("EventAdapter", "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        // 🔎 Log full object
        Log.d("EventAdapter", "onBindViewHolder - Event[$position]: $event")

        // Banner
        if (!event.bannerUrl.isNullOrBlank() && event.bannerUrl != ".") {
            Glide.with(holder.itemView.context)
                .load(event.bannerUrl)
                .placeholder(R.drawable.ic_placeholder)
                .into(holder.bannerImage)
            Log.d("EventAdapter", "Loaded banner from URL: ${event.bannerUrl}")
        } else {
            holder.bannerImage.setImageResource(R.drawable.ic_placeholder)
            Log.d("EventAdapter", "No valid banner URL, using placeholder")
        }

        // Title & Description
        holder.eventTitle.text = event.title.ifEmpty { "Untitled Event" }
        holder.eventDescription.text = event.description.ifEmpty { "No description available" }

        // Time
        val formattedTime = formatTime(event.startTime, event.endTime)
        holder.eventDate.text = formattedTime
        Log.d("EventAdapter", "Event time: $formattedTime")

        // Distance calculation
        val distance = event.geoLocation?.let {
            val d = haversine(userLat, userLng, it.latitude, it.longitude)
            Log.d("EventAdapter", "Calculated distance = $d km for ${event.title}")
            d
        } ?: run {
            Log.w("EventAdapter", "GeoLocation missing for ${event.title}")
            0.0
        }

        holder.eventDistance.text = "${event.address} • ${"%.1f".format(distance)} km away"
        holder.itemView.setOnClickListener {
            onItemClick(event)
        }
    }

    override fun getItemCount(): Int {
        Log.d("EventAdapter", "getItemCount = ${events.size}")
        return events.size
    }

    private fun formatTime(start: Timestamp?, end: Timestamp?): String {
        if (start == null || end == null) {
            Log.w("EventAdapter", "Missing start or end time")
            return "Time not available"
        }
        val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a")
        return "${sdf.format(start.toDate())} - ${sdf.format(end.toDate())}"
    }

    // Haversine Formula
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
