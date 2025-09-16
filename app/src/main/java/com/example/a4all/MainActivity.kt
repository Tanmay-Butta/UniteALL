package com.example.a4all

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.example.a4all.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var locationTitle: TextView
    private lateinit var locationSubtitle: TextView
    private lateinit var locationLayout: LinearLayout
    private lateinit var profileButton: ImageView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
        )
        navView.setupWithNavController(navController)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Find views
        locationTitle = findViewById(R.id.locationTitle)
        locationSubtitle = findViewById(R.id.locationSubtitle)
        locationLayout = findViewById(R.id.locationLayout)
        profileButton = findViewById(R.id.profileButton)

        // Load user profile photo in top bar
        loadUserProfilePhoto()

        // Fetch GPS or Firestore location
        getLocation()

        locationLayout.setOnClickListener {
            val intent = Intent(this, LocationActivity::class.java)
            startActivity(intent)
        }

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadUserProfilePhoto() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val imageUrl = document.getString("profileImage")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(imageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person)
                        .into(profileButton)
                }
            }
            .addOnFailureListener {
                // Keep default icon if error
            }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val subLocality = address.subLocality ?: ""
                        val thoroughfare = address.thoroughfare ?: ""
                        val locality = address.locality ?: ""
                        val adminArea = address.adminArea ?: ""

                        val precise = when {
                            subLocality.isNotEmpty() -> subLocality
                            thoroughfare.isNotEmpty() -> thoroughfare
                            else -> locality
                        }

                        locationTitle.text = precise
                        locationSubtitle.text = if (locality.isNotEmpty()) locality else adminArea
                    }

                    // ✅ Pass to HomeFragment
                    navigateToHome(location.latitude, location.longitude)

                } else {
                    // fallback to Firestore user profile location
                    val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                    db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                        val userLat = doc.getDouble("latitude") ?: 0.0
                        val userLng = doc.getDouble("longitude") ?: 0.0
                        locationTitle.text = "From profile"
                        locationSubtitle.text = "Stored location"
                        navigateToHome(userLat, userLng)
                    }
                }
            }
        }
    }

    private fun navigateToHome(lat: Double, lng: Double) {
        val bundle = Bundle().apply {
            putDouble("userLat", lat)
            putDouble("userLng", lng)
        }
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navController.navigate(R.id.navigation_home, bundle)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        }
    }
}
