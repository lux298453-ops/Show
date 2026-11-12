package com.wireforge.common;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Accept static SVG artwork without rewriting its geometry or styling. Fail closed on active content. */
public final class SvgAssetValidator {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final String INVALID = "SVG 含有不安全或不支持的内容，请导出为静态 SVG 或 PNG 后重试";
    private static final Set<String> ELEMENTS = Set.of(
            "svg", "g", "defs", "symbol", "use", "title", "desc", "metadata", "style",
            "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "image",
            "text", "tspan", "textPath", "linearGradient", "radialGradient", "stop",
            "clipPath", "mask", "pattern", "marker", "filter", "feBlend", "feColorMatrix",
            "feComponentTransfer", "feComposite", "feConvolveMatrix", "feDiffuseLighting",
            "feDisplacementMap", "feDistantLight", "feDropShadow", "feFlood", "feFuncA",
            "feFuncB", "feFuncG", "feFuncR", "feGaussianBlur", "feMerge", "feMergeNode",
            "feMorphology", "feOffset", "fePointLight", "feSpecularLighting", "feSpotLight",
            "feTile", "feTurbulence");
    private static final Pattern FRAGMENT = Pattern.compile("#[-\\p{L}\\p{N}_][\\p{L}\\p{N}_.:-]*");
    private static final Pattern RASTER_DATA = Pattern.compile(
            "data:image/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/=\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL_URL = Pattern.compile(
            "url\\(\\s*(['\"]?)#[-\\p{L}\\p{N}_][\\p{L}\\p{N}_.:-]*\\1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_START = Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_CSS = Pattern.compile(
            "expression\\s*\\(|-moz-binding|behavior\\s*:|javascript\\s*:|vbscript\\s*:", Pattern.CASE_INSENSITIVE);

    private SvgAssetValidator() { }

    public static boolean isSvg(String filename, String contentType) {
        return (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".svg"))
                || (contentType != null && contentType.split(";", 2)[0].trim().equalsIgnoreCase("image/svg+xml"));
    }

    public static void validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(INVALID);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "128");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External XML is disabled"); });
            var document = builder.parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();
            if (root == null || !"svg".equals(root.getLocalName()) || !SVG_NS.equals(root.getNamespaceURI())) {
                throw new IllegalArgumentException(INVALID);
            }
            inspect(document, 0, new int[1]);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("SVG 安全校验器初始化失败", e);
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    private static void inspect(Node node, int depth, int[] count) {
        if (depth > 128 || ++count[0] > 100_000) throw new IllegalArgumentException(INVALID);
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE, Node.TEXT_NODE, Node.CDATA_SECTION_NODE, Node.COMMENT_NODE -> { }
            case Node.ELEMENT_NODE -> inspectElement((Element) node);
            // Includes xml-stylesheet processing instructions and entity/document-type nodes.
            default -> throw new IllegalArgumentException(INVALID);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            inspect(child, depth + 1, count);
        }
    }

    private static void inspectElement(Element element) {
        if (!SVG_NS.equals(element.getNamespaceURI()) || !ELEMENTS.contains(element.getLocalName())) {
            throw new IllegalArgumentException(INVALID);
        }
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName().toLowerCase(Locale.ROOT);
            String namespace = attribute.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)) continue;
            if (name.startsWith("on") || "base".equals(name)
                    || (namespace != null && !XMLConstants.XML_NS_URI.equals(namespace) && !XLINK_NS.equals(namespace))) {
                throw new IllegalArgumentException(INVALID);
            }
            if ("href".equals(name)) {
                String value = attribute.getNodeValue().trim();
                boolean embeddedRaster = "image".equals(element.getLocalName()) && RASTER_DATA.matcher(value).matches();
                if (!FRAGMENT.matcher(value).matches() && !embeddedRaster) throw new IllegalArgumentException(INVALID);
            } else {
                inspectCss(attribute.getNodeValue());
            }
        }
        if ("style".equals(element.getLocalName())) inspectCss(element.getTextContent());
    }

    private static void inspectCss(String value) {
        // CSS escapes/comments must not disguise imports, bindings or external url() references.
        String css = value.replaceAll("(?s)/\\*.*?\\*/", "");
        if (css.indexOf('\\') >= 0 || css.indexOf('@') >= 0 || css.contains("/*") || UNSAFE_CSS.matcher(css).find()
                || URL_START.matcher(LOCAL_URL.matcher(css).replaceAll("")).find()) {
            throw new IllegalArgumentException(INVALID);
        }
    }
}
