package com.nishith.photopickerDemoApp

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.nishith.photopicker.base.BaseActivity
import com.nishith.photopicker.fileselector.MediaSelectHelper
import com.nishith.photopicker.fileselector.MediaSelector
import com.nishith.photopicker.fileselector.OutPutFileAny
import com.nishith.photopicker.utils.FileHelperKit.getPath
import com.nishith.photopickerDemoApp.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mediaSelectHelper: MediaSelectHelper

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSelectHelper = MediaSelectHelper(this)
        initPlayer()
        setImagePicker()
        setClickListener()
    }

    private fun initPlayer() = with(binding) {
        player = ExoPlayer.Builder(this@MainActivity).build()
        playerView.player = player
    }

    override fun createViewBinding(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    private fun setClickListener() = with(binding) {
        btnLaunchPicker.setOnClickListener {
            mediaSelectHelper.canSelectMultipleImages(false)
            mediaSelectHelper.selectOptionsForImagePicker(true)
            //mediaSelectHelper.openCameraPictureIntent(true,CROP_SQUARE)
        }

        btnLaunchVideoPicker.setOnClickListener {
            mediaSelectHelper.canSelectMultipleVideo(false)
            mediaSelectHelper.selectOptionsForVideoPicker()
            //mediaSelectHelper.openCameraVideoIntent()
        }
    }

    private fun setImagePicker() = with(binding) {
        mediaSelectHelper.registerCallback(object : MediaSelector {
            override fun onVideoUri(uri: Uri) {
                super.onVideoUri(uri)
                getPath(this@MainActivity, uri)?.let { it1 ->
                    playerView.show()
                    imageView.hide()
                    setUpVideoUrl(uri)
                }
            }

            override fun onImageUri(uri: Uri) {
                playerView.hide()
                imageView.show()
                uri.path?.let {
                    imageView.loadImagefromServerAny(it)
                }
            }

            override fun onCameraVideoUri(uri: Uri) {
                playerView.show()
                imageView.hide()
                setUpVideoUrl(uri)
            }


            override fun onImageUriList(uriArrayList: ArrayList<Uri>) {
                    super.onImageUriList(uriArrayList)
            }

            override fun onVideoURIList(uriArrayList: ArrayList<Uri>) {
                super.onVideoURIList(uriArrayList)
            }

            override fun onAnyFileSelected(outPutFileAny: OutPutFileAny) {
                super.onAnyFileSelected(outPutFileAny)
            }
        }, supportFragmentManager)
    }

    private fun setUpVideoUrl(uri: Uri) {
        val mediaItem: MediaItem = MediaItem.fromUri(uri)
        player!!.setMediaItem(mediaItem)
        player!!.prepare()
        player!!.playWhenReady = true
    }
}

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.hide() {
    this.visibility = View.GONE
}

fun ImageView?.loadImagefromServerAny(path: Any?) {
    this?.context?.let {
        Glide.with(it)
            .load(path)
            .into(this)
    }
}