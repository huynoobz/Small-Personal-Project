package com.example.readalldocs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.readalldocs.ui.theme.ReadAllDocsTheme
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReadAllDocsTheme {
                ReaderApp(initialUri = intent?.data)
            }
        }
    }
}

private enum class DocumentType {
    PDF, TEXT, IMAGE, OFFICE, UNSUPPORTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderApp(initialUri: Uri?) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedType by remember { mutableStateOf(DocumentType.UNSUPPORTED) }
    var selectedName by remember { mutableStateOf("No document selected") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        selectedUri = uri
        selectedType = detectType(context, uri)
        selectedName = queryDisplayName(context, uri) ?: "Document"
    }

    LaunchedEffect(initialUri) {
        val uri = initialUri ?: return@LaunchedEffect
        selectedUri = uri
        selectedType = detectType(context, uri)
        selectedName = queryDisplayName(context, uri) ?: "Document"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Read All Docs (Light)") })
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    launcher.launch(arrayOf("*/*"))
                }) {
                    Text("Open Document")
                }
                Text(
                    text = selectedName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val uri = selectedUri
            if (uri == null) {
                Text(
                    text = "Choose a file to start reading. Supported in-app: PDF, text, and images.\n" +
                        "Office files can be opened in an external read-only viewer.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                when (selectedType) {
                    DocumentType.PDF -> PdfReader(uri = uri)
                    DocumentType.TEXT -> TextReader(uri = uri)
                    DocumentType.IMAGE -> ImageReader(uri = uri)
                    DocumentType.OFFICE -> OfficeFallback(uri = uri)
                    DocumentType.UNSUPPORTED -> UnsupportedFile(uri = uri)
                }
            }
        }
    }
}

@Composable
private fun TextReader(uri: Uri) {
    val context = LocalContext.current
    var textContent by remember { mutableStateOf("Loading text...") }

    LaunchedEffect(uri) {
        textContent = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val maxChars = 300_000
                val data = CharArray(maxChars)
                val read = reader.read(data)
                if (read <= 0) "File is empty." else String(data, 0, read)
            } ?: "Failed to open file."
        }.getOrElse { "Error reading text: ${it.message}" }
    }

    SelectionContainer {
        Text(
            text = textContent,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ImageReader(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Loading image...") }

    LaunchedEffect(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                bitmap = BitmapFactory.decodeStream(stream, null, opts)
            }
            status = if (bitmap == null) "Unable to decode image." else ""
        }.onFailure {
            status = "Error loading image: ${it.message}"
        }
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { copyUriToClipboard(context, uri) }) {
                Text("Copy")
            }
            Button(onClick = { saveAsCopy(context, uri) }) {
                Text("Save As")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (val currentBitmap = bitmap) {
            null -> Text(status)
            else -> Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Selected image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun PdfReader(uri: Uri) {
    val context = LocalContext.current
    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    var pageCount by remember(uri) { mutableIntStateOf(0) }
    var renderedPage by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var errorText by remember(uri) { mutableStateOf<String?>(null) }

    val rendererHolder = remember(uri) {
        openPdfRenderer(context, uri)
    }

    DisposableEffect(rendererHolder) {
        pageCount = rendererHolder?.renderer?.pageCount ?: 0
        pageIndex = 0
        onDispose {
            rendererHolder?.close()
            renderedPage?.recycle()
            renderedPage = null
        }
    }

    LaunchedEffect(uri, pageIndex, rendererHolder) {
        val renderer = rendererHolder?.renderer
        if (renderer == null) {
            errorText = "Cannot open this PDF. Try external viewer."
            return@LaunchedEffect
        }
        runCatching {
            if (pageCount == 0) {
                errorText = "PDF has no pages."
                return@runCatching
            }
            val targetIndex = pageIndex.coerceIn(0, pageCount - 1)
            renderer.openPage(targetIndex).use { page ->
                val width = (page.width * 1.7f).toInt().coerceAtLeast(600)
                val height = (page.height * 1.7f).toInt().coerceAtLeast(800)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                renderedPage?.recycle()
                renderedPage = bmp
                errorText = null
            }
        }.onFailure {
            errorText = "Failed to render page: ${it.message}"
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                enabled = pageCount > 0 && pageIndex > 0
            ) { Text("Prev") }
            Text(
                text = if (pageCount == 0) "No pages" else "Page ${pageIndex + 1} / $pageCount",
                fontWeight = FontWeight.Medium
            )
            Button(
                onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                enabled = pageCount > 0 && pageIndex < pageCount - 1
            ) { Text("Next") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { openExternally(context, uri) }) { Text("Open External") }
        }

        Spacer(modifier = Modifier.height(10.dp))
        when {
            errorText != null -> Text(errorText ?: "Unknown error")
            renderedPage == null -> Text("Rendering page...")
            else -> Image(
                bitmap = renderedPage!!.asImageBitmap(),
                contentDescription = "PDF page",
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun OfficeFallback(uri: Uri) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Office formats are opened with an external viewer to keep memory usage low.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = { openExternally(context, uri) }) {
            Text("Open Read-Only Viewer")
        }
    }
}

@Composable
private fun UnsupportedFile(uri: Uri) {
    val context = LocalContext.current
    Text("This format is not supported in-app. Use external viewer.")
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = { openExternally(context, uri) }) {
        Text("Open External")
    }
}

private fun detectType(context: Context, uri: Uri): DocumentType {
    val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase(Locale.US)

    if (mimeType == "application/pdf" || extension == "pdf") return DocumentType.PDF
    if (mimeType.startsWith("text/") || extension in setOf("txt", "csv", "md", "log")) return DocumentType.TEXT
    if (mimeType.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) {
        return DocumentType.IMAGE
    }
    if (extension in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp")) {
        return DocumentType.OFFICE
    }
    if (mimeType.contains("officedocument") || mimeType.contains("msword") || mimeType.contains("excel")) {
        return DocumentType.OFFICE
    }
    return DocumentType.UNSUPPORTED
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }
}

private data class PdfRendererHolder(
    val descriptor: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    fun close() {
        renderer.close()
        descriptor.close()
    }
}

private fun openPdfRenderer(context: Context, uri: Uri): PdfRendererHolder? {
    return runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        PdfRendererHolder(pfd, PdfRenderer(pfd))
    }.getOrNull()
}

private fun openExternally(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Open document")
    runCatching {
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "No compatible app found", Toast.LENGTH_SHORT).show()
    }
}

private fun copyUriToClipboard(context: Context, uri: Uri) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newUri(context.contentResolver, "image", uri)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun saveAsCopy(context: Context, sourceUri: Uri) {
    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "image_copy_$time.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
    }

    val targetUri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    )

    if (targetUri == null) {
        Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("Cannot write destination")
        } ?: throw IOException("Cannot read source")
        Toast.makeText(context, "Saved to Pictures", Toast.LENGTH_SHORT).show()
    }.onFailure {
        context.contentResolver.delete(targetUri, null, null)
        Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}