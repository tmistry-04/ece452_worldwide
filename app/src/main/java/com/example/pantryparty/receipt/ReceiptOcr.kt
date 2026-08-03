package com.example.pantryparty.receipt

import android.util.Log
import com.example.pantryparty.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device text recognition, bundled with the app.
 *
 * Created once and reused: the recognizer loads a model on first use, so building one
 * per capture would add that cost to every scan. It lives for the process lifetime,
 * which is what ML Kit's own samples do for a repeatedly-used recognizer.
 */
private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}

/** Logcat tag for the scan pipeline: `adb logcat -s PantryScan`. */
private const val SCAN_TAG = "PantryScan"

/**
 * Reads receipt rows off a captured frame, top-to-bottom.
 *
 * Runs entirely on-device, so it needs no network and no API key — which is what keeps
 * a scan inside NFR 3.2's 5 s budget and lets the feature work in a store with bad
 * reception. Returns [Result] to match [com.example.pantryparty.network.SpoonacularRepository]'s
 * convention rather than throwing at the call site.
 */
suspend fun recognizeReceiptLines(image: InputImage): Result<List<String>> =
    suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val runs = text.textBlocks
                    .flatMap { block -> block.lines }
                    .mapNotNull { line ->
                        line.boundingBox?.let { box ->
                            OcrTextRun(
                                text = line.text,
                                left = box.left,
                                top = box.top,
                                bottom = box.bottom
                            )
                        }
                    }
                val rows = rowsFromTextRuns(runs)
                // Debug only. Receipt bugs are near-impossible to diagnose from the
                // review screen alone — it shows the parsed result, not what the model
                // actually read. `adb logcat -s PantryScan` gives the real input.
                if (BuildConfig.DEBUG) {
                    Log.d(SCAN_TAG, "OCR produced ${rows.size} rows:")
                    rows.forEachIndexed { i, row -> Log.d(SCAN_TAG, "  [$i] $row") }
                }
                continuation.resume(Result.success(rows))
            }
            .addOnFailureListener { error ->
                continuation.resume(Result.failure(error))
            }
    }
