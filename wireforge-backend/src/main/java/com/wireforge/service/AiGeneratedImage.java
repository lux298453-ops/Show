package com.wireforge.service;

/** A persisted image belonging to an adjustment result, not a provider's temporary URL. */
public record AiGeneratedImage(String name, String url, String kind) { }
