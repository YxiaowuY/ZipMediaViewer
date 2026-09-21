package com.example.zipmedia.data

import android.os.Parcel
import android.os.Parcelable

/** 条目类型 */
enum class MediaType {
    IMAGE, VIDEO, OTHER, DIRECTORY;

    val isMedia: Boolean get() = this == IMAGE || this == VIDEO
}

/** 压缩包内一个文件条目（Parcelable 便于在 Activity 间传递） */
data class ArchiveEntry(
    val path: String,          // 在压缩包内的完整路径
    val name: String,          // 文件名（不含路径）
    val size: Long,            // 未压缩大小
    val isDirectory: Boolean,
    val modifiedMillis: Long,
    val type: MediaType
) : Parcelable {

    val displayName: String get() = name.takeIf { it.isNotBlank() } ?: path

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readByte().toInt() != 0,
        parcel.readLong(),
        MediaType.valueOf(parcel.readString() ?: MediaType.OTHER.name)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeString(name)
        parcel.writeLong(size)
        parcel.writeByte(if (isDirectory) 1 else 0)
        parcel.writeLong(modifiedMillis)
        parcel.writeString(type.name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ArchiveEntry> {
        override fun createFromParcel(parcel: Parcel) = ArchiveEntry(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ArchiveEntry?>(size)
    }
}

/** 按照扩展名识别媒体类型 */
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts", "flv")

fun String.extension(): String =
    substringAfterLast('.', "").lowercase()

fun String.isImageName(): Boolean = extension() in IMAGE_EXT

fun String.isVideoName(): Boolean = extension() in VIDEO_EXT

fun String.detectMediaType(isDir: Boolean): MediaType {
    if (isDir) return MediaType.DIRECTORY
    return when {
        isImageName() -> MediaType.IMAGE
        isVideoName() -> MediaType.VIDEO
        else -> MediaType.OTHER
    }
}