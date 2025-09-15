package com.example.a4all

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.a4all.databinding.ActivityLocationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*

class LocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationBinding
    private lateinit var mapView: MapView
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup OSM
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val startPoint = GeoPoint(20.5937, 78.9629) // India center
        mapView.controller.setZoom(5.5)
        mapView.controller.setCenter(startPoint)

        // Enable GPS location
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)

        locationOverlay.runOnFirstFix {
            val myLocation = locationOverlay.myLocation
            if (myLocation != null) {
                runOnUiThread {
                    val point = GeoPoint(myLocation.latitude, myLocation.longitude)
                    addMarker(point)
                    mapView.controller.setZoom(15.0)
                    mapView.controller.animateTo(point)

                    selectedLat = myLocation.latitude
                    selectedLng = myLocation.longitude
                    fillAddress(myLocation.latitude, myLocation.longitude)
                }
            }
        }

        // Tap map to choose location
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    selectedLat = p.latitude
                    selectedLng = p.longitude
                    addMarker(p)
                    fillAddress(p.latitude, p.longitude)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))

        // Handle manual location entry (when user presses Enter/Done)
        binding.etManualLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                updateLocationFromManual()
                true
            } else {
                false
            }
        }

        binding.btnSaveLocation.setOnClickListener {
            saveLocation()
        }
    }

    private fun addMarker(point: GeoPoint) {
        mapView.overlays.removeAll { it is Marker }
        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Selected Location"
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun fillAddress(lat: Double, lng: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val addressLine = addresses[0].getAddressLine(0)
            binding.etManualLocation.setText(addressLine)
        }
    }

    private fun updateLocationFromManual() {
        val manualText = binding.etManualLocation.text.toString().trim()
        if (manualText.isNotEmpty()) {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(manualText, 1)
            if (!addresses.isNullOrEmpty()) {
                val lat = addresses[0].latitude
                val lng = addresses[0].longitude
                val point = GeoPoint(lat, lng)

                selectedLat = lat
                selectedLng = lng
                addMarker(point)
                mapView.controller.setZoom(15.0)
                mapView.controller.animateTo(point)

                Toast.makeText(this, "Location updated on map", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not find location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveLocation() {
        val uid = auth.currentUser?.uid ?: return
        val manualAddress = binding.etManualLocation.text.toString().trim()

        if (manualAddress.isNotEmpty() && (selectedLat == null || selectedLng == null)) {
            // Try to geocode manually entered address
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(manualAddress, 1)
            if (!addresses.isNullOrEmpty()) {
                selectedLat = addresses[0].latitude
                selectedLng = addresses[0].longitude
            }
        }

        if (selectedLat == null || selectedLng == null || manualAddress.isEmpty()) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            return
        }

        val locationData = hashMapOf(
            "latitude" to selectedLat,
            "longitude" to selectedLng,
            "address" to manualAddress
        )

        db.collection("users").document(uid).update(locationData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "Location saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
