package com.wireforge.service;

import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Single source for controls in saved HTML, standalone export and the canvas host. */
public final class PrototypeControls {
    private static final String CSS = resource("controls.css");
    private static final String JS = resource("controls.js");

    public static void apply(Document doc) {
        doc.select("[data-wf-controls-style],[data-wf-controls-runtime]").remove();
        doc.head().appendElement("style").attr("data-wf-controls-style", "1").appendChild(new DataNode(CSS));
        // Must register before the generic prototype runtime.
        doc.body().appendElement("script").attr("data-wf-controls-runtime", "1").appendChild(new DataNode(JS));
    }

    private static String resource(String name) {
        try (var in = PrototypeControls.class.getResourceAsStream("/prototype/" + name)) {
            if (in == null) throw new IllegalStateException("缺少标准控件资源: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
