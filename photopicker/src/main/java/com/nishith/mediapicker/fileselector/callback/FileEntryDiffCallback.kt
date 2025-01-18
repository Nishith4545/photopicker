package com.nishith.mediapicker.fileselector.callback

import androidx.recyclerview.widget.DiffUtil
import com.nishith.mediapicker.data.FileEntry

class FileEntryDiffCallback(
    private val oldList: List<FileEntry>,
    private val newList: List<FileEntry>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Use a unique identifier to check if items are the same
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Compare the contents of the items
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
