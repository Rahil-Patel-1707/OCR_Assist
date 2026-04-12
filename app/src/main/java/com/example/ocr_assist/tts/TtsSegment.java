package com.example.ocr_assist.tts;
import com.example.ocr_assist.model.Language;


/**
 * TtsSegment represents a chunk of text to be spoken by the Android TTS engine.
 * It contains the detected language, playback speed, and logical queue identifiers.
 */
public class TtsSegment {
    public final int id;
    public final String text;
    public final String languageCode;
    public final float pitch;
    public final float speed;
    public final boolean isLanguageSwitch;

    public TtsSegment(int id, String text, String languageCode, float pitch, float speed, boolean isLanguageSwitch) {
        this.id = id;
        this.text = text;
        this.languageCode = languageCode;
        this.pitch = pitch;
        this.speed = speed;
        this.isLanguageSwitch = isLanguageSwitch;
    }
}



