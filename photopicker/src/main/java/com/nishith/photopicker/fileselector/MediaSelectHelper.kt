package com.nishith.photopicker.fileselector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ContentResolver
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
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nishith.photopicker.R
import com.nishith.photopicker.cropper.CropImage
import com.nishith.photopicker.cropper.CropImageView
import com.nishith.photopicker.extention.showToast
import com.nishith.photopicker.fileselector.MediaSelectHelper.Constant.CROP_SQUARE
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MediaSelectHelper(private var mActivity: AppCompatActivity) :
    FileSelectorMethods, DefaultLifecycleObserver {

    private var canSelectMultipleFlag: Boolean = false
    private var canSelectMultipleVideo: Boolean = false
    private var isSelectAnyFile: Boolean = false
    private var isSelectingVideo: Boolean = false
    private var mMediaSelector: MediaSelector? = null
    private var fragmentManager: FragmentManager? = null
    private var fileForCameraIntent: String = ""
    private var photoFile: File? = null
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
    private var singlePhotoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var multiplePhotoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var cacheDir: File? = mActivity.cacheDir
    private var selectedDataFromPicker = false

    val MAX_FILE_SIZE = 300 * 1024 * 1024 // 100 MB

    fun getFileSize(uri: Uri): Long {
        var fileSize: Long = 0
        try {
            // Query the URI for its file size using OpenableColumns.SIZE
            val cursor = mActivity.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )
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

        singlePhotoPickerLauncher =
            mActivity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                // Callback is invoked after the user selects a media item or closes the
                // photo picker.
                if (uri != null) {
                    if (isSelectingVideo) {
                        val fileSize = getFileSize(uri)
                        if (fileSize > MAX_FILE_SIZE) {
                            // Show a message if the file is too large
                            Toast.makeText(
                                mActivity,
                                "The selected file is too large. Please select a smaller video.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            mMediaSelector?.onVideoUri(uri)
                        }
                    } else {
                        selectedDataFromPicker = true
                        openCropViewOrNot(uri)
                    }
                }
            }

        multiplePhotoPickerLauncher =
            mActivity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(25)) { imageUris: List<Uri> ->
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
                        Toast.makeText(
                            mActivity,
                            "One or more files are too large. Please select smaller files.",
                            Toast.LENGTH_LONG
                        ).show()
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
                1 -> launchPhotoPicker()
            }
        }

        builder.show()
    }


    private fun launchPhotoPicker() {
        //  if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable()) {
        val mediaType = if (isSelectingVideo) ActivityResultContracts.PickVisualMedia.VideoOnly
        else ActivityResultContracts.PickVisualMedia.ImageOnly

        val pickMediaRequest = PickVisualMediaRequest(mediaType)

        if (canSelectMultipleFlag) {
            multiplePhotoPickerLauncher.launch(pickMediaRequest)
        } else {
            singlePhotoPickerLauncher?.launch(pickMediaRequest)
        }
        // }
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
                1 -> launchPhotoPicker()
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
    }

    override fun canSelectMultipleVideo(canSelect: Boolean, extraMimeType: Array<String>) {
        canSelectMultipleVideo = canSelect
        canSelectMultipleFlag = canSelect
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
        const val MEDIA_PICKER = "mediaPicker"
    }

    override fun onDestroy(owner: LifecycleOwner) {
        clearCacheFile()
        super.onDestroy(owner)
    }

}

interface FileSelectorMethods {
    fun setLifecycle(lifecycleOwner: LifecycleOwner)
    fun registerCallback(mMediaSelector: MediaSelector, fragmentManager: FragmentManager)
    fun selectOptionsForVideoPicker(extraMimeType: Array<String> = arrayOf())
    fun selectOptionsForImagePicker(
        isCrop1: Boolean,
        cropType: String = CROP_SQUARE,
    )

    fun canSelectMultipleImages(canSelect: Boolean)
    fun canSelectMultipleVideo(canSelect: Boolean, extraMimeType: Array<String> = arrayOf())
    fun openCameraPictureIntent(isCrop1: Boolean, cropType: String)
    fun openCameraVideoIntent()
    fun getThumbnailFromVideo(uri: Uri): File
}


interface MediaSelector {
    fun onImageUri(uri: Uri) {}

    fun onVideoUri(uri: Uri) {
    }

    fun onCameraVideoUri(uri: Uri) {
    }

    fun onImageUriList(uriArrayList: ArrayList<Uri>) {}
    fun onVideoURIList(uriArrayList: ArrayList<Uri>) {

    }
}
