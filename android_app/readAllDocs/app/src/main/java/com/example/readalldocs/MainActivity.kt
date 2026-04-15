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
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import kotlin.math.sqrt

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

private const val TAG = "ReadAllDocs"

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
                        "Office files (Word, Excel, PowerPoint) are rendered in-app.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                when (selectedType) {
                    DocumentType.PDF -> PdfReader(uri = uri)
                    DocumentType.TEXT -> TextReader(uri = uri)
                    DocumentType.IMAGE -> ImageReader(uri = uri)
                    DocumentType.OFFICE -> OfficeReader(uri = uri)
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
    var pageCount by remember(uri) { mutableIntStateOf(0) }
    var errorText by remember(uri) { mutableStateOf<String?>(null) }

    val rendererHolder = remember(uri) {
        openPdfRenderer(context, uri)
    }

    DisposableEffect(rendererHolder) {
        pageCount = rendererHolder?.renderer?.pageCount ?: 0
        onDispose {
            rendererHolder?.close()
        }
    }

    LaunchedEffect(uri, rendererHolder, pageCount) {
        if (rendererHolder == null) {
            Log.e(TAG, "PdfRenderer is null for uri=$uri")
            errorText = "Cannot open this PDF. Try external viewer."
            return@LaunchedEffect
        }
        if (pageCount == 0) {
            Log.w(TAG, "PDF has no pages. uri=$uri")
            errorText = "PDF has no pages."
            return@LaunchedEffect
        }
        errorText = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (pageCount == 0) "No pages" else "$pageCount pages",
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = { openExternally(context, uri) }) { Text("Open External") }
        }

        Spacer(modifier = Modifier.height(10.dp))
        when {
            errorText != null -> Text(errorText ?: "Unknown error")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pageCount, key = { it }) { pageIndex ->
                    PdfPageItem(
                        rendererHolder = rendererHolder,
                        uri = uri,
                        pageIndex = pageIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    rendererHolder: PdfRendererHolder?,
    uri: Uri,
    pageIndex: Int
) {
    var renderedPage by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var errorText by remember(uri, pageIndex) { mutableStateOf<String?>(null) }

    DisposableEffect(uri, pageIndex) {
        onDispose {
            renderedPage?.recycle()
            renderedPage = null
        }
    }

    LaunchedEffect(rendererHolder, uri, pageIndex) {
        val holder = rendererHolder
        if (holder == null) {
            errorText = "Cannot open page ${pageIndex + 1}."
            return@LaunchedEffect
        }
        runCatching {
            Log.d(TAG, "Rendering pdf uri=$uri page=$pageIndex")
            holder.renderMutex.withLock {
                holder.renderer.openPage(pageIndex).use { page ->
                    renderPdfPageLowMemory(page)
                }
            }
        }.onSuccess { bitmap ->
            renderedPage?.recycle()
            renderedPage = bitmap
            errorText = null
        }.onFailure {
            renderedPage?.recycle()
            renderedPage = null
            Log.e(TAG, "PDF render failure uri=$uri pageIndex=$pageIndex message=${it.message}", it)
            errorText = "Failed to render page ${pageIndex + 1}."
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Page ${pageIndex + 1}",
            fontWeight = FontWeight.Medium
        )
        when {
            errorText != null -> Text(errorText ?: "Unknown error")
            renderedPage == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Text("Rendering page ${pageIndex + 1}...")
            }
            else -> Image(
                bitmap = renderedPage!!.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun OfficeReader(uri: Uri) {
    val context = LocalContext.current
    var content by remember(uri) { mutableStateOf("Loading office document...") }
    var errorText by remember(uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        runCatching {
            val extractedText = extractOfficeText(context, uri)
            if (extractedText.isBlank()) "No readable text found in this document." else extractedText
        }.onSuccess {
            content = it
            errorText = null
        }.onFailure {
            Log.e(TAG, "Office parse failure uri=$uri message=${it.message}", it)
            content = ""
            errorText = "Cannot render this Office file in-app: ${it.message ?: "unknown error"}"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(onClick = { openExternally(context, uri) }) {
            Text("Open External")
        }
        when (val err = errorText) {
            null -> SelectionContainer {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> Text(
                text = err,
                style = MaterialTheme.typography.bodyLarge
            )
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
    val extensionFromUrl = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase(Locale.US)
    val extensionFromName = queryDisplayName(context, uri)
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.US)
        .orEmpty()
    val extension = extensionFromName.ifBlank { extensionFromUrl }

    if (mimeType == "application/pdf" || extension == "pdf") return DocumentType.PDF
    if (mimeType.startsWith("text/") || extension in setOf("txt", "csv", "md", "log")) return DocumentType.TEXT
    if (mimeType.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) {
        return DocumentType.IMAGE
    }
    if (extension in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp")) {
        return DocumentType.OFFICE
    }
    if (
        mimeType.contains("officedocument") ||
        mimeType.contains("msword") ||
        mimeType.contains("excel") ||
        mimeType.contains("powerpoint") ||
        mimeType.contains("presentation")
    ) {
        return DocumentType.OFFICE
    }
    return DocumentType.UNSUPPORTED
}

private fun extractOfficeText(context: Context, uri: Uri): String {
    val extension = queryDisplayName(context, uri)
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.US)
        .orEmpty()
    val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
    return context.contentResolver.openInputStream(uri)?.use { input ->
        when {
            extension == "docx" || mimeType.contains("wordprocessingml") -> extractDocxText(input)
            extension == "doc" || mimeType.contains("msword") -> extractDocText(input)
            extension == "xlsx" || mimeType.contains("spreadsheetml") -> extractWorkbookText(XSSFWorkbook(input))
            extension == "xls" || mimeType.contains("ms-excel") -> extractWorkbookText(HSSFWorkbook(input))
            extension == "pptx" || mimeType.contains("presentationml") -> extractPptxText(input)
            extension == "ppt" || mimeType.contains("ms-powerpoint") -> extractPptText(input)
            else -> throw IOException("Unsupported office subtype")
        }
    } ?: throw IOException("Cannot open office document stream")
}

private fun extractDocxText(input: InputStream): String {
    return org.apache.poi.xwpf.usermodel.XWPFDocument(input).use { doc ->
        buildString {
            doc.paragraphs.forEach { paragraph ->
                val text = paragraph.text.orEmpty().trim()
                if (text.isNotBlank()) appendLine(text)
            }
            doc.tables.forEachIndexed { tableIndex, table ->
                appendLine()
                appendLine("Table ${tableIndex + 1}")
                table.rows.forEach { row ->
                    appendLine(row.tableCells.joinToString(" | ") { it.text.trim() })
                }
            }
        }.trim()
    }
}

private fun extractDocText(input: InputStream): String {
    return HWPFDocument(input).use { doc ->
        WordExtractor(doc).use { extractor ->
            extractor.text.orEmpty().trim()
        }
    }
}

private fun extractWorkbookText(workbook: Workbook): String {
    return workbook.use { wb ->
        val dataFormatter = DataFormatter()
        val evaluator = wb.creationHelper.createFormulaEvaluator()
        buildString {
            wb.forEach { sheet ->
                appendLine("Sheet: ${sheet.sheetName}")
                sheet.forEach { row ->
                    val rowValues = row.map { cell ->
                        when (cell.cellType) {
                            CellType.FORMULA -> dataFormatter.formatCellValue(cell, evaluator)
                            else -> dataFormatter.formatCellValue(cell)
                        }.trim()
                    }
                    if (rowValues.any { it.isNotBlank() }) {
                        appendLine(rowValues.joinToString(" | "))
                    }
                }
                appendLine()
            }
        }.trim()
    }
}

private fun extractPptxText(input: InputStream): String {
    return XMLSlideShow(input).use { show ->
        buildString {
            show.slides.forEachIndexed { index, slide ->
                appendLine("Slide ${index + 1}")
                slide.shapes.forEach { shape ->
                    appendSlideShapeText(shape, this)
                }
                appendLine()
            }
        }.trim()
    }
}

private fun extractPptText(input: InputStream): String {
    return HSLFSlideShow(input).use { show ->
        buildString {
            show.slides.forEachIndexed { index, slide ->
                appendLine("Slide ${index + 1}")
                slide.shapes.forEach { shape ->
                    appendHslfShapeText(shape, this)
                }
                appendLine()
            }
        }.trim()
    }
}

private fun appendSlideShapeText(shape: XSLFShape, builder: StringBuilder) {
    when (shape) {
        is org.apache.poi.xslf.usermodel.XSLFTextShape -> {
            val text = shape.text.orEmpty().trim()
            if (text.isNotBlank()) builder.appendLine(text)
        }
        is org.apache.poi.xslf.usermodel.XSLFGroupShape -> {
            shape.shapes.forEach { appendSlideShapeText(it, builder) }
        }
        else -> Unit
    }
}

private fun appendHslfShapeText(shape: HSLFShape, builder: StringBuilder) {
    when (shape.shapeType) {
        ShapeType.TEXT_BOX, ShapeType.RECT -> {
            val textShape = shape as? org.apache.poi.hslf.usermodel.HSLFTextShape
            val text = textShape?.text.orEmpty().trim()
            if (text.isNotBlank()) builder.appendLine(text)
        }
        else -> Unit
    }
    if (shape is org.apache.poi.hslf.usermodel.HSLFGroupShape) {
        shape.shapes.forEach { appendHslfShapeText(it, builder) }
    }
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
    val renderer: PdfRenderer,
    val cachedFile: File?,
    val renderMutex: Mutex = Mutex()
) {
    fun close() {
        renderer.close()
        descriptor.close()
        cachedFile?.delete()
    }
}

private fun openPdfRenderer(context: Context, uri: Uri): PdfRendererHolder? {
    Log.d(TAG, "Opening PdfRenderer for uri=$uri mime=${context.contentResolver.getType(uri)}")
    return runCatching {
        val cacheFile = copyPdfToCache(context, uri)
        Log.d(TAG, "Cached PDF path=${cacheFile.absolutePath} size=${cacheFile.length()}")
        val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRendererHolder(pfd, PdfRenderer(pfd), cacheFile)
    }.onFailure {
        Log.e(TAG, "Failed to open PdfRenderer for uri=$uri message=${it.message}", it)
    }.getOrNull()
}

private fun copyPdfToCache(context: Context, uri: Uri): File {
    val fileName = "active_pdf_${System.currentTimeMillis()}.pdf"
    val cachedFile = File(context.cacheDir, fileName)
    Log.d(TAG, "Copying PDF to cache uri=$uri destination=${cachedFile.absolutePath}")
    context.contentResolver.openInputStream(uri)?.use { input ->
        cachedFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IOException("Unable to read PDF source")
    Log.d(TAG, "Copied PDF bytes=${cachedFile.length()}")
    return cachedFile
}

private fun renderPdfPageLowMemory(page: PdfRenderer.Page): Bitmap {
    val baseScale = 1.5f
    val desiredW = (page.width * baseScale).toInt().coerceAtLeast(480)
    val desiredH = (page.height * baseScale).toInt().coerceAtLeast(640)

    // PdfRenderer requires ARGB_8888 output, so keep the bitmap under a safe cap.
    val maxPixels = 2_100_000f // ~8.4MB in ARGB_8888
    val currentPixels = desiredW.toFloat() * desiredH.toFloat()
    val ratio = if (currentPixels > maxPixels) sqrt(maxPixels / currentPixels) else 1f
    val targetW = (desiredW * ratio).toInt().coerceAtLeast(320)
    val targetH = (desiredH * ratio).toInt().coerceAtLeast(480)

    val attempts = listOf(
        targetW to targetH,
        (targetW * 0.75f).toInt().coerceAtLeast(240) to (targetH * 0.75f).toInt().coerceAtLeast(320),
        (targetW * 0.55f).toInt().coerceAtLeast(180) to (targetH * 0.55f).toInt().coerceAtLeast(260)
    )
    Log.d(
        TAG,
        "PDF page size original=${page.width}x${page.height} desired=${desiredW}x${desiredH} target=${targetW}x${targetH} attempts=$attempts"
    )

    var lastError: Throwable? = null
    for ((w, h) in attempts) {
        var bmp: Bitmap? = null
        try {
            Log.d(TAG, "Render attempt bitmap=${w}x$h")
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            Log.d(TAG, "Render success bitmap=${w}x$h")
            return bmp
        } catch (t: Throwable) {
            bmp?.recycle()
            Log.e(TAG, "Render attempt failed bitmap=${w}x$h message=${t.message}", t)
            lastError = t
        }
    }
    Log.e(TAG, "All render attempts failed for page")
    throw IOException("All render attempts failed", lastError)
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