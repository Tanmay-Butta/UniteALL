package com.example.a4all.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a4all.R
import com.example.a4all.adapters.EventAdapter
import com.example.a4all.models.Event
import com.example.a4all.ui.events.EventDetailsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventAdapter
    private val events = mutableListOf<Event>()

    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            userLat = it.getDouble("userLat", 0.0)
            userLng = it.getDouble("userLng", 0.0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.eventsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

       getUserLocation { lat, lng ->
            adapter = EventAdapter(events, lat, lng) { event ->
                // ✅ Navigate to EventDetailsActivity
                val intent = Intent(requireContext(), EventDetailsActivity::class.java)
                intent.putExtra("eventId", event.eventId) // from your model
                startActivity(intent)
            }
            recyclerView.adapter = adapter
            loadEvents()
        }


        return view
    }

    private fun getUserLocation(callback: (Double, Double) -> Unit) {
        if (userLat != 0.0 && userLng != 0.0) {
            callback(userLat, userLng)
        } else {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { doc ->
                        val lat = doc.getDouble("lat") ?: 0.0
                        val lng = doc.getDouble("lng") ?: 0.0
                        callback(lat, lng)
                    }
                    .addOnFailureListener { callback(0.0, 0.0) }
            } else {
                callback(0.0, 0.0)
            }
        }
    }

    // ✅ Load events from Firestore
    private fun loadEvents() {
        FirebaseFirestore.getInstance()
            .collection("events")
            .get()
            .addOnSuccessListener { snapshot ->
                events.clear()
                for (doc in snapshot.documents) {
                    val event = doc.toObject(Event::class.java)
                    if (event != null) {
                        events.add(event)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                // TODO: Handle errors gracefully (Toast/snackbar)
            }
    }
}
