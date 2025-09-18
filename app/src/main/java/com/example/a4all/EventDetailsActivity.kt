package com.example.a4all.ui.events

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.a4all.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Locale

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var eventBanner: ImageView
    private lateinit var eventTitle: TextView
    private lateinit var eventDescription: TextView
    private lateinit var eventAddress: TextView
    private lateinit var eventCategory: TextView
    private lateinit var eventDateTime: TextView
    private lateinit var participantsContainer: LinearLayout
    private lateinit var registerButton: Button
    private lateinit var navigateButton: Button
    private lateinit var mapView: MapView

    private var eventLat: Double? = null
    private var eventLng: Double? = null
    private var eventId: String? = null

    private val TAG = "EventDetailsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Load osmdroid config
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_event_details)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // UI binding
        eventBanner = findViewById(R.id.eventBanner)
        eventTitle = findViewById(R.id.eventTitle)
        eventDescription = findViewById(R.id.eventDescription)
        eventAddress = findViewById(R.id.eventAddress)
        eventCategory = findViewById(R.id.eventCategory)
        eventDateTime = findViewById(R.id.eventDateTime)
        participantsContainer = findViewById(R.id.participantsContainer)
        registerButton = findViewById(R.id.registerButton)
        navigateButton = findViewById(R.id.navigateButton)
        mapView = findViewById(R.id.mapView)

        // Configure OSM map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Get Event Id from Intent
        eventId = intent.getStringExtra("eventId")
        Log.d(TAG, "Received eventId: $eventId")

        if (eventId != null) {
            loadEventDetails(eventId!!)
        } else {
            Log.e(TAG, "No eventId found in Intent extras!")
        }

        registerButton.setOnClickListener { registerForEvent() }
        navigateButton.setOnClickListener { navigateToEvent() }
    }

    private fun loadEventDetails(eventId: String) {
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Log.d(TAG, "Event document found: ${doc.data}")

                    val title = doc.getString("Title") ?: ""
                    val description = doc.getString("Description") ?: ""
                    val address = doc.getString("Address") ?: ""
                    val category = doc.getString("Category") ?: ""
                    val bannerUrl = doc.getString("bannerurl")
                    val participants = doc.get("Participants") as? List<String> ?: emptyList()

                    // ✅ GeoPoint for location
                    val geoPoint = doc.getGeoPoint("geolocation")
                    eventLat = geoPoint?.latitude
                    eventLng = geoPoint?.longitude

                    // ✅ Format dates
                    val startDate = doc.getTimestamp("startTime")?.toDate()
                    val endDate = doc.getTimestamp("endTime")?.toDate()

                    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    val formattedStart = startDate?.let { sdf.format(it) } ?: "N/A"
                    val formattedEnd = endDate?.let { sdf.format(it) } ?: "N/A"

                    // Populate UI
                    eventTitle.text = title
                    eventDescription.text = description
                    eventAddress.text = address
                    eventCategory.text = category
                    eventDateTime.text = "From: $formattedStart\nTo: $formattedEnd"

                    if (!bannerUrl.isNullOrEmpty()) {
                        Glide.with(this).load(bannerUrl).into(eventBanner)
                    }

                    // Load participants
                    loadParticipants(participants)

                    // Check if current user is registered
                    val userId = auth.currentUser?.uid
                    Log.d(TAG, "Current user: $userId, Participants: $participants")
                    if (userId != null && participants.contains(userId)) {
                        setRegisteredState()
                    }

                    // Show location on OSM
                    if (eventLat != null && eventLng != null) {
                        val mapController = mapView.controller
                        mapController.setZoom(15.0)
                        mapController.setCenter(GeoPoint(eventLat!!, eventLng!!))

                        val marker = Marker(mapView)
                        marker.position = GeoPoint(eventLat!!, eventLng!!)
                        marker.title = title
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mapView.overlays.add(marker)
                        mapView.invalidate()
                    }
                } else {
                    Log.e(TAG, "Event document does not exist for ID: $eventId")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch event details", e)
            }
    }



    private fun loadParticipants(participantIds: List<String>) {
        Log.d(TAG, "Loading participants: $participantIds")
        participantsContainer.removeAllViews()

        for (uid in participantIds) {
            db.collection("users")
                .whereEqualTo("userId", uid) // ✅ match the field, not the doc ID
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val userDoc = querySnapshot.documents[0]
                        val name = userDoc.getString("username") ?: "Unknown"
                        Log.d(TAG, "Loaded participant: $uid ($name)")

                        val tv = TextView(this)
                        tv.text = "• $name"
                        tv.textSize = 16f
                        participantsContainer.addView(tv)
                    } else {
                        Log.w(TAG, "No user found for uid: $uid")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load participant $uid", e)
                }
        }
    }


    private fun registerForEvent() {
        val userId = auth.currentUser?.uid ?: return
        eventId?.let { id ->
            val eventRef = db.collection("events").document(id)
            Log.d(TAG, "Registering user $userId for event $id")

            // ✅ Use "Participants" (capital P) to match Firestore
            eventRef.update("Participants", FieldValue.arrayUnion(userId))
                .addOnSuccessListener {
                    Log.d(TAG, "User $userId registered successfully")
                    setRegisteredState()
                    Toast.makeText(this, "Registered successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register for event", e)
                }
        }
    }


    private fun setRegisteredState() {
        Log.d(TAG, "Setting registered state UI")
        registerButton.isEnabled = false
        registerButton.text = "Registered"
        registerButton.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
    }

    private fun navigateToEvent() {
        if (eventLat != null && eventLng != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("geo:$eventLat,$eventLng")
                intent.setPackage("net.osmand") // force OsmAnd app
                startActivity(intent)
            } catch (e: Exception) {
                // fallback: open in browser if OsmAnd not installed
                val browserUri = Uri.parse("https://www.openstreetmap.org/?mlat=$eventLat&mlon=$eventLng#map=15/$eventLat/$eventLng")
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        mapView.onPause()
    }
}
