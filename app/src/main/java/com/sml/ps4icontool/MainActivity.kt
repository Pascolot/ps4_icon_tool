package com.sml.ps4icontool

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sml.ps4icontool.ui.theme.PS4IconToolTheme
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

// --- COLORS ---
val BackgroundBlack = Color(0xFF0F0F13)
val DarkGray = Color(0xFF1E1E26)
val FolderBlue = Color(0xFF5082E6)
val FolderYellow = Color(0xFFE6C650)
val FolderRed = Color(0xFFE65050)
val PS4Blue = Color(0xFF003087) // PS4 Blue
val CheckGreen = Color(0xFF2ECC71)
val LimeGreen = Color(0xFF32CD32)
val Cyan = Color(0xFF00E5FF)

val bitmapCache = mutableMapOf<String, Bitmap>()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PS4IconToolTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundBlack) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topScrollState = rememberScrollState()

    val prefs = remember { context.getSharedPreferences("config", Context.MODE_PRIVATE) }
    var ip by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }

    var folders by remember { mutableStateOf(listOf<String>()) }
    var selectedFolder by remember { mutableStateOf("") }
    var filesOnPS4 by remember { mutableStateOf(setOf<String>()) }
    var currentMode by remember { mutableStateOf("icon0.png") }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var originalBmp by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    var gameName by remember { mutableStateOf("") }
    var originalName by remember { mutableStateOf("") }

    var showMusicDialog by remember { mutableStateOf(false) }

    val galleryLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    scope.launch {
                        loading = true
                        previewBmp = processImage(context, it, currentMode)
                        loading = false
                    }
                }
            }

    if (showMusicDialog) {
        DeleteConfirmationDialog(
                onConfirm = {
                    showMusicDialog = false
                    scope.launch {
                        if (deleteFile(ip, selectedFolder, "snd0.at9", context))
                            filesOnPS4 -= "snd0.at9"
                    }
                },
                onDismiss = { showMusicDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {

        // --- TOP SECTION ---
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(topScrollState)) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                .height(160.dp)
                                .background(Color.Black, RoundedCornerShape(12.dp))
                                .border(2.dp, DarkGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(color = PS4Blue)
                } else {
                    previewBmp?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit
                        )
                        Box(
                                modifier =
                                        Modifier.align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Gray.copy(0.7f))
                                            .clickable {
                                                saveImageToGallery(context, bmp, currentMode)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Telecharger", // TODO: translate to "Download"
                                color = Color.White.copy(0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                            ?: Text(
                                    "AUCUNE IMAGE", // TODO: translate to "NO IMAGE"
                                    color = Color.White.copy(0.2f),
                                    fontWeight = FontWeight.Bold
                            )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                        value = gameName,
                        onValueChange = { gameName = it },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        placeholder = {
                            Text(
                                "Nom du jeu...", // TODO: translate to "Game name..."
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        textStyle =
                                LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                ),
                        trailingIcon = {
                            if (gameName.isNotEmpty()) {
                                IconButton(onClick = { gameName = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PS4Blue,
                                    unfocusedBorderColor = DarkGray,
                                    cursorColor = PS4Blue
                                ),
                        shape = RoundedCornerShape(8.dp)
                )
                Text(
                    "Concu par Kizeo, édité par Sômôlô_Games", // TODO: translate to "Designed by Somolo_Games"
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button
                IconButton(
                        onClick = {
                            previewBmp = originalBmp
                            gameName = originalName
                        },
                        modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        "Reinitialiser", // TODO: translate to "Reset"
                        tint = Cyan,
                        modifier = Modifier.size(45.dp)
                    )
                }

                Row(
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    val fileExists = filesOnPS4.contains(currentMode)
                    Button(
                            onClick = {
                                scope.launch {
                                    if (deleteFile(ip, selectedFolder, currentMode, context)) {
                                        filesOnPS4 -= currentMode
                                        previewBmp = null
                                        originalBmp = null
                                    }
                                }
                            },
                            enabled = fileExists && selectedFolder.isNotEmpty(),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF3D1F1F),
                                            disabledContainerColor = Color.DarkGray.copy(0.2f)
                                    ),
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                                Icons.Default.Delete,
                                null,
                                tint = Color.Red,
                                modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                                "SUPPRIMER", // TODO: translate to "DELETE"
                                fontSize = 10.sp,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1.4f).height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                                if (fileExists) "MODIFIER" else "CREER", // TODO: translate to "EDIT" / "CREATE"
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LimeGreen
                        )
                    }
                }

                val hasChanges =
                        (previewBmp != originalBmp && previewBmp != null) ||
                                (gameName != originalName && gameName.isNotEmpty())
                val animScale by animateFloatAsState(if (hasChanges) 1.15f else 1f)
                // Apply / upload button
                IconButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                var success = true
                                if (previewBmp != originalBmp && previewBmp != null)
                                        success =
                                                uploadFile(
                                                        ip,
                                                        selectedFolder,
                                                        currentMode,
                                                        previewBmp!!,
                                                        context
                                                )
                                if (gameName != originalName &&
                                                gameName.isNotEmpty() &&
                                                success
                                ) {
                                    if (modifySFOName(ip, selectedFolder, gameName)) {
                                        originalName = gameName
                                    } else success = false
                                }
                                if (success) {
                                    filesOnPS4 += currentMode
                                    originalBmp = previewBmp
                                }
                                loading = false
                            }
                        },
                        enabled = hasChanges,
                        modifier = Modifier.size(55.dp)
                ) {
                    Icon(
                            Icons.Default.CheckCircle,
                            "Appliquer", // TODO: translate to "Apply"
                            tint = if (hasChanges) CheckGreen else Color.White.copy(0.1f),
                            modifier = Modifier.size(50.dp).scale(animScale)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CleanupButton(
                        "RETIRER MUSIQUE", // TODO: translate to "REMOVE MUSIC"
                        filesOnPS4.contains("snd0.at9"),
                        Modifier.weight(1f)
                ) { showMusicDialog = true }
            }

            Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Icon mode button
                Button(
                        onClick = {
                            currentMode = "icon0.png"
                            scope.launch {
                                val img = fetchWithCache(ip, selectedFolder, "icon0.png")
                                previewBmp = img
                                originalBmp = img
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor =
                                                if (currentMode == "icon0.png") PS4Blue
                                                else PS4Blue.copy(0.12f)
                                ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                            "ICONE", // TODO: translate to "ICON"
                            color =
                                    if (currentMode == "icon0.png") Color.White
                                    else Color.White.copy(0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                    )
                }

                val isPicMode = currentMode.startsWith("pic")
                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                                if (isPicMode) PS4Blue
                                                else PS4Blue.copy(0.12f)
                                        )
                                        .pointerInput(selectedFolder) {
                                            detectTapGestures(
                                                    onTap = {
                                                        if (!isPicMode) {
                                                            currentMode = "pic1.png"
                                                            scope.launch {
                                                                val img =
                                                                        fetchWithCache(
                                                                                ip,
                                                                                selectedFolder,
                                                                                "pic1.png"
                                                                        )
                                                                previewBmp = img
                                                                originalBmp = img
                                                            }
                                                        }
                                                    },
                                                    onLongPress = {
                                                        // Cycle through cover modes on long press
                                                        if (selectedFolder.isNotEmpty()) {
                                                            val cycle =
                                                                    listOf("pic1.png", "pic0.png")
                                                            currentMode =
                                                                    cycle[
                                                                            (cycle.indexOf(
                                                                                    currentMode
                                                                            ) + 1) % cycle.size]
                                                            scope.launch {
                                                                val img =
                                                                        fetchWithCache(
                                                                                ip,
                                                                                selectedFolder,
                                                                                currentMode
                                                                        )
                                                                previewBmp = img
                                                                originalBmp = img
                                                            }
                                                        }
                                                    }
                                            )
                                        },
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            when (currentMode) {
                                "pic1.png" -> "COUVERTURE" // TODO: translate to "COVER"
                                "pic0.png" -> "PIC0"
                                else -> "COUVERTURE"       // TODO: translate to "COVER"
                            },
                            color = if (isPicMode) Color.White else Color.White.copy(0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                    )
                }
            }

            OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = {
                        Text(
                                "IP de votre PS4...", // TODO: translate to "Your PS4 IP..."
                                color = Color.Gray,
                                fontSize = 13.sp
                        )
                    },
                    label = { Text("IP PS4", color = Color.Gray, fontSize = 11.sp) },
                    trailingIcon = {
                        // Connect button
                        IconButton(
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        val result = connectFTP(ip)
                                        if (result != null) {
                                            folders = result
                                            prefs.edit().putString("last_ip", ip).apply()
                                        } else {
                                            Toast.makeText(
                                                            context,
                                                            "Erreur de connexion", // TODO: translate to "Connection error"
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                        loading = false
                                    }
                                }
                        ) { Icon(Icons.Default.Send, null, tint = CheckGreen) }
                    },
                    colors =
                            OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = FolderBlue
                            ),
                    shape = RoundedCornerShape(8.dp)
            )
        }

        // --- GAME LIST (4 COLUMNS) ---
        Text(
                "LISTE DES JEUX", // TODO: translate to "GAME LIST"
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
        )

        LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(folders) { item ->
                val isSelected = item == selectedFolder
                val iconColor =
                        when {
                            item.startsWith("CUSA") -> FolderBlue
                            item.startsWith("NP") -> FolderYellow
                            else -> FolderRed
                        }
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                                Modifier.clickable {
                                    selectedFolder = item
                                    currentMode = "icon0.png"
                                    scope.launch {
                                        loading = true
                                        filesOnPS4 = scanFiles(ip, item)
                                        val img = fetchWithCache(ip, item, "icon0.png")
                                        previewBmp = img
                                        originalBmp = img
                                        val name = getSFOName(ip, item)
                                        gameName = name
                                        originalName = name
                                        loading = false
                                    }
                                }
                ) {
                    Box(
                            modifier =
                                    Modifier.size(70.dp)
                                            .background(iconColor, RoundedCornerShape(10.dp))
                                            .border(
                                                    if (isSelected) 2.dp else 0.dp,
                                                    Color.White,
                                                    RoundedCornerShape(10.dp)
                                            ),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.List,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                            text = item,
                            fontSize = 8.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp).fillMaxWidth()
                    )
                }
            }
        }
    }
}

// --- FTP FUNCTIONS ---
suspend fun getSFOName(ip: String, folder: String): String =
        withContext(Dispatchers.IO) {
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                ftp.setFileType(FTP.BINARY_FILE_TYPE)

                // Try /system_data/priv/appmeta/ first
                val stream = ftp.retrieveFileStream("/system_data/priv/appmeta/$folder/param.sfo")
                if (stream == null) {
                    // If it fails, disconnect and return fallback
                    ftp.disconnect()
                    return@withContext "Sans Nom" // TODO: translate to "No Name"
                }

                val data = stream.readBytes()
                stream.close()
                ftp.completePendingCommand()
                ftp.disconnect()

                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val keyTableOffset = buffer.getInt(0x08)
                val dataTableOffset = buffer.getInt(0x0C)
                val entryCount = buffer.getInt(0x10)

                for (i in 0 until entryCount) {
                    val keyOffset = buffer.getShort(0x14 + (i * 16)).toInt() and 0xFFFF
                    val dataOffset = buffer.getInt(0x14 + (i * 16) + 0x0C)
                    val keyBuilder = StringBuilder()
                    var k = keyTableOffset + keyOffset
                    while (data[k] != 0.toByte()) {
                        keyBuilder.append(data[k].toInt().toChar())
                        k++
                    }
                    if (keyBuilder.toString() == "TITLE") {
                        val start = dataTableOffset + dataOffset
                        var end = start
                        while (data[end] != 0.toByte()) end++
                        return@withContext String(data, start, end - start, Charsets.UTF_8)
                    }
                }
                "Sans Nom" // TODO: translate to "No Name"
            } catch (e: Exception) {
                "Erreur SFO" // TODO: translate to "SFO Error"
            }
}

suspend fun modifySFOName(ip: String, folder: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                ftp.setFileType(FTP.BINARY_FILE_TYPE)

                val inputStream =
                        ftp.retrieveFileStream("/system_data/priv/appmeta/$folder/param.sfo")
                if (inputStream == null) {
                    ftp.disconnect()
                    return@withContext false
                }

                val data = inputStream.readBytes()
                inputStream.close()
                ftp.completePendingCommand()

                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val keyTableOffset = buffer.getInt(0x08)
                val dataTableOffset = buffer.getInt(0x0C)
                val entryCount = buffer.getInt(0x10)

                for (i in 0 until entryCount) {
                    val keyOffset = buffer.getShort(0x14 + (i * 16)).toInt() and 0xFFFF
                    val dataOffset = buffer.getInt(0x14 + (i * 16) + 0x0C)
                    val dataLength = buffer.getInt(0x14 + (i * 16) + 0x04)
                    val keyBuilder = StringBuilder()
                    var k = keyTableOffset + keyOffset
                    while (data[k] != 0.toByte()) {
                        keyBuilder.append(data[k].toInt().toChar())
                        k++
                    }
                    val key = keyBuilder.toString()
                    if (key == "TITLE" || key.startsWith("TITLE_")) {
                        val start = dataTableOffset + dataOffset
                        val nameBytes = newName.toByteArray(Charsets.UTF_8)
                        // Clear the existing title field
                        for (j in 0 until dataLength) if (start + j < data.size) data[start + j] = 0
                        // Write the new title bytes
                        for (j in nameBytes.indices) if (j < dataLength - 1 && (start + j) < data.size)
                                data[start + j] = nameBytes[j]
                    }
                }
                val success =
                        ftp.storeFile(
                                "/system_data/priv/appmeta/$folder/param.sfo",
                                ByteArrayInputStream(data)
                        )
                ftp.disconnect()
                success
            } catch (e: Exception) {
                false
            }
}

fun saveImageToGallery(context: Context, bitmap: Bitmap, fileName: String) {
    val outputFileName = "${fileName.replace(".", "_")}_${System.currentTimeMillis()}.png"
    try {
        val outputStream: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/PS4IconTool"
                        )
                    }
            val imageUri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = imageUri?.let { resolver.openOutputStream(it) }
        } else {
            outputStream =
                    FileOutputStream(
                            File(
                                    Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_PICTURES
                                    ),
                                    outputFileName
                            )
                    )
        }
        outputStream?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(context, "Enregistre dans la Galerie", Toast.LENGTH_SHORT).show() // TODO: translate to "Saved to Gallery"
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show() // TODO: translate to "Error while saving"
    }
}

@Composable
fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                        "Avertissement", // TODO: translate to "Warning"
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                        "Etes-vous sur de vouloir supprimer ce fichier ?", // TODO: translate to "Are you sure you want to delete this file?"
                        color = Color.LightGray
                )
            },
            containerColor = DarkGray,
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                            "OUI", // TODO: translate to "YES"
                            color = Color.Red,
                            fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                            "NON", // TODO: translate to "NO"
                            color = Color.White
                    )
                }
            }
    )
}

@Composable
fun CleanupButton(label: String, isActive: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
            onClick = onClick,
            enabled = isActive,
            modifier = modifier.height(38.dp),
            colors =
                    ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF252525),
                            disabledContainerColor = Color(0xFF151515)
                    ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(0.dp)
    ) { Text(label, fontSize = 8.sp, color = if (isActive) Color.White else Color.DarkGray) }
}

suspend fun processImage(ctx: Context, uri: Uri, mode: String): Bitmap =
        withContext(Dispatchers.IO) {
            val stream = ctx.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(stream)
            // Determine target dimensions based on image mode
            val (width, height) =
                    when (mode) {
                        "icon0.png" -> 512 to 512
                        "pic1.png" -> 1920 to 1080
                        "pic0.png" -> 1920 to 1080
                        else -> 512 to 512
                    }
            Bitmap.createScaledBitmap(original, width, height, true)
}

suspend fun connectFTP(ip: String): List<String>? =
        withContext(Dispatchers.IO) {
            if (ip.isEmpty()) return@withContext null
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                val dirs =
                        ftp.listDirectories("/user/appmeta/").map { it.name }.filter {
                            !it.startsWith(".") && it.length > 4
                        }
                ftp.disconnect()
                dirs.sorted()
            } catch (e: Exception) {
                null
            }
}

suspend fun scanFiles(ip: String, folder: String): Set<String> =
        withContext(Dispatchers.IO) {
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                val list =
                        ftp.listFiles("/user/appmeta/$folder/")
                                .map { it.name }
                                .filter { it.endsWith(".png") || it.endsWith(".at9") }
                                .toSet()
                ftp.disconnect()
                list
            } catch (e: Exception) {
                emptySet()
            }
}

suspend fun fetchWithCache(ip: String, folder: String, file: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val cacheKey = "$folder-$file"
            if (bitmapCache.containsKey(cacheKey)) return@withContext bitmapCache[cacheKey]
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                val stream = ftp.retrieveFileStream("/user/appmeta/$folder/$file")
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) bitmapCache[cacheKey] = bmp
                ftp.disconnect()
                bmp
            } catch (e: Exception) {
                null
            }
        }

suspend fun uploadFile(
        ip: String,
        folder: String,
        fileName: String,
        bmp: Bitmap,
        ctx: Context
): Boolean =
        withContext(Dispatchers.IO) {
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                val outputStream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val success =
                        ftp.storeFile(
                                "/user/appmeta/$folder/$fileName",
                                ByteArrayInputStream(outputStream.toByteArray())
                        )
                ftp.disconnect()
                bitmapCache.remove("$folder-$fileName")
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "$fileName mis a jour !", Toast.LENGTH_SHORT).show() // TODO: translate to "$fileName updated!"
                }
                success
            } catch (e: Exception) {
                false
            }
}

suspend fun deleteFile(ip: String, folder: String, fileName: String, ctx: Context): Boolean =
        withContext(Dispatchers.IO) {
            val ftp = FTPClient()
            try {
                ftp.connect(ip, 2121)
                ftp.login("anonymous", "")
                val success = ftp.deleteFile("/user/appmeta/$folder/$fileName")
                ftp.disconnect()
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Fichier supprime", Toast.LENGTH_SHORT).show() // TODO: translate to "File deleted"
                }
                success
            } catch (e: Exception) {
                false
            }
}