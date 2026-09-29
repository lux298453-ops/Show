package com.wireforge.service;

import com.wireforge.entity.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按已经缩放到画布上的方框，把同一次点击收成一组。不调用模型。
 * 先匹配到的规则优先，一个元素只进一组。
 */
public final class ClickGrouper {

    static final double NEAR = 12;
    static final double BOTTOM_BAND = 0.82;

    private ClickGrouper() {
    }

    public static final class Group {
        public final Element anchor;
        public final List<Element> members;
        /** btn / tab / entry */
        public final String kind;

        public Group(Element anchor, List<Element> members, String kind) {
            this.anchor = anchor;
            this.members = members;
            this.kind = kind;
        }
    }

    public static List<Group> group(List<Element> elements, double canvasH, long pageId) {
        List<Element> pool = new ArrayList<>();
        for (Element e : elements) {
            if (e.getId() == null || skip(e)) continue;
            pool.add(e);
        }
        Set<Long> used = new HashSet<>();
        List<Group> out = new ArrayList<>();

        List<Element> buttons = new ArrayList<>();
        for (Element e : pool) {
            if ("button".equals(typeOf(e))) buttons.add(e);
        }
        buttons.sort(Comparator.comparingDouble(ClickGrouper::area).reversed());
        for (Element btn : buttons) {
            if (!used.add(btn.getId())) continue;
            List<Element> members = new ArrayList<>();
            for (Element e : pool) {
                if (e.getId().equals(btn.getId()) || used.contains(e.getId())) continue;
                String t = typeOf(e);
                if (!"text".equals(t) && !"badge".equals(t) && !"icon".equals(t)) continue;
                if (!containsCenter(btn, e)) continue;
                if (area(e) >= area(btn) * 0.85) continue;
                members.add(e);
            }
            if (members.isEmpty()) {
                used.remove(btn.getId());
                continue;
            }
            for (Element m : members) used.add(m.getId());
            out.add(new Group(btn, members, "btn"));
        }

        double bandTop = canvasH * BOTTOM_BAND;
        List<Element> band = new ArrayList<>();
        for (Element e : pool) {
            if (used.contains(e.getId())) continue;
            if (centerY(e) < bandTop) continue;
            if (!navCandidate(e)) continue;
            band.add(e);
        }
        band.sort(Comparator.comparingDouble(ClickGrouper::centerX));
        for (List<Element> col : clusterColumns(band)) {
            List<Element> icons = new ArrayList<>();
            List<Element> labels = new ArrayList<>();
            for (Element e : col) {
                if ("icon".equals(typeOf(e))) icons.add(e);
                else labels.add(e);
            }
            icons.sort(Comparator.comparingDouble(ClickGrouper::centerY));
            Set<Long> paired = new HashSet<>();
            for (Element icon : icons) {
                if (used.contains(icon.getId())) continue;
                Element best = nearestLabel(icon, labels, paired);
                List<Element> members = new ArrayList<>();
                if (best != null) {
                    members.add(best);
                    paired.add(best.getId());
                    used.add(best.getId());
                }
                used.add(icon.getId());
                out.add(new Group(icon, members, "tab"));
            }
            for (Element text : labels) {
                if (paired.contains(text.getId()) || used.contains(text.getId())) continue;
                if (labelOf(text).length() == 0 || labelOf(text).length() > 6) continue;
                if (y(text) + h(text) < canvasH - 36) continue;
                used.add(text.getId());
                out.add(new Group(text, List.of(), "tab"));
            }
        }

        List<Element> icons = new ArrayList<>();
        List<Element> texts = new ArrayList<>();
        for (Element e : pool) {
            if (used.contains(e.getId())) continue;
            if ("icon".equals(typeOf(e))) icons.add(e);
            else if ("text".equals(typeOf(e))) texts.add(e);
        }
        List<double[]> pairs = new ArrayList<>();
        for (int i = 0; i < icons.size(); i++) {
            for (int j = 0; j < texts.size(); j++) {
                double gap = adjacencyGap(icons.get(i), texts.get(j));
                if (gap <= NEAR) pairs.add(new double[]{gap, i, j});
            }
        }
        pairs.sort(Comparator.comparingDouble(a -> a[0]));
        for (double[] pair : pairs) {
            Element icon = icons.get((int) pair[1]);
            Element text = texts.get((int) pair[2]);
            if (used.contains(icon.getId()) || used.contains(text.getId())) continue;
            Element anchor = preferAnchor(icon, text);
            Element member = anchor == icon ? text : icon;
            used.add(anchor.getId());
            used.add(member.getId());
            out.add(new Group(anchor, List.of(member), "entry"));
        }
        return out;
    }

    public static String keyFor(long pageId, Group group, int index) {
        if ("tab".equals(group.kind)) return "p" + pageId + "-tab-" + index;
        if ("btn".equals(group.kind)) return "p" + pageId + "-btn-" + group.anchor.getId();
        return "p" + pageId + "-entry-" + group.anchor.getId();
    }

    static boolean skip(Element e) {
        String t = typeOf(e);
        return t.equals("input") || t.equals("textarea") || t.equals("search")
                || t.equals("divider") || t.equals("line") || t.equals("progress")
                || t.equals("effect") || t.equals("background");
    }

    private static boolean navCandidate(Element e) {
        String t = typeOf(e);
        if ("icon".equals(t)) return true;
        if ("text".equals(t) || "badge".equals(t)) return labelOf(e).length() > 0 && labelOf(e).length() <= 8;
        return false;
    }

    /** 同一列里，图标只和上下挨着的那一行文字合成一项，不拿更上面的说明文字。 */
    private static Element nearestLabel(Element icon, List<Element> labels, Set<Long> paired) {
        Element best = null;
        double bestGap = 25;
        for (Element text : labels) {
            if (paired.contains(text.getId())) continue;
            if (!verticallyClose(icon, text)) continue;
            double gap = gapY(icon, text);
            if (gap < bestGap) {
                bestGap = gap;
                best = text;
            }
        }
        return best;
    }

    private static List<List<Element>> clusterColumns(List<Element> sorted) {
        List<List<Element>> cols = new ArrayList<>();
        for (Element e : sorted) {
            List<Element> joined = null;
            for (List<Element> col : cols) {
                for (Element other : col) {
                    if (sameColumn(e, other)) {
                        joined = col;
                        break;
                    }
                }
                if (joined != null) break;
            }
            if (joined == null) {
                List<Element> col = new ArrayList<>();
                col.add(e);
                cols.add(col);
            } else {
                joined.add(e);
            }
        }
        return cols;
    }

    private static boolean sameColumn(Element a, Element b) {
        if (overlapRatio(horizontalOverlap(a, b), Math.min(w(a), w(b))) >= 0.25) return true;
        return gapX(a, b) <= 16 && Math.abs(centerX(a) - centerX(b)) <= 40;
    }

    private static boolean verticallyClose(Element a, Element b) {
        return gapY(a, b) <= 24 || verticalOverlap(a, b) > 0;
    }

    /** 水平相邻且纵向重叠过半，或垂直相邻且横向重叠过半。不相邻返回一个大于 NEAR 的数。 */
    static double adjacencyGap(Element a, Element b) {
        double vOverlap = overlapRatio(verticalOverlap(a, b), Math.min(h(a), h(b)));
        double hOverlap = overlapRatio(horizontalOverlap(a, b), Math.min(w(a), w(b)));
        if (gapX(a, b) <= NEAR && vOverlap >= 0.5) return gapX(a, b);
        if (gapY(a, b) <= NEAR && hOverlap >= 0.5) return gapY(a, b);
        return NEAR + 1;
    }

    private static Element preferAnchor(Element icon, Element text) {
        if ("button".equals(typeOf(icon)) || "button".equals(typeOf(text))) {
            return "button".equals(typeOf(icon)) ? icon : text;
        }
        if ("icon".equals(typeOf(icon)) || "icon".equals(typeOf(text))) {
            return "icon".equals(typeOf(icon)) ? icon : text;
        }
        return area(icon) >= area(text) ? icon : text;
    }

    private static boolean containsCenter(Element box, Element inner) {
        double cx = centerX(inner);
        double cy = centerY(inner);
        return cx >= x(box) && cx <= x(box) + w(box) && cy >= y(box) && cy <= y(box) + h(box);
    }

    private static String typeOf(Element e) {
        return e.getType() == null ? "" : e.getType().trim().toLowerCase();
    }

    private static String labelOf(Element e) {
        return e.getLabel() == null ? "" : e.getLabel().trim();
    }

    private static double x(Element e) { return e.getPositionX() == null ? 0 : e.getPositionX(); }
    private static double y(Element e) { return e.getPositionY() == null ? 0 : e.getPositionY(); }
    private static double w(Element e) { return e.getWidth() == null ? 0 : e.getWidth(); }
    private static double h(Element e) { return e.getHeight() == null ? 0 : e.getHeight(); }
    private static double area(Element e) { return Math.max(0, w(e)) * Math.max(0, h(e)); }
    private static double centerX(Element e) { return x(e) + w(e) / 2; }
    private static double centerY(Element e) { return y(e) + h(e) / 2; }

    private static double gapX(Element a, Element b) {
        double left = Math.min(x(a), x(b));
        double right = Math.max(x(a) + w(a), x(b) + w(b));
        return Math.max(0, right - left - w(a) - w(b));
    }

    private static double gapY(Element a, Element b) {
        double top = Math.min(y(a), y(b));
        double bottom = Math.max(y(a) + h(a), y(b) + h(b));
        return Math.max(0, bottom - top - h(a) - h(b));
    }

    private static double horizontalOverlap(Element a, Element b) {
        double left = Math.max(x(a), x(b));
        double right = Math.min(x(a) + w(a), x(b) + w(b));
        return Math.max(0, right - left);
    }

    private static double verticalOverlap(Element a, Element b) {
        double top = Math.max(y(a), y(b));
        double bottom = Math.min(y(a) + h(a), y(b) + h(b));
        return Math.max(0, bottom - top);
    }

    private static double overlapRatio(double overlap, double smaller) {
        if (smaller <= 0) return 0;
        return overlap / smaller;
    }
}
