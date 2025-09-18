package com.example.a4all

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.a4all.databinding.ActivityDetailsBinding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.location.Geocoder
import androidx.core.widget.addTextChangedListener
import java.util.*

class DetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var mapView: MapView
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    // Sports list for dropdown/chips
    private val sportsList = listOf("Cricket", "Football", "Basketball", "Tennis", "Hockey", "Badminton")

    // Coroutine scope for geocoding debounce
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup OpenStreetMap
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Default zoom + location (India center if GPS not available)
        val startPoint = GeoPoint(20.5937, 78.9629)
        mapView.controller.setZoom(5.5)
        mapView.controller.setCenter(startPoint)

        // --- USER LOCATION OVERLAY ---
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        mapView.overlays.add(locationOverlay)

        // Once GPS gets location, set marker automatically
        locationOverlay.runOnFirstFix {
            val myLocation = locationOverlay.myLocation
            if (myLocation != null) {
                runOnUiThread {
                    val userPoint = GeoPoint(myLocation.latitude, myLocation.longitude)
                    addMarker(userPoint, "Your Current Location")
                    mapView.controller.setZoom(15.0)
                    mapView.controller.animateTo(userPoint)

                    selectedLat = myLocation.latitude
                    selectedLng = myLocation.longitude
                    binding.etLocation.setText("${myLocation.latitude}, ${myLocation.longitude}")
                }
            }
        }

        // Marker for manual selection
        val marker = Marker(mapView)

        // Listen for map taps (manual selection)
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    mapView.overlays.remove(marker)
                    marker.position = p
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Selected Location"
                    mapView.overlays.add(marker)

                    selectedLat = p.latitude
                    selectedLng = p.longitude
                    binding.etLocation.setText("${p.latitude}, ${p.longitude}")

                    mapView.invalidate()
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                Toast.makeText(this@DetailsActivity, "Long press at: $p", Toast.LENGTH_SHORT).show()
                return true
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(mapEventsOverlay)

        // Setup sports chips
        setupSportsChips()
        binding.etAddress.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val addressText = binding.etAddress.text.toString().trim()
                if (addressText.isNotEmpty()) {
                    updateMapFromAddress(addressText)
                }
            }
        }

        binding.btnSave.setOnClickListener {
            saveUserDetails()
        }
    }

    private fun addMarker(point: GeoPoint, title: String) {
        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun setupSportsChips() {
        val chipGroup = binding.chipGroupSports
        chipGroup.removeAllViews()

        for (sport in sportsList) {
            val chip = Chip(this).apply {
                text = sport
                isCheckable = true
                isClickable = true
            }
            chipGroup.addView(chip)
        }
    }



    private suspend fun geocodeAddress(address: String): GeoPoint? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@DetailsActivity, Locale.getDefault())
            val results = geocoder.getFromLocationName(address, 1)
            if (!results.isNullOrEmpty()) {
                val loc = results[0]
                return@withContext GeoPoint(loc.latitude, loc.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun updateMapFromAddress(address: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val point = geocodeAddress(address)
            if (point != null) {
                addMarker(point, address)
                mapView.controller.setZoom(15.0)
                mapView.controller.animateTo(point)

                selectedLat = point.latitude
                selectedLng = point.longitude
                binding.etLocation.setText("${point.latitude}, ${point.longitude}")
            } else {
                Toast.makeText(this@DetailsActivity, "Location not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserDetails() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        val address = binding.etAddress.text.toString().trim()

        if (address.isEmpty()) {
            Toast.makeText(this, "Please enter your address", Toast.LENGTH_SHORT).show()
            return
        }

        val username = binding.etUsername.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()

        if (username.isEmpty() || ageStr.isEmpty() || phone.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageStr.toIntOrNull()
        if (age == null || age <= 0) {
            Toast.makeText(this, "Please enter a valid age", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.length < 10) {
            Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLat == null || selectedLng == null) {
            Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedSports = mutableListOf<String>()
        for (i in 0 until binding.chipGroupSports.childCount) {
            val chip = binding.chipGroupSports.getChildAt(i) as Chip
            if (chip.isChecked) {
                selectedSports.add(chip.text.toString())
            }
        }

        if (selectedSports.isEmpty()) {
            Toast.makeText(this, "Please select at least one sport", Toast.LENGTH_SHORT).show()
            return
        }

        val user = hashMapOf(
            "userId" to uid,
            "username" to username,
            "age" to age,
            "phone" to phone,
            "location" to location,
            "address" to address,
            "latitude" to selectedLat,
            "longitude" to selectedLng,
            "sports" to selectedSports,
            "email" to auth.currentUser?.email
        )

        db.collection("users").document(uid).set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Details saved!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
