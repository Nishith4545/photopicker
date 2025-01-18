package com.nishith.photopicker.extention

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide

fun Activity.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
