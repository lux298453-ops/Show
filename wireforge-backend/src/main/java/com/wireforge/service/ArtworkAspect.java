package com.wireforge.service;

import org.jsoup.nodes.Element;

import java.util.Locale;

enum ArtworkAspect {
    PORTRAIT("1024x1536"), SQUARE("1024x1024"), LANDSCAPE("1536x1024");

    static final String ATTRIBUTE = "data-wf-artwork-aspect";
    // Geometric midpoint between the square and landscape ratios. Its reciprocal is the portrait boundary.
    private static final double SQUARE_LIMIT = Math.sqrt(1.5);
    private final String size;

    ArtworkAspect(String size) {
        this.size = size;
    }

    String size() {
        return size;
    }

    static ArtworkAspect declared(String value) {
        if (value == null) return null;
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "portrait" -> PORTRAIT;
            case "square" -> SQUARE;
            case "landscape" -> LANDSCAPE;
            default -> null;
        };
    }

    static ArtworkAspect fromDimensions(Double width, Double height) {
        if (width == null || height == null || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0) return null;
        double ratio = width / height;
        if (ratio > SQUARE_LIMIT) return LANDSCAPE;
        if (ratio < 1 / SQUARE_LIMIT) return PORTRAIT;
        return SQUARE;
    }

    static ArtworkAspect fromMarkup(Element slot) {
        String width = slot.attr("width"), height = slot.attr("height"), aspectRatio = "";
        for (String declaration : slot.attr("style").split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String value = declaration.substring(colon + 1).replaceFirst("(?i)\\s*!\\s*important\\s*$", "").strip();
            switch (declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT)) {
                case "width" -> width = value;
                case "height" -> height = value;
                case "aspect-ratio" -> aspectRatio = value;
                default -> { }
            }
        }
        ArtworkAspect dimensions = fromDimensions(pixels(width), pixels(height));
        if (dimensions != null) return dimensions;
        // Responsive CSS often supplies just width plus an explicit aspect ratio.
        String[] ratio = aspectRatio.strip().split("/", -1);
        if (ratio.length == 1) return fromDimensions(number(ratio[0]), 1d);
        return ratio.length == 2 ? fromDimensions(number(ratio[0]), number(ratio[1])) : null;
    }

    private static Double pixels(String value) {
        String size = value.strip().toLowerCase(Locale.ROOT);
        String numeric = size.endsWith("px") ? size.substring(0, size.length() - 2) : size;
        return number(numeric);
    }

    private static Double number(String value) {
        try {
            return Double.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
