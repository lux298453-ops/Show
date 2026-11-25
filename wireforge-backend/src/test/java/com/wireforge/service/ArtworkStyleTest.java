package com.wireforge.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkStyleTest {
    @Test void equivalentWhitespaceAndCaseReuseWhileDifferentDetailLevelsRemainDistinct() {
        var a = new ArtworkStyle("2D cartoon", "large head", "clean lines", "yellow", "flat", "none", "simple");
        var b = new ArtworkStyle(" 2d  CARTOON ", "large head", "clean lines", "yellow", "flat", "none", "simple");
        var c = new ArtworkStyle("2D cartoon", "large head", "clean lines", "yellow", "layered", "none", "intricate");
        assertTrue(a.matches(b)); assertFalse(a.matches(c));
        assertEquals(a, ArtworkStyle.parse(a.json()));
    }

    @Test void missingLegacyAndMalformedProfilesDoNotPretendToBeKnownReferenceStyles() {
        for (String value : new String[]{"", "not-json", "{}", "{\"rendering\":\"2D\",\"detail\":\"simple/moderate/intricate\"}",
                "{\"rendering\":[],\"detail\":\"simple\"}"}) assertNull(ArtworkStyle.parse(value));
    }
}
