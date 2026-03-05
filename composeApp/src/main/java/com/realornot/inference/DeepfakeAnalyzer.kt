package com.realornot.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.realornot.data.ApiKeyManager
import com.realornot.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

data class AnalysisResult(
    val verdict: String,
    val confidence: Float,
    val modelUsed: String,
    val processingTimeMs: Long,
    val videoVerdict: String? = null,
    val videoConfidence: Float? = null,
    val audioVerdict: String? = null,
    val audioConfidence: Float? = null,
    val reasoning: String? = null,
)

class DeepfakeAnalyzer(private val context: Context) {

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val FRAMES_PER_VIDEO = 16

        // ── IMAGE FORENSIC PROMPT ──────────────────────────────────────
        private const val IMAGE_PROMPT = """
You are a world-class digital forensics expert specializing in AI-generated and manipulated media detection.
Your job is to determine whether the provided image is REAL (authentic photograph) or AI-GENERATED / MANIPULATED.

Perform a THOROUGH multi-layer forensic analysis checking ALL of the following:

**FACIAL ANALYSIS (Critical for face-swap / deepfake detection):**
- Skin texture: Real skin has pores, fine wrinkles, micro-blemishes. AI skin is often too smooth or has repeating texture patterns.
- Eye analysis: Check for iris symmetry, pupil shape, corneal reflections consistency, eyelash regularity, and sclera texture.
- Teeth & mouth: Look for blurred teeth, inconsistent tooth count, gum-line artifacts, lip-edge blending errors.
- Hair: Check strand-level detail, hairline naturalness, hair-skin boundary sharpness.
- Ears: Asymmetry artifacts, blurred inner ear structure, earring attachment points.
- Facial symmetry: AI often produces overly symmetric faces or has subtle left-right inconsistencies in jawline, cheekbones.
- Face boundary blending: In face-swaps, the boundary between the swapped face and original head/neck often shows color mismatch, blur transition, or edge artifacts.

**STRUCTURAL & GEOMETRIC ANALYSIS:**
- Hands & fingers: Count fingers (AI commonly generates 6 fingers or fused digits), check nail detail, knuckle creases.
- Body proportions: Limb length ratios, shoulder width, neck-to-head ratio.
- Background consistency: Check for warped lines, impossible geometry, repeating patterns.
- Text/signage: AI frequently produces garbled, nonsensical, or inconsistent text.
- Reflections & shadows: Directional consistency, shadow-object alignment.

**PIXEL-LEVEL ANALYSIS:**
- Compression artifacts: JPEG grid alignment, inconsistent compression across regions (indicates splicing).
- Noise patterns: Uniform vs region-specific noise (authentic cameras have consistent sensor noise).
- Color channel analysis: Look for color bleeding, chromatic aberration inconsistencies.
- Edge sharpness: AI upscaling or generation often produces unnaturally sharp or soft edges in specific areas.
- GAN fingerprints: Spectral artifacts, checkerboard patterns in frequency domain.

**GENERATION MODEL SIGNATURES:**
- Stable Diffusion: Often has difficulties with text, hands, and produces characteristic "dreamy" lighting.
- Midjourney: Tends toward painterly quality, specific color grading patterns.
- DALL-E: Characteristic edge handling and object boundary treatment.
- Face-swap tools (DeepFaceLab, FaceSwap, Roop): Face boundary color mismatch, inconsistent skin tone between face and neck, lighting direction mismatch on face vs scene.
- GAN-based: Periodic spectral artifacts, blob-like micro-textures.

**IMPORTANT RULES:**
- Be SKEPTICAL. Default assumption should lean toward scrutiny, not trust.
- If you see EVEN ONE strong indicator of manipulation, flag it as AI-GENERATED.
- Confidence should reflect how certain you are: 90-100% = very clear indicators, 70-89% = moderate indicators, 50-69% = subtle/ambiguous.
- Do NOT default to 50%. That means you didn't analyze anything. Commit to a decision.

You MUST respond with ONLY valid JSON in this exact structure (no markdown, no extra text):
{"verdict": "REAL" or "AI-GENERATED", "confidence": <float 0-100>, "reasoning": "<detailed 2-4 sentence forensic explanation>"}
"""

        // ── VIDEO VISUAL FORENSIC PROMPT ───────────────────────────────
        private const val VIDEO_VISUAL_PROMPT = """
You are a world-class video forensics expert specializing in deepfake and face-swap detection.
These are sequential frames extracted from a video at regular intervals. Analyze them for manipulation.

Perform a THOROUGH temporal and spatial forensic analysis:

**TEMPORAL CONSISTENCY (Frame-to-Frame):**
- Face identity drift: Does the face subtly change identity across frames? Face-swaps often have micro-flickering.
- Lighting consistency: Does lighting on the face match the scene lighting across all frames?
- Blinking patterns: Deepfakes often have unnatural blink frequency or missing blinks entirely.
- Expression transitions: Are expression changes smooth and natural, or do they have sudden jumps?
- Head pose vs face alignment: In face-swaps, the face may lag behind head rotation.
- Skin tone consistency: Does skin color shift unnaturally between frames?

**SPATIAL ANALYSIS (Per-Frame):**
- Face-neck boundary: Color mismatch, blur, or hard edge between face and neck is a strong face-swap indicator.
- Occlusion handling: How does the face behave when partially occluded by hands, hair, or objects?
- Resolution mismatch: Is the face at a different resolution than the background?
- Glasses/accessories: Reflections and edge handling around glasses frames.
- Eye gaze coherence: Do eyes track naturally or have robotic/fixed gaze?

**DEEPFAKE-SPECIFIC TELLS:**
- DeepFaceLab: Characteristic face boundary blending, forehead artifacts.
- Wav2Lip / audio-driven: Lip region may have different texture/resolution than rest of face.
- First Order Motion Model: Warping artifacts around face edges during motion.
- reenactment deepfakes: Expression transfer artifacts, unnatural micro-expressions.

**IMPORTANT:** Be aggressive in detection. If ANY frames show manipulation indicators, verdict should be AI-GENERATED.

You MUST respond with ONLY valid JSON (no markdown):
{"verdict": "REAL" or "AI-GENERATED", "confidence": <float 0-100>, "reasoning": "<detailed 2-4 sentence forensic explanation referencing specific frames if possible>"}
"""

        // ── AUDIO FORENSIC PROMPT ──────────────────────────────────────
        private const val AUDIO_FORENSIC_PROMPT = """
You are a world-class audio forensics expert specializing in AI voice cloning and synthetic speech detection.
Analyze the audio track for signs of AI generation or voice cloning.

Perform a THOROUGH audio forensic analysis:

**VOICE NATURALNESS:**
- Breathing: Real speech has natural breath intake sounds, pauses for breathing. AI-generated speech often lacks breaths or has mechanical breath sounds.
- Micro-pauses: Natural speech has hesitations, filler sounds (um, uh), and variable pacing. TTS is often unnaturally smooth.
- Pitch variation: Human pitch varies naturally with emotion and emphasis. Cloned voices often have reduced pitch range or unnatural pitch jumps.
- Formant analysis: Human formants have natural transitions. Voice clones from ElevenLabs, Bark, or XTTS often have slight formant artifacts.

**TECHNICAL ARTIFACTS:**
- Spectral gaps: AI-generated audio may have unusual gaps or artifacts in the spectrogram above 8kHz.
- Room acoustics: Does the reverb/echo sound natural and consistent? AI-generated audio often has inconsistent or absent room tone.
- Background noise: Real recordings have consistent ambient noise. AI audio is often too clean or has synthetic noise.
- Audio clipping and distortion: Natural vs synthetic distortion patterns.

**VOICE CLONING SIGNATURES:**
- ElevenLabs: Characteristic smoothness, sometimes metallic quality in consonants.
- Bark/Tortoise TTS: Specific artifacts in sibilants (s, sh sounds), sometimes robotic quality.
- RVC (Retrieval-based Voice Conversion): Source speaker artifacts bleeding through, pitch artifacts.
- So-VITS-SVC: Musical quality to speech, characteristic vibrato artifacts.
- XTTS/Coqui: Specific formant handling patterns, sometimes unnatural word boundaries.

**IMPORTANT:** Voice cloning technology is very advanced now. Listen for SUBTLE markers.

You MUST respond with ONLY valid JSON (no markdown):
{"verdict": "REAL" or "AI-GENERATED", "confidence": <float 0-100>, "reasoning": "<detailed 2-4 sentence forensic explanation>"}
"""
    }

    private val generationConfig = GenerationConfig.builder().apply {
        temperature = 0.1f
        topP = 0.5f
        topK = 20
        candidateCount = 1
    }.build()

    /** Lazily initialized so it picks up the API key when first used. */
    private val generativeModel by lazy {
        val apiKey = ApiKeyManager.getApiKey(context)
            ?: throw IllegalStateException("No Gemini API key configured. Please set one in Settings.")
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig
        )
    }

    suspend fun analyze(
        uri: Uri,
        mediaType: MediaType,
        onProgress: suspend (Float, String) -> Unit,
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        when (mediaType) {
            MediaType.IMAGE -> analyzeImage(uri, t0, onProgress)
            MediaType.VIDEO -> analyzeVideo(uri, t0, onProgress)
            MediaType.AUDIO -> analyzeAudio(uri, t0, onProgress)
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // IMAGE ANALYSIS
    // ═════════════════════════════════════════════════════════════════
    private suspend fun analyzeImage(
        uri: Uri, t0: Long, onProgress: suspend (Float, String) -> Unit,
    ): AnalysisResult {
        onProgress(0.15f, "Loading image…")
        val bitmap = loadBitmap(uri)

        onProgress(0.30f, "Running forensic analysis …")
        val response = generativeModel.generateContent(
            content {
                image(bitmap)
                text(IMAGE_PROMPT)
            }
        )
        bitmap.recycle()

        onProgress(0.90f, "Parsing forensic report…")
        return parseGeminiResponse(response.text, "Gemini 2.5 Flash (Image)", t0)
    }

    // ═════════════════════════════════════════════════════════════════
    // VIDEO ANALYSIS (Frames + Audio)
    // ═════════════════════════════════════════════════════════════════
    private suspend fun analyzeVideo(
        uri: Uri, t0: Long, onProgress: suspend (Float, String) -> Unit,
    ): AnalysisResult {
        onProgress(0.05f, "Extracting video frames…")

        // 1. EXTRACT 16 FRAMES FOR VISUAL ANALYSIS
        val frames = extractFrames(uri)

        // 2. READ ENTIRE MP4 FILE FOR AUDIO ANALYSIS
        val fileBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: OutOfMemoryError) {
            null
        }

        onProgress(0.20f, "Analyzing ${frames.size} frames for AI generated content")
        val visualResponse = generativeModel.generateContent(
            content {
                frames.forEach { image(it) }
                text(VIDEO_VISUAL_PROMPT)
            }
        )
        frames.forEach { it.recycle() }
        val visualResult = parseGeminiResponse(visualResponse.text, "Gemini 2.5 Flash", t0)

        onProgress(0.60f, "Analyzing audio track for voice cloning…")
        var audioVerdict = "N/A"
        var audioConf = 0f
        var mergedReasoning = visualResult.reasoning

        if (fileBytes != null) {
            try {
                val audioResp = generativeModel.generateContent(
                    content {
                        blob("video/mp4", fileBytes)
                        text(AUDIO_FORENSIC_PROMPT)
                    }
                )
                val audioResult = parseGeminiResponse(audioResp.text, "Gemini 2.5 Flash", t0)
                audioVerdict = audioResult.verdict
                audioConf = audioResult.confidence
                mergedReasoning = "🎬 Visual Analysis: ${visualResult.reasoning}\n\n🔊 Audio Analysis: ${audioResult.reasoning}"
            } catch (e: Exception) {
                audioVerdict = "N/A"
                mergedReasoning = "🎬 Visual Analysis: ${visualResult.reasoning}\n\n🔊 Audio Analysis: Could not be performed (${e.message})"
            }
        }

        onProgress(0.95f, "Finalizing forensic report…")
        // If EITHER visual or audio is flagged, the combined verdict is AI-GENERATED
        val finalVerdict = if (visualResult.verdict == "AI-GENERATED" || audioVerdict == "AI-GENERATED") "AI-GENERATED" else "REAL"
        val finalConf = if (audioVerdict == "N/A") visualResult.confidence else max(visualResult.confidence, audioConf)

        return AnalysisResult(
            verdict = finalVerdict,
            confidence = finalConf,
            modelUsed = "Gemini 2.5 Flash (Cloud)",
            processingTimeMs = System.currentTimeMillis() - t0,
            videoVerdict = visualResult.verdict,
            videoConfidence = visualResult.confidence,
            audioVerdict = audioVerdict,
            audioConfidence = audioConf,
            reasoning = mergedReasoning
        )
    }

    // ═════════════════════════════════════════════════════════════════
    // AUDIO ANALYSIS
    // ═════════════════════════════════════════════════════════════════
    private suspend fun analyzeAudio(
        uri: Uri, t0: Long, onProgress: suspend (Float, String) -> Unit,
    ): AnalysisResult {
        onProgress(0.20f, "Uploading audio for forensic analysis…")
        val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Failed to read audio file")

        // Detect MIME type from URI
        val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"

        onProgress(0.40f, "Analyzing voice patterns …")
        val response = generativeModel.generateContent(
            content {
                blob(mimeType, fileBytes)
                text(AUDIO_FORENSIC_PROMPT)
            }
        )

        onProgress(0.90f, "Parsing forensic report…")
        return parseGeminiResponse(response.text, "Gemini 2.5 Flash (Audio)", t0)
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private fun parseGeminiResponse(responseText: String?, modelUsed: String, t0: Long): AnalysisResult {
        if (responseText == null) {
            return AnalysisResult("REAL", 50f, modelUsed, System.currentTimeMillis() - t0, reasoning = "API returned an empty response.")
        }

        return try {
            // Gemini might wrap JSON in markdown block: ```json ... ```
            val cleanJson = responseText.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$cleanJson}")

            val verdict = json.optString("verdict", "REAL").uppercase().trim()
            val normalizedVerdict = if (verdict.contains("AI") || verdict.contains("GENERATED") || verdict.contains("FAKE") || verdict.contains("MANIPULATED")) {
                "AI-GENERATED"
            } else {
                "REAL"
            }

            AnalysisResult(
                verdict = normalizedVerdict,
                confidence = json.optDouble("confidence", 50.0).toFloat().coerceIn(0f, 100f),
                modelUsed = modelUsed,
                processingTimeMs = System.currentTimeMillis() - t0,
                reasoning = json.optString("reasoning", "No reasoning provided.")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // If JSON parsing fails, try to extract verdict from raw text
            val rawLower = responseText.lowercase()
            val fallbackVerdict = if (rawLower.contains("ai-generated") || rawLower.contains("fake") || rawLower.contains("manipulated") || rawLower.contains("synthetic")) {
                "AI-GENERATED"
            } else {
                "REAL"
            }
            AnalysisResult(fallbackVerdict, 75f, modelUsed, System.currentTimeMillis() - t0, reasoning = "Forensic analysis completed but response format was non-standard. Raw analysis: ${responseText.take(500)}")
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file")
        // Load at a reasonable resolution to preserve fine details for forensic analysis
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1 // Full resolution for maximum detail
        }
        return stream.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Cannot decode image")
    }

    private fun extractFrames(uri: Uri): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(context, uri) }
        catch (_: Exception) { return emptyList() }

        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L

        if (durationMs <= 0) { retriever.release(); return emptyList() }

        val frames = mutableListOf<Bitmap>()
        try {
            for (i in 0 until FRAMES_PER_VIDEO) {
                val timeUs = (durationMs * 1000L * i) / max(1, FRAMES_PER_VIDEO - 1)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) frames.add(frame)
            }
        } catch (_: Exception) { }
        finally { retriever.release() }
        return frames
    }

    fun close() {
        // No native resources to close
    }
}
