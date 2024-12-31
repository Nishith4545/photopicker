package com.nishith.mediaselectorapp

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.nishith.mediapicker.base.BaseActivity
import com.nishith.mediapicker.extention.hide
import com.nishith.mediapicker.extention.loadImagefromServerAny
import com.nishith.mediapicker.extention.show
import com.nishith.mediapicker.fileselector.MediaSelectHelper
import com.nishith.mediapicker.fileselector.MediaSelector
import com.nishith.mediapicker.utils.FileHelperKit.getPath
import com.nishith.mediaselectorapp.databinding.ActivityMainBinding


class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    lateinit var mediaSelectHelper: MediaSelectHelper

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSelectHelper = MediaSelectHelper(this)
        //mediaSelectHelper.setLimitedAccessLayoutBackgroundColor(R.color.teal_200)
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
        }

        btnLaunchVideoPicker.setOnClickListener {
            mediaSelectHelper.canSelectMultipleVideo(false)
            mediaSelectHelper.selectOptionsForVideoPicker()
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

            override fun onUpdatedStorageMedia(
                storageAccess: String,
                canSelectMultipleImages: Boolean,
                canSelectMultipleVideos: Boolean,
                selectFilter: String,
                mediaPath: String
            ) {
                super.onUpdatedStorageMedia(
                    storageAccess,
                    canSelectMultipleImages,
                    canSelectMultipleVideos,
                    selectFilter,
                    mediaPath
                )
                if (selectFilter == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()){
                    imageView.loadImagefromServerAny(
                        mediaPath
                    )
                    playerView.hide()
                    imageView.show()
                }else{
                    playerView.show()
                    imageView.hide()
                    val uri = Uri.parse(mediaPath)
                    setUpVideoUrl(uri)
                }

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
