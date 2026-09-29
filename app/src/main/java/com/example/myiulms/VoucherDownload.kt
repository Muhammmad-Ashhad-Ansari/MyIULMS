package com.example.myiulms

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

data class VoucherDocument(
    val bytes: ByteArray,
    val contentType: String
) {
    fun isPdf(): Boolean =
        contentType.contains("application/pdf", ignoreCase = true) ||
            bytes.take(5).toByteArray().toString(Charsets.ISO_8859_1) == "%PDF-"

    fun asHtml(): String = bytes.toString(Charsets.UTF_8)
}

private data class PdfDestination(
    val uri: Uri,
    val descriptor: ParcelFileDescriptor,
    val mediaStoreUri: Uri? = null
)

suspend fun saveVoucherAsPdf(
    context: Context,
    voucher: Voucher,
    document: VoucherDocument
): Uri {
    val displayName = "MyIULMS-Voucher-${safeFilePart(voucher.number)}.pdf"
    val destination = createPdfDestination(context, displayName)

    return try {
        if (document.isPdf()) {
            withContext(Dispatchers.IO) {
                FileOutputStream(destination.descriptor.fileDescriptor).use { output ->
                    output.write(document.bytes)
                    output.flush()
                }
            }
        } else {
            val html = document.asHtml()
            if (!html.contains("<html", ignoreCase = true) &&
                !html.contains("<!doctype", ignoreCase = true)
            ) {
                throw IllegalStateException("IULMS returned an unsupported voucher format")
            }
            renderHtmlToPdf(context, html, destination)
        }

        finalizeDestination(context, destination)
        destination.uri
    } catch (t: Throwable) {
        runCatching { destination.descriptor.close() }
        deleteDestination(context, destination)
        throw t
    }
}

fun openVoucherPdf(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun createPdfDestination(context: Context, displayName: String): PdfDestination {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MyIULMS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Could not create voucher file")

        val descriptor = context.contentResolver.openFileDescriptor(uri, "w")
            ?: run {
                context.contentResolver.delete(uri, null, null)
                throw IllegalStateException("Could not open voucher file")
            }

        PdfDestination(uri, descriptor, uri)
    } else {
        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("External storage is unavailable")
        val dir = File(downloads, "MyIULMS").apply { mkdirs() }
        val file = File(dir, displayName)
        val descriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_READ_WRITE
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        PdfDestination(uri, descriptor)
    }
}

private fun finalizeDestination(context: Context, destination: PdfDestination) {
    runCatching { destination.descriptor.close() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && destination.mediaStoreUri != null) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(destination.mediaStoreUri, values, null, null)
    }
}

private fun deleteDestination(context: Context, destination: PdfDestination) {
    destination.mediaStoreUri?.let {
        runCatching { context.contentResolver.delete(it, null, null) }
    }
}

private suspend fun renderHtmlToPdf(
    context: Context,
    html: String,
    destination: PdfDestination
) = suspendCancellableCoroutine<Unit> { continuation ->
    val webView = WebView(context)

    fun cleanup() {
        runCatching { webView.stopLoading() }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.destroy() }
    }

    fun fail(t: Throwable) {
        runCatching { destination.descriptor.close() }
        cleanup()
        if (continuation.isActive) continuation.resumeWithException(t)
    }

    fun renderNow() {
        try {
            if (!continuation.isActive) {
                cleanup()
                return
            }

            val pageWidth = 595
            val pageHeight = 842

            val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                pageWidth,
                android.view.View.MeasureSpec.EXACTLY
            )
            val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                0,
                android.view.View.MeasureSpec.UNSPECIFIED
            )

            webView.measure(widthSpec, heightSpec)

            val contentHeight = webView.measuredHeight
                .coerceAtLeast(pageHeight)
                .coerceAtMost(pageHeight * 12)

            webView.layout(0, 0, pageWidth, contentHeight)

            val document = PdfDocument()
            try {
                val pageCount = ceil(
                    contentHeight.toDouble() / pageHeight.toDouble()
                ).toInt().coerceIn(1, 12)

                for (pageIndex in 0 until pageCount) {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        pageIndex + 1
                    ).create()

                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.save()
                    canvas.translate(
                        0f,
                        -(pageIndex * pageHeight).toFloat()
                    )
                    webView.draw(canvas)
                    canvas.restore()
                    document.finishPage(page)
                }

                FileOutputStream(destination.descriptor.fileDescriptor).use { output ->
                    document.writeTo(output)
                    output.flush()
                }
            } finally {
                runCatching { document.close() }
            }

            cleanup()
            if (continuation.isActive) continuation.resume(Unit)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    webView.settings.javaScriptEnabled = false
    webView.settings.loadsImagesAutomatically = true
    webView.settings.blockNetworkImage = false
    webView.settings.domStorageEnabled = false

    val sanitizedHtml = html.replace(
        Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE),
        ""
    )

    var rendered = false

    fun renderOnce() {
        if (!rendered) {
            rendered = true
            webView.postDelayed({ renderNow() }, 900L)
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            renderOnce()
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) {
            renderOnce()
        }
    }

    continuation.invokeOnCancellation {
        cleanup()
    }

    webView.loadDataWithBaseURL(
        "$BASE/sic/",
        sanitizedHtml,
        "text/html",
        "UTF-8",
        null
    )

    webView.postDelayed({ renderOnce() }, 2500L)
}

private fun safeFilePart(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "voucher" }
