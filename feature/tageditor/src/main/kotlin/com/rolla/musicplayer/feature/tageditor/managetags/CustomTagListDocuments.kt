package com.rolla.musicplayer.feature.tageditor.managetags

import android.content.Context
import android.net.Uri
import com.rolla.musicplayer.feature.tageditor.io.TagEditorIoDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * The text of the `content://` document the user picked through the Storage Access Framework for Manage tags' list
 * file (custom tags phase 3): what an export writes and an import reads. A seam, so the ViewModel test needs no
 * ContentResolver.
 */
interface CustomTagListDocuments {

    /**
     * Replaces the document at [uri] with [text] in UTF-8.
     *
     * @throws IOException or the provider's own exception when the document cannot be written.
     */
    suspend fun write(uri: String, text: String)

    /**
     * The document at [uri] as UTF-8 text, without a leading byte order mark.
     *
     * @throws IOException or the provider's own exception when the document cannot be read, and when it is larger
     *   than a list file can be (a large file picked by mistake is refused rather than read into memory).
     */
    suspend fun read(uri: String): String
}

/** [CustomTagListDocuments] through the [android.content.ContentResolver], on the tag editor's IO dispatcher. */
internal class ContentResolverCustomTagListDocuments @Inject constructor(
    @ApplicationContext private val context: Context,
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CustomTagListDocuments {

    override suspend fun write(uri: String, text: String): Unit = withContext(ioDispatcher) {
        // "wt": truncate, so a document that already holds a longer file keeps none of its old tail.
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
            ?: throw IOException("Cannot open $uri for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun read(uri: String): String = withContext(ioDispatcher) {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Cannot open $uri for reading")
        val bytes = stream.use { it.readAtMost(MAX_LIST_FILE_BYTES) }
        if (bytes.size > MAX_LIST_FILE_BYTES) throw IOException("$uri is larger than a list file can be")
        String(bytes, Charsets.UTF_8).removePrefix(Char(BYTE_ORDER_MARK).toString())
    }

    /** Reads to EOF or until more than [limit] bytes have accumulated, so an oversized file is never buffered whole. */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val accumulated = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (accumulated.size() <= limit) {
            val read = read(buffer)
            if (read == -1) break
            accumulated.write(buffer, 0, read)
        }
        return accumulated.toByteArray()
    }

    private companion object {
        /** Far above any real list file (two lists of 40-character names), far below what could exhaust memory. */
        const val MAX_LIST_FILE_BYTES = 4 * 1024 * 1024
        const val COPY_BUFFER_BYTES = 8 * 1024

        /** U+FEFF, which some editors put before UTF-8 text and the JSON parser would refuse. */
        const val BYTE_ORDER_MARK = 0xFEFF
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CustomTagListDocumentsModule {
    @Binds
    abstract fun bindCustomTagListDocuments(impl: ContentResolverCustomTagListDocuments): CustomTagListDocuments
}
