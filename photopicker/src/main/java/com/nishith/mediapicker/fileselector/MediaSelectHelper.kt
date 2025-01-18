package com.nishith.mediapicker.fileselector

import android.Manifest
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nishith.mediapicker.R
import com.nishith.mediapicker.cropper.CropImage
import com.nishith.mediapicker.cropper.CropImageView
import com.nishith.mediapicker.data.FileEntry
import com.nishith.mediapicker.extention.showToast
import com.nishith.mediapicker.utils.FileHelperKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


class MediaSelectHelper(private var mActivity: AppCompatActivity) :
    FileSelectorMethods, DefaultLifecycleObserver {

    private suspend fun getVisualMedia(
        contentResolver: ContentResolver,
        videoOrImageType: String
    ): List<FileEntry> {
        return withContext(Dispatchers.IO) {
            // List of columns we want to fetch
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED,
            )

            val collectionUri =
                // This allows us to query all the device storage volumes instead of the primary only
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val visualMedia = mutableListOf<FileEntry>()

            contentResolver.query(
                // Queried collection
                collectionUri,
                // List of columns we want to fetch
                projection,
                // Filtering parameters (in this case [MEDIA_TYPE] column)
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                arrayOf(
                    videoOrImageType
                ),
                // Sorting order (recent -> older files)
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeTypeColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateAddedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getInt(idColumn)
                    val uri = ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn))
                    val name = cursor.getString(displayNameColumn)
                    val size = cursor.getLong(sizeColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    visualMedia.add(FileEntry(id, uri, name, size, mimeType, dateAdded))
                }
            }

            return@withContext visualMedia
        }
    }

    private var permissionPreference: String? = null
    private var canSelectMultipleFlag: Boolean = false
    private var canSelectMultipleVideo: Boolean = false
    private var isSelectAnyFile: Boolean = false
    private var isSelectingVideo: Boolean = false
    private var mMediaSelector: MediaSelector? = null
    private var fragmentManager: FragmentManager? = null
    private var fileForCameraIntent: String = ""
    private var photoFile: File? = null
    private var galleryIntent: Intent? = null
    private var videoIntent: Intent? = null
    private var storageAccess = FULL
    private var cropType = Constant.CROP_SQUARE
    private var isCrop = false

    private val cameraPermissionList = arrayOf(
        Manifest.permission.CAMERA
    )

    object Constant {
        const val CROP_SQUARE = "CropSquare"
        const val CROP_RECTANGLE = "CropRectangle"
        const val CROP_CIRCLE = "CropCircle"
    }

    private var cameraResult: ActivityResultLauncher<Intent>
    private var activityResultLauncherCamera: ActivityResultLauncher<Array<String>>
    private var cropResult: ActivityResultLauncher<Intent>
    private var galleryResult: ActivityResultLauncher<Intent>
    private var galleryVideoResult: ActivityResultLauncher<Intent>
    private var activityResultLauncherGallery: ActivityResultLauncher<Array<String>>
    private var readExternalStorage: ActivityResultLauncher<String>
    private var singlePhotoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var multiplePhotoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var anyFilePicker: ActivityResultLauncher<Intent>
    private var cacheDir: File? = mActivity.cacheDir
    private var selectedDataFromPicker = false

    val MAX_FILE_SIZE = 300 * 1024 * 1024 // 100 MB

    fun getFileSize(uri: Uri): Long {
        var fileSize: Long = 0
        try {
            // Query the URI for its file size using OpenableColumns.SIZE
            val cursor = mActivity.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            cursor?.use {
                // Check if the cursor is not empty and the column exists
                val sizeColumnIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumnIndex != -1 && it.moveToFirst()) {
                    fileSize = it.getLong(sizeColumnIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileSize
    }


    init {
        galleryResult =
            mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.apply {
                        if (!canSelectMultipleFlag) {
                            val retriever = MediaMetadataRetriever()
                            val selectedMedia: Uri? = data
                            val cR = mActivity.contentResolver
                            val mime = MimeTypeMap.getSingleton()
                            val type =
                                mime.getExtensionFromMimeType(cR.getType(selectedMedia!!))
                            if (Objects.requireNonNull(type)
                                    .equals("png", ignoreCase = true) || type!!.equals(
                                    "jpeg", ignoreCase = true
                                ) || type.equals("jpg", ignoreCase = true)
                            ) {
                                if (selectedMedia.toString().contains("image")) {
                                    openCropViewOrNot(selectedMedia)
                                } else {
                                    openCropViewOrNot(selectedMedia)
                                }
                            }
                            retriever.release()
                        } else {
                            if (this.clipData != null) {
                                val mClipData: ClipData? = this.clipData
                                val mArrayUri = ArrayList<Uri>()
                                for (i in 0 until mClipData?.itemCount!!) {
                                    val item = mClipData.getItemAt(i)
                                    val uri: Uri = item.uri
                                    mArrayUri.add(uri)
                                    // Get the cursor
                                }
                                mMediaSelector?.onImageUriList(mArrayUri)
                            } else {
                                val selectedMedia: Uri? = data
                                selectedMedia?.let {
                                    ArrayList<Uri>().apply {
                                        add(it)
                                        mMediaSelector?.onImageUriList(this)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        cameraResult =
            mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    if (isSelectingVideo) {
                        mMediaSelector?.onCameraVideoUri(Uri.fromFile(File(fileForCameraIntent)))
                    } else {
                        openCropViewOrNot(Uri.fromFile(File(fileForCameraIntent)))
                    }
                }
            }

        activityResultLauncherCamera =
            mActivity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
                if (checkAllPermission(grantResults)) {
                    if (isSelectingVideo) dispatchTakeVideoIntent()
                    else openCamera()
                } else if (!checkAllPermission(grantResults)) {
                    if (!deniedForever(grantResults)) {
                        mActivity.showToast("Please allow permission from setting")
                    }
                }
            }

        activityResultLauncherGallery =
            mActivity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                checkSelfPermission(permissionPreference?: IMAGE_PERMISSION)
            }

        singlePhotoPickerLauncher =
            mActivity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                // Callback is invoked after the user selects a media item or closes the
                // photo picker.
                if (uri != null) {
                    if (isSelectingVideo) {
                        val fileSize = getFileSize(uri)
                        if (fileSize > MAX_FILE_SIZE) {
                            // Show a message if the file is too large
                            Toast.makeText(mActivity, "The selected file is too large. Please select a smaller video.", Toast.LENGTH_LONG).show()
                        }else{
                            mMediaSelector?.onVideoUri(uri)
                        }
                    } else {
                        selectedDataFromPicker = true
                        openCropViewOrNot(uri)
                    }
                }
            }

        anyFilePicker =
            mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.apply {
                        val selectedMedia: Uri? = data
                        val cR = mActivity.contentResolver
                        val mime = MimeTypeMap.getSingleton()
                        mime.getExtensionFromMimeType(cR.getType(selectedMedia!!))

                        val contentResolver = mActivity.contentResolver
                        when (contentResolver?.getType(selectedMedia)) {
                            "image/jpeg", "image/png" -> {
                                mMediaSelector?.onAnyFileSelected(
                                    OutPutFileAny(
                                        Uri.fromFile(
                                            getActualPath(selectedMedia, createImageFile())
                                        ), FileType.Image
                                    )
                                )
                            }

                            "application/pdf" -> {
                                val copyFile =
                                    getActualPath(selectedMedia, createAnyFile(".pdf"))
                                mMediaSelector?.onAnyFileSelected(
                                    OutPutFileAny(
                                        Uri.fromFile(
                                            copyFile
                                        ), FileType.Pdf
                                    )
                                )

                            }

                            "application/msword" -> {
                                val copyFile =
                                    getActualPath(selectedMedia, createAnyFile(".doc"))
                                mMediaSelector?.onAnyFileSelected(
                                    OutPutFileAny(
                                        Uri.fromFile(
                                            copyFile
                                        ), FileType.Doc
                                    )
                                )

                            }

                            else -> {

                            }
                        }
                    }
                }
            }

        multiplePhotoPickerLauncher =
            mActivity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(25)) { imageUris: List<Uri> ->
                /*if (imageUris.isNotEmpty()) {
                    if (isSelectingVideo) {
                        mMediaSelector?.onVideoURIList(imageUris as ArrayList<Uri>)
                    } else {
                        selectedDataFromPicker = true
                        mMediaSelector?.onImageUriList(imageUris as ArrayList<Uri>)
                    }
                }*/
                if (imageUris.isNotEmpty()) {
                    var isFileTooLarge = false

                    // Check file size for each selected URI
                    for (uri in imageUris) {
                        val fileSize = getFileSize(uri)

                        if (fileSize > MAX_FILE_SIZE) {
                            isFileTooLarge = true
                            break // No need to check further if we found a large file
                        }
                    }

                    if (isFileTooLarge) {
                        // Show a message if any file is too large
                        Toast.makeText(mActivity, "One or more files are too large. Please select smaller files.", Toast.LENGTH_LONG).show()
                    } else {
                        // Proceed with the video or image selection as per your logic
                        if (isSelectingVideo) {
                            mMediaSelector?.onVideoURIList(imageUris as ArrayList<Uri>)
                        } else {
                            selectedDataFromPicker = true
                            mMediaSelector?.onImageUriList(imageUris as ArrayList<Uri>)
                        }
                    }
                }
            }

        galleryVideoResult =
            mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.apply {
                        if (!canSelectMultipleVideo) {
                            val retriever = MediaMetadataRetriever()
                            val selectedMedia: Uri? = data
                            val fileSize = selectedMedia?.let { getFileSize(it) }
                            if (fileSize != null) {
                                if (fileSize > MAX_FILE_SIZE) {
                                    Toast.makeText(mActivity, "The selected file is too large. Please select a smaller video.", Toast.LENGTH_LONG).show()

                                }else{
                                    val cR = mActivity.contentResolver
                                    val mime = MimeTypeMap.getSingleton()
                                    mime.getExtensionFromMimeType(cR.getType(selectedMedia!!))
                                    mMediaSelector?.onVideoUri(selectedMedia)
                                    retriever.release()
                                }
                            }
                        } else {
                            if (this.clipData != null) {
                                val mClipData: ClipData? = this.clipData
                                val mArrayUri = ArrayList<Uri>()
                                if (mArrayUri.isNotEmpty()) {
                                    var isFileTooLarge = false
                                    for (uri in mArrayUri) {
                                        val fileSize = getFileSize(uri)

                                        if (fileSize > MAX_FILE_SIZE) {
                                            isFileTooLarge = true
                                            break // No need to check further if we found a large file
                                        }
                                    }
                                    if (isFileTooLarge) {
                                        // Show a message if any file is too large
                                        Toast.makeText(mActivity, "One or more files are too large. Please select smaller files.", Toast.LENGTH_LONG).show()
                                    }else{
                                        for (i in 0 until mClipData?.itemCount!!) {
                                            val item = mClipData.getItemAt(i)
                                            val uri: Uri = item.uri
                                            mArrayUri.add(uri)
                                            // Get the cursor
                                        }
                                        mMediaSelector?.onVideoURIList(mArrayUri)
                                    }
                                }
                            } else {
                                val selectedMedia: Uri? = data
                                val fileSize = selectedMedia?.let { getFileSize(it) }
                                if (fileSize != null) {
                                    if (fileSize > MAX_FILE_SIZE) {
                                        Toast.makeText(mActivity, "The selected file is too large. Please select a smaller video.", Toast.LENGTH_LONG).show()

                                    }else{
                                        selectedMedia.let {
                                            ArrayList<Uri>().apply {
                                                add(it)
                                                mMediaSelector?.onVideoURIList(this)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }


        cropResult =
            mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { data: ActivityResult ->
                if (data.resultCode == RESULT_OK) {
                    val result = CropImage.getActivityResult(data.data)
                    val resultUri = result?.uri
                    try {
                        if (resultUri != null) {
                            mMediaSelector?.onImageUri(resultUri)
                        }
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }
                }
            }

        readExternalStorage =
            mActivity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { permission ->
                if (permission) {
                    if (isPhotoPickerAvailable()) {
                        if (canSelectMultipleFlag) multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                        else singlePhotoPickerLauncher?.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } else {
                        galleryResult.launch(galleryIntent)
                    }
                }
            }

        canSelectMultipleImages(false)
    }

    override fun setLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /*Register This for getting callbacks*/
    override fun registerCallback(mMediaSelector: MediaSelector, fragmentManager: FragmentManager) {
        this.fragmentManager = fragmentManager
        this.mMediaSelector = null
        this.mMediaSelector = mMediaSelector
    }

    private var limitedAccessBackgroundColor = R.color.colorBg

    fun setLimitedAccessLayoutBackgroundColor(color: Int) {
        limitedAccessBackgroundColor = color
    }

    override fun selectVideoAndImagePicker(
        childFragmentManager: FragmentManager,
        isCrop1: Boolean,
        cropType: String,
        extraMimeType: Array<String>
    ) {
        this.cropType = cropType
        this.isCrop = isCrop1
        isSelectingVideo = false
        isSelectAnyFile = false

        /* DialogSelectMediaForImageVideo {
             when (it.id) {
                 R.id.layoutTakePhoto -> {
                     openCamera()
                 }

                 R.id.layoutSelectPhoto -> {
                     openGallery()
                 }

                 R.id.layoutTakeVideo -> {
                     isSelectingVideo = true
                     dispatchTakeVideoIntent()
                 }

                 R.id.layoutSelectVideo -> {
                     isSelectingVideo = true
                     selectVideoFromGallery(extraMimeType)
                 }

             }
         }.apply {
         }.show(childFragmentManager, "")*/
    }

    /**
     * Launch the Image Picker View.
     * **/
    override fun selectOptionsForImagePicker(isCrop1: Boolean, cropType: String) {
        this.cropType = cropType
        this.isCrop = isCrop1
        isSelectingVideo = false
        isSelectAnyFile = false

        val builder = AlertDialog.Builder(mActivity)
        builder.setTitle("Choose Image Source")

        val items = arrayOf(
            mActivity.resources?.getString(R.string.label_camera),
            mActivity.resources?.getString(R.string.label_gallery),
        )

        builder.setItems(items) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> checkSelfPermission(IMAGE_PERMISSION)/*checkStoragePermission()*/
            }
        }

        builder.show()
    }

    /**
     * Below code is for launching the custom screen after user selects the media (Only given access media).
     * **/
    private fun onNewImplementedStorageAccordingAccess(
        storageAccess: String,
        canSelectMultipleFlag: Boolean,
        canSelectMultipleVideo: Boolean,
        imageOrVideo: String,
        isCrop: Boolean
    ) {
        if (fragmentManager != null) {
            CoroutineScope(Dispatchers.IO).launch {
                getVisualMedia(
                    mActivity.contentResolver,
                    imageOrVideo
                ).let {
                    withContext(Dispatchers.Main) {
                        fragmentManager?.findFragmentByTag(MEDIA_PICKER).let { j ->
                            if (j != null) {
                                if (it.isNotEmpty()) {
                                    (j as CustomImageVideoListDialogFragment).setItemsAndSetSpan(
                                        it
                                    )
                                }
                            } else {
                                val customImageVideoListDialogFragment =
                                    CustomImageVideoListDialogFragment({ entries ->
                                        if (imageOrVideo == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) {
                                            entries.map {
                                                FileHelperKit.getPath(
                                                    mActivity,
                                                    it.uri
                                                )
                                                    ?.let { new ->
                                                        mMediaSelector?.onUpdatedStorageMedia(
                                                            storageAccess,
                                                            canSelectMultipleFlag,
                                                            canSelectMultipleVideo,
                                                            imageOrVideo,
                                                            new
                                                        )
                                                    }
                                            }
                                        } else {
                                            entries.map {
                                                FileHelperKit.getPath(
                                                    mActivity,
                                                    it.uri
                                                )?.let { newPath ->
                                                    mMediaSelector?.onUpdatedStorageMedia(
                                                        storageAccess,
                                                        canSelectMultipleFlag,
                                                        canSelectMultipleVideo,
                                                        imageOrVideo,
                                                        newPath
                                                    )
                                                }
                                            }
                                        }
                                    }, mActivity, this@MediaSelectHelper)
                                customImageVideoListDialogFragment.arguments =
                                    bundleOf(
                                        SELECTED_IMAGE_VIDEO_LIST to it,
                                        CAN_SELECT_MULTIPLE_FLAG to canSelectMultipleFlag,
                                        CAN_SELECT_MULTIPLE_VIDEO to canSelectMultipleFlag,
                                        VIDEO_OR_IMAGE to imageOrVideo,
                                        CROP_AVAILABLE to true
                                    )
                                customImageVideoListDialogFragment.setCropAndType(
                                    isCrop,
                                    cropType = if (cropType == Constant.CROP_CIRCLE) CropImageView.CropShape.OVAL else if (cropType == Constant.CROP_RECTANGLE) CropImageView.CropShape.RECTANGLE else null
                                )
                                customImageVideoListDialogFragment.setLimitedAccessLayoutBackgroundColor(
                                    limitedAccessBackgroundColor
                                )
                                customImageVideoListDialogFragment.show(
                                    fragmentManager!!,
                                    MEDIA_PICKER
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check version and user given permission and further operation depending user select the permission.
     * **/
    private fun checkSelfPermission(permissionType: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (
                    ContextCompat.checkSelfPermission(
                        mActivity,
                        READ_MEDIA_IMAGES
                    ) == PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                mActivity,
                                READ_MEDIA_VIDEO
                            ) == PERMISSION_GRANTED
                    )
        ) {
            // Full access on Android 13 (API level 33) or higher
            storageAccess = FULL
            if (isSelectAnyFile) {
                openAnyIntent()
            } else {
                if (isSelectingVideo) {
                    if (isPhotoPickerAvailable()) {
                        (fragmentManager?.findFragmentByTag(MEDIA_PICKER) as? CustomImageVideoListDialogFragment)?.dismiss()
                        if (canSelectMultipleFlag) multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        )
                        else singlePhotoPickerLauncher?.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        )
                    } else {
                        galleryResult.launch(galleryIntent)
                    }
                } else {
                    if (isPhotoPickerAvailable()) {
                        (fragmentManager?.findFragmentByTag(MEDIA_PICKER) as? CustomImageVideoListDialogFragment)?.dismiss()
                        if (canSelectMultipleFlag) multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                        else singlePhotoPickerLauncher?.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } else {
                        galleryResult.launch(galleryIntent)
                    }
                }
            }
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                mActivity,
                READ_MEDIA_VISUAL_USER_SELECTED
            ) == PERMISSION_GRANTED
        ) {
            // Partial access on Android 14 (API level 34) or higher
            storageAccess = PARTIAL
            if (isSelectAnyFile) {
                openAnyIntent()
            } else {
                if (isSelectingVideo) {
                    //selectVideoFromGallery(extraMimeTypeVideo)
                    onNewImplementedStorageAccordingAccess(
                        PARTIAL,
                        canSelectMultipleFlag,
                        canSelectMultipleVideo,
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                        isCrop
                    )
                } else {
                    onNewImplementedStorageAccordingAccess(
                        PARTIAL,
                        canSelectMultipleFlag,
                        canSelectMultipleVideo,
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        isCrop
                    )
                }
            }
        } else if (ContextCompat.checkSelfPermission(
                mActivity,
                READ_EXTERNAL_STORAGE
            ) == PERMISSION_GRANTED
        ) {
            // Full access up to Android 12 (API level 32)
            storageAccess = FULL
            // Full access up to Android 12
            if (isSelectAnyFile) {
                openAnyIntent()
            } else {
                if (isSelectingVideo) {
                    if (isPhotoPickerAvailable()) {
                        if (storageAccess == FULL) {
                            (fragmentManager?.findFragmentByTag(MEDIA_PICKER) as? CustomImageVideoListDialogFragment)?.dismiss()
                            if (canSelectMultipleFlag) multiplePhotoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                            else singlePhotoPickerLauncher?.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        } else {
                            onNewImplementedStorageAccordingAccess(
                                "Direct",
                                canSelectMultipleFlag,
                                canSelectMultipleVideo,
                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                                isCrop
                            )
                        }
                    } else {
                        galleryVideoResult.launch(videoIntent)
                    }
                } else {
                    if (isPhotoPickerAvailable()) {
                        if (canSelectMultipleFlag) multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                        else singlePhotoPickerLauncher?.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } else {
                        galleryResult.launch(galleryIntent)
                    }
                }
            }
        } else {
            // Access denied
            requestThePermission(permissionType)
        }

    }

    /**
     * Request the permission.
     * **/
    private fun requestThePermission(permissionType: String) {
        if (permissionType == IMAGE_PERMISSION) {
            this.permissionPreference = IMAGE_PERMISSION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activityResultLauncherGallery.launch(
                    arrayOf(
                        READ_MEDIA_IMAGES,
                        READ_MEDIA_VISUAL_USER_SELECTED
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityResultLauncherGallery.launch(arrayOf(READ_MEDIA_IMAGES))
            } else {
                activityResultLauncherGallery.launch(arrayOf(READ_EXTERNAL_STORAGE))
            }
        } else {
            this.permissionPreference = VIDEO_PERMISSION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activityResultLauncherGallery.launch(
                    arrayOf(
                        READ_MEDIA_VIDEO,
                        READ_MEDIA_VISUAL_USER_SELECTED
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityResultLauncherGallery.launch(arrayOf(READ_MEDIA_VIDEO))
            } else {
                activityResultLauncherGallery.launch(arrayOf(READ_EXTERNAL_STORAGE))
            }
        }
    }

    /**
     * Launch the Video picker view.
     * **/
    override fun selectOptionsForVideoPicker(extraMimeType: Array<String>) {
        isSelectAnyFile = false
        isSelectingVideo = true
        this.isCrop = false
        AlertDialog.Builder(mActivity).setTitle("Choose Video Source").setItems(
            arrayOf(
                mActivity.resources?.getString(R.string.label_camera),
                mActivity.resources?.getString(R.string.label_gallery)
            )
        ) { _, which ->

            when (which) {
                0 -> dispatchTakeVideoIntent()
                1 -> checkSelfPermission(VIDEO_PERMISSION)/*checkStorageVideoPermission(extraMimeType)*/
            }
        }.show()
    }

    /**
     * Launch the camera to take picture.
     * **/
    override fun openCameraPictureIntent(isCrop1: Boolean, cropType: String) {
        this.cropType = cropType
        this.isCrop = isCrop1
        isSelectingVideo = false
        isSelectAnyFile = false
        openCamera()
    }

    /**
     * Launch the camera to take video.
     * **/
    override fun openCameraVideoIntent() {
        isSelectAnyFile = false
        isSelectingVideo = true
        this.isCrop = false
        dispatchTakeVideoIntent()
    }

    /**
     * Check permission and Open camera to take image.
     **/
    private fun openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.CAMERA
            ) != PERMISSION_GRANTED
        ) {
            activityResultLauncherCamera.launch(cameraPermissionList)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.CAMERA
            ) != PERMISSION_GRANTED
        ) {
            activityResultLauncherCamera.launch(cameraPermissionList)
        } else {
            dispatchTakePictureIntent()
        }

    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            // Error occurred while creating the File
            null
        }
        // Continue only if the File was successfully created
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                mActivity, "${mActivity.packageName}.provider", it
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            cameraResult.launch(takePictureIntent)

        }
    }

    private fun dispatchTakeVideoIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                mActivity, Manifest.permission.CAMERA
            ) != PERMISSION_GRANTED
        ) {
            activityResultLauncherCamera.launch(cameraPermissionList)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && (ContextCompat.checkSelfPermission(
                mActivity, Manifest.permission.CAMERA
            ) != PERMISSION_GRANTED)
        ) {
            activityResultLauncherCamera.launch(cameraPermissionList)
        } else {
            val takePictureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            // Ensure that there's a camera activity to handle the intent
            // Create the File where the photo should go
            try {
                photoFile = createAnyFile(".mp4")
                fileForCameraIntent = photoFile!!.absolutePath
            } catch (ex: IOException) {
                // Error occurred while creating the File
                ex.printStackTrace()
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    mActivity, "${mActivity.packageName}.provider", photoFile!!
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cameraResult.launch(takePictureIntent)
            } else {
                //Log.e("++++dispatchTakeVideoIntent", "ELSE")
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cameraResult.launch(takePictureIntent)
            }
        }
    }

    override fun openAnyIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                mActivity, READ_EXTERNAL_STORAGE
            ) != PERMISSION_GRANTED
        ) {
            activityResultLauncherGallery.launch(arrayOf(READ_EXTERNAL_STORAGE))
        } else {
            anyFilePicker.launch(getFileChooserIntent(arrayOf("image/*", "application/pdf")))
        }
    }

    override fun openPdfIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                mActivity, READ_EXTERNAL_STORAGE
            ) != PERMISSION_GRANTED
        ) {
            activityResultLauncherGallery.launch(arrayOf(READ_EXTERNAL_STORAGE))
        } else {
            anyFilePicker.launch(
                getFileChooserIntent(
                    arrayOf(
                        "application/msword", "application/pdf"
                    )
                )
            )
        }
    }

    private fun getFileChooserIntent(type: Array<String>): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = if (type.size == 1) type[0] else "*/*"
        if (type.isNotEmpty()) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, type)
        }
        return intent
    }

    /**
     * Check Photo-picker is available or not
     * **/
    private fun isPhotoPickerAvailable(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                true
            }
            else -> false
        }
    }

    override fun canSelectMultipleImages(canSelect: Boolean) {
        canSelectMultipleFlag = canSelect
        if (canSelect) {
            galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
            galleryIntent!!.type = "*/*"
            galleryIntent!!.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))

            galleryIntent!!.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        } else {
            galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryIntent!!.type = "image/*"
        }
    }

    override fun checkSelfStorageAndOpenPhotoPickerWindowForSelection(type: String) {
        storageAccess = PARTIAL
        if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) {
            requestThePermission(IMAGE_PERMISSION)
        } else {
            requestThePermission(VIDEO_PERMISSION)
        }
    }

    override fun canSelectMultipleVideo(canSelect: Boolean, extraMimeType: Array<String>) {
        canSelectMultipleVideo = canSelect
        canSelectMultipleFlag = canSelect
        videoIntent = Intent(Intent.ACTION_GET_CONTENT)
        videoIntent?.type = "*/*"

        if (extraMimeType.isEmpty()) videoIntent?.putExtra(
            Intent.EXTRA_MIME_TYPES, arrayOf("video/*")
        )
        else videoIntent?.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeType)
        videoIntent?.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, canSelect)
    }

    override fun getThumbnailFromVideo(uri: Uri): File {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(mActivity, uri)
        val thumbnail = retriever.frameAtTime
        retriever.release()

        val file = createImageFile()

        try {
            FileOutputStream(file).use { out ->
                thumbnail?.compress(
                    Bitmap.CompressFormat.PNG, 100, out
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file
    }

    private fun openCropViewOrNot(file: Uri) {
        if (isCrop) {
            val intent: Intent = when (this.cropType) {
                Constant.CROP_SQUARE -> CropImage.activity(file).setAspectRatio(4, 4)
                    .getIntent(mActivity)

                Constant.CROP_RECTANGLE -> CropImage.activity(file)
                    .setCropShape(CropImageView.CropShape.RECTANGLE).setAspectRatio(2, 1)
                    .getIntent(mActivity)

                Constant.CROP_CIRCLE -> CropImage.activity(file)
                    .setCropShape(CropImageView.CropShape.OVAL).setAspectRatio(1, 1)
                    .getIntent(mActivity)

                else -> CropImage.activity(file).getIntent(mActivity)
            }
            cropResult.launch(intent)
        } else {
            mMediaSelector?.onImageUri(Uri.fromFile(getActualPath(file, createImageFile())))
        }
    }

    @SuppressLint("Recycle")
    private fun getActualPath(uri: Uri, outPutFile: File): File {
        val fos = FileOutputStream(outPutFile)
        val inputStream = mActivity.contentResolver.openInputStream(uri)

        val buffer = ByteArray(1024)
        var len: Int
        try {
            len = inputStream?.read(buffer)!!
            while (len != -1) {
                fos.write(buffer, 0, len)
                len = inputStream.read(buffer)
            }
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return outPutFile
    }

    @Throws(IOException::class)
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val image = File.createTempFile(
            timeStamp, /* prefix */
            ".jpg", /* suffix */
            cacheDir      /* directory */
        )
        fileForCameraIntent = image.absolutePath
        return image
    }

    @Throws(IOException::class)
    private fun createAnyFile(extension: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile(
            timeStamp, /* prefix */
            extension, /* suffix */
            cacheDir      /* directory */
        )
    }

    private fun clearCacheFile() {
        if (cacheDir?.isDirectory == true) {
            val files = cacheDir?.listFiles()
            if (files != null) {
                for (file in files) {
                    file.delete()
                }
            }
        }
    }

    private fun checkAllPermission(grantResults: Map<String, Boolean>): Boolean {
        for (data in grantResults) {
            if (!data.value) return false
        }
        return true
    }

    fun getFilePathFromUri(context: Context, uri: Uri, uniqueName: Boolean): String =
        if (uri.path?.contains("file://") == true) uri.path!!
        else getFileFromContentUri(context, uri, uniqueName).path

    private fun getFileFromContentUri(
        context: Context,
        contentUri: Uri,
        uniqueName: Boolean,
    ): File {
        // Preparing Temp file name
        val fileExtension = getFileExtension(context, contentUri) ?: ""
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = ("temp_file_" + if (uniqueName) timeStamp else "") + ".$fileExtension"
        // Creating Temp file
        val tempFile = File(context.cacheDir, fileName)
        tempFile.createNewFile()
        // Initialize streams
        var oStream: FileOutputStream? = null
        var inputStream: InputStream? = null

        try {
            oStream = FileOutputStream(tempFile)
            inputStream = context.contentResolver.openInputStream(contentUri)

            inputStream?.let { copy(inputStream, oStream) }
            oStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Close streams
            inputStream?.close()
            oStream?.close()
        }

        return tempFile
    }

    private fun getFileExtension(context: Context, uri: Uri): String? =
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri))
        else uri.path?.let {
            MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(it)).toString())
        }

    @Throws(IOException::class)
    private fun copy(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8192)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }

    private fun deniedForever(grantResults: Map<String, Boolean>): Boolean {
        for (data in grantResults) {
            if (!mActivity.shouldShowRequestPermissionRationale(data.key)) return false
        }
        return true
    }

    companion object {
        const val SELECTED_IMAGE_VIDEO_LIST = "selectedImagesVideoList"
        const val CAN_SELECT_MULTIPLE_FLAG = "canSelectMultipleFlag"
        const val CAN_SELECT_MULTIPLE_VIDEO = "canSelectMultipleVideo"
        const val VIDEO_OR_IMAGE = "imageOrVideo"
        const val CROP_AVAILABLE = "cropAvailable"
        const val MEDIA_PICKER = "mediaPicker"
        const val FULL = "Full"
        const val PARTIAL = "Partial"
        const val IMAGE_PERMISSION = "imagePermission"
        const val VIDEO_PERMISSION = "videoPermission"
    }


    override fun onDestroy(owner: LifecycleOwner) {
        clearCacheFile()
        super.onDestroy(owner)
    }
}


data class OutPutFileAny(val uri: Uri, val type: FileType, val thumbImage: String? = null)

enum class FileType {
    Image, Pdf, Doc
}

interface FileSelectorMethods {
    fun setLifecycle(lifecycleOwner: LifecycleOwner)
    fun registerCallback(mMediaSelector: MediaSelector, fragmentManager: FragmentManager)
    fun selectOptionsForVideoPicker(extraMimeType: Array<String> = arrayOf())
    fun selectOptionsForImagePicker(
        isCrop1: Boolean,
        cropType: String = MediaSelectHelper.Constant.CROP_SQUARE,
    )

    fun selectVideoAndImagePicker(
        childFragmentManager: FragmentManager,
        isCrop1: Boolean,
        cropType: String = MediaSelectHelper.Constant.CROP_SQUARE,
        extraMimeType: Array<String> = arrayOf()
    )

    fun canSelectMultipleImages(canSelect: Boolean)
    fun canSelectMultipleVideo(canSelect: Boolean, extraMimeType: Array<String> = arrayOf())
    fun openAnyIntent()
    fun openCameraPictureIntent(isCrop1: Boolean, cropType: String)
    fun openCameraVideoIntent()
    fun openPdfIntent()
    fun getThumbnailFromVideo(uri: Uri): File
    fun checkSelfStorageAndOpenPhotoPickerWindowForSelection(type: String) {
    }
}

interface MediaSelector {
    fun onImageUri(uri: Uri) {}
    fun onUpdatedStorageMedia(
        storageAccess: String,
        canSelectMultipleImages: Boolean,
        canSelectMultipleVideos: Boolean,
        selectFilter: String,
        mediaPath: String
    ) {
    }

    fun onVideoUri(uri: Uri) {
    }

    fun onCameraVideoUri(uri: Uri) {
    }

    fun onImageUriList(uriArrayList: ArrayList<Uri>) {}
    fun onVideoURIList(uriArrayList: ArrayList<Uri>) {

    }

    fun onAnyFileSelected(outPutFileAny: OutPutFileAny) {

    }
}