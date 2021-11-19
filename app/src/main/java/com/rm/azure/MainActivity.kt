package com.rm.azure

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rm.azure.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view)
    setSupportActionBar(binding.toolbar)

    supportFragmentManager.beginTransaction()
      .replace(R.id.fragment_container_view, LoginFragment(), null)
      .commit()
  }
}