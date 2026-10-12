package com.rolla.musicplayer.core.model

/** The display name of the volume root, the folder whose path is "" (spec §9.4). */
const val ROOT_FOLDER_NAME = "Internal storage"

/** A folder's display name (spec §9.4): its last path segment, or [ROOT_FOLDER_NAME] for the root "". */
fun folderName(path: String): String = if (path.isEmpty()) ROOT_FOLDER_NAME else path.substringAfterLast('/')

/** A folder's display path (spec §9.4): "/Internal storage" for the root, "/Internal storage/Music/Chill" below it. */
fun folderDisplayPath(path: String): String = "/$ROOT_FOLDER_NAME" + if (path.isEmpty()) "" else "/$path"
