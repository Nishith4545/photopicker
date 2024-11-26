package com.nishith.mediapicker.fileselector

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nishith.mediapicker.R
import com.nishith.mediapicker.cropper.CropImage
import com.nishith.mediapicker.cropper.CropImageView
import com.nishith.mediapicker.data.FileEntry
import com.nishith.mediapicker.databinding.CustomImageVideoListDialogFragmentBinding
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.CAN_SELECT_MULTIPLE_FLAG
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.CAN_SELECT_MULTIPLE_VIDEO
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.SELECTED_IMAGE_VIDEO_LIST
import com.nishith.mediapicker.fileselector.MediaSelectHelper.Companion.VIDEO_OR_IMAGE
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileNotFoundException
import javax.inject.Inject

@AndroidEntryPoint
class CustomImageVideoListDialogFragment(
    private val onEventListener: (imageList: ArrayList<FileEntry>) -> Unit,
    private val mActivity: FragmentActivity
) : BottomSheetDialogFragment() {

    private var _binding: CustomImageVideoListDialogFragmentBinding? = null

    private val binding get() = _binding!!

    private var cropResult: ActivityResultLauncher<Intent>? = null

    @Inject
    lateinit var mediaSelectHelper: MediaSelectHelper

    private val imageVideoList by lazy {
        arguments?.getParcelableArrayList<FileEntry>(SELECTED_IMAGE_VIDEO_LIST)
    }

    private val canSelectMultipleFlag by lazy {
        arguments?.getBoolean(CAN_SELECT_MULTIPLE_FLAG)
    }

    private val canSelectMultipleVideo by lazy {
        arguments?.getBoolean(CAN_SELECT_MULTIPLE_VIDEO)
    }

    private val imageVideoString by lazy {
        arguments?.getString(VIDEO_OR_IMAGE)
    }

    private val customImageVideoListAdapter by lazy {
        imageVideoList?.let {
            CustomImageVideoListAdapter(
                items = it,
                canSelectMultipleFlag = canSelectMultipleFlag ?: false,
                canSelectMultipleVideo = canSelectMultipleVideo ?: false
            ) {

            }
        }
    }

    private var isCropAvailable: Boolean = false

    private var cropType: CropImageView.CropShape? = null

    private var selectedImage: FileEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cropResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { data: ActivityResult ->
                if (data.resultCode == RESULT_OK) {
                    val result = CropImage.getActivityResult(data.data)
                    val resultUri = result.uri
                    try {
                        selectedImage?.uri = resultUri
                        onEventListener.invoke(arrayListOf(selectedImage!!))
                        dismissAllowingStateLoss()
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            "Something went wrong!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            val parent = view.parent as? ViewGroup
            parent?.let {
                val params = it.layoutParams
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                it.layoutParams = params
            }
        }
        init()
        setRecyclerViewData()
        setListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CustomImageVideoListDialogFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet =
            dialog?.window?.decorView?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val mBehavior = BottomSheetBehavior.from(bottomSheet!!)
        mBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun init() = with(binding) {
        if (imageVideoString == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) {
            textViewLabel.text = getString(R.string.label_select_photos)
            textViewSubLabel.text =
                getString(R.string.label_select_photos_to_allow_only_this_app_manage)
        } else {
            textViewLabel.text = getString(R.string.label_select_videos)
            textViewSubLabel.text =
                getString(R.string.label_select_videos_to_allow_only_this_app_manage)
        }
    }

    private fun openCropViewOrNot(file: Uri) {
        val intent: Intent = when (this.cropType) {
            CropImageView.CropShape.RECTANGLE -> CropImage.activity(file)
                .setCropShape(CropImageView.CropShape.RECTANGLE).setAspectRatio(2, 1)
                .getIntent(mActivity)

            CropImageView.CropShape.OVAL -> CropImage.activity(file)
                .setCropShape(CropImageView.CropShape.OVAL).setAspectRatio(1, 1)
                .getIntent(mActivity)

            else -> CropImage.activity(file).setAspectRatio(4, 4)
                .getIntent(mActivity)
        }
        cropResult?.launch(intent)

    }

    private fun setListeners() = with(binding) {
        /*customImageVideoListAdapter.setOnItemClickListener {
            buttonSelect.show()
        }*/

        buttonSelect.setOnClickListener {
            if ((customImageVideoListAdapter?.getItems()
                    ?.filter { data -> data.isImageSelected } as ArrayList<FileEntry>).isEmpty()
            ) {
                Toast.makeText(requireContext(), "Please select media", Toast.LENGTH_SHORT).show()
            } else {

                if (isCropAvailable && canSelectMultipleFlag == false) {
                    (customImageVideoListAdapter?.getItems()
                        ?.filter { data -> data.isImageSelected } as ArrayList<FileEntry>).getOrNull(
                        0
                    )?.let { it1 ->
                        selectedImage = it1
                        openCropViewOrNot(
                            it1.uri
                        )
                    }
                } else {
                    onEventListener.invoke(
                        customImageVideoListAdapter?.getItems()
                            ?.filter { data -> data.isImageSelected } as ArrayList<FileEntry>)
                    dismissAllowingStateLoss()
                }
            }
        }

        textViewSubLabel.setOnClickListener {
            imageVideoString?.let { it1 ->
                mediaSelectHelper.checkSelfStorageAndOpenPhotoPickerWindowForSelection(
                    it1
                )
            }
        }
    }

    private fun setRecyclerViewData() = with(binding) {
        recyclerViewImagesVideo.adapter = customImageVideoListAdapter
        if (imageVideoList.isNullOrEmpty()) {
            recyclerViewImagesVideo.layoutManager = GridLayoutManager(requireContext(), 1)
        } else {
            recyclerViewImagesVideo.layoutManager = GridLayoutManager(requireContext(), 3)
        }
    }

    fun setItemsAndSetSpan(data: List<FileEntry>) = with(binding) {
        (recyclerViewImagesVideo.layoutManager as GridLayoutManager).spanCount = 3
        customImageVideoListAdapter?.updateItems(data)
    }

    fun setCropAndType(isCropAvailable: Boolean, cropType: CropImageView.CropShape? = null) {
        this@CustomImageVideoListDialogFragment.isCropAvailable = isCropAvailable
        if (cropType != null) {
            this@CustomImageVideoListDialogFragment.cropType = cropType
        }
    }
}