package com.example.simplehexcr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.OrientationEventListener
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.util.Hex
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.io.InputStream
import java.lang.Exception

// represent a hexadecimal-encoded ascii character (2 hex chars = 1 ascii char)
data class HexEncodedAsciiChar(val hexString: String, val ascii: Char, val hexCharPosition1: Int, val hexCharPosition2: Int)

class ImageViewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var imageTextView: EditText
    private lateinit var textTranslationView: TextView
    private lateinit var justHexView: TextView
    private var currentRotation: Int = 0
    private var extractedText: String = ""
    private var cursorPosition: Int = 0
    private val colorRainbow = listOf("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#00FFFF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)

        imageView = findViewById(R.id.imageView)
        imageTextView = findViewById(R.id.imageText)
        textTranslationView = findViewById(R.id.hexTranslation)
        justHexView = findViewById(R.id.justHex)
        val btnGoBack: Button = findViewById(R.id.btnGoBack)

        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // set up watcher on edittext
        imageTextView.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                cursorPosition = imageTextView.selectionStart
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // unimplemented
            }

            override fun afterTextChanged(s: Editable?) {
                val editedText = s.toString()
                if (editedText != extractedText) {
                    applyText(editedText)
                }
            }
        })

        // Retrieve the captured image file path from CameraActivity
        val imagePath = intent.getStringExtra("imagePath")
        val imageUriString = intent.getStringExtra("imageUri")
        var rotation: Int = 0;
        var rotatedBitmap: Bitmap;
        // Load and display the captured image
        if (imagePath != null) {
            val bitmap: Bitmap = BitmapFactory.decodeFile(imagePath)

            rotation = getCameraPhotoOrientation(imagePath)
            Log.v("imageView", "Rotation from EXIF data: $rotation")
            rotatedBitmap = rotateBitmap(bitmap, rotation)

        } else if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            val contentResolver: ContentResolver = this.contentResolver
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
            rotation = getMediaPhotoOrientation(inputStream!!)
            Log.v("imageView", "Rotation from EXIF data: $rotation")
            rotatedBitmap = rotateBitmap(bitmap, rotation)
            inputStream?.close()
        } else {
            throw Exception("Invalid: ImageView requires imagePath or imageUri")
        }

        imageView.setImageBitmap(rotatedBitmap)

        val imageForRecognition = InputImage.fromBitmap(rotatedBitmap, 0)
        imageTextView.text = Editable.Factory.getInstance().newEditable("Processing image...")
        val result = textRecognizer.process(imageForRecognition)
            .addOnSuccessListener { visionText ->
                Log.v("ImageViewActivity", visionText.text)
                // show just lines with hex
                val linesWithHex = extractLinesWithPotentialHex(visionText)
                applyText(postProcess(linesWithHex))
            }
            .addOnFailureListener { e ->
                imageTextView.text = Editable.Factory.getInstance().newEditable("Error processing image.")
                e.printStackTrace()
            }

        // Provide an option to go back to the previous view
        btnGoBack.setOnClickListener {
            finish() // Close the current activity and go back
        }


    }

    private fun applyText(text: String) {
        runOnUiThread {
            extractedText = text
            val editableImageText = Editable.Factory.getInstance().newEditable(extractedText)
            val hexSequence = extractHexAsciiFromString(extractedText)
            val justHexMatches = extractJustHex(extractedText)
            colorizeFullText(editableImageText, hexSequence)
            imageTextView.text = editableImageText

            val justHex = justHexMatches.map { it.value }.toList().joinToString("")
            val translatedHex = getAsciiTranslation(hexSequence)
            justHexView.text = colorizeString(justHex, 2)
            textTranslationView.text = colorizeString(translatedHex, 1)
            imageTextView.setSelection(cursorPosition)
        }
    }

    private fun getMediaPhotoOrientation(imageInputStream: InputStream): Int {
        var exifInterface: ExifInterface;
        try {
            exifInterface = ExifInterface(imageInputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
        return getOrientationFromExifInterface(exifInterface)
    }
    private fun getCameraPhotoOrientation(imagePath: String): Int {
        var exifInterface: ExifInterface;
        try {
            exifInterface = ExifInterface(imagePath)
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
        return getOrientationFromExifInterface(exifInterface)
    }
    private fun getOrientationFromExifInterface(exifInterface: ExifInterface): Int {
        val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
        Log.v("cameraOrientation", "Orientation is $orientation")
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                180;
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> {
                90
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                270
            }

            else -> {
                0
            }
        }
    }

    private fun colorizeString(s: String, spanLength: Int = 1): SpannableString {
        val spannable = SpannableString(s)
        for (i in 0 until s.length) {
            val color = Color.parseColor(colorRainbow[(i / spanLength) % colorRainbow.size])
            val span = ForegroundColorSpan(color)
            spannable.setSpan(span, i, i+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun colorizeFullText(es: Editable, hexAsciiSequence: Sequence<HexEncodedAsciiChar>) {
        var i = 0
        for (hexAscii in hexAsciiSequence) {
            val color = Color.parseColor(colorRainbow[i % colorRainbow.size])
            val span = ForegroundColorSpan(color)
            es.setSpan(span, hexAscii.hexCharPosition1, hexAscii.hexCharPosition1 + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            es.setSpan(ForegroundColorSpan(color), hexAscii.hexCharPosition2, hexAscii.hexCharPosition2 + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            i += 1
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun extractLinesWithPotentialHex(result: Text): String {
        val sb: StringBuilder = StringBuilder()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text
                if (containsHex(lineText)) {
                    sb.append(lineText)
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    private fun postProcess(s: String): String {
        return s.replace("G", "6")
    }

    private fun extractHexAsciiFromString(s: String): Sequence<HexEncodedAsciiChar> {
        val hexMatches = extractJustHex(s)
        // "flatten" groups of potential hex strings into characters, so that we can chunk them by two
        val individualHexCharsToPosition: Sequence<Pair<Char, Int>> = hexMatches.flatMap {
            it.value.mapIndexed { index, value ->
                Pair(value, it.range.first + index)
            }
        }
        val hexChunks = individualHexCharsToPosition.chunked(2)
        return hexChunks.mapNotNull { it ->
            val char1 = it[0]
            // for the last chunk, if we have an odd character, just return null as un-parseable
            val char2 = it.getOrNull(1) ?: return@mapNotNull null
            val hexString: String = char1.first.toString() + char2.first.toString()
            var ascii: Char
            try {
                ascii = hexString.toInt(16).toChar()
            } catch (e: Exception) {
                return@mapNotNull null
            }

            HexEncodedAsciiChar(hexString = hexString, ascii = ascii, hexCharPosition1 = char1.second, hexCharPosition2 = char2.second)
        }
    }

    private fun getAsciiTranslation(hexSequence: Sequence<HexEncodedAsciiChar>): String {
        return hexSequence.map {
            it.ascii
        }.toList().joinToString("")
    }

    private fun translateHex(s: String): String {
        val hexChunks = s.chunked(2)
        val asciiChars = hexChunks.mapNotNull {
            try {
                it.toInt(16).toChar()
            } catch (e: Exception) {
                null
            }
        }.toCharArray()
        return String(asciiChars)
    }

    private fun extractJustHex(s: String): Sequence<MatchResult> {
        // use negative lookback assertion to ensure no overlapping matches
        val hexPattern = Regex("(?<!\\p{XDigit})[0-9A-F][0-9A-F]+")
        val matches = hexPattern.findAll(s)
        return matches
    }

    private fun containsHex(s: String): Boolean {
        val hexPattern = Regex(".*[0-9A-F][0-9A-F]+.*")
        return hexPattern.matches(s)
    }
}