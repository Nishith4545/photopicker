package com.nishith.justtestmodule

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.core.os.bundleOf
import com.nishith.justtestmodule.databinding.ActivityMainBinding
import com.nishith.mediapicker.base.BaseActivity
import com.nishith.mediapicker.extention.loadImagefromServerAny
import com.nishith.mediapicker.fileselector.CustomImageVideoListDialogFragment
import com.nishith.mediapicker.fileselector.MediaSelectHelper
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.CAN_SELECT_MULTIPLE_FLAG
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.CAN_SELECT_MULTIPLE_VIDEO
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.CROP_AVAILABLE
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.MEDIA_PICKER
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.SELECTED_IMAGE_VIDEO_LIST
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.VIDEO_OR_IMAGE
import com.nishith.mediapicker.fileselector.MediaSelector
import com.nishith.mediapicker.utils.FileHelperKit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var mediaSelectHelper: MediaSelectHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setImagePicker()
        setClickListener()
    }

    override fun createViewBinding(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return  binding.root
    }

    private fun setClickListener() = with(binding) {
        btnLaunchPicker.setOnClickListener {
            mediaSelectHelper.canSelectMultipleImages(false)
            mediaSelectHelper.selectOptionsForImagePicker(false)
        }
    }

    private fun setImagePicker() = with(binding) {
        mediaSelectHelper.registerCallback(object : MediaSelector {
            override fun onImageUri(uri: Uri) {
                uri.path?.let {
                    imageView.loadImagefromServerAny(it)
                }
            }

            override fun onCameraVideoUri(uri: Uri) {
                uri.path?.let {
                    imageView.loadImagefromServerAny(it)
                }
            }

            override fun onNewImplementedStorageAccordingAccess(
                storageAccess: String,
                canSelectMultipleFlag: Boolean,
                canSelectMultipleVideo: Boolean,
                imageOrVideo: String
            ) {
                super.onNewImplementedStorageAccordingAccess(
                    storageAccess,
                    canSelectMultipleFlag,
                    canSelectMultipleVideo,
                    imageOrVideo
                )

                CoroutineScope(Dispatchers.IO).launch {
                    mediaSelectHelper.getVisualMedia(
                        this@MainActivity.contentResolver,
                        imageOrVideo
                    )
                        .let {
                            withContext(Dispatchers.Main) {
                                supportFragmentManager.findFragmentByTag(MEDIA_PICKER).let { j ->
                                    if (j != null) {
                                        if (it.isNotEmpty()) {
                                            (j as CustomImageVideoListDialogFragment).setItemsAndSetSpan(
                                                it
                                            )
                                        }
                                    } else {
                                        var customImageVideoListDialogFragment =
                                            CustomImageVideoListDialogFragment({
                                                if (imageOrVideo == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) {
                                                    it.map {
                                                        FileHelperKit.getPath(
                                                            this@MainActivity,
                                                            it.uri
                                                        )
                                                            ?.let { new ->
                                                                imageView.loadImagefromServerAny(
                                                                    new
                                                                )
                                                            }
                                                    }
                                                } else {
                                                    it.map {
                                                        FileHelperKit.getPath(
                                                            this@MainActivity,
                                                            it.uri
                                                        )?.let { newPath ->
                                                            imageView.loadImagefromServerAny(
                                                                newPath
                                                            )
                                                        }
                                                    }
                                                }
                                            }, this@MainActivity)
                                        customImageVideoListDialogFragment.arguments =
                                            bundleOf(
                                                SELECTED_IMAGE_VIDEO_LIST to it,
                                                CAN_SELECT_MULTIPLE_FLAG to canSelectMultipleFlag,
                                                CAN_SELECT_MULTIPLE_VIDEO to canSelectMultipleVideo,
                                                VIDEO_OR_IMAGE to imageOrVideo,
                                                CROP_AVAILABLE to true
                                            )
                                        customImageVideoListDialogFragment.setCropAndType(
                                            true
                                        )
                                        customImageVideoListDialogFragment.show(
                                            supportFragmentManager,
                                            MEDIA_PICKER
                                        )
                                    }
                                }

                            }
                        }
                }
            }
        })
    }
}
