package com.wireforge.service;

import com.wireforge.entity.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 渲染前按格子排位置。只改这份内存里的坐标，不写回数据库。
 */
public final class LayoutNormalizer {

    private LayoutNormalizer() {
    }

    public static void apply(List<Element> elements, double canvasW, double canvasH) {
        if (elements == null || elements.size() < 2 || canvasW <= 0 || canvasH <= 0) return;
        List<Entry> entries = entries(elements, canvasW, canvasH);
        Set<Entry> placed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        alignRepeated(entries, elements, placed, true, canvasW, canvasH);
        alignRepeated(entries, elements, placed, false, canvasW, canvasH);
        layoutNav(elements, canvasW, canvasH);
    }

    /** 组件内部已经重新排版，只在原来同一排的卡片之间规整外框，不移动识别元素。 */
    static void normalizeCardFrames(List<Element> frames, double canvasW, double canvasH) {
        List<Element> full=frames.stream().filter(e -> w(e)>=48 && x(e)>=0 && x(e)+w(e)<=canvasW+2)
                .sorted(Comparator.comparingDouble(LayoutNormalizer::y)).toList();
        Set<Element> used=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Element seed:full) {
            if (used.contains(seed)) continue;
            List<Element> row=new ArrayList<>();
            for (Element other:full) {
                if (!used.contains(other) && Math.abs(y(other)-y(seed))<=12
                        && Math.abs(w(other)/w(seed)-1)<=.2 && Math.abs(h(other)/h(seed)-1)<=.4) row.add(other);
            }
            if (row.size()<2) continue;
            row.sort(Comparator.comparingDouble(LayoutNormalizer::x));
            double width=median(row.stream().map(LayoutNormalizer::w).toList());
            double top=median(row.stream().map(LayoutNormalizer::y).toList());
            double height=row.stream().mapToDouble(LayoutNormalizer::h).max().orElse(h(seed));
            double left=x(row.get(0));
            Element last=row.get(row.size()-1);
            double gap=(x(last)+w(last)-left-row.size()*width)/(row.size()-1);
            double nextTop=frames.stream().filter(e -> !row.contains(e) && y(e)>top+16)
                    .mapToDouble(LayoutNormalizer::y).min().orElse(canvasH);
            if (gap<6 || gap>width*.8 || top+height>Math.min(canvasH,nextTop-6)) continue;
            boolean safe=true;
            for (int i=0;i<row.size();i++) if (Math.abs(left+i*(width+gap)-x(row.get(i)))>12) safe=false;
            if (!safe) continue;
            for (int i=0;i<row.size();i++) {
                Element e=row.get(i);e.setPositionX(left+i*(width+gap));e.setPositionY(top);e.setWidth(width);e.setHeight(height);used.add(e);
            }
        }
    }

    private static void layoutNav(List<Element> elements, double canvasW, double canvasH) {
        List<String> keys = new ArrayList<>();
        for (Element e : elements) {
            String key = navKey(e, canvasH);
            if (key.contains("-tab-") && !keys.contains(key)) keys.add(key);
        }
        keys.removeIf(key -> !isNavigationGroup(elements.stream().filter(e -> key.equals(navKey(e,canvasH))).toList(),elements,canvasH));
        if (keys.size() < 2 || canvasW <= 0) return;
        keys.sort(Comparator.comparingDouble(k -> {
            double sum = 0;
            int n = 0;
            for (Element e : elements) {
                if (k.equals(navKey(e, canvasH))) {
                    sum += centerX(e);
                    n++;
                }
            }
            return n == 0 ? 0 : sum / n;
        }));
        double top = Double.MAX_VALUE;
        double bottom = 0;
        for (Element e : elements) {
            String key = navKey(e, canvasH);
            if (!keys.contains(key)) continue;
            top = Math.min(top, y(e));
            bottom = Math.max(bottom, y(e) + h(e));
        }
        if (top == Double.MAX_VALUE) return;
        if (top < canvasH * 0.78 || bottom - top > canvasH * 0.2) return;
        bottom = Math.min(canvasH, bottom);
        top = Math.max(top, canvasH - Math.min(72, canvasH * .1));
        double barH = Math.max(36, bottom - top);
        double cellW = canvasW / keys.size();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Element icon = null;
            Element text = null;
            for (Element e : elements) {
                if (!key.equals(navKey(e, canvasH))) continue;
                if ("icon".equals(typeOf(e))) icon = e;
                else if ("button".equals(typeOf(e))) icon = e;
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
                if ("button".equals(typeOf(icon))) {
                    icon.setPositionX(cellX);
                    icon.setPositionY(top);
                    icon.setWidth(cellW);
                    icon.setHeight(barH);
                    continue;
                }
                icon.setPositionX(cellX + (cellW - w(icon)) / 2);
                icon.setPositionY(top + Math.max(0, (barH - h(icon)) / 2));
            } else if (text != null) {
                text.setPositionX(cellX + (cellW - w(text)) / 2);
                text.setPositionY(top + Math.max(0, (barH - h(text)) / 2));
            }
        }
    }

    static boolean isNavigationItem(Element e, double canvasH) {
        String label = e.getLabel() == null ? "" : e.getLabel().trim();
        return label.matches("家园|首页|收集|扭蛋|商城|我的|发现|消息|个人中心|收藏|分类") && y(e)>=canvasH*.88;
    }

    /** 自动分组键也可能来自卡片角标，只有底部独立的图文导航才允许等分。 */
    static boolean isNavigationGroup(List<Element> parts, List<Element> all, double canvasH) {
        if (parts.isEmpty() || parts.stream().anyMatch(e -> y(e)<canvasH*.88 || h(e)>80)) return false;
        String label=parts.stream().filter(e -> "text".equals(typeOf(e)) && e.getLabel()!=null && !e.getLabel().isBlank())
                .map(e -> e.getLabel().trim()).findFirst().orElseGet(() -> parts.stream().map(e -> e.getLabel()==null?"":e.getLabel().trim())
                        .filter(s -> !s.isBlank()).findFirst().orElse(""));
        if (label.isBlank() || label.length()>8 || label.matches(".*(锁定|启用|应用|左滑|右滑|喂养|领取|完成|碎片).*|[+\\-\\d\\s.,%/:：]+")) return false;
        boolean named=parts.stream().anyMatch(e -> isNavigationItem(e,canvasH));
        if (!named && !(parts.stream().anyMatch(e -> "text".equals(typeOf(e)))
                && parts.stream().anyMatch(e -> "icon".equals(typeOf(e)) || "button".equals(typeOf(e))))) return false;
        return parts.stream().noneMatch(e -> all.stream().anyMatch(panel -> !parts.contains(panel) && "container".equals(typeOf(panel))
                && w(panel)<180 && h(panel)>=40 && contains(box(panel),box(e),2)));
    }

    private static String navKey(Element e, double canvasH) {
        if (key(e).contains("-tab-")) return key(e);
        return isNavigationItem(e, canvasH) ? "render-tab-" + e.getId() : "";
    }


    private record Entry(Element anchor, List<Element> parts, Element panel, String family) {}
    private record Box(double x, double y, double w, double h) {
        double right() { return x+w; }
        double bottom() { return y+h; }
        double cx() { return x+w/2; }
        double cy() { return y+h/2; }
    }

    /** 图标语义和排版共用关联；在布局调整之前调用，拒绝数字及跨容器配对。 */
    static Element associatedLabel(Element icon, List<Element> all) {
        Element grouped = nearestLabel(icon, all, true);
        return grouped != null ? grouped : nearestLabel(icon, all, false);
    }

    private static Element nearestLabel(Element icon, List<Element> all, boolean groupOnly) {
        Element best=null, host=panel(icon,all);
        double score=Double.MAX_VALUE, second=Double.MAX_VALUE;
        for (Element text:all) {
            if (text==icon || !("text".equals(typeOf(text)) || "badge".equals(typeOf(text))) || !shortLabel(text.getLabel())) continue;
            boolean same=!key(icon).isEmpty() && key(icon).equals(key(text));
            if (groupOnly && !same || !same && !key(text).isEmpty() || host!=panel(text,all)) continue;
            Box a=box(icon), b=box(text);
            double down=b.y-a.bottom(), right=b.x-a.right();
            boolean below=down>=-4 && down<=Math.min(28,a.h*.8) && Math.abs(a.cx()-b.cx())<=Math.max(10,Math.min(a.w,b.w)*.45);
            boolean side=right>=-4 && right<=Math.min(24,a.w*.8) && Math.abs(a.cy()-b.cy())<=Math.max(6,Math.min(a.h,b.h)*.5);
            boolean inside=same && contains(a,b,3);
            if (!below && !side && !inside) continue;
            double distance=inside?0:below?Math.max(0,down)+Math.abs(a.cx()-b.cx()):Math.max(0,right)+Math.abs(a.cy()-b.cy());
            if (distance<score) {second=score;score=distance;best=text;} else second=Math.min(second,distance);
        }
        return second-score<2?null:best;
    }

    private static boolean shortLabel(String label) {
        if (label==null) return false;
        String s=label.trim();
        return !s.isEmpty() && s.length()<=12 && !s.contains("\n") && !s.contains("\\n")
                && !s.matches("[+\\-\\d\\s.,%/:：]+") && !s.matches(".*(底板|容器|占位|背景|区域|插画).*");
    }

    private static List<Entry> entries(List<Element> all, double cw, double ch) {
        List<Entry> result=new ArrayList<>();
        Set<Element> claimed=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Element e:all) {
            String t=typeOf(e), label=e.getLabel()==null?"":e.getLabel();
            if (!t.matches("button|icon|image") || isNavigationItem(e, ch) || "member".equals(e.getGroupRole())) continue;
            if (w(e)<18 || h(e)<18 || w(e)>Math.min(130,cw*.36) || h(e)>Math.min(110,ch*.15)) continue;
            if (label.startsWith("+") || label.contains("\n") || label.contains("\\n")
                    || label.matches(".*(气泡|领取奖励|送个礼物|下载完成).*") || label.matches("[\\d+%.,/ ]+")) continue;
            Element text=associatedLabel(e,all);
            if (t.equals("image") && text==null && !shortLabel(label)) continue;
            List<Element> parts=new ArrayList<>();parts.add(e);
            if (text!=null && !claimed.contains(text)) {parts.add(text);claimed.add(text);}
            for (Element m:all) {
                if (m==e || parts.contains(m) || claimed.contains(m)) continue;
                boolean member=!key(e).isEmpty() && key(e).equals(key(m));
                boolean badge=typeOf(m).equals("badge") && key(m).isEmpty() && w(m)<=26 && h(m)<=24
                        && Math.abs(x(m)-(x(e)+w(e)-w(m)/2))<=12 && Math.abs(y(m)-y(e))<=10;
                if ((member||badge) && contains(new Box(x(e)-20,y(e)-12,w(e)+40,h(e)+48),box(m),0)) {
                    parts.add(m);claimed.add(m);
                }
            }
            String family=(t.equals("button")?"button":"illustrated")+(w(e)/h(e)>1.55?"-wide":"-square");
            result.add(new Entry(e,parts,panel(e,all),family));
        }
        return result;
    }

    private static void alignRepeated(List<Entry> entries,List<Element> all,Set<Entry> placed,boolean vertical,double cw,double ch) {
        List<Entry> sorted=new ArrayList<>(entries);
        sorted.sort(Comparator.comparingDouble(e->vertical?y(e.anchor):x(e.anchor)));
        for (Entry seed:sorted) {
            if (placed.contains(seed)) continue;
            List<Entry> group=new ArrayList<>();group.add(seed);
            for (Entry other:sorted) {
                if (other==seed || placed.contains(other) || other.panel!=seed.panel || !other.family.equals(seed.family)) continue;
                if (Math.abs(w(other.anchor)/w(seed.anchor)-1)>.24 || Math.abs(h(other.anchor)/h(seed.anchor)-1)>.24) continue;
                double cross=vertical?Math.abs(centerX(other.anchor)-centerX(seed.anchor)):Math.abs(centerY(other.anchor)-centerY(seed.anchor));
                if (cross<=Math.min(12,(vertical?w(seed.anchor):h(seed.anchor))*.22)) group.add(other);
            }
            group.sort(Comparator.comparingDouble(e->vertical?y(e.anchor):x(e.anchor)));
            List<Entry> run=new ArrayList<>();
            for (Entry e:group) {
                if (!run.isEmpty()) {
                    Box prev=bounds(run.get(run.size()-1).parts),cur=bounds(e.parts);
                    double gap=vertical?cur.y-prev.bottom():cur.x-prev.right();
                    if (gap < -5 || gap>Math.max(32,(vertical?prev.h:prev.w)*1.25)) {
                        if (normalize(run,all,vertical,cw,ch)) placed.addAll(run);
                        run=new ArrayList<>();
                    }
                }
                run.add(e);
            }
            if (normalize(run,all,vertical,cw,ch)) placed.addAll(run);
        }
    }

    private static boolean normalize(List<Entry> group,List<Element> all,boolean vertical,double cw,double ch) {
        if (group.size()<3) return false;
        double width=median(group.stream().map(e->w(e.anchor)).toList()), height=median(group.stream().map(e->h(e.anchor)).toList());
        double cross=median(group.stream().map(e->vertical?centerX(e.anchor):centerY(e.anchor)).toList());
        Box first=box(group.get(0).anchor), last=box(group.get(group.size()-1).anchor);
        double start=vertical?first.y:first.x, span=vertical?last.bottom()-start:last.right()-start;
        double size=vertical?height:width, gap=(span-group.size()*size)/(group.size()-1);
        if (gap<5 || gap>size*1.6) return false;
        java.util.Map<Element,Box> proposed=new java.util.IdentityHashMap<>();
        for (int i=0;i<group.size();i++) {
            Entry entry=group.get(i); Element anchor=entry.anchor;
            double nx=vertical?cross-width/2:start+i*(size+gap), ny=vertical?start+i*(size+gap):cross-height/2;
            if (Math.abs(nx-x(anchor))>Math.min(20,width*.4) || Math.abs(ny-y(anchor))>Math.min(24,height*.45)) return false;
            proposed.put(anchor,new Box(nx,ny,width,height));
            double dx=nx+width/2-centerX(anchor), dy=ny+height/2-centerY(anchor);
            for (Element part:entry.parts) if (part!=anchor) proposed.put(part,new Box(x(part)+dx,y(part)+dy,w(part),h(part)));
        }
        if (!safe(proposed,all,cw,ch)) return false;
        proposed.forEach((e,b)->{e.setPositionX(b.x);e.setPositionY(b.y);e.setWidth(b.w);e.setHeight(b.h);});
        return true;
    }

    private static boolean safe(java.util.Map<Element,Box> proposed,List<Element> all,double cw,double ch) {
        for (var change:proposed.entrySet()) {
            Element e=change.getKey(); Box b=change.getValue();
            if (b.x<0 || b.y<0 || b.right()>cw || b.bottom()>ch) return false;
            Element host=panel(e,all);
            if (host!=null && !contains(box(host),b,3)) return false;
            for (Element other:all) {
                if (other==e || !key(e).isEmpty() && key(e).equals(key(other))) continue;
                if (typeOf(other).matches("background|effect|container|list|modal|image|banner")) continue;
                Box target=proposed.getOrDefault(other,box(other));
                double oldOverlap=overlap(box(e),box(other)), newOverlap=overlap(b,target);
                if (newOverlap>oldOverlap+8 && newOverlap>Math.min(b.w*b.h,target.w*target.h)*.08) return false;
            }
        }
        return true;
    }

    private static Element panel(Element e,List<Element> all) {
        Element result=null; double area=Double.MAX_VALUE;
        for (Element c:all) {
            if (c==e || !typeOf(c).matches("container|list|modal")) continue;
            double a=w(c)*h(c);
            if (a<=w(e)*h(e)*1.4 || a>=area) continue;
            if (contains(box(c),box(e),3)) {result=c;area=a;}
        }
        return result;
    }
    private static Box bounds(List<Element> parts) {
        double left=Double.MAX_VALUE,top=Double.MAX_VALUE,right=0,bottom=0;
        for (Element e:parts) {left=Math.min(left,x(e));top=Math.min(top,y(e));right=Math.max(right,x(e)+w(e));bottom=Math.max(bottom,y(e)+h(e));}
        return new Box(left,top,right-left,bottom-top);
    }
    private static boolean contains(Box a,Box b,double pad) {return b.x>=a.x-pad && b.y>=a.y-pad && b.right()<=a.right()+pad && b.bottom()<=a.bottom()+pad;}
    private static double overlap(Box a,Box b) {return Math.max(0,Math.min(a.right(),b.right())-Math.max(a.x,b.x))*Math.max(0,Math.min(a.bottom(),b.bottom())-Math.max(a.y,b.y));}
    private static Box box(Element e) {return new Box(x(e),y(e),w(e),h(e));}
    private static String key(Element e) {return e.getGroupKey()==null?"":e.getGroupKey();}

    private static String typeOf(Element e) {
        return e.getType() == null ? "" : e.getType().trim().toLowerCase();
    }

    private static double x(Element e) { return e.getPositionX() == null ? 0 : e.getPositionX(); }
    private static double y(Element e) { return e.getPositionY() == null ? 0 : e.getPositionY(); }
    private static double w(Element e) { return e.getWidth() == null ? 0 : e.getWidth(); }
    private static double h(Element e) { return e.getHeight() == null ? 0 : e.getHeight(); }
    private static double centerX(Element e) { return x(e) + w(e) / 2; }
    private static double centerY(Element e) { return y(e) + h(e) / 2; }

    private static double median(List<Double> vs) {
        if (vs.isEmpty()) return 0;
        List<Double> s = new ArrayList<>(vs);
        s.sort(Double::compare);
        int mid = s.size() / 2;
        return s.size() % 2 == 0 ? (s.get(mid - 1) + s.get(mid)) / 2 : s.get(mid);
    }
}
