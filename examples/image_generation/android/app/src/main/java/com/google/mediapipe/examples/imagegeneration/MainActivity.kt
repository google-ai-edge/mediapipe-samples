package com.google.mediapipe.examples.imagegeneration

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.imagegeneration.databinding.ActivityMainBinding
import com.google.mediapipe.examples.imagegeneration.diffusion.DiffusionActivity
import com.google.mediapipe.examples.imagegeneration.loraweights.LoRAWeightActivity
import com.google.mediapipe.examples.imagegeneration.plugins.PluginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDiffusion.setOnClickListener {
            startActivity(Intent(this, DiffusionActivity::class.java))
        }

        binding.btnPlugins.setOnClickListener {
            startActivity(Intent(this, PluginActivity::class.java))
        }

        binding.btnLoRA.setOnClickListener {
            startActivity(Intent(this, LoRAWeightActivity::class.java))
        }
    }

}