# PhotoPicker Android Kotlin Library

<img src="image_2024_11_28T10_44_04_760Z.png" alt="MediaPicker Screenshot" width="250"/>

This is a simple Android Kotlin library to integrate the new Android Photo Picker in your app, in compliance with Google's new policy regarding the use of photo and video permissions. The library allows you to easily access photos and videos while adhering to privacy guidelines.

## Library Version

- Version: 1.0.0

## Implementation

Add the following dependency in your `build.gradle` file to use the library:

```gradle
   dependencies {
       //use latest version
       implementation 'com.github.Nishith4545:photopicker:1.0.0'
   }
```
### Overview

The **PhotoPicker Library** is designed for apps that have a one-time or infrequent need to access photos and videos. The library uses the system picker (Android Photo Picker) and is compliant with Google's latest policy updates.

This library is ideal for scenarios like:
- Allowing users to select one or few images/videos at a time.
- No persistent or frequent access to media files is required.

### Features

- Complies with Android's Photo Picker and the new Play Store policy.
- Simple integration with minimal permissions.
- Supports both image and video selection.
- Compatible with lower Android versions as well.
                                                      
## Usage

### Step 1: Initialize the MediaSelectHelper
In your Fragment or Activity's onCreate method, initialize the MediaSelectHelper instance.

```koltin
lateinit var mediaSelectHelper: MediaSelectHelper

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    
    mediaSelectHelper = MediaSelectHelper(this)
}
```
### Step 2: Launch the Picker
You can launch the photo picker for images or videos. The following examples show how to configure the picker for single or multiple selections.

For Images:
To pick a single image, use the following code:
```kotlin
mediaSelectHelper.canSelectMultipleImages(false)
mediaSelectHelper.selectOptionsForImagePicker(true)
```
For Videos:
To pick a single video, use the following code:
```kotlin
mediaSelectHelper.canSelectMultipleVideo(false)
mediaSelectHelper.selectOptionsForVideoPicker()
```
### Step 3: Set up the Media Selection Callback
To handle the media selection result, register the callback using the `MediaSelector` interface. The callback functions will provide the selected media URIs, including image and video.

```kotlin
private fun setImagePicker() = with(binding) {
    mediaSelectHelper.registerCallback(object : MediaSelector {
        override fun onVideoUri(uri: Uri) {
            super.onVideoUri(uri)
            getPath(this@MainActivity, uri)?.let { path ->
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
    }, supportFragmentManager)
}
```

### Compatibility
This library is compatible with backward and lower versions of Android, allowing you to use the new Photo Picker on all supported devices.

### Permissions
The library helps you comply with the Play Photo and Video Permissions policy. It requests access to photos and videos using the system picker, eliminating the need for READ_MEDIA_IMAGES and READ_MEDIA_VIDEOS permissions. Ensure that your app is in compliance with the new policy guidelines to avoid enforcement actions.


