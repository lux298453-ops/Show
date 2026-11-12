package com.wireforge.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SvgAssetValidatorTest {
    private static byte[] svg(String body) {
        return ("<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'>"
                + body + "</svg>").getBytes(StandardCharsets.UTF_8);
    }

    static Stream<String> activeContent() {
        return Stream.of(
                "<script>window.__svgProbe=1</script>",
                "<g onload='window.__svgProbe=1'/>",
                "<image href='#safe' onerror='window.__svgProbe=1'/>",
                "<g onLoad='window.__svgProbe=1'/>",
                "<foreignObject><div xmlns='http://www.w3.org/1999/xhtml'>html</div></foreignObject>",
                "<script xmlns='http://www.w3.org/1999/xhtml'>window.__svgProbe=1</script>",
                "<a href='javascript:window.__svgProbe=1'><text>click</text></a>",
                "<use href='javascript:window.__svgProbe=1'/>",
                "<use xlink:href='data:image/svg+xml;base64,PHN2Zy8+'/>",
                "<use href='https://example.invalid/payload.svg#icon'/>",
                "<image href='//example.invalid/pixel.png'/>",
                "<image href='/api/projects'/>",
                "<image href='data:image/svg+xml;base64,PHN2Zy8+'/>",
                "<g xml:base='https://example.invalid/'><use href='#icon'/></g>",
                "<animate attributeName='href' values='#safe;javascript:window.__svgProbe=1'/>",
                "<set attributeName='onload' to='window.__svgProbe=1'/>",
                "<style>@import 'https://example.invalid/style.css';</style>",
                "<style>.a{fill:url(https://example.invalid/pixel)}</style>",
                "<style>.a{fill:u/**/rl(https://example.invalid/pixel)}</style>",
                "<style>.a{fill:u\\72l(https://example.invalid/pixel)}</style>",
                "<path style='width:expression(window.__svgProbe=1)'/>",
                "<path fill='url(&quot;https://example.invalid/pixel&quot;)'/>",
                "<?xml-stylesheet type='text/css' href='https://example.invalid/style.css'?>",
                "<g xmlns='http://www.w3.org/1999/xhtml'><script>window.__svgProbe=1</script></g>"
        );
    }

    @ParameterizedTest
    @MethodSource("activeContent")
    void rejectsActiveContentAndExternalReferences(String body) {
        assertThrows(IllegalArgumentException.class, () -> SvgAssetValidator.validate(svg(body)));
    }

    @Test
    void acceptsStaticShapesTextCssGradientsFiltersMasksAndEmbeddedRasterImages() {
        byte[] artwork = svg("""
                <style>.paint { fill: url('#0paint'); stroke: #888 } /* ordinary CSS */</style>
                <defs><linearGradient id="0paint"><stop stop-color="#fff"/></linearGradient>
                <filter id="shadow"><feGaussianBlur stdDeviation="2"/></filter>
                <mask id="cutout"><rect width="20" height="20" fill="white"/></mask>
                <path id="icon" d="M0 0L10 10"/></defs>
                <g mask="url(#cutout)" filter="url(#shadow)" transform="translate(2 3)">
                <use xlink:href="#icon" class="paint"/><text>中文 &amp; text</text>
                <image href="data:image/png;base64,iVBORw0KGgo=" width="10" height="10"/></g>
                """);
        assertDoesNotThrow(() -> SvgAssetValidator.validate(artwork));
    }

    @Test
    void allBundledSvgAssetsRemainSupported() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:assets/wireframe/**/*.svg");
        assertTrue(resources.length > 0);
        for (var resource : resources) {
            assertDoesNotThrow(() -> SvgAssetValidator.validate(resource.getContentAsByteArray()), resource.getFilename());
        }
    }

    @Test
    void rejectsDoctypesEntityExpansionStylesheetInstructionsAndMalformedDocuments() {
        for (String document : new String[]{
                "<!DOCTYPE svg [<!ENTITY xxe SYSTEM 'file:///does-not-exist'>]><svg xmlns='http://www.w3.org/2000/svg'>&xxe;</svg>",
                "<!DOCTYPE svg SYSTEM 'http://127.0.0.1:1/external.dtd'><svg xmlns='http://www.w3.org/2000/svg'/>",
                "<?xml-stylesheet href='https://example.invalid/x.xsl'?><svg xmlns='http://www.w3.org/2000/svg'/>",
                "<html><script>window.__svgProbe=1</script></html>", "<svg/>", "<svg", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SvgAssetValidator.validate(document.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> SvgAssetValidator.validate(svg("<g>".repeat(150) + "</g>".repeat(150))));
    }

    @Test
    void parsesXmlEncodingsInsteadOfSearchingOnlyUtf8PayloadText() {
        String document = "<?xml version='1.0' encoding='UTF-16'?><svg xmlns='http://www.w3.org/2000/svg' onload='window.__svgProbe=1'/>";
        assertThrows(IllegalArgumentException.class,
                () -> SvgAssetValidator.validate(document.getBytes(StandardCharsets.UTF_16)));
        assertTrue(SvgAssetValidator.isSvg("ICON.SVG", "application/octet-stream"));
        assertTrue(SvgAssetValidator.isSvg("unknown", "image/svg+xml;charset=utf-8"));
        assertFalse(SvgAssetValidator.isSvg("icon.png", "image/png"));
    }
}
