package com.youtube;

import com.lagradost.cloudstream3.AudioFile;
import java.util.Collections;
import java.util.Map;

public final class AudioHelper {
    private AudioHelper() {}

    public static AudioFile createAudioFile(String url, Map<String, String> headers) {
        return new AudioFile(url, headers != null ? headers : Collections.emptyMap());
    }
}
