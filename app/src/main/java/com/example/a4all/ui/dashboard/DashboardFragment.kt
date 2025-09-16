package com.example.a4all.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.a4all.databinding.FragmentDashboardBinding
import com.example.a4all.ui.events.PostEventActivity

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Optional: Keep dashboard text logic
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            binding.textHeading.text = it  // now using the heading text instead of old text_dashboard
        }

        // Handle "Post New Event" button click
        binding.btnPostEvent.setOnClickListener {
            // TODO: Replace PostEventActivity::class.java with your actual activity
            val intent = Intent(requireContext(), PostEventActivity::class.java)
            startActivity(intent)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
