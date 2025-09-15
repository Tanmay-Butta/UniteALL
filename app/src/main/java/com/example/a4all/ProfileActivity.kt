package com.example.a4all

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.a4all.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val PICK_IMAGE_REQUEST = 1001
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserDetails()

        binding.btnUploadPhoto.setOnClickListener {
            openImageChooser()
        }
    }

    private fun loadUserDetails() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    binding.tvUsername.text = "Name: ${document.getString("username")}"
                    binding.tvEmail.text = "Email: ${document.getString("email")}"
                    binding.tvAge.text = "Age: ${document.getLong("age")}"
                    binding.tvPhone.text = "Phone: ${document.getString("phone")}"
                    binding.tvLocation.text = "Location: ${document.getString("location")}"
                    binding.tvSports.text =
                        "Sports: ${(document.get("sports") as? List<*>)?.joinToString(", ")}"

                    // Load profile image if URL exists
                    val imageUrl = document.getString("profileImage")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(imageUrl).into(binding.profileImage)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.data
            uploadImageToCloudinary()
        }
    }

    private fun uploadImageToCloudinary() {
        val uri = imageUri ?: return

        MediaManager.get().upload(uri)
            .unsigned("mobile_unsigned") // Replace with your unsigned preset
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: MutableMap<Any?, Any?>) {
                    val imageUrl = resultData["secure_url"] as? String
                    if (imageUrl != null) {
                        // Show uploaded image
                        Glide.with(this@ProfileActivity).load(imageUrl).into(binding.profileImage)
                        // Save to Firestore
                        saveProfileImageUrlToFirestore(imageUrl)
                    }
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Upload failed: ${error.description}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Upload rescheduled: ${error.description}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }).dispatch()
    }

    private fun saveProfileImageUrlToFirestore(imageUrl: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("profileImage", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save profile image URL", Toast.LENGTH_SHORT).show()
            }
    }
}
