package com.wireforge.service;

import com.wireforge.entity.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 渲染前按格子排位置。只改这份内存里的坐标，不写回数据库。
 */
public final class LayoutNormalizer {

    private LayoutNormalizer() {
    }

    public static void apply(List<Element> elements, double canvasW, double canvasH) {
        if (elements == null || elements.size() < 2) return;
        Set<Long> navIds = new HashSet<>();
        Set<Long> buttonMemberIds = new HashSet<>();
        for (Element e : elements) {
            String key = e.getGroupKey() == null ? "" : e.getGroupKey();
            if (key.contains("-tab-")) navIds.add(e.getId());
            if (key.contains("-btn-") && "member".equals(e.getGroupRole())) buttonMemberIds.add(e.getId());
        }

        alignRows(elements, canvasW, canvasH, navIds, buttonMemberIds);
        alignIconTextColumns(elements, navIds, buttonMemberIds);
        layoutNav(elements, canvasW, canvasH);
        centerButtonMembers(elements);
        alignCardRows(elements, canvasW);
    }

    private static void alignRows(List<Element> elements, double canvasW, double canvasH,
                                  Set<Long> navIds, Set<Long> buttonMemberIds) {
        List<Element> candidates = new ArrayList<>();
        for (Element e : elements) {
            if (e.getId() != null && (navIds.contains(e.getId()) || buttonMemberIds.contains(e.getId()))) continue;
            if (ClickGrouper.skip(e)) continue;
            double h = h(e);
            double w = w(e);
            if (h <= 0 || w <= 0) continue;
            if (h > canvasH * 0.45 || w > canvasW * 0.92) continue;
            candidates.add(e);
        }
        candidates.sort(Comparator.comparingDouble(LayoutNormalizer::y));
        boolean[] used = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;
            List<Element> row = new ArrayList<>();
            row.add(candidates.get(i));
            for (int j = i + 1; j < candidates.size(); j++) {
                if (used[j]) continue;
                Element other = candidates.get(j);
                if (verticalOverlapRatio(candidates.get(i), other) < 0.5) continue;
                double hr = h(other) / Math.max(1, h(candidates.get(i)));
                if (hr < 0.45 || hr > 2.2) continue;
                row.add(other);
                used[j] = true;
            }
            if (row.size() < 2) continue;
            used[i] = true;
            List<Double> nonTextH = new ArrayList<>();
            List<Double> tops = new ArrayList<>();
            for (Element e : row) {
                tops.add(y(e));
                if (!"text".equals(typeOf(e))) nonTextH.add(h(e));
            }
            double top = median(tops);
            double rowH = nonTextH.isEmpty() ? median(row.stream().map(LayoutNormalizer::h).toList()) : median(nonTextH);
            for (Element e : row) {
                if ("text".equals(typeOf(e))) {
                    double ny = top + Math.max(0, (rowH - h(e)) / 2);
                    e.setPositionY(ny);
                } else {
                    e.setPositionY(top);
                    e.setHeight(rowH);
                }
            }
        }
    }

    /** 图标在上、文字在下：共用一条竖中线。 */
    private static void alignIconTextColumns(List<Element> elements, Set<Long> navIds, Set<Long> buttonMemberIds) {
        List<Element> icons = new ArrayList<>();
        List<Element> texts = new ArrayList<>();
        for (Element e : elements) {
            if (e.getId() != null && (navIds.contains(e.getId()) || buttonMemberIds.contains(e.getId()))) continue;
            if ("icon".equals(typeOf(e))) icons.add(e);
            else if ("text".equals(typeOf(e))) texts.add(e);
        }
        Set<Long> used = new HashSet<>();
        for (Element icon : icons) {
            Element best = null;
            double bestGap = 21;
            for (Element text : texts) {
                if (used.contains(text.getId())) continue;
                if (horizontalOverlapRatio(icon, text) < 0.5) continue;
                double gap = gapY(icon, text);
                if (gap <= 0 || gap > 20) continue;
                if (gap < bestGap) {
                    bestGap = gap;
                    best = text;
                }
            }
            if (best == null) continue;
            used.add(best.getId());
            double mid = (centerX(icon) + centerX(best)) / 2;
            icon.setPositionX(mid - w(icon) / 2);
            best.setPositionX(mid - w(best) / 2);
        }
    }

    private static void layoutNav(List<Element> elements, double canvasW, double canvasH) {
        List<String> keys = new ArrayList<>();
        for (Element e : elements) {
            String key = e.getGroupKey() == null ? "" : e.getGroupKey();
            if (key.contains("-tab-") && !keys.contains(key)) keys.add(key);
        }
        if (keys.size() < 2 || canvasW <= 0) return;
        keys.sort(Comparator.comparingDouble(k -> {
            double sum = 0;
            int n = 0;
            for (Element e : elements) {
                if (k.equals(e.getGroupKey())) {
                    sum += centerX(e);
                    n++;
                }
            }
            return n == 0 ? 0 : sum / n;
        }));
        double top = Double.MAX_VALUE;
        double bottom = 0;
        for (Element e : elements) {
            String key = e.getGroupKey() == null ? "" : e.getGroupKey();
            if (!key.contains("-tab-")) continue;
            top = Math.min(top, y(e));
            bottom = Math.max(bottom, y(e) + h(e));
        }
        if (top == Double.MAX_VALUE) return;
        bottom = Math.max(bottom, Math.min(canvasH, top + 64));
        double barH = Math.max(36, bottom - top);
        double cellW = canvasW / keys.size();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Element icon = null;
            Element text = null;
            for (Element e : elements) {
                if (!key.equals(e.getGroupKey())) continue;
                if ("icon".equals(typeOf(e))) icon = e;
                else if ("text".equals(typeOf(e))) text = e;
                else if ("anchor".equals(e.getGroupRole()) && icon == null) icon = e;
            }
            double cellX = i * cellW;
            if (icon != null && text != null) {
                double stackH = h(icon) + 4 + h(text);
                double stackTop = top + Math.max(0, (barH - stackH) / 2);
                icon.setPositionX(cellX + (cellW - w(icon)) / 2);
                icon.setPositionY(stackTop);
                text.setPositionX(cellX + (cellW - w(text)) / 2);
                text.setPositionY(stackTop + h(icon) + 4);
            } else if (icon != null) {
                icon.setPositionX(cellX + (cellW - w(icon)) / 2);
                icon.setPositionY(top + Math.max(0, (barH - h(icon)) / 2));
            } else if (text != null) {
                text.setPositionX(cellX + (cellW - w(text)) / 2);
                text.setPositionY(top + Math.max(0, (barH - h(text)) / 2));
            }
        }
    }

    private static void centerButtonMembers(List<Element> elements) {
        List<String> keys = new ArrayList<>();
        for (Element e : elements) {
            String key = e.getGroupKey() == null ? "" : e.getGroupKey();
            if (key.contains("-btn-") && !keys.contains(key)) keys.add(key);
        }
        for (String key : keys) {
            Element btn = null;
            List<Element> members = new ArrayList<>();
            for (Element e : elements) {
                if (!key.equals(e.getGroupKey())) continue;
                if ("anchor".equals(e.getGroupRole())) btn = e;
                else members.add(e);
            }
            if (btn == null || members.isEmpty()) continue;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Element m : members) {
                minX = Math.min(minX, x(m));
                minY = Math.min(minY, y(m));
                maxX = Math.max(maxX, x(m) + w(m));
                maxY = Math.max(maxY, y(m) + h(m));
            }
            double clusterCx = (minX + maxX) / 2;
            double clusterCy = (minY + maxY) / 2;
            double dx = centerX(btn) - clusterCx;
            double dy = centerY(btn) - clusterCy;
            for (Element m : members) {
                m.setPositionX(x(m) + dx);
                m.setPositionY(y(m) + dy);
            }
        }
    }

    /** 同一行、宽度接近的卡片：宽高取中位数，间距取相邻间隙的中位数。 */
    private static void alignCardRows(List<Element> elements, double canvasW) {
        List<Element> cards = new ArrayList<>();
        for (Element e : elements) {
            if (!"container".equals(typeOf(e))) continue;
            if (w(e) <= 30 || h(e) <= 30) continue;
            if (w(e) > canvasW * 0.92) continue;
            cards.add(e);
        }
        cards.sort(Comparator.comparingDouble(LayoutNormalizer::y));
        boolean[] used = new boolean[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            if (used[i]) continue;
            List<Element> row = new ArrayList<>();
            row.add(cards.get(i));
            for (int j = i + 1; j < cards.size(); j++) {
                if (used[j]) continue;
                if (verticalOverlapRatio(cards.get(i), cards.get(j)) < 0.5) continue;
                row.add(cards.get(j));
            }
            if (row.size() < 2) continue;
            row.sort(Comparator.comparingDouble(LayoutNormalizer::x));
            double medW = median(row.stream().map(LayoutNormalizer::w).toList());
            boolean widthClose = true;
            for (Element c : row) {
                if (Math.abs(w(c) - medW) > medW * 0.35) widthClose = false;
            }
            if (!widthClose) continue;
            for (int k = 0; k < row.size(); k++) {
                if (row.get(k) != cards.get(i)) {
                    int idx = cards.indexOf(row.get(k));
                    if (idx >= 0) used[idx] = true;
                }
            }
            used[i] = true;
            double medH = median(row.stream().map(LayoutNormalizer::h).toList());
            double medY = median(row.stream().map(LayoutNormalizer::y).toList());
            List<Double> gaps = new ArrayList<>();
            for (int k = 0; k < row.size() - 1; k++) {
                double gap = x(row.get(k + 1)) - (x(row.get(k)) + w(row.get(k)));
                if (gap >= 0 && gap < medW) gaps.add(gap);
            }
            double gap = gaps.isEmpty() ? 8 : median(gaps);
            double cursor = x(row.get(0));
            for (Element c : row) {
                c.setPositionX(cursor);
                c.setPositionY(medY);
                c.setWidth(medW);
                c.setHeight(medH);
                cursor += medW + gap;
            }
        }
    }

    private static String typeOf(Element e) {
        return e.getType() == null ? "" : e.getType().trim().toLowerCase();
    }

    private static double x(Element e) { return e.getPositionX() == null ? 0 : e.getPositionX(); }
    private static double y(Element e) { return e.getPositionY() == null ? 0 : e.getPositionY(); }
    private static double w(Element e) { return e.getWidth() == null ? 0 : e.getWidth(); }
    private static double h(Element e) { return e.getHeight() == null ? 0 : e.getHeight(); }
    private static double centerX(Element e) { return x(e) + w(e) / 2; }
    private static double centerY(Element e) { return y(e) + h(e) / 2; }

    private static double verticalOverlapRatio(Element a, Element b) {
        double top = Math.max(y(a), y(b));
        double bottom = Math.min(y(a) + h(a), y(b) + h(b));
        double overlap = Math.max(0, bottom - top);
        double smaller = Math.min(h(a), h(b));
        return smaller <= 0 ? 0 : overlap / smaller;
    }

    private static double horizontalOverlapRatio(Element a, Element b) {
        double left = Math.max(x(a), x(b));
        double right = Math.min(x(a) + w(a), x(b) + w(b));
        double overlap = Math.max(0, right - left);
        double smaller = Math.min(w(a), w(b));
        return smaller <= 0 ? 0 : overlap / smaller;
    }

    private static double gapY(Element a, Element b) {
        double top = Math.min(y(a), y(b));
        double bottom = Math.max(y(a) + h(a), y(b) + h(b));
        return Math.max(0, bottom - top - h(a) - h(b));
    }

    private static double median(List<Double> vs) {
        if (vs.isEmpty()) return 0;
        List<Double> s = new ArrayList<>(vs);
        s.sort(Double::compare);
        return s.get(s.size() / 2);
    }
}
