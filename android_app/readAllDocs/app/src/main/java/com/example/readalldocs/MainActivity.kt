package com.example.readalldocs

import android.app.ActivityManager
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
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.readalldocs.ui.theme.ReadAllDocsTheme
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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

private fun isLowRam(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.isLowRamDevice
}

private enum class DocumentType {
    PDF, TEXT, IMAGE, OFFICE, UNSUPPORTED
}

private sealed class OfficeHtmlResult {
    data class Single(val html: String) : OfficeHtmlResult()
    data class Sheets(val sheets: List<Pair<String, String>>) : OfficeHtmlResult()
}

// ---------------------------------------------------------------------------
// App shell
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderApp(initialUri: Uri?) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedType by remember { mutableStateOf(DocumentType.UNSUPPORTED) }
    var selectedName by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
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

    val hasDocument = selectedUri != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (hasDocument) {
                        Text(
                            text = selectedName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = "Read All Docs",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = "Open file"
                        )
                    }
                    if (hasDocument) {
                        IconButton(onClick = { openExternally(context, selectedUri!!) }) {
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = "Open in external app"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        val uri = selectedUri
        if (uri == null) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpen = { launcher.launch(arrayOf("*/*")) }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
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
private fun EmptyState(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No document open",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "PDF, Word, Excel, PowerPoint, text, and images",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(28.dp))
        FilledTonalButton(onClick = onOpen) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open a document")
        }
    }
}

// ---------------------------------------------------------------------------
// Shared loading / error composables
// ---------------------------------------------------------------------------

@Composable
private fun LoadingIndicator(label: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, uri: Uri) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Unable to display",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = { openExternally(context, uri) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in another app")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Text viewer
// ---------------------------------------------------------------------------

@Composable
private fun TextReader(uri: Uri) {
    val context = LocalContext.current
    var textContent by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val maxChars = 200_000
                    val data = CharArray(maxChars)
                    val read = reader.read(data)
                    if (read <= 0) "File is empty." else String(data, 0, read)
                } ?: "Failed to open file."
            }
        }.onSuccess { textContent = it; errorText = null }
            .onFailure { errorText = "Error reading text: ${it.message}" }
    }

    when {
        errorText != null -> ErrorCard(message = errorText!!, uri = uri)
        textContent == null -> LoadingIndicator("Loading text...")
        else -> ZoomableBox(modifier = Modifier.fillMaxSize()) {
            DocumentSurface {
                SelectionContainer {
                    Text(
                        text = textContent!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Image viewer
// ---------------------------------------------------------------------------

@Composable
private fun ImageReader(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uri) {
        onDispose { bitmap?.recycle(); bitmap = null }
    }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val imgBudget = if (isLowRam(context)) 2_000_000 else 3_000_000
                decodeSampledBitmap(context, uri, maxPixels = imgBudget)
            }
        }.onSuccess {
            if (it == null) errorText = "Unable to decode image."
            else { bitmap?.recycle(); bitmap = it; errorText = null }
        }.onFailure {
            errorText = "Error loading image: ${it.message}"
        }
    }

    when {
        errorText != null -> ErrorCard(message = errorText!!, uri = uri)
        bitmap == null -> LoadingIndicator("Loading image...")
        else -> Box(modifier = Modifier.fillMaxSize()) {
            ZoomableBox(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Selected image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalIconButton(onClick = { copyUriToClipboard(context, uri) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy image")
                }
                FilledTonalIconButton(onClick = { saveAsCopy(context, uri) }) {
                    Icon(Icons.Filled.SaveAlt, contentDescription = "Save copy")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PDF viewer
// ---------------------------------------------------------------------------

@Composable
private fun PdfReader(uri: Uri) {
    val context = LocalContext.current
    var rendererHolder by remember { mutableStateOf<PdfRendererHolder?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { rendererHolder?.close() }
    }

    LaunchedEffect(uri) {
        val old = rendererHolder
        rendererHolder = null
        pageCount = 0
        errorText = null
        val holder = withContext(Dispatchers.IO) {
            old?.renderMutex?.withLock { old.close() }
            openPdfRenderer(context, uri)
        }
        rendererHolder = holder
        pageCount = holder?.renderer?.pageCount ?: 0
        errorText = when {
            holder == null -> "Cannot open this PDF."
            pageCount == 0 -> "PDF has no pages."
            else -> null
        }
    }

    when {
        errorText != null -> ErrorCard(message = errorText!!, uri = uri)
        pageCount == 0 -> LoadingIndicator("Opening PDF...")
        else -> {
            val listState = rememberLazyListState()
            val visiblePage by remember {
                derivedStateOf { listState.firstVisibleItemIndex + 1 }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                ZoomableBox(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "$visiblePage / $pageCount",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
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
    val context = LocalContext.current
    val pdfPixelBudget = remember { if (isLowRam(context)) 800_000f else 1_200_000f }
    var renderedPage by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var errorText by remember(uri, pageIndex) { mutableStateOf<String?>(null) }

    DisposableEffect(uri, pageIndex) {
        onDispose {
            renderedPage?.recycle()
            renderedPage = null
        }
    }

    LaunchedEffect(rendererHolder, uri, pageIndex) {
        val holder = rendererHolder ?: run {
            errorText = "Cannot open page ${pageIndex + 1}."
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                holder.renderMutex.withLock {
                    holder.renderer.openPage(pageIndex).use { page ->
                        renderPdfPageLowMemory(page, pdfPixelBudget)
                    }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        when {
            errorText != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            renderedPage == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            else -> Image(
                bitmap = renderedPage!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Office viewer
// ---------------------------------------------------------------------------

@Composable
private fun OfficeReader(uri: Uri) {
    val context = LocalContext.current
    var result by remember(uri) { mutableStateOf<OfficeHtmlResult?>(null) }
    var errorText by remember(uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching { extractOfficeHtml(context, uri) }
        }.onSuccess { result = it; errorText = null }
            .onFailure {
                Log.e(TAG, "Office parse failure uri=$uri message=${it.message}", it)
                errorText = it.message ?: "Unknown error"
            }
    }

    when {
        errorText != null -> ErrorCard(message = "Cannot render this Office file: $errorText", uri = uri)
        result == null -> LoadingIndicator("Reading document...")
        else -> when (val r = result!!) {
            is OfficeHtmlResult.Single -> OfficeWebView(html = r.html)
            is OfficeHtmlResult.Sheets -> OfficeSheetTabs(sheets = r.sheets)
        }
    }
}

@Composable
private fun OfficeWebView(html: String) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy(); webViewRef = null }
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun OfficeSheetTabs(sheets: List<Pair<String, String>>) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy(); webViewRef = null }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            sheets.forEachIndexed { index, (name, _) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        val currentHtml = sheets[selectedTab].second
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.javaScriptEnabled = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    loadDataWithBaseURL(null, currentHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, currentHtml, "text/html", "UTF-8", null)
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// Unsupported file
// ---------------------------------------------------------------------------

@Composable
private fun UnsupportedFile(uri: Uri) {
    ErrorCard(message = "This file format is not supported for in-app viewing.", uri = uri)
}

// ---------------------------------------------------------------------------
// Shared UI: document surface card
// ---------------------------------------------------------------------------

@Composable
private fun DocumentSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Shared UI: pinch-to-zoom container
// ---------------------------------------------------------------------------

@Composable
private fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                    val maxX = (containerSize.width * (newScale - 1)) / 2f
                    val maxY = (containerSize.height * (newScale - 1)) / 2f
                    scale = newScale
                    offsetX = (offsetX + pan.x * newScale).coerceIn(-maxX, maxX)
                    offsetY = (offsetY + pan.y * newScale).coerceIn(-maxY, maxY)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                })
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Detection helpers
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Office HTML extraction: router
// ---------------------------------------------------------------------------

private fun extractOfficeHtml(context: Context, uri: Uri): OfficeHtmlResult {
    val extension = queryDisplayName(context, uri)
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.US)
        .orEmpty()
    val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
    return context.contentResolver.openInputStream(uri)?.use { input ->
        when {
            extension == "docx" || mimeType.contains("wordprocessingml") ->
                OfficeHtmlResult.Single(extractDocxHtml(input))
            extension == "xlsx" || mimeType.contains("spreadsheetml") ->
                extractXlsxSheets(input)
            extension == "pptx" || mimeType.contains("presentationml") ->
                OfficeHtmlResult.Single(extractPptxHtml(input))
            extension == "doc" || mimeType.contains("msword") -> {
                val text = extractDocText(input)
                val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                OfficeHtmlResult.Single(buildPrintHtml(listOf(
                    "<pre>$escaped</pre>"
                )))
            }
            extension == "xls" || mimeType.contains("ms-excel") ->
                extractXlsSheets(input)
            extension == "ppt" || mimeType.contains("ms-powerpoint") ->
                OfficeHtmlResult.Single(wrapPlainTextHtml(extractPptText(input)))
            else -> throw IOException("Unsupported office subtype")
        }
    } ?: throw IOException("Cannot open office document stream")
}

private fun wrapPlainTextHtml(text: String): String {
    val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return buildHtmlPage("<pre style=\"white-space:pre-wrap;word-break:break-word;\">$escaped</pre>")
}

private fun buildHtmlPage(body: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { box-sizing: border-box; }
  body {
    font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    color: #1a1a1a; background: #fff;
    margin: 0; padding: 16px;
    line-height: 1.6; font-size: 14px;
  }
  h1 { font-size: 22px; margin: 18px 0 8px; font-weight: 700; }
  h2 { font-size: 18px; margin: 16px 0 6px; font-weight: 600; }
  h3 { font-size: 16px; margin: 14px 0 4px; font-weight: 600; }
  h4 { font-size: 15px; margin: 12px 0 4px; font-weight: 600; }
  h5 { font-size: 14px; margin: 10px 0 4px; font-weight: 600; }
  h6 { font-size: 13px; margin: 10px 0 4px; font-weight: 600; }
  p  { margin: 0 0 4px; }
  table {
    border-collapse: collapse; width: 100%;
    margin: 12px 0; font-size: 13px;
  }
  th, td {
    border: 1px solid #c0c0c0; padding: 6px 10px;
    text-align: left; vertical-align: top;
  }
  th { background: #f0f0f0; font-weight: 600; }
  tr:nth-child(even) { background: #fafafa; }
  .sheet-title {
    font-size: 16px; font-weight: 600;
    margin: 20px 0 6px; color: #333;
    border-bottom: 2px solid #4285f4; padding-bottom: 4px;
  }
  .slide {
    border: 1px solid #c0c0c0; border-radius: 6px;
    padding: 28px 24px 24px; margin: 16px 0;
    background: #fff; page-break-inside: avoid;
    box-shadow: 0 2px 6px rgba(0,0,0,0.12);
    min-height: 200px;
    background-size: cover; background-position: center;
    overflow: hidden;
  }
  .slide-number {
    font-size: 10px; color: inherit; opacity: 0.5;
    margin-bottom: 12px; font-weight: 600;
    text-transform: uppercase; letter-spacing: 0.5px;
  }
  .slide p { margin: 6px 0; }
  .slide img { border-radius: 4px; }
  img { max-width: 100%; height: auto; margin: 8px 0; }
  pre { font-size: 13px; line-height: 1.5; }
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()

// ---------------------------------------------------------------------------
// OOXML → HTML converters
// ---------------------------------------------------------------------------

private fun extractDocxHtml(input: InputStream): String {
    val allEntries = unzipAllEntries(input)
    val docXml = allEntries["word/document.xml"]
        ?: throw IOException("No word/document.xml in docx")
    val body = parseDocxToHtml(docXml, allEntries)
    val pages = body.split("<!--PB-->").map { it.trim() }.filter { it.isNotEmpty() }
    return buildPrintHtml(if (pages.isEmpty()) listOf(body) else pages)
}

private fun buildPrintHtml(pages: List<String>): String {
    val pagesDivs = pages.joinToString("\n") { "<div class=\"page\">$it</div>" }
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { box-sizing: border-box; }
  body {
    font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    color: #1a1a1a; background: #d0d0d0;
    margin: 0; padding: 16px 8px;
    line-height: 1.6; font-size: 14px;
  }
  .page {
    background: #fff;
    max-width: 680px;
    margin: 0 auto 24px;
    padding: 56px 48px;
    box-shadow: 0 1px 4px rgba(0,0,0,0.2);
    border-radius: 2px;
    min-height: 880px;
    overflow-wrap: break-word;
    word-break: break-word;
  }
  h1 { font-size: 22px; margin: 18px 0 8px; font-weight: 700; }
  h2 { font-size: 18px; margin: 16px 0 6px; font-weight: 600; }
  h3 { font-size: 16px; margin: 14px 0 4px; font-weight: 600; }
  h4 { font-size: 15px; margin: 12px 0 4px; font-weight: 600; }
  h5 { font-size: 14px; margin: 10px 0 4px; font-weight: 600; }
  h6 { font-size: 13px; margin: 10px 0 4px; font-weight: 600; }
  p  { margin: 0 0 4px; }
  table {
    border-collapse: collapse; width: 100%;
    margin: 12px 0; font-size: 13px;
  }
  th, td {
    border: 1px solid #c0c0c0; padding: 6px 10px;
    text-align: left; vertical-align: top;
  }
  th { background: #f0f0f0; font-weight: 600; }
  tr:nth-child(even) { background: #fafafa; }
  img { max-width: 100%; height: auto; margin: 8px 0; }
  pre { font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
</style>
</head>
<body>
$pagesDivs
</body>
</html>
""".trimIndent()
}

private fun extractXlsxSheets(input: InputStream): OfficeHtmlResult {
    val allEntries = unzipAllEntries(input)
    val sharedStrings = allEntries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
    val sheetNames = allEntries["xl/workbook.xml"]?.let { parseXlsxSheetNames(it) } ?: emptyList()

    val sheets = mutableListOf<Pair<String, String>>()
    var sheetIdx = 0
    for (i in 1..50) {
        val sheetXml = allEntries["xl/worksheets/sheet$i.xml"] ?: continue
        val name = sheetNames.getOrElse(sheetIdx) { "Sheet $i" }
        sheetIdx++
        val tableHtml = parseXlsxSheetToHtml(sheetXml, sharedStrings)
        sheets.add(name to buildHtmlPage(tableHtml))
    }
    if (sheets.size <= 1) {
        return OfficeHtmlResult.Single(sheets.firstOrNull()?.second ?: buildHtmlPage("<p>Empty spreadsheet</p>"))
    }
    return OfficeHtmlResult.Sheets(sheets)
}

private fun extractPptxHtml(input: InputStream): String {
    val allEntries = unzipAllEntries(input)
    val body = buildString {
        for (i in 1..200) {
            val slideXml = allEntries["ppt/slides/slide$i.xml"] ?: continue
            val (slideHtml, bgColor) = parsePptxSlideToHtml(slideXml, i, allEntries)
            val styles = mutableListOf<String>()
            if (bgColor != null) {
                styles.add("background:$bgColor")
                val hex = bgColor.removePrefix("#")
                if (hex.length >= 6) {
                    val r = hex.substring(0, 2).toInt(16)
                    val g = hex.substring(2, 4).toInt(16)
                    val b = hex.substring(4, 6).toInt(16)
                    if (0.299 * r + 0.587 * g + 0.114 * b < 128) {
                        styles.add("color:#fff")
                    }
                }
            }
            val styleAttr = if (styles.isNotEmpty()) " style=\"${styles.joinToString(";")}\"" else ""
            append("<div class=\"slide\"$styleAttr>")
            append("<div class=\"slide-number\">Slide $i</div>")
            append(slideHtml)
            append("</div>")
        }
    }
    return buildHtmlPage(body)
}

private fun escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private const val MAX_ZIP_ENTRY_BYTES = 5L * 1024 * 1024
private const val MAX_ZIP_TOTAL_BYTES = 25L * 1024 * 1024

private fun unzipAllEntries(input: InputStream): Map<String, ByteArray> {
    val result = mutableMapOf<String, ByteArray>()
    var totalBytes = 0L
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val knownSize = entry.size
                if (knownSize > MAX_ZIP_ENTRY_BYTES) {
                    Log.w(TAG, "Skipping oversized zip entry ${entry.name} size=$knownSize")
                } else if (totalBytes >= MAX_ZIP_TOTAL_BYTES) {
                    Log.w(TAG, "Total zip budget exceeded, skipping ${entry.name}")
                } else {
                    val bytes = zip.readBytes()
                    if (bytes.size <= MAX_ZIP_ENTRY_BYTES) {
                        result[entry.name] = bytes
                        totalBytes += bytes.size
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}

// --- docx → HTML ---

private fun parseDocxToHtml(xml: ByteArray, allEntries: Map<String, ByteArray>): String {
    val parser = newNsParser(xml)
    val html = StringBuilder()

    var inParagraph = false
    var inRun = false
    var inText = false
    var inPPr = false
    var inRPr = false

    var bold = false
    var italic = false
    var underline = false
    var strikethrough = false
    var fontSize: Int? = null

    var pAlign: String? = null
    var pStyle: String? = null
    var pIndentLeft: Int? = null
    var pContent = StringBuilder()

    var inTable = false
    var inTableRow = false
    var inTableCell = false
    var inDrawing = false

    var sectionBreakPending = false
    var inSectPr = false
    var sectPrIsContinuous = false

    val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "p" -> {
                    inParagraph = true
                    pAlign = null; pStyle = null; pIndentLeft = null
                    pContent = StringBuilder()
                    bold = false; italic = false; underline = false; strikethrough = false; fontSize = null
                }
                "pPr" -> if (inParagraph && !inRun) inPPr = true
                "jc" -> if (inPPr) {
                    pAlign = parser.getAttributeValue(wNs, "val")
                        ?: parser.getAttributeValue(null, "val")
                }
                "pStyle" -> if (inPPr) {
                    pStyle = parser.getAttributeValue(wNs, "val")
                        ?: parser.getAttributeValue(null, "val")
                }
                "ind" -> if (inPPr) {
                    val left = parser.getAttributeValue(wNs, "left")
                        ?: parser.getAttributeValue(null, "left")
                    pIndentLeft = left?.toIntOrNull()
                }
                "r" -> { inRun = true; bold = false; italic = false; underline = false; strikethrough = false; fontSize = null }
                "rPr" -> if (inRun) inRPr = true
                "t" -> if (inRun) inText = true
                "b" -> {
                    val off = parser.getAttributeValue(wNs, "val") ?: parser.getAttributeValue(null, "val")
                    if (off != "0" && off != "false") {
                        if (inRPr) bold = true
                        else if (inPPr) bold = true
                    }
                }
                "i" -> {
                    val off = parser.getAttributeValue(wNs, "val") ?: parser.getAttributeValue(null, "val")
                    if (off != "0" && off != "false") {
                        if (inRPr) italic = true
                        else if (inPPr) italic = true
                    }
                }
                "u" -> if (inRPr || inPPr) underline = true
                "strike" -> if (inRPr || inPPr) strikethrough = true
                "sz" -> if (inRPr) {
                    val v = parser.getAttributeValue(wNs, "val")
                        ?: parser.getAttributeValue(null, "val")
                    fontSize = v?.toIntOrNull()
                }
                "tbl" -> { inTable = true; html.append("<table>") }
                "tr" -> { inTableRow = true; html.append("<tr>") }
                "tc" -> { inTableCell = true; html.append("<td>") }
                "drawing", "pict" -> inDrawing = true
                "br" -> if (inRun) {
                    val brType = parser.getAttributeValue(wNs, "type")
                        ?: parser.getAttributeValue(null, "type")
                    if (brType == "page") {
                        if (pContent.toString().isBlank()) {
                            html.append("<!--PB-->")
                        } else {
                            sectionBreakPending = true
                        }
                    }
                }
                "sectPr" -> if (inPPr) {
                    inSectPr = true
                    sectPrIsContinuous = false
                }
                "type" -> if (inSectPr) {
                    val v = parser.getAttributeValue(wNs, "val")
                        ?: parser.getAttributeValue(null, "val")
                    if (v == "continuous") sectPrIsContinuous = true
                }
                "blip" -> {
                    val embed = parser.getAttributeValue(null, "embed")
                        ?: parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                    if (embed != null) {
                        val imgPath = resolveDocxImage(embed, allEntries)
                        if (imgPath != null) {
                            val imgBytes = allEntries[imgPath]
                            if (imgBytes != null && imgBytes.size <= 1024 * 1024) {
                                val mime = when {
                                    imgPath.endsWith(".png") -> "image/png"
                                    imgPath.endsWith(".gif") -> "image/gif"
                                    else -> "image/jpeg"
                                }
                                val b64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)
                                pContent.append("<img src=\"data:$mime;base64,$b64\">")
                            } else if (imgBytes != null) {
                                pContent.append("<p style=\"color:#888;font-style:italic;\">[Image too large to display inline]</p>")
                            }
                        }
                    }
                }
            }
            XmlPullParser.TEXT -> {
                if (inText) {
                    val text = escHtml(parser.text.orEmpty())
                    if (text.isNotEmpty()) {
                        val sb = StringBuilder()
                        if (fontSize != null && fontSize!! > 28) {
                            sb.append("<span style=\"font-size:${fontSize!! / 2}pt;\">")
                        }
                        if (bold) sb.append("<b>")
                        if (italic) sb.append("<i>")
                        if (underline) sb.append("<u>")
                        if (strikethrough) sb.append("<s>")
                        sb.append(text)
                        if (strikethrough) sb.append("</s>")
                        if (underline) sb.append("</u>")
                        if (italic) sb.append("</i>")
                        if (bold) sb.append("</b>")
                        if (fontSize != null && fontSize!! > 28) sb.append("</span>")
                        pContent.append(sb)
                    }
                }
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "t" -> inText = false
                "rPr" -> inRPr = false
                "r" -> { inRun = false; bold = false; italic = false; underline = false; strikethrough = false; fontSize = null }
                "pPr" -> inPPr = false
                "p" -> {
                    if (inTableCell) {
                        html.append(pContent)
                        html.append("<br>")
                    } else {
                        val content = pContent.toString()
                        val styleparts = mutableListOf<String>()
                        when (pAlign) {
                            "center" -> styleparts.add("text-align:center")
                            "right", "end" -> styleparts.add("text-align:right")
                            "both", "distribute" -> styleparts.add("text-align:justify")
                        }
                        if (pIndentLeft != null && pIndentLeft!! > 0) {
                            val px = pIndentLeft!! / 15
                            styleparts.add("margin-left:${px}px")
                        }
                        val styleAttr = if (styleparts.isNotEmpty()) " style=\"${styleparts.joinToString(";")}\"" else ""
                        val headingLevel = pStyle?.let { s ->
                            when {
                                s.equals("Title", ignoreCase = true) -> 1
                                s.startsWith("Heading", ignoreCase = true) || s.startsWith("heading", ignoreCase = true) ->
                                    s.removePrefix("Heading").removePrefix("heading").trim().toIntOrNull()?.coerceIn(1, 6)
                                else -> null
                            }
                        }
                        if (headingLevel != null) {
                            html.append("<h$headingLevel$styleAttr>$content</h$headingLevel>\n")
                        } else if (content.isBlank()) {
                            html.append("<p$styleAttr>&nbsp;</p>\n")
                        } else {
                            html.append("<p$styleAttr>$content</p>\n")
                        }
                    }
                    if (sectionBreakPending && !inTableCell) {
                        html.append("<!--PB-->")
                        sectionBreakPending = false
                    }
                    inParagraph = false
                }
                "tc" -> { html.append("</td>"); inTableCell = false }
                "tr" -> { html.append("</tr>"); inTableRow = false }
                "tbl" -> { html.append("</table>"); inTable = false }
                "drawing", "pict" -> inDrawing = false
                "sectPr" -> {
                    if (inSectPr && !sectPrIsContinuous) {
                        sectionBreakPending = true
                    }
                    inSectPr = false
                }
            }
        }
        event = parser.next()
    }
    return html.toString()
}

private fun resolveDocxImage(embedId: String, allEntries: Map<String, ByteArray>): String? {
    val relsXml = allEntries["word/_rels/document.xml.rels"] ?: return null
    val parser = newNsParser(relsXml)
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
            val id = parser.getAttributeValue(null, "Id")
            val target = parser.getAttributeValue(null, "Target")
            if (id == embedId && target != null) {
                return if (target.startsWith("/")) target.removePrefix("/")
                else "word/$target"
            }
        }
        event = parser.next()
    }
    return null
}

// --- xlsx → HTML ---

private fun parseXlsxSheetToHtml(xml: ByteArray, sharedStrings: List<String>): String {
    val parser = newNsParser(xml)
    val html = StringBuilder()
    html.append("<table>")

    val rowCells = mutableListOf<String>()
    var cellType: String? = null
    var inValue = false
    var cellText = StringBuilder()
    var isFirstRow = true
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "row" -> rowCells.clear()
                "c" -> {
                    cellType = parser.getAttributeValue(null, "t")
                    cellText = StringBuilder()
                }
                "v", "t" -> inValue = true
            }
            XmlPullParser.TEXT -> {
                if (inValue) cellText.append(parser.text.orEmpty())
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "v", "t" -> inValue = false
                "c" -> {
                    val raw = cellText.toString()
                    val display = if (cellType == "s") {
                        val idx = raw.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                    } else raw
                    rowCells.add(escHtml(display))
                }
                "row" -> {
                    if (rowCells.any { it.isNotBlank() }) {
                        val tag = if (isFirstRow) "th" else "td"
                        html.append("<tr>")
                        rowCells.forEach { html.append("<$tag>$it</$tag>") }
                        html.append("</tr>")
                        isFirstRow = false
                    }
                }
            }
        }
        event = parser.next()
    }
    html.append("</table>")
    return html.toString()
}

private fun parseSharedStrings(xml: ByteArray): List<String> {
    val parser = newNsParser(xml)
    val strings = mutableListOf<String>()
    var currentString = StringBuilder()
    var inSi = false
    var inT = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "si" -> { inSi = true; currentString = StringBuilder() }
                "t" -> if (inSi) inT = true
            }
            XmlPullParser.TEXT -> {
                if (inT) currentString.append(parser.text.orEmpty())
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "t" -> inT = false
                "si" -> { inSi = false; strings.add(currentString.toString()) }
            }
        }
        event = parser.next()
    }
    return strings
}

private fun parseXlsxSheetNames(xml: ByteArray): List<String> {
    val parser = newNsParser(xml)
    val names = mutableListOf<String>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
            parser.getAttributeValue(null, "name")?.let { names.add(it) }
        }
        event = parser.next()
    }
    return names
}

// --- pptx → HTML ---

private fun parsePptxSlideToHtml(
    xml: ByteArray,
    slideIndex: Int,
    allEntries: Map<String, ByteArray>
): Pair<String, String?> {
    val parser = newNsParser(xml)
    val html = StringBuilder()
    val rNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    var inParagraph = false
    var inRun = false
    var inRunProps = false
    var inText = false
    var inBg = false
    var inSolidFill = false

    var bold = false
    var italic = false
    var underline = false
    var fontSize: Int? = null
    var textColor: String? = null
    var pAlign: String? = null

    var bgColor: String? = null
    var pContent = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "bg" -> inBg = true
                "solidFill" -> inSolidFill = true
                "srgbClr" -> if (inSolidFill) {
                    val color = parser.getAttributeValue(null, "val")
                    if (color != null) {
                        if (inBg) bgColor = "#$color"
                        else if (inRunProps) textColor = "#$color"
                    }
                }
                "p" -> {
                    val ns = parser.namespace.orEmpty()
                    if (ns.contains("drawingml")) {
                        inParagraph = true
                        pContent = StringBuilder()
                        pAlign = null
                    }
                }
                "pPr" -> if (inParagraph && !inRun) {
                    pAlign = parser.getAttributeValue(null, "algn")
                }
                "r" -> if (inParagraph) {
                    inRun = true
                    bold = false; italic = false; underline = false
                    fontSize = null; textColor = null
                }
                "rPr" -> if (inRun || inParagraph) {
                    inRunProps = true
                    bold = parser.getAttributeValue(null, "b").let { it == "1" || it == "true" }
                    italic = parser.getAttributeValue(null, "i").let { it == "1" || it == "true" }
                    underline = parser.getAttributeValue(null, "u").let { it != null && it != "none" }
                    fontSize = parser.getAttributeValue(null, "sz")?.toIntOrNull()
                }
                "t" -> if (inRun) inText = true
                "blip" -> if (!inBg) {
                    val embed = parser.getAttributeValue(rNs, "embed")
                        ?: parser.getAttributeValue(null, "embed")
                    if (embed != null) {
                        val imgPath = resolvePptxImage(embed, slideIndex, allEntries)
                        if (imgPath != null) {
                            val imgBytes = allEntries[imgPath]
                            if (imgBytes != null && imgBytes.size <= 1024 * 1024) {
                                val mime = when {
                                    imgPath.endsWith(".png", true) -> "image/png"
                                    imgPath.endsWith(".gif", true) -> "image/gif"
                                    imgPath.endsWith(".emf", true) -> null
                                    imgPath.endsWith(".wmf", true) -> null
                                    else -> "image/jpeg"
                                }
                                if (mime != null) {
                                    val b64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)
                                    val tag = "<img src=\"data:$mime;base64,$b64\">"
                                    if (inParagraph) pContent.append(tag) else html.append(tag)
                                }
                            } else if (imgBytes != null) {
                                val note = "<p style=\"color:#888;font-style:italic\">[Image too large]</p>"
                                if (inParagraph) pContent.append(note) else html.append(note)
                            }
                        }
                    }
                }
            }
            XmlPullParser.TEXT -> {
                if (inText) {
                    val text = escHtml(parser.text.orEmpty())
                    if (text.isNotEmpty()) {
                        val sb = StringBuilder()
                        val styles = mutableListOf<String>()
                        if (fontSize != null) {
                            val ptSize = fontSize!! / 100
                            if (ptSize > 16) styles.add("font-size:${ptSize}pt")
                        }
                        if (textColor != null) styles.add("color:$textColor")
                        if (styles.isNotEmpty()) sb.append("<span style=\"${styles.joinToString(";")}\">")
                        if (bold) sb.append("<b>")
                        if (italic) sb.append("<i>")
                        if (underline) sb.append("<u>")
                        sb.append(text)
                        if (underline) sb.append("</u>")
                        if (italic) sb.append("</i>")
                        if (bold) sb.append("</b>")
                        if (styles.isNotEmpty()) sb.append("</span>")
                        pContent.append(sb)
                    }
                }
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "bg" -> inBg = false
                "solidFill" -> inSolidFill = false
                "t" -> inText = false
                "rPr" -> inRunProps = false
                "r" -> {
                    inRun = false; bold = false; italic = false
                    underline = false; fontSize = null; textColor = null
                }
                "p" -> {
                    if (inParagraph && parser.namespace.orEmpty().contains("drawingml")) {
                        val content = pContent.toString()
                        if (content.isNotBlank()) {
                            val style = when (pAlign) {
                                "ctr" -> " style=\"text-align:center\""
                                "r" -> " style=\"text-align:right\""
                                "just" -> " style=\"text-align:justify\""
                                else -> ""
                            }
                            html.append("<p$style>$content</p>\n")
                        }
                        inParagraph = false
                    }
                }
            }
        }
        event = parser.next()
    }
    return Pair(html.toString(), bgColor)
}

private fun resolvePptxImage(embedId: String, slideIndex: Int, allEntries: Map<String, ByteArray>): String? {
    val relsXml = allEntries["ppt/slides/_rels/slide$slideIndex.xml.rels"] ?: return null
    val parser = newNsParser(relsXml)
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
            val id = parser.getAttributeValue(null, "Id")
            val target = parser.getAttributeValue(null, "Target")
            if (id == embedId && target != null) {
                return when {
                    target.startsWith("/") -> target.removePrefix("/")
                    target.startsWith("../") -> "ppt/" + target.removePrefix("../")
                    else -> "ppt/slides/$target"
                }
            }
        }
        event = parser.next()
    }
    return null
}

private val xmlFactory: XmlPullParserFactory by lazy {
    XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
}

private fun newNsParser(xml: ByteArray): XmlPullParser {
    val parser = xmlFactory.newPullParser()
    parser.setInput(xml.inputStream(), null)
    return parser
}

// ---------------------------------------------------------------------------
// Legacy binary formats: POI core + scratchpad
// ---------------------------------------------------------------------------

private fun extractDocText(input: InputStream): String {
    return HWPFDocument(input).use { doc ->
        WordExtractor(doc).use { extractor ->
            extractor.text.orEmpty().trim()
        }
    }
}

private fun extractXlsSheets(input: InputStream): OfficeHtmlResult {
    return HSSFWorkbook(input).use { wb ->
        val dataFormatter = DataFormatter()
        val evaluator = wb.creationHelper.createFormulaEvaluator()
        val sheets = mutableListOf<Pair<String, String>>()
        wb.forEach { sheet ->
            val tableHtml = buildString {
                append("<table>")
                var isFirstRow = true
                sheet.forEach { row ->
                    val rowValues = row.map { cell ->
                        val value = when (cell.cellType) {
                            CellType.FORMULA -> dataFormatter.formatCellValue(cell, evaluator)
                            else -> dataFormatter.formatCellValue(cell)
                        }.trim()
                        escHtml(value)
                    }
                    if (rowValues.any { it.isNotBlank() }) {
                        val tag = if (isFirstRow) "th" else "td"
                        append("<tr>")
                        rowValues.forEach { append("<$tag>$it</$tag>") }
                        append("</tr>")
                        isFirstRow = false
                    }
                }
                append("</table>")
            }
            sheets.add(sheet.sheetName to buildHtmlPage(tableHtml))
        }
        if (sheets.size <= 1) {
            OfficeHtmlResult.Single(sheets.firstOrNull()?.second ?: buildHtmlPage("<p>Empty spreadsheet</p>"))
        } else {
            OfficeHtmlResult.Sheets(sheets)
        }
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

// ---------------------------------------------------------------------------
// Utility helpers
// ---------------------------------------------------------------------------

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
    val cutoff = System.currentTimeMillis() - 60_000
    context.cacheDir.listFiles()?.forEach { f ->
        if (f.name.startsWith("active_pdf_") && f.lastModified() < cutoff) f.delete()
    }
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

private fun renderPdfPageLowMemory(page: PdfRenderer.Page, maxPixels: Float = 1_200_000f): Bitmap {
    val baseScale = 1.5f
    val desiredW = (page.width * baseScale).toInt().coerceAtLeast(480)
    val desiredH = (page.height * baseScale).toInt().coerceAtLeast(640)

    val currentPixels = desiredW.toFloat() * desiredH.toFloat()
    val ratio = if (currentPixels > maxPixels) sqrt(maxPixels / currentPixels) else 1f
    val targetW = (desiredW * ratio).toInt().coerceAtLeast(320)
    val targetH = (desiredH * ratio).toInt().coerceAtLeast(480)

    val attempts = listOf(
        targetW to targetH,
        (targetW * 0.7f).toInt().coerceAtLeast(240) to (targetH * 0.7f).toInt().coerceAtLeast(320),
        (targetW * 0.5f).toInt().coerceAtLeast(180) to (targetH * 0.5f).toInt().coerceAtLeast(260)
    )

    var lastError: Throwable? = null
    for ((w, h) in attempts) {
        var bmp: Bitmap? = null
        try {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } catch (t: Throwable) {
            bmp?.recycle()
            Log.w(TAG, "Render attempt failed bitmap=${w}x$h: ${t.message}")
            lastError = t
        }
    }
    throw IOException("All render attempts failed", lastError)
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxPixels: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null

    var sampleSize = 1
    while ((w / sampleSize) * (h / sampleSize) > maxPixels) sampleSize *= 2

    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
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
