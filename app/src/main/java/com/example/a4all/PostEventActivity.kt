package com.example.a4all.ui.events

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.a4all.R
import com.example.a4all.models.Event
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver

class PostEventActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etAddress: EditText
    private lateinit var etCategory: EditText
    private lateinit var etDescription: EditText
    private lateinit var ivBanner: ImageView
    private lateinit var btnPickBanner: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnPostEvent: Button

    private lateinit var mapView: MapView
    private var marker: Marker? = null

    private var startTime: Timestamp? = null
    private var endTime: Timestamp? = null
    private var bannerUrl: String = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var geoPoint: GeoPoint? = null

    // Cloudinary config
    private val cloudName = "dib4zp8ke"
    private val uploadPreset = "mobile_unsigned"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_event)

        etTitle = findViewById(R.id.etTitle)
        etAddress = findViewById(R.id.etAddress)
        etCategory = findViewById(R.id.etCategory)
        etDescription = findViewById(R.id.etDescription)
        ivBanner = findViewById(R.id.ivBanner)
        btnPickBanner = findViewById(R.id.btnPickBanner)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnPostEvent = findViewById(R.id.btnPostEvent)
        mapView = findViewById(R.id.mapView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // --- Setup OSM Map ---
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        mapView.setMultiTouchControls(true)

        val defaultPoint = OsmGeoPoint(28.6139, 77.2090) // Delhi default
        mapView.controller.setZoom(18.0)
        mapView.controller.setCenter(defaultPoint)

        marker = Marker(mapView)
        marker!!.position = defaultPoint
        marker!!.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker!!.title = "Event Location"
        mapView.overlays.add(marker)

        // ✅ Add MapEventsOverlay to handle user taps
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                if (p != null) {
                    marker!!.position = p
                    geoPoint = GeoPoint(p.latitude, p.longitude)
                    mapView.invalidate()
                }
                return true
            }

            override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                return false
            }
        }
        val eventsOverlay = MapEventsOverlay(eventsReceiver)
        mapView.overlays.add(eventsOverlay)

        // ✅ Update map when user enters address and presses "Done"/"Enter"
        etAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val addressText = etAddress.text.toString().trim()
                if (addressText.isNotEmpty()) {
                    updateMapFromAddress(addressText)
                }
                true
            } else false
        }

        // Pick Start Time
        btnStartTime.setOnClickListener {
            pickDateTime { timestamp ->
                startTime = timestamp
                btnStartTime.text = timestamp.toDate().toString()
            }
        }

        // Pick End Time
        btnEndTime.setOnClickListener {
            pickDateTime { timestamp ->
                endTime = timestamp
                btnEndTime.text = timestamp.toDate().toString()
            }
        }

        // Pick Banner from Gallery
        btnPickBanner.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Post Event
        btnPostEvent.setOnClickListener {
            checkLocationPermissionAndFetch()
        }
    }

    // ✅ Function: Convert address into lat/lng and update map
    private fun updateMapFromAddress(address: String) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                val location = addresses[0]
                val point = OsmGeoPoint(location.latitude, location.longitude)

                marker?.position = point
                mapView.controller.setZoom(18.0)
                mapView.controller.setCenter(point)
                mapView.invalidate()

                geoPoint = GeoPoint(location.latitude, location.longitude)
                Toast.makeText(this, "Location updated from address", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Pick Image ---
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                ivBanner.setImageURI(it)
                uploadToCloudinary(it)
            }
        }

    private fun uploadToCloudinary(uri: Uri) {
        val filePath = getRealPathFromURI(uri) ?: return
        val file = File(filePath)

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("image/*".toMediaTypeOrNull())
            )
            .addFormDataPart("upload_preset", uploadPreset)
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PostEventActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "")
                bannerUrl = json.optString("secure_url")
                runOnUiThread {
                    Toast.makeText(this@PostEventActivity, "Image Uploaded!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return if (cursor != null) {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            val path = cursor.getString(idx)
            cursor.close()
            path
        } else uri.path
    }

    // --- Pick Date/Time ---
    private fun pickDateTime(onPicked: (Timestamp) -> Unit) {
        val cal = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val timePicker = TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        cal.set(year, month, dayOfMonth, hour, minute)
                        onPicked(Timestamp(cal.time))
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                )
                timePicker.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // --- Permission check before fetching location ---
    private fun checkLocationPermissionAndFetch() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
            return
        }
        fetchLocationAndPost()
    }

    // --- Fetch GPS Location then Post Event ---
    private fun fetchLocationAndPost() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        geoPoint = GeoPoint(location.latitude, location.longitude)

                        val userPoint = OsmGeoPoint(location.latitude, location.longitude)
                        mapView.controller.setCenter(userPoint)
                        marker?.position = userPoint
                        mapView.invalidate()
                    } else {
                        Toast.makeText(this, "Could not get location", Toast.LENGTH_SHORT).show()
                        geoPoint = GeoPoint(28.6139, 77.2090)
                    }
                    postEvent()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Location error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission missing!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun postEvent() {
        val title = etTitle.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val category = etCategory.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (title.isEmpty() || address.isEmpty() || category.isEmpty() ||
            description.isEmpty() || bannerUrl.isEmpty() ||
            startTime == null || endTime == null || geoPoint == null
        ) {
            Toast.makeText(this, "Please fill all fields and upload image", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val eventId = UUID.randomUUID().toString()

        val event = Event(
            title = title,
            description = description,
            bannerUrl = bannerUrl,
            address = address,
            category = category,
            participants = listOf(),
            eventId = eventId,
            geoLocation = geoPoint,
            startTime = startTime,
            endTime = endTime,
            organiserId = currentUser.uid
        )

        FirebaseFirestore.getInstance()
            .collection("events")
            .document(eventId)
            .set(event)
            .addOnSuccessListener {
                Toast.makeText(this, "Event Posted!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Handle user’s response to permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocationAndPost()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
