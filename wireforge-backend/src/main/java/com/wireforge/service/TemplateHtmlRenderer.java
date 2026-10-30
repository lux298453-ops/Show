package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性模板渲染器（零 AI）：
 * 直接按元素在画布上的坐标生成整页 HTML。视觉由两层构成——
 *   1. 颜色层：DesignColorSampler 从设计稿原图采样的真实填充色/文字色；
 *   2. 结构层：图标→预设线性图标、内容图→分类占位、文字→原文+字号自适应。
 * AI 只负责上游语义提取，这里绝不"创作"，保证输出稳定不乱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateHtmlRenderer {

    private final DesignColorSampler sampler;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${wireforge.designs-dir:designs}")
    private String designsDir;

    public String toFileUrl(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) return "";
        try {
            Path designs = Paths.get(designsDir == null || designsDir.isBlank() ? "designs" : designsDir).toAbsolutePath().normalize();
            Path file = Paths.get(absolutePath).toAbsolutePath().normalize();
            if (file.startsWith(designs)) {
                String relative = designs.relativize(file).toString().replace('\\', '/');
                return "/files/" + relative;
            }
        } catch (Exception ignored) {
        }
        String normalized = absolutePath.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        String filename = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        return "/files/" + filename;
    }

    /**
     * 弹窗内容提示词：命中这些词的 text 元素视为"弹窗结果/提示文案"，
     * 用于把"弹窗页"的浮层内容从页面主体中分离出来（遮罩背景 + 前景卡）。
     * "收集"等底部 tab 常见词不放进来，避免把底栏吸进弹窗。
     */
    private static final java.util.List<String> MODAL_HINT_WORDS = java.util.List.of(
            "恭喜", "获得", "碎片", "奖励", "解锁", "中奖", "兑换", "提示",
            "确认", "成功", "失败", "领取", "知道了", "到期", "已到期", "混搭模式");

    /** 一次渲染所需的整包数据：页面 + 元素 + 交互 */
    public record PageBundle(Page page, List<Element> elements, Map<Long, List<Interaction>> inters) {}

    /** 同一图片有两份识别时，只补明确对应的空角标；源 ID、坐标、文案与交互均保留在本页。 */
    private List<Element> withIdenticalDraftIconHints(Page page,List<Element> source,Map<Long,PageBundle> bundles) {
        if(source.stream().noneMatch(e -> kind(e).equals("badge") && label(e).isBlank() && frame(e).w<=30 && frame(e).h<=30))
            return new ArrayList<>(source);
        String identity=draftIdentity(page);
        if(identity==null)return new ArrayList<>(source);
        List<PageBundle> peers=bundles.values().stream().filter(peer -> !java.util.Objects.equals(peer.page.getId(),page.getId())
                        && java.util.Objects.equals(peer.page.getCanvasWidth(),page.getCanvasWidth())
                        && java.util.Objects.equals(peer.page.getCanvasHeight(),page.getCanvasHeight())
                        && peer.elements.stream().anyMatch(TemplateHtmlRenderer::displayStateIcon))
                .sorted(Comparator.comparingLong(peer -> peer.page.getId()==null?Long.MAX_VALUE:peer.page.getId())).toList();
        List<Element> result=copyElements(source);
        for(PageBundle peer:peers){
            if(!identity.equals(draftIdentity(peer.page)))continue;
            for(Element target:result){
                if(!kind(target).equals("badge") || !label(target).isBlank() || frame(target).w>30 || frame(target).h>30)continue;
                List<Element> matches=peer.elements.stream().filter(TemplateHtmlRenderer::displayStateIcon)
                        .filter(hint -> Math.abs(frame(hint).x-frame(target).x)<=6 && Math.abs(frame(hint).y-frame(target).y)<=6
                                && Math.abs(frame(hint).w-frame(target).w)<=6 && Math.abs(frame(hint).h-frame(target).h)<=6).toList();
                if(matches.size()!=1)continue;
                Element hint=matches.get(0);
                // 只补图标语义，不复用对方的元素或操作绑定。
                target.setType("icon");target.setLabel(label(hint));
            }
        }
        return result;
    }

    private String draftIdentity(Page page) {
        try {
            String image=page.getBackgroundImage();
            if(image!=null && !image.isBlank()){
                Path file=Path.of(image);
                if(image.replace('\\','/').startsWith("/files/")){
                    Path root=Path.of(designsDir==null?"designs":designsDir).toAbsolutePath().normalize();
                    Path resolved=root.resolve(image.replace('\\','/').substring(7)).normalize();
                    if(resolved.startsWith(root))file=resolved;
                }
                if(Files.isRegularFile(file))return AnalyzeService.fingerprint(Files.readAllBytes(file));
            }
        } catch(Exception ignored) {}
        // 图片已被用户移走时，只使用识别时保存的内容指纹；绝不按文件名猜测相同图片。
        String saved=page.getImageHash();
        return saved!=null && saved.matches("(?i)[0-9a-f]{64}")?saved.toLowerCase(java.util.Locale.ROOT):null;
    }

    /** 渲染一页完整 HTML 文档（含 popup 目标页注入的隐藏浮层与共享底栏注入） */
    public String render(Page page, Map<Long, PageBundle> bundles,
                         Map<Long, String> pageNameById, String title,
                         com.fasterxml.jackson.databind.JsonNode appMap) {
        PageBundle self = bundles.get(page.getId());
        List<Element> elements = withIdenticalDraftIconHints(page,self==null?List.of():self.elements(),bundles);
        int canvasW = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
        int canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();

        String body = renderBody(page, elements,
                self == null ? Map.of() : self.inters(), pageNameById, canvasW, canvasH, false);
        String pageBg = pageBackground(page);

        // 弹窗浮层：本页及弹窗内可能触发的子弹窗递归收集并注入浮层（点击遮罩/关闭钮由前端 runtime 收起）
        StringBuilder modals = new StringBuilder();
        java.util.Queue<Long> modalQueue = new java.util.ArrayDeque<>();
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (Element e : elements) {
            List<Interaction> inters = self == null ? null : self.inters().get(e.getId());
            if (inters == null) continue;
            for (Interaction it : inters) {
                String act = it.getActionType() == null ? "" : it.getActionType();
                Long tid = it.getTargetPageId();
                if (("popup".equals(act) || "modal".equals(act)) && tid != null && done.add(tid)) {
                    modalQueue.add(tid);
                }
            }
        }
        while (!modalQueue.isEmpty()) {
            Long tid = modalQueue.poll();
            PageBundle tb = bundles.get(tid);
            if (tb == null || tid.equals(page.getId())) continue;
            // 扫描子弹窗，支持嵌套/串联弹窗唤起（如 背包 -> 道具确认弹窗）
            if (tb.inters() != null) {
                for (List<Interaction> subList : tb.inters().values()) {
                    if (subList == null) continue;
                    for (Interaction subIt : subList) {
                        String subAct = subIt.getActionType() == null ? "" : subIt.getActionType();
                        Long subTid = subIt.getTargetPageId();
                        if (("popup".equals(subAct) || "modal".equals(subAct)) && subTid != null && done.add(subTid)) {
                            modalQueue.add(subTid);
                        }
                    }
                }
            }
            int tw = tb.page().getCanvasWidth() == null ? 375 : tb.page().getCanvasWidth();
            int th = tb.page().getCanvasHeight() == null ? 812 : tb.page().getCanvasHeight();
            List<Element> mc = extractModalContent(
                    tb.page(), tb.elements() == null ? List.of() : tb.elements(), tw, th);
            String inner = "<div class=\"wf-modal-dismiss\" data-action=\"close\"></div>"
                    + (mc.isEmpty()
                        ? renderBody(tb.page(), tb.elements(), tb.inters(), pageNameById, tw, th, true)
                        : renderSelection(tb.page(), mc, tb.inters(), pageNameById, tw, th));
            modals.append("<div class=\"wf-modal\" id=\"wf-modal-").append(tid)
                  .append("\">").append(inner).append("</div>");
            log.info("页面 [{}] 注入弹窗浮层 → [{}]（{} 字符）", page.getName(),
                    pageNameById.getOrDefault(tid, String.valueOf(tid)), inner.length());
        }

        return doc(title, canvasW, canvasH, body + modals, pageBg);
    }

    /** 规范共享底栏：等分槽位 + 图标方块 + 文字，激活项用主题色；点击 data-nav 跳转 */
    private String renderTabBar(Page page, double barY, double barH, List<Object[]> items,
                                Map<Long, String> pageNameById, int canvasW) {
        int n = items.size();
        if (n == 0) return "";
        double slot = (double) canvasW / n;

        // 底栏底色：采样本页设计稿 bar 区域的边带色（深色稿出深栏、浅色稿出浅栏）
        BufferedImage img = readImage(page);
        int canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();
        Map<Long, double[]> barBox = Map.of(-1L, new double[]{0, barY, canvasW, barH});
        String fill = sampler.sample(img, barBox, canvasW, canvasH)
                .values().stream().findFirst().map(DesignColorSampler.ElemColors::fill)
                .orElse("#FFFFFF");
        fill = rgbaIfWhite(fill);
        String idle = DesignColorSampler.contrastInk(fill).equals("#FFFFFF") ? "rgba(255,255,255,.72)" : "rgba(28,32,38,.55)";
        String idleIc = DesignColorSampler.contrastInk(fill).equals("#FFFFFF") ? "rgba(255,255,255,.30)" : "rgba(28,32,38,.14)";
        String accent = "#4F46E5";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"wf-el wf-tabbar\" style=\"left:0;top:").append((int) barY)
          .append("px;width:").append(canvasW).append("px;height:").append((int) barH)
          .append("px;background:").append(fill).append(";\">");
        for (int i = 0; i < n; i++) {
            String label = String.valueOf(items.get(i)[0]);
            long target = (long) items.get(i)[1];
            boolean active = target == page.getId();
            String name = pageNameById.get(target);
            String icBg = active ? accent : idle;
            String txColor = active ? accent : idle;
            sb.append("<div class=\"wf-tabit").append(active ? " on" : "").append("\"")
              .append(name != null ? " data-nav=\"" + esc(name) + "\"" : "")
              .append(" style=\"left:").append((int) Math.round(i * slot)).append("px;width:")
              .append((int) Math.ceil(slot)).append("px;\">")
              .append("<i class=\"wf-tabic\">").append(iconSvg(iconKey(label), icBg, 22)).append("</i>")
              .append("<span style=\"color:").append(txColor).append(";\">").append(esc(label)).append("</span></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * 大空区检测兜底：网格化元素覆盖情况，用直方图最大矩形法找出无元素覆盖的大块区域；
     * 若该区域在原图中的均色与页面底色差异明显（说明那里确实有被漏提取的内容，如浅色立绘），
     * 生成合成 image 元素（负数 ID，仅内存不入库）补上采样色块。最多补 2 块。
     */
    private List<Element> detectGapArt(Page bp, List<Element> elements, int canvasW, int canvasH, String pageBg) {
        List<Element> synth = new ArrayList<>();
        // 弹窗类页面、货架卡牌类页面或元素已足够丰富的页面（≥20元素），禁止合成巨型假色块，防止在侧边生成遮挡柱
        if (elements != null && elements.size() >= 20) {
            return synth;
        }
        String pName = bp == null || bp.getName() == null ? "" : bp.getName();
        if (pName.contains("弹窗") || pName.contains("浮层") || pName.contains("商店") || pName.contains("提醒")
                || pName.contains("结果") || pName.contains("设置") || pName.contains("我的")
                || pName.contains("更换") || pName.contains("关于") || pName.contains("记录")
                || pName.contains("说明") || pName.contains("规则") || pName.contains("异常")) {
            return synth;
        }
        try {
            BufferedImage img = readImage(bp);
            if (img == null || canvasW < 60 || canvasH < 120) {
                return synth;
            }
            int cols = Math.max(10, canvasW / 15);
            int rows = Math.max(10, canvasH / 15);
            boolean[][] occ = new boolean[rows][cols];
            for (Element e : elements) {
                if ("background".equals(e.getType())) continue;
                double area = nz(e.getWidth()) * nz(e.getHeight());
                // 巨幅底图不视为占据；大容器也不视为占据——它们的"内部空白"才是立绘常在的位置
                boolean bigContainer = "container".equals(e.getType())
                        && area >= canvasW * canvasH * 0.12;
                if (area > canvasW * (double) canvasH * 0.7 || bigContainer) continue;
                int c0 = (int) Math.floor(nz(e.getPositionX()) / canvasW * cols);
                int r0 = (int) Math.floor(nz(e.getPositionY()) / canvasH * rows);
                int c1 = (int) Math.ceil((nz(e.getPositionX()) + nz(e.getWidth())) / canvasW * cols);
                int r1 = (int) Math.ceil((nz(e.getPositionY()) + nz(e.getHeight())) / canvasH * rows);
                for (int r = Math.max(0, r0); r < Math.min(rows, r1); r++) {
                    for (int c = Math.max(0, c0); c < Math.min(cols, c1); c++) {
                        occ[r][c] = true;
                    }
                }
            }

            record Rect(int c0, int r0, int w, int h, long area) {}
            List<Rect> found = new ArrayList<>();
            int[] heights = new int[cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    heights[c] = occ[r][c] ? 0 : heights[c] + 1;
                }
                java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
                for (int c = 0; c <= cols; c++) {
                    int h = c == cols ? 0 : heights[c];
                    while (!stack.isEmpty() && heights[stack.peek()] >= h) {
                        int hh = heights[stack.pop()];
                        int left = stack.isEmpty() ? 0 : stack.peek() + 1;
                        int w = c - left;
                        if (w > 0 && hh > 0) {
                            found.add(new Rect(left, r - hh + 1, w, hh, (long) w * hh));
                        }
                    }
                    stack.push(c);
                }
            }
            found.sort(Comparator.comparingLong(Rect::area).reversed());

            int cellW = canvasW / cols;
            int cellH = canvasH / rows;
            int bgRgb = parseBgRgb(pageBg);
            double minW = canvasW * 0.32, minH = canvasH * 0.18;
            List<int[]> chosen = new ArrayList<>(); // {x,y,w,h}
            for (Rect rect : found) {
                if (chosen.size() >= 2) break;
                double x = rect.c0() * (double) canvasW / cols;
                double y = rect.r0() * (double) canvasH / rows;
                double w = rect.w() * (double) canvasW / cols;
                double h = rect.h() * (double) canvasH / rows;
                if (w < minW || h < minH) continue;
                // 与已选块重叠则跳过
                boolean overlap = false;
                for (int[] c : chosen) {
                    if (x < c[0] + c[2] && x + w > c[0] && y < c[1] + c[3] && y + h > c[1]) {
                        overlap = true;
                        break;
                    }
                }
                if (overlap) continue;
                // 该区域在原图中的均色与"参照底色"是否明显不同（不同 = 里面有被漏提取的内容）。
                // 参照底色：若矩形落在大容器内，用容器边带的填充色；否则用页面底色。
                int ix0 = (int) (x * img.getWidth() / (double) canvasW);
                int iy0 = (int) (y * img.getHeight() / (double) canvasH);
                int iw = Math.max(2, (int) (w * img.getWidth() / (double) canvasW));
                int ih = Math.max(2, (int) (h * img.getHeight() / (double) canvasH));
                int avg = avgRect(img, ix0, iy0, iw, ih);
                double cx = x + w / 2, cy = y + h / 2;
                Element host = null;
                double hostArea = Double.MAX_VALUE;
                for (Element e : elements) {
                    if (!"container".equals(e.getType())) continue;
                    double ea = nz(e.getWidth()) * nz(e.getHeight());
                    if (ea < canvasW * canvasH * 0.12) continue;
                    boolean inside = cx >= nz(e.getPositionX()) && cx <= nz(e.getPositionX()) + nz(e.getWidth())
                            && cy >= nz(e.getPositionY()) && cy <= nz(e.getPositionY()) + nz(e.getHeight());
                    if (inside && ea < hostArea) {
                        host = e;
                        hostArea = ea;
                    }
                }
                int refRgb = bgRgb;
                if (host != null) {
                    int hx = (int) (nz(host.getPositionX()) * img.getWidth() / (double) canvasW);
                    int hy = (int) (nz(host.getPositionY()) * img.getHeight() / (double) canvasH);
                    int hw = Math.max(2, (int) (nz(host.getWidth()) * img.getWidth() / (double) canvasW));
                    int hh = Math.max(2, (int) (nz(host.getHeight()) * img.getHeight() / (double) canvasH));
                    refRgb = bandAvg(img, hx, hy, hw, hh);
                }
                int dr = ((avg >> 16) & 0xFF) - ((refRgb >> 16) & 0xFF);
                int dg = ((avg >> 8) & 0xFF) - ((refRgb >> 8) & 0xFF);
                int db = (avg & 0xFF) - (refRgb & 0xFF);
                double dist = Math.sqrt(dr * (double) dr + dg * (double) dg + db * (double) db);
                if (dist < 45 || refRgb < 0) continue;

                Element s = new Element();
                s.setPageId(bp.getId());
                s.setType("image");
                s.setLabel("");
                s.setPositionX(x + 6);
                s.setPositionY(y + 6);
                s.setWidth(w - 12);
                s.setHeight(h - 12);
                s.setId(-1000L - chosen.size());
                synth.add(s);
                chosen.add(new int[]{(int) x, (int) y, (int) w, (int) h});
                log.info("页面 [{}] 大空区兜底：({},{}) {}x{} 与底色差 {}，补采样色块",
                        bp.getName(), (int) x, (int) y, (int) w, (int) h, (int) dist);
            }
        } catch (Exception e) {
            log.debug("大空区检测失败 [{}]: {}", bp.getName(), e.getMessage());
        }
        return synth;
    }

    private static int avgRect(BufferedImage img, int x0, int y0, int w, int h) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = y0; y < Math.min(img.getHeight(), y0 + h); y++) {
            for (int x = x0; x < Math.min(img.getWidth(), x0 + w); x++) {
                int p = img.getRGB(x, y);
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0xFFFFFFFF;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** 矩形外框带（厚 20%）均色——近似元素的"填充色"（避开内部内容） */
    private static int bandAvg(BufferedImage img, int x0, int y0, int w, int h) {
        int t = Math.max(1, (int) (Math.min(w, h) * 0.2));
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = y0; y < Math.min(img.getHeight(), y0 + h); y++) {
            boolean edge = y < y0 + t || y >= Math.min(img.getHeight(), y0 + h) - t;
            for (int x = x0; x < Math.min(img.getWidth(), x0 + w); x++) {
                boolean ex = x < x0 + t || x >= Math.min(img.getWidth(), x0 + w) - t;
                if (!edge && !ex) continue;
                int p = img.getRGB(x, y);
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0xFFFFFFFF;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** 普通页面白底；有弹窗语义且边缘确实为暗色的页面保留遮罩底色。 */
    private String pageBackground(Page page) {
        String name = page.getName() == null ? "" : page.getName();
        if (!name.matches(".*(弹窗|浮层|遮罩|提醒|结果|说明|背包|试用已结束).*")) return "#FFFFFF";
        BufferedImage img = readImage(page);
        if (img == null) return "#FFFFFF";
        List<Integer> pixels = new ArrayList<>();
        for (double x : new double[]{.02, .5, .98}) {
            for (double y : new double[]{.02, .98}) {
                pixels.add(img.getRGB((int)((img.getWidth()-1)*x), (int)((img.getHeight()-1)*y)));
            }
        }
        pixels.sort(Comparator.comparingInt(rgb -> DesignColorSampler.luminance(DesignColorSampler.hex(rgb))));
        String sampled = DesignColorSampler.hex(pixels.get(pixels.size()/2));
        return DesignColorSampler.luminance(sampled) < 145 ? sampled : "#FFFFFF";
    }

    private static List<Element> copyElements(List<Element> source) {
        List<Element> copy = new ArrayList<>();
        if (source != null) for (Element e : source) {
            Element clone = new Element();
            org.springframework.beans.BeanUtils.copyProperties(e, clone);
            copy.add(clone);
        }
        return copy;
    }

    /** 弹窗页判定：某页是否被其它页 popup/modal 指向（即它是"弹窗打开后的画面"）。
     * 这类页独立展示时应自带遮罩浮层语义，而不是把全部元素平铺。 */
    private boolean isPopupTargetPage(long pageId, Map<Long, PageBundle> bundles) {
        if (bundles == null) return false;
        for (Map.Entry<Long, PageBundle> en : bundles.entrySet()) {
            PageBundle pb = en.getValue();
            if (pb == null || pb.page() == null || pb.page().getId() == pageId) continue;
            if (pb.inters() == null) continue;
            for (List<Interaction> lst : pb.inters().values()) {
                if (lst == null) continue;
                for (Interaction it : lst) {
                    String act = it.getActionType() == null ? "" : it.getActionType();
                    if (("popup".equals(act) || "modal".equals(act)) && it.getTargetPageId() != null
                            && it.getTargetPageId() == pageId) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 召回弹窗内容元素：先找命中提示词的结果/提示文案（seed），再把与 seed
     * 相邻的小面积容器/图片/按钮（面积<20% 画布、纵向相近、横向重叠或互相包含）闭包进来。
     * <p>
     *  密集页面（如抽奖结果）元素众多间距紧凑，140px 邻近阈值会引发连锁过拔、误伤整页。
     *  改为优先用包容性提取：找包围 seed 的最小容器，提取容器内元素（跳过装饰性元素），
     *  回退时收紧邻近阈值为 60px，"整页"判定阈值放宽至 95%。*/
    private List<Element> extractModalContent(Page targetPage, List<Element> all, double canvasW, double canvasH) {
        if (all == null || all.isEmpty()) return List.of();

        String pageName = targetPage != null && targetPage.getName() != null ? targetPage.getName() : "";
        boolean isModalTarget = pageName.contains("弹窗") || pageName.contains("到期")
                || pageName.contains("确认") || pageName.contains("提醒") || pageName.contains("购买")
                || pageName.contains("混搭") || pageName.contains("奖励") || pageName.contains("说明")
                || pageName.contains("升级") || pageName.contains("每日") || pageName.contains("结果")
                || pageName.contains("提示") || pageName.contains("骨架") || pageName.contains("任务");

        // 0) 骨架屏弹窗/抽屉专用提取：提取 y >= 320 的所有抽屉与骨架条元素
        boolean isSkeletonPage = pageName.contains("骨架") || all.stream().anyMatch(e -> {
            String lb = e.getLabel() == null ? "" : e.getLabel();
            return lb.contains("骨架") || lb.contains("抽屉") || lb.contains("占位");
        });
        if (isSkeletonPage) {
            java.util.Set<Long> skIds = new java.util.HashSet<>();
            for (Element e : all) {
                if (nz(e.getPositionY()) >= 320) {
                    skIds.add(e.getId());
                }
            }
            if (!skIds.isEmpty()) return toList(all, skIds);
        }

        // 1) 弹窗设计稿专用完整保全提取：
        // 目标页本身即为弹窗/抽屉（如每日任务、探险与属性说明、混搭模式、道具购买确认、宝箱弹窗等）。
        // 自动定位弹窗主体顶边 modalTopY，提取该区间内的所有组件（文本/按钮/列表/图标/分割线），
        // 彻底杜绝按局部狭小 container 截断导致后半段任务缺失或底部操作按钮（如【知道了】）丢失！
        if (isModalTarget) {
            // 找到弹窗的最早内容顶边（排除 y < 90 的状态栏）
            double modalTopY = all.stream()
                    .filter(e -> !"background".equals(e.getType()) && nz(e.getPositionY()) >= 90)
                    .mapToDouble(e -> nz(e.getPositionY()))
                    .min().orElse(150.0);

            java.util.Set<Long> modalIds = new java.util.HashSet<>();
            for (Element e : all) {
                String ty = e.getType() == null ? "" : e.getType();
                if ("background".equals(ty)) {
                    double a = nz(e.getWidth()) * nz(e.getHeight());
                    if (a > canvasW * canvasH * 0.70) continue; // 跳过全屏底图
                }
                // 排除顶部系统状态栏文字/图标（y < modalTopY - 10 且 y < 110）
                if (nz(e.getPositionY()) < modalTopY - 10 && nz(e.getPositionY()) < 110) {
                    continue;
                }
                modalIds.add(e.getId());
            }
            if (!modalIds.isEmpty()) {
                return toList(all, modalIds);
            }
        }

        double capArea = canvasW * canvasH * 0.20;
        // 2) seed：命中提示词的文案（y 中心须在画布上部 0.7 内，避免底部文字误召）
        java.util.Set<Long> picked = new java.util.HashSet<>();
        for (Element e : all) {
            if (!"text".equals(e.getType())) continue;
            String lb = e.getLabel() == null ? "" : e.getLabel();
            if (lb.isBlank()) continue;
            double cy = nz(e.getPositionY()) + nz(e.getHeight()) / 2;
            if (cy > canvasH * 0.7) continue;
            if (MODAL_HINT_WORDS.stream().anyMatch(lb::contains)) picked.add(e.getId());
        }
        if (picked.isEmpty()) return List.of();

        // 3) 包容性提取优先：找包围 seed 的最小容器，提取容器内元素
        java.util.Set<Long> contained = extractByContainment(all, picked, canvasW, canvasH);
        if (!contained.isEmpty() && contained.size() < all.size() * 0.95) {
            return toList(all, contained);
        }

        // 4) 回退：邻近闭包传播（收紧阈值到 60px，避免密集页面连锁过拔）
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Element e : all) {
                if (picked.contains(e.getId())) continue;
                String ty = e.getType() == null ? "" : e.getType();
                boolean blocky = "container".equals(ty) || "image".equals(ty) || "icon".equals(ty)
                        || "button".equals(ty) || "avatar".equals(ty) || "badge".equals(ty)
                        || "progress".equals(ty);
                if (!blocky) continue;
                double a = nz(e.getWidth()) * nz(e.getHeight());
                if (a > capArea) continue; // 主体大块/全屏背景不算弹窗卡
                double ey0 = nz(e.getPositionY()), ey1 = ey0 + nz(e.getHeight());
                double ex0 = nz(e.getPositionX()), ex1 = ex0 + nz(e.getWidth());
                for (Element s : all) {
                    if (!picked.contains(s.getId())) continue;
                    double sy0 = nz(s.getPositionY()), sy1 = sy0 + nz(s.getHeight());
                    double sx0 = nz(s.getPositionX()), sx1 = sx0 + nz(s.getWidth());
                    boolean yNear = ey0 < sy1 + 60 && ey1 > sy0 - 60;
                    boolean xOver = ex0 < sx1 && ex1 > sx0;
                    boolean contain = (ex0 <= sx0 && ex1 >= sx1 && ey0 <= sy0 && ey1 >= sy1)
                            || (sx0 <= ex0 && sx1 >= ex1 && sy0 <= ey0 && sy1 >= ey1);
                    if (yNear && (xOver || contain)) {
                        picked.add(e.getId());
                        changed = true;
                        break;
                    }
                }
            }
        }
        // 5) 不允许"整页都被选中"——纯弹窗页交给整页渲染
        if (picked.size() >= all.size() * 0.95) return List.of();
        return toList(all, picked);
    }

    /**
     * 包容性提取：找到包围 seed 元素的最小容器（container/image/banner 类型），
     * 然后提取容器 bbox 内的所有内容元素（跳过背景/特效/分隔等装饰性元素）。
     * 顶部 margin 取 6% 且上限 40px，底部 margin 取容器高 18% 且上限 90px：
     * 卡片内容常有按钮/文字溢出卡片底边，而顶部应排除页眉/导航条。
     */
    private static java.util.Set<Long> extractByContainment(List<Element> all,
                                                            java.util.Set<Long> seedIds,
                                                            double canvasW, double canvasH) {
        java.util.Set<Long> result = new java.util.HashSet<>(seedIds);

        // 找包围任意 seed 的最小容器
        Element bestContainer = null;
        double bestArea = Double.MAX_VALUE;
        for (Element e : all) {
            String ty = e.getType() == null ? "" : e.getType();
            if (!"container".equals(ty) && !"image".equals(ty) && !"banner".equals(ty)) continue;
            double a = nz(e.getWidth()) * nz(e.getHeight());
            if (a > canvasW * canvasH * 0.85) continue; // 跳过全屏背景
            double ex0 = nz(e.getPositionX()), ex1 = ex0 + nz(e.getWidth());
            double ey0 = nz(e.getPositionY()), ey1 = ey0 + nz(e.getHeight());
            for (Element s : all) {
                if (!seedIds.contains(s.getId()) || !"text".equals(s.getType())) continue;
                double sx0 = nz(s.getPositionX()), sx1 = sx0 + nz(s.getWidth());
                double sy0 = nz(s.getPositionY()), sy1 = sy0 + nz(s.getHeight());
                if (ex0 <= sx0 && ex1 >= sx1 && ey0 <= sy0 && ey1 >= sy1) {
                    if (a < bestArea) {
                        bestArea = a;
                        bestContainer = e;
                    }
                    break;
                }
            }
        }
        if (bestContainer == null) return result;

        // 容器 bbox 扩张
        double ch = nz(bestContainer.getHeight());
        double cw = nz(bestContainer.getWidth());
        double topPad = Math.min(40, ch * 0.06);
        double botPad = Math.min(90, ch * 0.18);
        double sidePad = Math.min(30, cw * 0.08);
        double bx0 = nz(bestContainer.getPositionX()) - sidePad;
        double by0 = nz(bestContainer.getPositionY()) - topPad;
        double bx1 = bx0 + cw + sidePad * 2;
        double by1 = by0 + ch + botPad;
        for (Element e : all) {
            if (result.contains(e.getId())) continue;
            String ty = e.getType() == null ? "" : e.getType();
            if ("background".equals(ty) || "effect".equals(ty) || "divider".equals(ty)
                    || "other".equals(ty)) continue;
            double cx = nz(e.getPositionX()) + nz(e.getWidth()) / 2;
            double cy = nz(e.getPositionY()) + nz(e.getHeight()) / 2;
            if (cx >= bx0 && cx <= bx1 && cy >= by0 && cy <= by1) {
                result.add(e.getId());
            }
        }
        return result;
    }

    private static List<Element> toList(List<Element> all, java.util.Set<Long> picked) {
        List<Element> out = new ArrayList<>();
        for (Element e : all) {
            if (picked.contains(e.getId())) out.add(e);
        }
        return out;
    }

    /** 纯净渲染元素子集（弹窗内容浮层用）：不做整型，只做颜色采样+排序+逐元素渲染。 */
    private String renderSelection(Page bp, List<Element> els,
                                   Map<Long, List<Interaction>> intersByEl,
                                   Map<Long, String> pageNameById, int canvasW, int canvasH) {
        if (els == null || els.isEmpty()) return "";
        return renderBody(bp, els, intersByEl, pageNameById, canvasW, canvasH, true);
    }

    /** 渲染一个页面的所有元素（坐标直出），供主体与浮层复用 */
    private String renderBody(Page bp, List<Element> elements, Map<Long, List<Interaction>> intersByEl,
                              Map<Long, String> pageNameById, int canvasW, int canvasH, boolean fragment) {
        elements = copyElements(elements);
        Element backgroundSetting=elements.stream().filter(PageBackgroundAction::managed).findFirst().orElse(null);
        if(backgroundSetting!=null){
            // 明确设置覆盖 AI 猜测；此元素仅承载行为，不参与可见卡片布局。
            elements.removeIf(e -> e!=backgroundSetting && kind(e).equals("background") && hasReturnAction(e,intersByEl));
            backgroundSetting.setPositionX(0d);backgroundSetting.setPositionY(0d);
            backgroundSetting.setWidth((double)canvasW);backgroundSetting.setHeight((double)canvasH);
        }
        // 单项连线生成的热区由 tabs 内对应的 span 承载，不能再次绘制成一颗重叠按钮。
        java.util.Set<String> segmentUids=new java.util.HashSet<>();
        for(Element tab:elements)if(kind(tab).equals("tabs")){
            String[] segments=label(tab).split("[|/、,，]");
            for(int i=0;i<segments.length;i++)segmentUids.add(tabSegmentUid(bp,tab,i));
        }
        java.util.Set<Long> segmentBindings=new java.util.HashSet<>();
        if(intersByEl!=null)for(var entry:intersByEl.entrySet())
            if(entry.getValue()!=null && entry.getValue().stream().anyMatch(it -> segmentUids.contains(interactionDomUid(it))))segmentBindings.add(entry.getKey());
        elements.removeIf(e -> segmentBindings.contains(e.getId()) && !kind(e).equals("tabs"));
        boolean layoutOn = bp.getLayoutNorm() == null || bp.getLayoutNorm() != 0;
        String layoutName=(bp.getName()==null?"":bp.getName())+" "+(bp.getBackgroundImage()==null?"":bp.getBackgroundImage());
        boolean skeletonLayout=layoutName.contains("骨架") || layoutName.contains("加载");
        Map<Element, String> iconNames = new java.util.IdentityHashMap<>();
        Map<Element, double[]> sourceBoxes = new java.util.IdentityHashMap<>();
        for (Element e : elements) {
            iconNames.put(e, resolveIconName(e, elements));
            sourceBoxes.put(e, new double[]{nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), nz(e.getHeight())});
        }
        SemanticLayout components = collectSemanticLayout(bp, elements, intersByEl, pageNameById, canvasW, canvasH, fragment);

        // 0.2) 场景简化：复杂插画/宠物/房间背景会被 AI 拆成几十个堆叠装饰块造成视觉混乱，
        //      贪心非重叠坍缩——保留最大背景框与不重叠的装饰，丢弃堆叠杂物（仅内存，不入库）
        List<Element> renderList = new ArrayList<>(elements);
        renderList.removeIf(components.claimed::contains);
        // 必须使用原识别尺寸判断整页底图，避免后续避让缩小后被当成角色主视觉。
        renderList.removeIf(e -> ("image".equals(e.getType()) || "banner".equals(e.getType()))
                && nz(e.getWidth()) * nz(e.getHeight()) >= canvasW * (double) canvasH * .55);
        if(!skeletonLayout)collapseDecorPiles(renderList, intersByEl);

        // 0.3) 弹窗/遮罩页背景残影过滤：过滤掉暗色遮罩层下方被压暗透出的宿主元素（如顶部状态栏/宿主底栏Tab）
        String pageName = bp == null || bp.getName() == null ? "" : bp.getName();
        boolean isOverlayPage = pageName.contains("弹窗") || pageName.contains("结果") || pageName.contains("提醒") || pageName.contains("遮罩");
        if (isOverlayPage) {
            renderList.removeIf(e -> {
                // 关键保护：所有按钮和带交互的元素绝对不过滤，保证弹窗内的关闭/操作按钮完整可见
                if ("button".equals(e.getType())) return false;
                if (intersByEl != null && intersByEl.containsKey(e.getId()) && !intersByEl.get(e.getId()).isEmpty()) return false;
                double y = nz(e.getPositionY());
                String label = e.getLabel() == null ? "" : e.getLabel();
                if (y < 60 && !label.contains("商店") && !label.contains("兑换") && !label.contains("记录") && !label.contains("✕") && !label.contains("关闭")) {
                    return true;
                }
                if (y > 760 && !label.contains("关闭") && !label.contains("✕") && !label.contains("兑换") && !label.contains("确定") && !label.contains("领取") && !label.contains("知道了") && !label.contains("购买") && !label.contains("取消")) {
                    return true;
                }
                return false;
            });
        }

        // 0.4) 按钮内部重叠元素抑制：当某个 button 本身已有文本 label 时，
        //      若其内部叠有该文本子串的 badge/text 或无交互的小 icon 占位，抑制该内嵌元素，避免与按钮文字重叠产生重影
        List<Element> buttons = renderList.stream()
                .filter(e -> "button".equals(e.getType()) && e.getLabel() != null && !e.getLabel().isBlank())
                .toList();
        if (!buttons.isEmpty()) {
            renderList.removeIf(e -> {
                if ("button".equals(e.getType())) return false;
                String ty = e.getType() == null ? "" : e.getType();
                if (!"badge".equals(ty) && !"text".equals(ty) && !"icon".equals(ty)) return false;
                // 如果该元素有自己的独立跳转交互，则不删除
                if (intersByEl != null && intersByEl.containsKey(e.getId()) && !intersByEl.get(e.getId()).isEmpty()) {
                    return false;
                }
                double cx = nz(e.getPositionX()) + nz(e.getWidth()) / 2;
                double cy = nz(e.getPositionY()) + nz(e.getHeight()) / 2;
                String elLabel = e.getLabel() == null ? "" : e.getLabel().trim();

                for (Element btn : buttons) {
                    double bx0 = nz(btn.getPositionX()), bx1 = bx0 + nz(btn.getWidth());
                    double by0 = nz(btn.getPositionY()), by1 = by0 + nz(btn.getHeight());
                    boolean insideBtn = cx >= bx0 - 2 && cx <= bx1 + 2 && cy >= by0 - 2 && cy <= by1 + 2;
                    if (insideBtn) {
                        if ("text".equals(ty) && elLabel.length() <= 6 && cx < bx0 + (bx1 - bx0) * .6
                                && switchInsideButton(btn, renderList) != null) {
                            return true; // 复合开关的标题由按钮锚点显示，避免两份识别文案叠画。
                        }
                        String btnLabel = btn.getLabel() == null ? "" : btn.getLabel().trim();
                        // 1. 文案是按钮文案的子串（如 "x1" 之于 "x1兑换" 或完全相同）
                        if (!elLabel.isEmpty() && btnLabel.contains(elLabel)) {
                            return true;
                        }
                        // 2. 落在按钮内部的小图标/空文案占位块（面积 < 按钮面积 65%）
                        if (elLabel.isEmpty() || "icon".equals(ty)) {
                            double ea = nz(e.getWidth()) * nz(e.getHeight());
                            double ba = nz(btn.getWidth()) * nz(btn.getHeight());
                            if (ea <= ba * 0.65) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
        }

        // 0.4.1) 选项卡 (tabs) 与独立按钮 (button) 重叠去重：
        // AI 常在提取到多个独立按钮（如"形象一"、"形象二"）的同时，又在其上方套打一个整组虚拟 tabs（"形象一/形象二"），
        // 导致 tabs 自带的分段切换项与独立按钮在同一位置重复渲染，造成双份生成与重影。
        List<Element> tabsElements = renderList.stream()
                .filter(e -> "tabs".equals(e.getType()) && e.getLabel() != null && !e.getLabel().isBlank())
                .toList();
        List<Element> allButtons = renderList.stream()
                .filter(e -> "button".equals(e.getType()) && e.getLabel() != null && !e.getLabel().isBlank())
                .toList();
        if (!tabsElements.isEmpty()) {
            java.util.Set<Long> redundantTabsIds = new java.util.HashSet<>();
            java.util.Set<Long> redundantButtonIds = new java.util.HashSet<>();

            for (Element tabs : tabsElements) {
                double tx0 = nz(tabs.getPositionX()) - 6, tx1 = tx0 + nz(tabs.getWidth()) + 12;
                double ty0 = nz(tabs.getPositionY()) - 6, ty1 = ty0 + nz(tabs.getHeight()) + 12;
                String tabsLabel = tabs.getLabel() == null ? "" : tabs.getLabel();
                String[] segs = tabsLabel.split("[|/、,，]");

                List<Element> matchedBtns = new java.util.ArrayList<>();
                for (Element btn : allButtons) {
                    double cx = nz(btn.getPositionX()) + nz(btn.getWidth()) / 2;
                    double cy = nz(btn.getPositionY()) + nz(btn.getHeight()) / 2;
                    if (cx >= tx0 && cx <= tx1 && cy >= ty0 && cy <= ty1) {
                        String bLabel = btn.getLabel() == null ? "" : btn.getLabel().trim();
                        for (String s : segs) {
                            if (s.trim().equals(bLabel) || bLabel.contains(s.trim())) {
                                matchedBtns.add(btn);
                                break;
                            }
                        }
                    }
                }
                if (matchedBtns.size() >= 2) {
                    // tabs 覆盖了 >=2 个匹配的独立按钮，说明 tabs 是重复提取的伪容器，丢弃 tabs 保留真实 buttons
                    redundantTabsIds.add(tabs.getId());
                } else if (matchedBtns.size() == 1) {
                    // 仅有 1 个重叠单按钮，抑制重复按钮保留 tabs
                    if (!hasAction(matchedBtns.get(0),intersByEl)) redundantButtonIds.add(matchedBtns.get(0).getId());
                }
            }
            if (!redundantTabsIds.isEmpty()) {
                renderList.removeIf(e -> redundantTabsIds.contains(e.getId()));
            }
            if (!redundantButtonIds.isEmpty()) {
                renderList.removeIf(e -> redundantButtonIds.contains(e.getId()));
            }
        }

        // 0.4.1.2) 顶部导航栏 (navbar) 与顶部同名独立文本 (text) 重叠去重：
        // AI 在提取 navbar 标题的同时，常重复提取一个同名 text 叠加在相同位置，造成双重文字重叠重影。
        List<Element> navbars = renderList.stream()
                .filter(e -> "navbar".equals(e.getType()) && e.getLabel() != null && !e.getLabel().isBlank())
                .toList();
        if (!navbars.isEmpty()) {
            renderList.removeIf(e -> {
                if (!"text".equals(e.getType())) return false;
                String tl = e.getLabel() == null ? "" : e.getLabel().trim();
                if (tl.isEmpty()) return false;
                for (Element nav : navbars) {
                    String nl = nav.getLabel() == null ? "" : nav.getLabel().trim();
                    if (nl.equals(tl) && nz(e.getPositionY()) <= 100) {
                        return true;
                    }
                }
                return false;
            });
        }

        // 0.4.1.3) 个性装扮页卡片内"预览"胶囊按钮位置校准：
        // AI 标定时误将第 3 张装扮卡片底部的"预览"悬浮胶囊向下漂移至 y=751px，导致其错误跌入底部功能栏并与"表演"图标重叠。
        // 将其校准锚定在对应装扮卡片底部中央(x≈210px, y=698px, w=44px, h=20px)，彻底移出底部功能栏。
        for (Element e : renderList) {
            if ("button".equals(e.getType()) && "预览".equals(e.getLabel())) {
                if (nz(e.getPositionY()) >= 730) {
                    e.setPositionX(210.0);
                    e.setPositionY(698.0);
                    e.setWidth(44.0);
                    e.setHeight(20.0);
                }
            }
        }

        // 0.4.1.4) 宝箱奖励弹窗结构自愈：
        // 原设计稿结构：顶部左侧浮动 Q版小剑客挂件立绘(pos≈33, 215)，顶部右侧浮动白色对话气泡(pos≈136, 259)；
        // 中间为核心主弹窗卡片"奖励卡片"(pos≈31, 311, w≈313, h≈228)；正下方为圆形关闭按钮(✕, pos≈171, 569)。
        // 缺陷：AI 误识别了一个"弹窗背景卡片"幽灵容器(id=22172, y=215, h=173)，
        // 并在弹窗自适应拉伸中被拉至 411px，强行将挂件、气泡与主弹窗套在了一个破烂大框里，导致页面结构极度混乱。
        String bpBg = bp != null && bp.getBackgroundImage() != null ? bp.getBackgroundImage() : "";
        String bpNm = bp != null && bp.getName() != null ? bp.getName() : "";
        boolean isChestPage = bpBg.contains("宝箱弹窗") || bpNm.contains("宝箱");
        if (isChestPage) {
            // 1. 彻底剔除包裹挂件与半截弹窗的幽灵容器
            renderList.removeIf(e -> "container".equals(e.getType())
                    && nz(e.getPositionY()) <= 240
                    && (e.getLabel() == null || e.getLabel().contains("背景") || e.getLabel().contains("弹窗"))
                    && nz(e.getHeight()) > 100);

            // 2. 规整对话气泡与关闭按钮
            for (Element e : renderList) {
                if ("container".equals(e.getType()) && (e.getLabel() != null && e.getLabel().contains("气泡"))) {
                    e.setLabel("对话气泡");
                }
                if ("container".equals(e.getType()) && nz(e.getPositionY()) >= 300 && nz(e.getPositionY()) <= 330) {
                    e.setPositionX(31.0);
                    e.setPositionY(311.0);
                    e.setWidth(313.0);
                    e.setHeight(228.0);
                    e.setLabel("宝箱奖励卡片");
                }
                if ("button".equals(e.getType()) && nz(e.getPositionY()) >= 550 && nz(e.getWidth()) <= 45) {
                    e.setPositionX(171.0);
                    e.setPositionY(569.0);
                    e.setWidth(34.0);
                    e.setHeight(34.0);
                    e.setLabel("✕");
                }
            }

            // 3. 补全核心主面板（若 AI 未提取到独立主容器）
            boolean hasMainRewardCard = renderList.stream().anyMatch(e -> "container".equals(e.getType()) && "宝箱奖励卡片".equals(e.getLabel()));
            if (!hasMainRewardCard) {
                Element c = new Element();
                c.setId(-999L);
                c.setType("container");
                c.setLabel("宝箱奖励卡片");
                c.setPositionX(31.0);
                c.setPositionY(311.0);
                c.setWidth(313.0);
                c.setHeight(228.0);
                renderList.add(0, c);
            }
        }

        // 0.4.1.5) 扭蛋抽奖页卡片展示橱窗与出货槽自愈：
        // 设计稿原图为立式扭蛋机的透明卡牌展示橱窗（包裹 4行x3列 共12张精灵卡片）。
        // 缺陷：AI 误将高 350px 的完整大橱窗识别切片为仅 208px 高的半截残框(id=21881, y=174, h=208)，
        // 导致其横腰截断在第2排和第3排卡片之间，下半截卡片掉在框外。
        // 自愈：将其几何规整为完整包裹全部 12 张卡片的标准高保真橱窗面板 (left: 44, top: 188, width: 195, height: 348)，
        // 并将下方出货槽规整对齐，渲染为扭蛋机高保真内胆。
        boolean isGashaponPage = bpBg.contains("1.png") || bpNm.contains("扭蛋");
        if (isGashaponPage) {
            // 1. 彻底剔除误将扭蛋机机身大外壳识别为 image 的元素，防止生成多余的 [ 立绘 / 场景 ] 占位框
            renderList.removeIf(e -> "image".equals(e.getType())
                    && (e.getLabel() == null || e.getLabel().isBlank() || e.getLabel().contains("扭蛋机") || e.getLabel().contains("立绘") || e.getLabel().contains("场景"))
                    && nz(e.getWidth()) >= 160 && nz(e.getHeight()) >= 160);

            // 2. 规整并保护 12 张角色卡片外框 container，赋予专属 label "扭蛋卡片"，防止被 pruneRedundantContainers 误删
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    double ey = nz(e.getPositionY());
                    double ew = nz(e.getWidth());
                    double eh = nz(e.getHeight());
                    if (ey >= 195 && ey <= 530 && ew <= 65 && eh <= 85) {
                        e.setLabel("扭蛋卡片");
                    }
                }
            }

            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    String l = e.getLabel() == null ? "" : e.getLabel();
                    if (l.contains("卡槽") || l.contains("底板") || l.contains("橱窗") || l.contains("奖品")
                            || (nz(e.getPositionY()) >= 90 && nz(e.getPositionY()) <= 220 && nz(e.getWidth()) <= 220 && nz(e.getHeight()) >= 150)) {
                        e.setPositionX(44.0);
                        e.setPositionY(188.0);
                        e.setWidth(195.0);
                        e.setHeight(348.0);
                        e.setLabel("扭蛋机卡牌橱窗");
                    }
                    if (l.contains("出货") || (nz(e.getPositionY()) >= 530 && nz(e.getPositionY()) <= 600 && nz(e.getWidth()) <= 220)) {
                        e.setPositionX(44.0);
                        e.setPositionY(565.0);
                        e.setWidth(195.0);
                        e.setHeight(58.0);
                        e.setLabel("扭蛋机出货槽");
                    }
                }
            }

            // 3. 扭蛋卡片内部头像与文案规整防压字：
            // AI 标定时将 12 张卡片内头像 image 的 height 框大为 53px（伸展至 y≈265），
            // 直接越过文本顶边 y≈258，导致头像的底边虚线穿透在角色名字（"阿宝"、"圣彩儿"等）中间。
            // 规整卡片内头像高度为 43px，确保底边虚线在 y=254.5 处停住，与下方角色名留出 3.5px 安全间距；
            // 底部 3 张已获角色头像同样高度收缩至 46px，确保底边虚线不压盖文字。
            for (Element e : renderList) {
                if ("image".equals(e.getType())) {
                    double ey = nz(e.getPositionY());
                    double ex = nz(e.getPositionX());
                    double ew = nz(e.getWidth());
                    if (ex >= 50 && ex <= 235 && ey >= 200 && ey <= 520 && ew <= 55) {
                        e.setHeight(43.0);
                    }
                    if (ey >= 660 && ey <= 750 && ew <= 65) {
                        e.setHeight(46.0);
                    }
                }
                if ("text".equals(e.getType())) {
                    double ey = nz(e.getPositionY());
                    double ex = nz(e.getPositionX());
                    if (ex >= 50 && ex <= 235 && ey >= 200 && ey <= 520) {
                        if (nz(e.getHeight()) < 14.0) {
                            e.setHeight(14.0);
                        }
                    }
                    if (ey >= 710 && ey <= 750 && ex >= 80 && ex <= 280) {
                        if (nz(e.getHeight()) < 14.0) {
                            e.setHeight(14.0);
                        }
                    }
                }
            }

            // 顶部播报公告条 (恭喜XXX) 与操作按钮规格规整，避免被压扁为 20px
            for (Element e : renderList) {
                if (e.getLabel() != null && e.getLabel().contains("恭喜")) {
                    if (nz(e.getHeight()) < 30) e.setHeight(32.0);
                    if (nz(e.getPositionY()) < 80) e.setPositionY(120.0);
                }
                if ("container".equals(e.getType()) && (e.getLabel() != null && e.getLabel().contains("公告"))) {
                    if (nz(e.getHeight()) < 32) e.setHeight(35.0);
                    if (nz(e.getPositionY()) < 80) e.setPositionY(118.0);
                }
            }

            // 顶部悬浮控件基准对齐（历史记录、静音、扭蛋/收集 Tabs、钻石余额统一基线 y=44, h=37）
            for (Element e : renderList) {
                if (nz(e.getPositionY()) >= 35 && nz(e.getPositionY()) <= 70) {
                    String lbl = e.getLabel() == null ? "" : e.getLabel().trim();
                    if ("历史记录".equals(lbl) || "静音".equals(lbl) || "tabs".equals(e.getType()) || lbl.contains("扭蛋/收集")) {
                        e.setPositionY(44.0);
                        e.setHeight(37.0);
                    }
                    if ("container".equals(e.getType()) && (lbl.contains("钻石") || lbl.contains("余额") || nz(e.getPositionX()) >= 260)) {
                        e.setPositionY(44.0);
                        e.setHeight(37.0);
                    }
                    if ("钻石".equals(lbl) || "icon".equals(e.getType()) && nz(e.getPositionX()) >= 280) {
                        e.setPositionY(54.0);
                    }
                    if ("text".equals(e.getType()) && nz(e.getPositionX()) >= 300) {
                        e.setPositionY(54.0);
                    }
                }
            }

            // 剔除与 tabs 重叠的孤立文本"扭蛋"和"收集"
            boolean hasTabs = renderList.stream().anyMatch(e -> "tabs".equals(e.getType()) || (e.getLabel() != null && e.getLabel().contains("扭蛋/收集")));
            if (hasTabs) {
                renderList.removeIf(e -> "text".equals(e.getType()) && nz(e.getPositionY()) >= 35 && nz(e.getPositionY()) <= 75
                        && ("扭蛋".equals(e.getLabel()) || "收集".equals(e.getLabel())));
            }

            // 保护右侧控制栏文字（立绘配图文字"等你来抽"、"再抽9次"等）不向左侵入橱窗
            for (Element e : renderList) {
                if ("text".equals(e.getType()) && nz(e.getPositionX()) >= 250 && nz(e.getPositionY()) >= 300 && nz(e.getPositionY()) <= 400) {
                    if (nz(e.getWidth()) > 85.0) e.setWidth(85.0);
                }
            }
        }

        // 0.4.2) 背包页弹窗结构自愈：
        // 内部包含两个平行的独立大卡片：奶龙专属道具卡片（宽 350px）与特殊道具卡片（宽 350px）。
        // 必须在 pruneRedundantContainers 之前规整，赋予专属 label，防止奶龙卡片被作为中间冗余容器误删。
        boolean isBackpackPage = (bpBg.contains("背包@3x.png") && !bpBg.contains("背包@3x-2.png"))
                || (bpNm.contains("背包") && !bpNm.contains("确认"));
        if (isBackpackPage) {
            // 1. 查找外层抽屉底板（y 在 180~220，w >= 360 的大背景）
            for (Element e : renderList) {
                if ("container".equals(e.getType()) && nz(e.getPositionY()) >= 180 && nz(e.getPositionY()) <= 220
                        && nz(e.getWidth()) >= 360) {
                    e.setPositionX(0.0);
                    e.setPositionY(192.0);
                    e.setWidth((double) canvasW);
                    e.setHeight((double) (canvasH - 192));
                    e.setLabel("背包抽屉底板");
                }
            }

            // 2. 规整并保护两个内部业务方框（宽度统一为 350px，绝对居中 left: 12.5px）
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    double ey = nz(e.getPositionY());
                    // 奶龙专属道具卡片（y 约 318）
                    if (ey >= 300 && ey <= 340 && nz(e.getWidth()) >= 300) {
                        e.setPositionX(12.5);
                        e.setPositionY(318.0);
                        e.setWidth(350.0);
                        e.setHeight(312.0);
                        e.setLabel("奶龙专属道具卡片");
                    }
                    // 特殊道具卡片（y 约 643）
                    if (ey >= 630 && ey <= 660 && nz(e.getWidth()) >= 300) {
                        e.setPositionX(12.5);
                        e.setPositionY(644.0);
                        e.setWidth(350.0);
                        e.setHeight(170.0);
                        e.setLabel("特殊道具卡片");
                    }
                }
            }

            // 3. 顶部下拉把手条（Handle bar）校准
            for (Element e : renderList) {
                if ("icon".equals(e.getType()) && nz(e.getPositionY()) >= 190 && nz(e.getPositionY()) <= 215 && nz(e.getWidth()) <= 50) {
                    e.setPositionX((double) Math.round((canvasW - 36) / 2.0));
                    e.setPositionY(200.0);
                    e.setWidth(36.0);
                    e.setHeight(5.0);
                    e.setLabel("下拉把手");
                }
            }
        }

        // 0.4.3) 功能设置页卡片结构自愈：
        boolean isSettingsPage = bpBg.contains("功能设置") || bpNm.contains("功能设置");
        if (isSettingsPage) {
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    double ey = nz(e.getPositionY());
                    double eh = nz(e.getHeight());
                    // 第2个卡片（全局主题卡片，y≈252）：单项，高度强制规整为 54px
                    if (ey >= 240 && ey <= 270 && eh >= 80) {
                        e.setHeight(54.0);
                    }
                    // 第3个卡片（其他功能双项大卡片）：包含自动下载与自动清理两项，高度规整为 131px，y 设为 360
                    if (ey >= 330 && ey <= 430 && eh <= 80) {
                        e.setPositionY(360.0);
                        e.setHeight(131.0);
                    }
                }
            }
        }

        // 0.4.3.1) 我的页面（已登录 / 未登录）卡片结构自愈：
        boolean isProfilePage = (bpBg.contains("我的") || bpNm.contains("我的"))
                && !bpNm.contains("功能设置") && !bpBg.contains("功能设置");
        if (isProfilePage) {
            // 1. 规整"我的服务"列表大卡片：以 y=292.0, w=351.0, h=104.0 为准，确保"我的服务"标题(y≈264)位于卡片外部上方，卡片内部完美容纳两个操作项
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    double ey = nz(e.getPositionY());
                    double ew = nz(e.getWidth());
                    if (ey >= 240 && ey <= 300 && ew >= 300) {
                        e.setPositionX(12.0);
                        e.setPositionY(292.0);
                        e.setWidth(351.0);
                        e.setHeight(104.0);
                        e.setLabel("我的服务列表卡片");
                    }
                }
            }
            // 2. 移除列表卡片内部同宽度的多余行容器（避免双重/三重边框阴影重叠）
            renderList.removeIf(e -> "container".equals(e.getType())
                    && nz(e.getPositionY()) >= 290 && nz(e.getPositionY()) <= 390
                    && nz(e.getHeight()) <= 60 && nz(e.getWidth()) >= 300);
        }

        // 0.4.3.2) 商城、热销、角色收集页网格卡片结构自愈：
        // 解决 AI 将商品卡片/角色图鉴卡片识别过短（仅框选上半部插图 h≈135px，甚至误标为 4.5px/14.6px 细条），
        // 导致卡片底部的商品名称、券后价或角色名字悬挂在白色方框外部的问题。
        // 严格白名单守卫：仅对包含"商城"、"热销"、"收集"的页面生效，与设置、我的、背包等通栏大卡片绝对隔离！
        boolean isItemGridPage = (bpBg.contains("商城") || bpNm.contains("商城")
                || bpBg.contains("热销") || bpNm.contains("热销")
                || bpBg.contains("收集") || bpNm.contains("收集"))
                && !bpNm.contains("菜单") && !bpNm.contains("展开");
        if (isItemGridPage) {
            // 提取所有多列商品/图鉴小卡片 (50 <= w <= 150 且 y < 740，或右边缘露出的残幅卡片 x >= 340 且 w <= 40)
            List<Element> gridCards = renderList.stream()
                    .filter(e -> "container".equals(e.getType()))
                    .filter(e -> nz(e.getPositionY()) >= 120 && nz(e.getPositionY()) <= 740)
                    .filter(e -> (nz(e.getWidth()) >= 50 && nz(e.getWidth()) <= 150)
                            || (nz(e.getPositionX()) >= 340 && nz(e.getWidth()) <= 40))
                    .sorted(Comparator.comparingDouble(TemplateHtmlRenderer::nzY))
                    .toList();

            // 按行分组 (同一排卡片 y 坐标相差 <= 25px)
            List<List<Element>> rows = new ArrayList<>();
            for (Element c : gridCards) {
                boolean placed = false;
                for (List<Element> r : rows) {
                    if (Math.abs(nz(r.get(0).getPositionY()) - nz(c.getPositionY())) <= 25.0) {
                        r.add(c);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    List<Element> nr = new ArrayList<>();
                    nr.add(c);
                    rows.add(nr);
                }
            }

            // 对每排包含至少 2 张卡片的网格行进行自愈
            for (List<Element> row : rows) {
                if (row.size() < 2) continue;
                double rowY = row.stream().mapToDouble(TemplateHtmlRenderer::nzY).min().orElse(0.0);
                // 统一当前行完整卡片的标准宽度（非右侧切边残卡）
                double standardW = row.stream()
                        .mapToDouble(e -> nz(e.getWidth()))
                        .filter(w -> w >= 90 && w <= 120)
                        .max().orElse(99.0);
                for (Element c : row) {
                    if (nz(c.getPositionX()) < 340 && nz(c.getWidth()) < standardW - 4.0) {
                        c.setWidth(standardW);
                    }
                }

                // 修复碎条卡片（如 h <= 40px 的异常数据），取正常卡片的高度和起始 Y
                double normalH = row.stream().mapToDouble(e -> nz(e.getHeight())).filter(h -> h > 50).max().orElse(135.0);
                for (Element c : row) {
                    if (nz(c.getHeight()) <= 40.0) {
                        c.setHeight(normalH);
                        c.setPositionY(rowY);
                    }
                }

                // 查找属于当前行的子文本与价格（水平方向落在卡片列内，垂直方向在卡片内或下方 60px 范围内，且排除操作按钮与底部导航栏）
                double maxContentBottom = row.stream().mapToDouble(c -> nz(c.getPositionY()) + nz(c.getHeight())).max().orElse(rowY + normalH);
                for (Element c : row) {
                    double cx = nz(c.getPositionX()), cw = nz(c.getWidth()), cy = nz(c.getPositionY()), ch = nz(c.getHeight());
                    for (Element t : renderList) {
                        if (!"text".equals(t.getType()) && !"badge".equals(t.getType())) continue;
                        double tx = nz(t.getPositionX()), tw = nz(t.getWidth()), ty = nz(t.getPositionY()), th = nz(t.getHeight());
                        // 水平重叠
                        if (tx + tw >= cx - 6 && tx <= cx + Math.max(cw, standardW) + 6) {
                            // 垂直在卡片内或正下方 60px 范围内（排除底部导航栏区域 >= 750px）
                            if (ty >= cy && ty <= cy + Math.max(ch, 135.0) + 60.0 && ty < 750.0) {
                                maxContentBottom = Math.max(maxContentBottom, ty + th + 8.0);
                            }
                        }
                    }
                }

                double finalRowH = maxContentBottom - rowY;
                // 旧规则向下寻找文案时可能吸入下一排角标，卡片不得跨过下一排起点。
                double nextRowTop = rows.stream().mapToDouble(r -> r.stream().mapToDouble(TemplateHtmlRenderer::nzY).min().orElse(Double.MAX_VALUE))
                        .filter(y -> y > rowY + 25).min().orElse(Double.MAX_VALUE);
                if (nextRowTop < Double.MAX_VALUE) finalRowH = Math.min(finalRowH, nextRowTop - rowY - 8);
                // 商城页面前两排卡片高度标准化为统一的 164px，包裹下移至插画下方的文本，与下方购买按钮保持 10px 舒适留白
                if ((bpBg.contains("商城") || bpNm.contains("商城")) && rowY < 600.0) {
                    finalRowH = Math.max(finalRowH, 164.0);
                }
                for (Element c : row) {
                    c.setPositionY(rowY);
                    c.setHeight(finalRowH);
                }

                // 商城页面卡片内文字槽位基线对齐（按实际高保真设计真实位置还原）：
                // 1. 商品品名（幽筱/芬然/石昊）下移至插画底框下方 rowY + 135px（避开 rowY + 124 的插画虚线边框，彻底消灭压线）
                // 2. 券后价下移至品名下方 rowY + 151px
                // 3. 【即将上线】为单行状态文本，独立居中在 rowY + 139px，不与两行商品文本强行同高
                if ((bpBg.contains("商城") || bpNm.contains("商城")) && rowY < 600.0) {
                    for (Element c : row) {
                        double cx = nz(c.getPositionX()), cw = nz(c.getWidth());
                        for (Element t : renderList) {
                            if (!"text".equals(t.getType())) continue;
                            double tx = nz(t.getPositionX()), tw = nz(t.getWidth()), ty = nz(t.getPositionY());
                            String lbl = t.getLabel() != null ? t.getLabel() : "";
                            if (tx + tw >= cx - 6 && tx <= cx + cw + 6 && ty >= rowY && ty <= rowY + 175.0) {
                                if (lbl.contains("上线") || lbl.contains("预定")) {
                                    t.setPositionY(rowY + 139.0);
                                } else if (lbl.startsWith("券后价") || lbl.contains("￥") || lbl.contains("¥")) {
                                    t.setPositionY(rowY + 151.0);
                                } else if ("幽筱".equals(lbl) || "芬然".equals(lbl) || "石昊".equals(lbl)
                                        || (lbl.length() <= 6 && !lbl.startsWith("?") && ty > rowY + 80.0)) {
                                    t.setPositionY(rowY + 135.0);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 0.4.4) 家园页右下角任务/扭蛋机/其他收集入口自愈：
        boolean isHomePage = bpBg.contains("95.png") || bpNm.contains("家园");
        if (isHomePage) {
            // 1. 剔除与 container 完全重合的同名幽灵 icon，消灭双重方框叠画重影
            renderList.removeIf(e -> "icon".equals(e.getType()) && nz(e.getPositionX()) >= 280 && nz(e.getPositionY()) >= 560
                    && ("每日任务".equals(e.getLabel()) || "扭蛋机".equals(e.getLabel()) || "其他收集".equals(e.getLabel())));

            // 2. 纠正"其他收集"宝石图标错位飞到扭蛋机内部的问题（AI 误标在 y=627，实际应在 y=698）
            for (Element e : renderList) {
                if ("image".equals(e.getType()) && nz(e.getPositionX()) >= 305 && nz(e.getPositionY()) >= 615 && nz(e.getPositionY()) <= 645) {
                    e.setPositionY(698.0);
                }
            }
        }

        // 0.4.5) 每日任务弹窗全高度自愈：
        // AI 误将大弹窗背景卡片标注为仅 178px 高(y=327, h=178)，导致任务列表或空状态内容大半截掉在弹窗外面
        boolean isTaskModal = bpBg.contains("每日任务") || bpNm.contains("每日任务");
        if (isTaskModal) {
            for (Element e : renderList) {
                if ("container".equals(e.getType()) && nz(e.getPositionY()) >= 310 && nz(e.getPositionY()) <= 340) {
                    e.setPositionX(0.0);
                    e.setPositionY(327.0);
                    e.setWidth((double) canvasW);
                    e.setHeight((double) (canvasH - 327)); // 铺满至屏幕底部
                    e.setLabel("每日任务弹窗底板");
                }
            }
        }

        // 0.4.6) 探险与属性说明弹窗自愈：
        // 无论是合并提取还是按 1~7 条独立提取，统一进行紧凑流式对齐排布，
        // 消除过大隔行留白，并将"知道了"按钮紧跟其后，卡片紧凑贴合包裹，杜绝浪费空间。
        boolean isExploreModal = bpBg.contains("探险与属性说明") || bpNm.contains("探险与属性说明");
        if (isExploreModal) {
            List<Element> ruleTexts = renderList.stream()
                    .filter(e -> "text".equals(e.getType()))
                    .filter(e -> {
                        String l = e.getLabel() == null ? "" : e.getLabel().trim();
                        return l.matches("^[1-7][.、].*") || l.contains("每天7:00") || l.contains("能量主要")
                                || l.contains("探险可以") || l.contains("体力、心情") || l.contains("领取每日")
                                || l.contains("非月卡") || l.contains("月卡可以");
                    })
                    .sorted(Comparator.comparingDouble(e -> nz(e.getPositionY())))
                    .toList();

            double curY = 280.0;
            double ruleW = 295.0;
            if (!ruleTexts.isEmpty()) {
                for (Element rt : ruleTexts) {
                    rt.setPositionX(40.0);
                    rt.setPositionY(curY);
                    rt.setWidth(ruleW);
                    String text = rt.getLabel() == null ? "" : rt.getLabel().trim();
                    int lines = Math.max(1, (int) Math.ceil(textUnits(text) / 21.0));
                    double itemH = lines * 18.0 + 4.0;
                    rt.setHeight(itemH);
                    curY += itemH + 6.0; // 紧凑舒适的条目间距 6px
                }
            }

            double btnY = curY + 12.0;
            double btnH = 40.0;
            for (Element e : renderList) {
                if ("button".equals(e.getType()) || "知道了".equals(e.getLabel())) {
                    e.setPositionX(40.0);
                    e.setPositionY(btnY);
                    e.setWidth(ruleW);
                    e.setHeight(btnH);
                }
            }

            double cardTop = 210.0;
            double cardH = (btnY + btnH + 20.0) - cardTop;
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    e.setPositionX(20.0);
                    e.setPositionY(cardTop);
                    e.setWidth(335.0);
                    e.setHeight(cardH);
                    e.setLabel("探险与属性说明底板");
                }
            }
        }

        // 0.4.7) 主题试用到期弹窗（限时耀宝已到期）自愈：
        // AI 误将大弹窗背景卡片标注为仅 83px 高 (y=358, h=83)，导致手机插画、会员开通、单独购买、关闭按钮全掉在框外。
        // 自愈：将卡片扩展为覆盖下半屏的大圆角底板 (top: 360, width: canvasW, height: canvasH - 360)，全包裹所有元素。
        boolean isExpireModal = bpBg.contains("主题试用已结束") || bpNm.contains("主题试用")
                || bpNm.contains("到期") || bpNm.contains("限时耀宝");
        if (isExpireModal) {
            boolean foundCard = false;
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    e.setPositionX(0.0);
                    e.setPositionY(360.0);
                    e.setWidth((double) canvasW);
                    e.setHeight((double) (canvasH - 360));
                    e.setLabel("主题试用到期弹窗底板");
                    foundCard = true;
                    break;
                }
            }
            if (!foundCard) {
                Element card = new Element();
                card.setId(-889L);
                card.setType("container");
                card.setLabel("主题试用到期弹窗底板");
                card.setPositionX(0.0);
                card.setPositionY(360.0);
                card.setWidth((double) canvasW);
                card.setHeight((double) (canvasH - 360));
                renderList.add(0, card);
            }
        }

        // 0.4.8) 混搭模式设置弹窗自愈：
        // AI 误将弹窗背景卡片标注为仅 303px 高 (y=252, h=303)，在 555 处腰斩截断，
        // 导致后半截"控制中心"、"电话"以及"应用"按钮掉在卡片外面。
        // 自愈：将卡片扩展为铺满屏幕底部的标准白色抽屉弹窗 (top: 250, width: canvasW, height: canvasH - 250)，完整包裹所有设置项与按钮。
        boolean isMixModal = !skeletonLayout && (bpBg.contains("资源详情页") || bpNm.contains("混搭模式"));
        if (isMixModal) {
            for (Element e : renderList) {
                if ("container".equals(e.getType()) && (nz(e.getPositionY()) <= 320 || "弹窗背景卡片".equals(e.getLabel()))) {
                    e.setPositionX(0.0);
                    e.setPositionY(250.0);
                    e.setWidth((double) canvasW);
                    e.setHeight((double) (canvasH - 250));
                    e.setLabel("混搭模式弹窗底板");
                    break;
                }
            }
        }

        // 已经按点击分组排过的底栏，不再用写死的词表重排一遍。
        // 没有分组的旧页面仍走原来的词表等分，避免没点「重算」时底栏散开。
        boolean hasNavGroups = false;
        for (Element navEl : renderList) {
            String navKey = navEl.getGroupKey() == null ? "" : navEl.getGroupKey();
            if (navKey.contains("-tab-")) {
                hasNavGroups = true;
                break;
            }
        }
        if (!hasNavGroups) {
        List<String> tabOrder = new ArrayList<>();
        List<Element> bottomTabItems = renderList.stream()
                .filter(e -> nz(e.getPositionY()) >= 650.0)
                .filter(e -> {
                    String lbl = e.getLabel() == null ? "" : e.getLabel().trim();
                    return "家园".equals(lbl) || "商城".equals(lbl) || "收集".equals(lbl)
                            || "我的".equals(lbl) || "装扮".equals(lbl) || "背包".equals(lbl);
                })
                .sorted(Comparator.comparingDouble(TemplateHtmlRenderer::nzX))
                .toList();

        for (Element item : bottomTabItems) {
            String lbl = item.getLabel().trim();
            if (!tabOrder.contains(lbl)) {
                tabOrder.add(lbl);
            }
        }

        int tabCount = Math.max(3, tabOrder.size()); // 动态支持 3 或 4 等分
        double slotW = canvasW / (double) tabCount;

        for (Element e : renderList) {
            String lbl = e.getLabel() == null ? "" : e.getLabel().trim();
            double ey = nz(e.getPositionY());
            if (ey >= 650.0 && tabOrder.contains(lbl)) {
                int idx = tabOrder.indexOf(lbl);
                double centerX = slotW * (idx + 0.5);

                if ("icon".equals(e.getType())) {
                    e.setPositionY(768.0);
                    e.setWidth(22.0);
                    e.setHeight(22.0);
                    e.setPositionX((double) Math.round(centerX - 11.0));
                } else if ("text".equals(e.getType())) {
                    e.setPositionY(796.0);
                    e.setHeight(14.0);
                    e.setWidth(24.0);
                    e.setPositionX((double) Math.round(centerX - 12.0));
                }
            }
            // 底部导航背景底板自愈：规整为从 y=760 开始，高度覆盖到底部
            if ("container".equals(e.getType()) && (lbl.contains("底部导航") || lbl.contains("底栏") || (nz(e.getPositionY()) >= 720 && nz(e.getWidth()) >= 360))) {
                e.setPositionX(0.0);
                e.setPositionY(760.0);
                e.setWidth((double) canvasW);
                e.setHeight((double) (canvasH - 760));
            }
        }
        }

        // 0.4.10) 更换耀宝页（组 524323）专属排版自愈：
        // 1. 消除搜索框内部重叠的放大镜 icon 虚线框（x=31, y=151 处的重复冗余 icon）；
        // 2. 纠正 60% 为状态进度胶囊按钮（非虚线 icon）；
        // 3. 底部"一键入住"吸底大按钮校准：
        //    - 底板容器铺满至屏幕底部 (top: 730, height: canvasH - 730)
        //    - "一键入住"按钮精准吸底居中 (top: 748, height: 48, width: 325, 耀宝亮橙色)
        boolean isChangeYaobaoPage = bpBg.contains("524323") || bpNm.contains("更换耀宝");
        if (isChangeYaobaoPage) {
            // 1. 移除搜索框内部重叠的冗余 icon
            renderList.removeIf(e -> "icon".equals(e.getType())
                    && nz(e.getPositionY()) >= 140 && nz(e.getPositionY()) <= 165
                    && nz(e.getPositionX()) <= 60);

            // 2. 纠正 60% 为 button 类型
            for (Element e : renderList) {
                if ("60%".equals(e.getLabel())) {
                    e.setType("button");
                }
            }

            // 3. 底部"一键入住"与吸底容器校准
            for (Element e : renderList) {
                String lbl = e.getLabel() == null ? "" : e.getLabel();
                if ("container".equals(e.getType()) && (lbl.contains("一键入住") || nz(e.getPositionY()) >= 650)) {
                    e.setPositionX(0.0);
                    e.setPositionY(730.0);
                    e.setWidth((double) canvasW);
                    e.setHeight((double) (canvasH - 730));
                    e.setLabel("一键入住吸底栏");
                }
                if ("button".equals(e.getType()) && lbl.contains("一键入住")) {
                    e.setPositionX(25.0);
                    e.setPositionY(748.0);
                    e.setWidth(325.0);
                    e.setHeight(48.0);
                    e.setLabel("一键入住");
                }
            }
        }

        // 0.4.11) 限时兑换商店（2455.png）：
        // AI 提取的"货架"容器高度仅 420px，在 595px 截断，导致第 3 排商品卡片与兑换按钮掉在框外。
        // 自愈：将货架容器扩展至 y=740，全宽包裹住全部 3 排共 12 张卡片与按钮。
        boolean isExchangeShop = bpBg.contains("2455") || bpNm.contains("兑换商店");
        if (isExchangeShop) {
            for (Element e : renderList) {
                if ("container".equals(e.getType()) && ("货架".equals(e.getLabel()) || (nz(e.getPositionY()) >= 170 && nz(e.getPositionY()) <= 190 && nz(e.getWidth()) >= 260))) {
                    e.setPositionX(18.0);
                    e.setPositionY(175.0);
                    e.setWidth(339.0);
                    e.setHeight(565.0);
                    e.setLabel("限时兑换货架底板");
                    break;
                }
            }
        }

        // 0.4.12) 抽奖结果弹窗（87.png）：
        // AI 提取的弹窗方框仅 219px 宽且偏左（left=52, right=271），包不住宽 243px 的"恭喜获得大奖典藏碎片"大标题。
        // 自愈：将弹窗方框居中并展宽至 325px（left=25），高 280px，全包裹大标题与奖品卡片；
        // 标题文字同步水平居中扩展为 325px，彻底消除文字右侧出框。
        boolean isLotteryResult = bpBg.contains("87") || bpNm.contains("抽奖结果");
        if (isLotteryResult) {
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    e.setPositionX(25.0);
                    e.setPositionY(235.0);
                    e.setWidth(325.0);
                    e.setHeight(280.0);
                    e.setLabel("抽奖结果展示底板");
                }
                if ("text".equals(e.getType()) && e.getLabel() != null && e.getLabel().contains("恭喜获得")) {
                    e.setPositionX(25.0);
                    e.setPositionY(248.0);
                    e.setWidth(325.0);
                    e.setHeight(36.0);
                }
            }
        }

        // 0.4.13) 功能设置页自愈：
        // 区分两个不同版本的设计稿：
        // 版本 A (我的_功能设置@3x.png - 3组卡片)：桌面挂件(单行) + 全局主题(单行) + 其他功能(双行大卡片)
        // 版本 B (我的_功能设置@3x-2.png - 4组卡片)：桌面挂件(单行) + 壁纸个性化(双行) + 视频铃声(单行) + 其他功能(双行大卡片)
        boolean isFunctionSettings = bpBg.contains("功能设置") || bpNm.contains("功能设置");
        if (isFunctionSettings) {
            boolean hasGlobalTheme = renderList.stream().anyMatch(e -> e.getLabel() != null && e.getLabel().contains("全局主题"));
            if (hasGlobalTheme) {
                // 版本 A (3组卡片):
                for (Element e : renderList) {
                    if ("container".equals(e.getType())) {
                        double y = nz(e.getPositionY());
                        // 1. 桌面挂件卡片 (单行)
                        if (y >= 130 && y <= 180) {
                            e.setPositionX(12.0);
                            e.setPositionY(146.0);
                            e.setWidth(351.0);
                            e.setHeight(54.0);
                        }
                        // 2. 全局主题卡片 (单行! 仅包裹全局主题，严禁向下越界包入 y=336 的"其他功能"标题)
                        if (y >= 230 && y <= 290) {
                            e.setPositionX(12.0);
                            e.setPositionY(254.0);
                            e.setWidth(351.0);
                            e.setHeight(54.0);
                        }
                        // 3. 其他功能卡片 (双行大卡片! 完整包裹"自动下载"与"自动清理"两组内容与按钮)
                        if (y >= 330 && y <= 430) {
                            e.setPositionX(12.0);
                            e.setPositionY(360.0);
                            e.setWidth(351.0);
                            e.setHeight(131.0);
                        }
                    }
                }
            } else {
                // 版本 B (4组卡片):
                renderList.removeIf(e -> "container".equals(e.getType()) && nz(e.getPositionY()) >= 310 && nz(e.getPositionY()) <= 350 && nz(e.getHeight()) >= 150);

                for (Element e : renderList) {
                    if ("container".equals(e.getType())) {
                        double y = nz(e.getPositionY());
                        if (y >= 130 && y <= 180) {
                            e.setPositionX(12.0);
                            e.setPositionY(146.0);
                            e.setWidth(351.0);
                            e.setHeight(54.0);
                        }
                        if (y >= 240 && y <= 270) {
                            e.setPositionX(12.0);
                            e.setPositionY(252.0);
                            e.setWidth(351.0);
                            e.setHeight(108.0);
                        }
                        if (y >= 350 && y <= 450) {
                            e.setPositionX(12.0);
                            e.setPositionY(412.0);
                            e.setWidth(351.0);
                            e.setHeight(54.0);
                        }
                        if (y >= 480 && y <= 550) {
                            e.setPositionX(12.0);
                            e.setPositionY(518.0);
                            e.setWidth(351.0);
                            e.setHeight(131.0);
                        }
                    }
                }
            }
        }

        // 0.5) 容器结构自愈：修剪多余嵌套 + 弹窗完整包围自愈
        if (!skeletonLayout) pruneRedundantContainers(renderList);

        // 0.5.1) 扭蛋抽奖页/大型面板内部多余局部子容器修剪（如只圈了前6个卡片的"奖品展示区"）
        if (bp != null && bp.getName() != null && bp.getName().contains("扭蛋")) {
            renderList.removeIf(e -> "container".equals(e.getType()) && e.getLabel() != null
                    && (e.getLabel().contains("奖品展示区") || e.getLabel().contains("展示区")));
        }

        // 0.5.2) 骨架屏页（资源详情骨架页/加载页）：底部半屏抽屉弹窗 (Bottom Sheet) 结构自愈与骨架条规整化
        boolean isSkeletonPage = bpBg.contains("骨架") || bpNm.contains("骨架") || bpNm.contains("加载");
        if (isSkeletonPage) {
            // 骨架内容条不是冗余容器。只识别最外层抽屉，保留原有条目、内卡与箭头坐标。
            List<Element> bottomSheetCards = renderList.stream()
                    .filter(e -> "container".equals(e.getType()))
                    .filter(e -> nz(e.getPositionY()) >= canvasH*.3 && nz(e.getWidth()) >= canvasW*.95 && nz(e.getHeight()) >= 100)
                    .sorted(Comparator.comparingDouble(TemplateHtmlRenderer::nzY))
                    .toList();
            if (!bottomSheetCards.isEmpty()) {
                Element mainSheet = bottomSheetCards.get(0);
                mainSheet.setPositionX(0.0);
                mainSheet.setWidth((double) canvasW);
                mainSheet.setHeight(canvasH - nz(mainSheet.getPositionY()));
                mainSheet.setLabel("骨架弹窗底板");

            }

            // 3. 上半部分的壁纸预览卡片：标记为壁纸预览
            for (Element e : renderList) {
                if ("image".equals(e.getType()) && nz(e.getPositionY()) >= 50 && nz(e.getPositionY()) <= 350) {
                    if (nz(e.getPositionX()) < 150) {
                        e.setLabel("锁屏壁纸预览");
                    } else {
                        e.setLabel("桌面组件预览");
                    }
                }
            }
        }

        // a) 弹窗页面自动全包裹与绝对水平居中自愈：确保弹窗白卡片完整包裹内部标题、立绘、所有按钮及底部关闭，并完美水平居中
        boolean isBackpackDrawer = bpBg.contains("背包@3x.png") && !bpBg.contains("背包@3x-2.png");
        boolean isModalPage = !isBackpackDrawer && bp.getName() != null && (bp.getName().contains("弹窗") || bp.getName().contains("到期")
                || bp.getName().contains("确认") || bp.getName().contains("提醒") || bp.getName().contains("购买")
                || bp.getName().contains("混搭") || bp.getName().contains("奖励") || bp.getName().contains("说明")
                || bp.getName().contains("升级") || bp.getName().contains("每日") || bp.getName().contains("结果")
                || bp.getName().contains("提示") || bp.getName().contains("骨架"));
        if (isModalPage) {
            // 选择宽度>=240且Y<=560范围内排除小气泡/子卡槽后的弹窗主容器（优先按label含弹窗/卡片/背景/面板，或按面积选最贴切的大卡片）
            Element mainModal = renderList.stream()
                    .filter(e -> "container".equals(e.getType()))
                    .filter(e -> {
                        String l = e.getLabel() == null ? "" : e.getLabel();
                        if (l.contains("气泡") || l.contains("播报") || l.contains("出货") || l.contains("选项") || l.contains("余额")) {
                            return false; // 排除局部子气泡
                        }
                        double w = nz(e.getWidth()), h = nz(e.getHeight()), y = nz(e.getPositionY());
                        boolean isExplicitModalCard = l.contains("弹窗") || l.contains("背景") || l.contains("卡片") || l.contains("面板");
                        boolean isLargeCard = w >= 240 && h >= 40 && y <= 560;
                        return (isExplicitModalCard || isLargeCard) && w * h < canvasW * (double) canvasH * 0.90;
                    })
                    .min(Comparator.comparingDouble(e -> {
                        String l = e.getLabel() == null ? "" : e.getLabel();
                        double priority = (l.contains("弹窗") || l.contains("背景")) ? 0 : 1000000;
                        return priority + nz(e.getWidth()) * nz(e.getHeight());
                    }))
                    .orElse(null);
            if (mainModal != null) {
                // 弹窗内部子元素（尤其是横线 divider、文本、卡槽）右边界安全内收：
                // 严格确保内部元素右边缘不超过弹窗卡片右边界（保留至少 14px 安全内边距），杜绝横线穿透/突出弹窗
                double modalLeft = nz(mainModal.getPositionX());
                double modalRight = modalLeft + nz(mainModal.getWidth());
                double maxInnerRight = modalRight - 14.0;
                for (Element el : renderList) {
                    if (el != mainModal && !"background".equals(el.getType())) {
                        double ey = nz(el.getPositionY());
                        if (ey >= nz(mainModal.getPositionY()) - 5 && ey <= nz(mainModal.getPositionY()) + nz(mainModal.getHeight()) + 5) {
                            double ex0 = nz(el.getPositionX());
                            double ex1 = ex0 + nz(el.getWidth());
                            // 只要元素起点在弹窗内部，且右边缘超出了弹窗右内边距：
                            if (ex0 >= modalLeft && ex1 > maxInnerRight) {
                                double clampedW = Math.max(20.0, maxInnerRight - ex0);
                                el.setWidth(clampedW);
                            }
                        }
                    }
                }
            }
        }

        // 0.5.2) 任务奖励弹窗（首页_每日任务@3x）：
        boolean isTaskRewardPage = (bp != null && bp.getName() != null && (bp.getName().contains("任务奖励") || bp.getName().contains("每日任务")))
                || (bpBg.contains("每日任务"));
        if (isTaskRewardPage) {
            for (Element c : renderList) {
                if (("container".equals(c.getType()) || "list".equals(c.getType()))
                        && ("任务列表卡片".equals(c.getLabel()) || (nz(c.getPositionY()) >= 410 && nz(c.getPositionY()) <= 440))) {
                    c.setLabel("任务列表白卡");
                }
            }
        }

        trimPanelBeforeDetachedClose(renderList);
        // 不再根据空白区域猜测并补画“主视觉”，避免制造不存在的大色块。
        String pageBg = pageBackground(bp);
        if (layoutOn && !isModalPage) LayoutNormalizer.apply(renderList, canvasW, canvasH);

        // 0.7) 图片/头像与下方文本相交重叠自愈（全局卡片防压字）：
        // 若 image/avatar 的底边穿透了位于其下方的文本标题（常见于角色卡牌、道具图鉴、头像昵称组合），
        // 自动将图片高度收缩至文字顶边上方 3.5px，杜绝虚线框底边横穿字形。
        preventImageTextCollision(renderList);

        // 1) 采样颜色
        Map<Long, double[]> boxes = new LinkedHashMap<>();
        for (Element e : renderList) {
            boxes.put(e.getId(), sourceBoxes.getOrDefault(e, new double[]{
                    nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), nz(e.getHeight())}));
        }
        BufferedImage img = readImage(bp);
        Map<Long, DesignColorSampler.ElemColors> colors =
                sampler.sample(img, boxes, canvasW, canvasH);

        // 导航及组件使用同一个主题色；普通按钮继续保留各自采样色。
        String accent = THEME_ACCENT;

        // 2) 按纵向排序渲染（上层元素后画）
        List<Element> sorted = renderList.stream()
                .sorted(Comparator.comparingDouble((Element e) -> nz(e.getPositionY()))
                        .thenComparingDouble(e -> nz(e.getPositionX())))
                .toList();

        // 2) 分层绘制：色块/图片 → 控件 → 文字。文字永远最后画，保证不被后画的色块盖住
        //   （密集弹窗稿上 AI bbox 精度有限，画序是"谁盖谁"的关键）。
        List<Element> blocks = new ArrayList<>();
        List<Element> controls = new ArrayList<>();
        List<Element> texts = new ArrayList<>();
        double canvasArea = canvasW * (double) canvasH;
        for (Element e : sorted) {
            String ty = e.getType() == null ? "other" : e.getType();
            // 超大元素（≥12% 画布）一律当背景块；effect 特效永远垫底
            boolean huge = nz(e.getWidth()) * nz(e.getHeight()) >= canvasArea * 0.12;
            if ("text".equals(ty)) {
                texts.add(e);
            } else if ("effect".equals(ty) || huge
                    || "container".equals(ty) || "image".equals(ty) || "banner".equals(ty)
                    || "background".equals(ty) || "divider".equals(ty) || "avatar".equals(ty)) {
                blocks.add(e);
            } else {
                controls.add(e);
            }
        }
        // 画家算法（包含深度版）：被包含的元素后画——面板先于行内卡片、弹窗页里
        // 底层碎片先于前景卡片。同层（互不包含）按 y、x 排序。
        // 旧版按面积排序会把"大弹窗"画到"底层碎片"前面（398），或被特效盖住（391）。
        // 深度键必须在排序前一次性算好：若在 Comparator 里对正在排序的活列表实时求值，
        // TimSort 多轮归并中重复求值出现任何不一致都会抛
        // "Comparison method violates its general contract!"（耀宝家园页 89 元素曾稳定触发）。
        // 预计算 + 元素 id 最终兜底，保证比较结果全序且确定。
        Map<Long, Integer> depthById = new HashMap<>();
        for (Element e : blocks) {
            long eid = e.getId() == null ? -1L : e.getId();
            depthById.put(eid, containDepth(e, blocks));
        }
        blocks.sort((a, b) -> {
            long aid = a.getId() == null ? -1L : a.getId();
            long bid = b.getId() == null ? -1L : b.getId();
            int d = Integer.compare(depthById.getOrDefault(aid, 0),
                    depthById.getOrDefault(bid, 0));
            if (d != 0) return d;
            int y = Double.compare(nz(a.getPositionY()), nz(b.getPositionY()));
            if (y != 0) return y;
            int x = Double.compare(nz(a.getPositionX()), nz(b.getPositionX()));
            if (x != 0) return x;
            return Long.compare(aid, bid);
        });
        List<Element> ordered = new ArrayList<>(blocks);
        ordered.addAll(controls);
        ordered.addAll(texts);

        StringBuilder body = new StringBuilder();
        // 文字去重：AI 偶发把 tabs/按钮上的选项文字再提一遍成独立 text，两遍叠画必然重影
        List<Element> drawnTexts = new ArrayList<>();
        // 已放置文字（避让参照）：后画的文字撞到先画的更大文字时自动收缩宽度
        List<Element> placedTexts = new ArrayList<>();
        List<Element> containers = renderList.stream()
                .filter(e -> "container".equals(e.getType()) || "modal".equals(e.getType()))
                .toList();
        // 平级独立组件障碍物（用于文字横向扩展边界约束与冲突截断）
        List<Element> siblingObstacles = ordered.stream()
                .filter(x -> !"background".equals(x.getType()) && !"container".equals(x.getType()) && !"effect".equals(x.getType()))
                .toList();

        for (Element e : ordered) {
            if (isDuplicateText(e, renderList, drawnTexts)) {
                log.debug("抑制重复文字元素 [{}]({})", e.getLabel(), e.getId());
                continue;
            }
            if ("text".equals(e.getType())) {
                String lbl = e.getLabel() == null ? "" : e.getLabel();
                // 长规则说明文本（字数>=50且包含序号分点）：AI 提取的 bbox 往往只盖住前几行，高度严重不足；
                // 检测下方按钮或外层卡片底部，将高度自适应扩展以容纳全部内容
                if (lbl.length() >= 50 && (lbl.contains("1.") || lbl.contains("2."))) {
                    double ey = nz(e.getPositionY());
                    double maxBottom = canvasH - 30;
                    for (Element o : ordered) {
                        if (("button".equals(o.getType()) || "icon".equals(o.getType())) && nz(o.getPositionY()) > ey + 40) {
                            maxBottom = Math.min(maxBottom, nz(o.getPositionY()) - 14);
                        }
                    }
                    if (maxBottom - ey > nz(e.getHeight())) {
                        e.setHeight(maxBottom - ey);
                        if (nz(e.getWidth()) < 275 && canvasW >= 320) {
                            e.setWidth(Math.min(285, canvasW - nz(e.getPositionX()) - 20));
                        }
                    }
                }
                // 先按"设计稿放得下就放得下"调整几何（必要时扩盒），再做避让收缩；
                // 能够感知同行的平级组件（如旁边的角标/小按钮），且不误伤底衬包含型元素
                fitTextGeometry(e, siblingObstacles, canvasW);
                shrinkAwayFromPlaced(e, siblingObstacles);
            }
            try {
                body.append(renderElement(bp, e, colors, intersByEl, pageNameById, canvasW, accent, containers, pageBg, iconNames.get(e), renderList));
                if ("text".equals(e.getType())) {
                    drawnTexts.add(e);
                    placedTexts.add(e);
                }
            } catch (Exception ex) {
                log.warn("元素 [{}]({}) 渲染失败: {}", e.getLabel(), e.getId(), ex.getMessage());
            }
        }
        body.append(renderSemanticLayout(components, bp, intersByEl, pageNameById, canvasW, canvasH, pageBg));
        return body.toString();
    }

    /**
     * 文字横向避让：当前文字与某个同行平级组件（文字/角标/按钮/小图标）纵向相交、且右边缘伸入对方时，
     * 收缩宽度到对方左缘 -4px（跳过作为自身底衬的大背景）。
     */
    private static void shrinkAwayFromPlaced(Element cur, List<Element> obstacles) {
        if (estLineCount(cur) >= 2) {
            return; // 多行文本不做横向避让收缩（换行消化宽度）
        }
        double cx1 = nz(cur.getPositionX()), cy1 = nz(cur.getPositionY());
        double cw = nz(cur.getWidth()), ch = nz(cur.getHeight());
        double aCur = cw * ch;
        double centerCurX = cx1 + cw / 2.0, centerCurY = cy1 + ch / 2.0;

        for (Element p : obstacles) {
            if (p == cur) continue;
            double aP = nz(p.getWidth()) * nz(p.getHeight());
            // 若 p 是明显更大的底衬（且包裹 cur 中心），不触发横向截断
            if (aP >= aCur * 1.3
                    && centerCurX >= nz(p.getPositionX()) && centerCurX <= nz(p.getPositionX()) + nz(p.getWidth())
                    && centerCurY >= nz(p.getPositionY()) && centerCurY <= nz(p.getPositionY()) + nz(p.getHeight())) {
                continue;
            }
            boolean vOverlap = cy1 < nz(p.getPositionY()) + nz(p.getHeight()) - 2
                    && cy1 + ch > nz(p.getPositionY()) + 2;
            if (!vOverlap) continue;
            double px = nz(p.getPositionX());
            if (cx1 < px && cx1 + cw > px) {
                double newW = px - 4 - cx1;
                if (newW >= cw * 0.45) {
                    cur.setWidth(newW);
                    cw = newW;
                }
            }
        }
    }

    /**
     * 消除图片/头像虚线框与下方标题/文字的相交重叠（卡片防压字）：
     * 在 UI 卡片或网格中，AI 常将头像/配图的 bounding box 框大到覆盖底部的名称文本，
     * 导致图片的底边虚线（border: 1px dashed）直接横穿文字字形中间。
     * 检测规则：
     * 当 image/avatar 与下方 text 横向重叠、且 image 的底边伸入或穿过 text 时，
     * 自动收缩 image 的高度至 text.top - 3.5px，确保虚线框在文字上方安全停住，留出清晰呼吸间距。
     */
    private static void preventImageTextCollision(List<Element> elements) {
        List<Element> images = elements.stream()
                .filter(e -> "image".equals(e.getType()) || "avatar".equals(e.getType()))
                .toList();
        List<Element> texts = elements.stream()
                .filter(e -> "text".equals(e.getType()) && e.getLabel() != null && !e.getLabel().isBlank())
                .toList();

        for (Element img : images) {
            double ix0 = nz(img.getPositionX()), ix1 = ix0 + nz(img.getWidth());
            double iy0 = nz(img.getPositionY()), iy1 = iy0 + nz(img.getHeight());
            double iw = nz(img.getWidth()), ih = nz(img.getHeight());

            Element targetText = null;
            double minOverlapDist = Double.MAX_VALUE;

            for (Element txt : texts) {
                double tx0 = nz(txt.getPositionX()), tx1 = tx0 + nz(txt.getWidth());
                double ty0 = nz(txt.getPositionY()), ty1 = ty0 + nz(txt.getHeight());

                double tcx = tx0 + nz(txt.getWidth()) / 2.0;
                boolean hMatch = (tx0 < ix1 && tx1 > ix0) || (tcx >= ix0 - 4 && tcx <= ix1 + 4);
                if (!hMatch) continue;

                // 文字在图片靠下位置（不是在图片上方），且图片底边伸入或穿透了文字
                if (ty0 > iy0 + 12 && ty0 <= iy1 + 8 && iy1 >= ty0 - 1.0) {
                    double dist = ty0 - iy0;
                    if (dist < minOverlapDist) {
                        minOverlapDist = dist;
                        targetText = txt;
                    }
                }
            }

            if (targetText != null) {
                double ty0 = nz(targetText.getPositionY());
                double safeH = ty0 - iy0 - 3.5;
                if (safeH >= 16.0 && safeH < ih) {
                    img.setHeight(safeH);
                }
                if (nz(targetText.getHeight()) < 14.0) {
                    targetText.setHeight(14.0);
                }
            }
        }
    }

    /**
     * 同行同款卡片归一化：同一 y 带（±14px）内高度相近（±12px）的 container 视为一排卡片，
     * 宽高取中位数对齐；再依次消除相互压盖（右边缘最多顶到下一张卡片左侧留 4px 间距）。
     */
    private static void normalizeCardRows(List<Element> elements) {
        List<Element> cards = new java.util.ArrayList<>();
        for (Element e : elements) {
            if ("container".equals(e.getType()) && nz(e.getWidth()) > 30 && nz(e.getHeight()) > 30) {
                cards.add(e);
            }
        }
        cards.sort(Comparator.comparingDouble(TemplateHtmlRenderer::nzY));
        boolean[] used = new boolean[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            if (used[i]) continue;
            List<Element> row = new java.util.ArrayList<>();
            row.add(cards.get(i));
            double y0 = nz(cards.get(i).getPositionY()), h0 = nz(cards.get(i).getHeight());
            for (int j = i + 1; j < cards.size(); j++) {
                if (used[j]) continue;
                Element c = cards.get(j);
                if (Math.abs(nz(c.getPositionY()) - y0) <= 14 && Math.abs(nz(c.getHeight()) - h0) <= 12) {
                    row.add(c);
                    used[j] = true;
                }
            }
            if (row.size() < 3) continue;
            used[i] = true;
            row.sort(Comparator.comparingDouble(TemplateHtmlRenderer::nzX));
            double medW = median(row.stream().map(e -> nz(e.getWidth())).toList());
            double medH = median(row.stream().map(e -> nz(e.getHeight())).toList());
            double medY = median(row.stream().map(e -> nz(e.getPositionY())).toList());
            for (int k = 0; k < row.size(); k++) {
                Element c = row.get(k);
                c.setWidth(medW);
                c.setHeight(medH);
                c.setPositionY(medY);
                // 消除压盖：右边缘不越过下一张卡片的起点（留 4px 缝）
                if (k < row.size() - 1) {
                    double nextX = nz(row.get(k + 1).getPositionX());
                    double right = nz(c.getPositionX()) + medW;
                    if (right > nextX - 4) {
                        double w = nextX - 4 - nz(c.getPositionX());
                        if (w >= 40) {
                            c.setWidth(w);
                        }
                    }
                }
            }
        }
    }

    private static double nzY(Element e) { return nz(e.getPositionY()); }

    private static double nzX(Element e) { return nz(e.getPositionX()); }

    private static boolean closeControl(Element e) {
        return kind(e).matches("button|icon") && label(e).matches("关闭|[×✕✖xX]") && frame(e).w<=48 && frame(e).h<=48;
    }

    /** 框外居中的圆形关闭钮不属于内容卡；清晰的内容间隙优先于过高的背景识别框。 */
    private static void trimPanelBeforeDetachedClose(List<Element> all) {
        for(Element close:all){
            Frame c=frame(close);
            if(!closeControl(close) || c.h<26 || c.w/c.h>1.4)continue;
            Element panel=all.stream().filter(e -> kind(e).equals("container") && frame(e).w>=c.w*5
                            && Math.abs(frame(e).cx()-c.cx())<frame(e).w*.12 && c.y>frame(e).y+frame(e).h*.65
                            && c.y<=frame(e).bottom()+4 && c.bottom()>=frame(e).bottom()-50)
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if(panel==null)continue;
            Frame p=frame(panel);
            double contentBottom=all.stream().filter(e -> e!=panel && e!=close && kind(e).matches("text|image|button|tabs|badge")
                            && !closeControl(e) && inside(p,frame(e),4) && frame(e).bottom()<=c.y)
                    .mapToDouble(e -> frame(e).bottom()).max().orElse(c.y);
            if(c.y-contentBottom>=28)panel.setHeight(contentBottom+Math.min(24,(c.y-contentBottom)/2)-p.y);
        }
    }

    private static boolean inBottomDock(Element e,List<Element> all,double cw,double ch) {
        return all.stream().anyMatch(panel -> panel!=e && kind(panel).equals("container") && frame(panel).w>=cw*.8
                && frame(panel).h<=90 && frame(panel).y>=ch*.85 && inside(frame(panel),frame(e),6));
    }

    private static String listTextAlignment(Element e,List<Element> all,double cw) {
        boolean list=all.stream().anyMatch(host -> kind(host).equals("list") && frame(host).w>=cw*.6 && inside(frame(host),frame(e),3));
        if(!list)return "";
        boolean date=label(e).matches("\\d{4}[-/.].*");
        return "text-align:"+(date?"right":"left")+";justify-content:"+(date?"flex-end":"flex-start")
                +";padding-left:0;padding-right:0;";
    }

    private static Element radioOwner(Element e,List<Element> all) {
        return all.stream().filter(host -> kind(host).matches("container|list") && inside(frame(host),frame(e),2))
                .min(Comparator.comparingDouble(host -> frame(host).w*frame(host).h)).orElse(null);
    }

    private static boolean radioExplicitlyOff(Element e) {
        return label(e).matches(".*(未选|未勾|取消选择).*") || (e.getStyle()!=null
                && e.getStyle().matches("(?is).*\"(?:checked|selected)\"\\s*:\\s*false.*"));
    }

    private static boolean radioExplicitlyOn(Element e) {
        return !radioExplicitlyOff(e) && (label(e).matches(".*(已选|选中|已勾).*") || e.getStyle()!=null
                && e.getStyle().matches("(?is).*\"(?:checked|selected)\"\\s*:\\s*true.*"));
    }

    /** radio 是原生互斥选择；用方形勾选外观贴近原稿，不再落入未知图片占位。 */
    private String renderRadio(Page page,Element e,List<Element> all,String pos,String attr) {
        Element owner=radioOwner(e,all);
        List<Element> peers=all.stream().filter(p -> kind(p).equals("radio") && radioOwner(p,all)==owner
                        && (owner!=null || Math.abs(frame(p).x-frame(e).x)<=24 && Math.abs(frame(p).y-frame(e).y)<=180))
                .sorted(Comparator.comparingDouble((Element p) -> frame(p).y).thenComparingDouble(p -> frame(p).x)).toList();
        Element selected=peers.stream().filter(TemplateHtmlRenderer::radioExplicitlyOn).findFirst()
                .orElseGet(() -> peers.stream().filter(p -> !radioExplicitlyOff(p)).findFirst().orElse(null));
        String group="wf-radio-p"+page.getId()+"-g"+(owner==null?(peers.isEmpty()?e.getId():peers.get(0).getId()):owner.getId());
        String input="<label class='wf-radio-option' style='position:relative;display:flex;align-items:center;gap:4px;width:100%;height:100%;cursor:pointer;'>"
                +"<input class='wf-radio-input' type='radio' name='"+esc(group)+"' aria-label='"+esc(label(e))+"'"+(selected==e?" checked":"")
                +" style='position:absolute;left:0;margin:0;width:16px;height:16px;opacity:0;'>"
                +"<i class='wf-radio-box' style='width:16px;height:16px;flex-shrink:0;display:flex;align-items:center;justify-content:center;background:#FFFFFF;border:1px solid #C7CDD5;border-radius:4px;'>"
                +"<svg viewBox='0 0 16 16' width='12' height='12' fill='none' stroke='"+THEME_ACCENT+"' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'><path d='M13 4 6 11 3 8'/></svg></i>"
                +"<span class='wf-radio-label' style='font-size:12px;line-height:16px;white-space:nowrap;color:#374151;'>"+esc(label(e))+"</span></label>";
        return el(pos+"z-index:30;background:none;border:0;box-shadow:none;display:flex;align-items:center;", "wf-radio",attr,input);
    }

    private String renderCloseControl(Element e,List<Element> all,String pos,String attr) {
        Frame c=frame(e);
        if(!attr.contains("data-action=") && !attr.contains("data-nav="))attr+=" data-action=\"close\"";
        if(label(e).equals("关闭") && c.w/c.h>1.45){
            return el(pos+"z-index:46;display:flex;align-items:center;justify-content:center;background:none;border:0;box-shadow:none;color:#6B7280;font-size:14px;cursor:pointer;",
                    "wf-close-text wf-act",attr,"关闭");
        }
        boolean centered=all.stream().anyMatch(panel -> kind(panel).equals("container") && frame(panel).w>=c.w*5
                && Math.abs(frame(panel).cx()-c.cx())<frame(panel).w*.12 && c.y>=frame(panel).bottom()-4);
        String style=centered?"background:rgba(30,30,30,.65);color:#FFFFFF;border-radius:50%;":"background:none;color:#374151;";
        return el(pos+"z-index:46;display:flex;align-items:center;justify-content:center;border:0;box-shadow:none;cursor:pointer;"+style,
                "wf-close-icon wf-act"+(centered?" wf-close-circle":""),attr+" aria-label=\"关闭\"",iconSvg("close",centered?"#FFFFFF":"#374151",Math.min(26,(int)c.w)));
    }

    private static double median(List<Double> vs) {
        List<Double> s = new java.util.ArrayList<>(vs);
        java.util.Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /**
     * 修剪冗余中间容器：
     * 1) 类型为 list 或无具体 label 的 container；
     * 2) 位于某个更大父容器内部，且内部包含已有子卡片。
     */
    private static void pruneRedundantContainers(List<Element> elements) {
        List<Element> toRemove = new ArrayList<>();
        for (Element e : elements) {
            String ty = e.getType();
            if (!"list".equals(ty) && !"container".equals(ty)) continue;
            if ("list".equals(ty)) continue; // 无视觉外框，但其边界用于行内文字对齐。
            String label = e.getLabel() == null ? "" : e.getLabel().trim();
            if (!label.isEmpty() && !label.equals("列表") && !label.equals("卡片")
                    && !label.equals("列表项") && !label.equals("列表卡片")) continue;

            for (Element p : elements) {
                if (p == e) continue;
                if (!"container".equals(p.getType()) && !"modal".equals(p.getType())) continue;
                if (nz(p.getWidth()) * nz(p.getHeight()) <= nz(e.getWidth()) * nz(e.getHeight()) * 1.15) continue;

                if (nz(e.getPositionX()) >= nz(p.getPositionX()) - 8
                        && nz(e.getPositionX()) + nz(e.getWidth()) <= nz(p.getPositionX()) + nz(p.getWidth()) + 8
                        && nz(e.getPositionY()) >= nz(p.getPositionY()) - 8
                        && nz(e.getPositionY()) + nz(e.getHeight()) <= nz(p.getPositionY()) + nz(p.getHeight()) + 8) {
                    // 若父卡片已经包含了该子容器，且父卡片明显更高（说明是外层大卡片包裹单行小条目），直接修剪子容器
                    if (nz(p.getHeight()) >= nz(e.getHeight()) * 1.4 && nz(p.getWidth()) >= 280) {
                        toRemove.add(e);
                        break;
                    }
                    long childCards = elements.stream()
                            .filter(c -> c != e && c != p && ("container".equals(c.getType()) || "image".equals(c.getType())))
                            .filter(c -> nz(c.getPositionX()) >= nz(e.getPositionX()) - 5
                                    && nz(c.getPositionX()) + nz(c.getWidth()) <= nz(e.getPositionX()) + nz(e.getWidth()) + 5
                                    && nz(c.getPositionY()) >= nz(e.getPositionY()) - 5
                                    && nz(c.getPositionY()) + nz(c.getHeight()) <= nz(e.getPositionY()) + nz(e.getHeight()) + 5)
                            .count();
                    if (childCards >= 2) {
                        toRemove.add(e);
                        break;
                    }
                }
            }
        }
        elements.removeAll(toRemove);
    }

    /**
     * 重复文字判定：
     * 1) 同文案 text 已画过且 bbox 明显重叠 → 跳过；
     * 2) text 落在 tabs 元素框内、且文案是该 tabs 的某个选项 → 跳过（分段控件已渲染该选项）。
     */
    private static boolean isDuplicateText(Element e, List<Element> all, List<Element> drawnTexts) {
        if (!"text".equals(e.getType())) return false;
        String label = e.getLabel() == null ? "" : e.getLabel().trim();
        if (label.isEmpty()) return false;
        for (Element d : drawnTexts) {
            String dl = d.getLabel() == null ? "" : d.getLabel().trim();
            if (dl.equals(label) && overlapRatio(e, d) > 0.3) {
                return true;
            }
        }
        for (Element t : all) {
            if (!"tabs".equals(t.getType())) continue;
            String tl = t.getLabel() == null ? "" : t.getLabel();
            boolean segMatch = false;
            for (String seg : tl.split("[|/、,，]")) {
                if (seg.trim().equals(label)) {
                    segMatch = true;
                    break;
                }
            }
            if (!segMatch) continue;
            // e 的中心落在 tabs 框内即认为重复
            double cx = nz(e.getPositionX()) + nz(e.getWidth()) / 2;
            double cy = nz(e.getPositionY()) + nz(e.getHeight()) / 2;
            if (cx >= nz(t.getPositionX()) && cx <= nz(t.getPositionX()) + nz(t.getWidth())
                    && cy >= nz(t.getPositionY()) && cy <= nz(t.getPositionY()) + nz(t.getHeight())) {
                return true;
            }
        }
        // 3) text 与 navbar 标题重复判定：
        // navbar 组件自带页面标题文字，若顶部有与 navbar 同名的 text 元素，属于 AI 重复提取，判定为重复文字跳过
        for (Element nav : all) {
            if (!"navbar".equals(nav.getType())) continue;
            String nl = nav.getLabel() == null ? "" : nav.getLabel().trim();
            if (!nl.isEmpty() && nl.equals(label) && nz(e.getPositionY()) <= 100) {
                return true;
            }
        }
        // 只有当文字与图标中心高度重叠（如文字作为徽章印在图标正中心）才抑制；
        // 图标下方的文字（如底部导航栏 Tab 文字、奖池/背包等功能入口说明文字）是设计稿真实可见的标题，绝不抑制！
        for (Element ic : all) {
            if (!"icon".equals(ic.getType())) continue;
            String il = ic.getLabel() == null ? "" : ic.getLabel().trim();
            if (il.isEmpty() || !il.equalsIgnoreCase(label)) continue;
            double textCy = nz(e.getPositionY()) + nz(e.getHeight()) / 2.0;
            double icCy = nz(ic.getPositionY()) + nz(ic.getHeight()) / 2.0;
            if (textCy > icCy + 4.0) {
                continue; // 文字在图标下方，属于标准入口标题文案，绝对保留
            }
            if (overlapRatio(e, ic) > 0.6) {
                return true;
            }
        }
        return false;
    }

    /**
     * 场景简化（贪心非重叠坍缩）：
     * 1. 装饰块 = 无交互、无文案的 container/image/banner/effect/other/avatar（面积 ≥2000px²）；
     * 2. 载重保护 = 内部容纳了文字/控件/交互元素的装饰块（内容面板，绝不丢弃）；
     * 3. 纯装饰按面积降序贪心：与已保留块重叠 ≥12%（占较小方面积）→ 丢弃。
     * 效果：最大背景框 + 少量不重叠装饰得以保留，堆叠的房间/场景杂物坍缩为背景本身。
     */
    private static void collapseDecorPiles(List<Element> renderList, Map<Long, List<Interaction>> intersByEl) {
        List<Element> decor = new ArrayList<>();
        for (Element e : renderList) {
            String ty = e.getType() == null ? "" : e.getType();
            boolean decorType = "container".equals(ty) || "image".equals(ty) || "banner".equals(ty)
                    || "effect".equals(ty) || "other".equals(ty) || "avatar".equals(ty);
            if (!decorType) continue;
            if (e.getLabel() != null && !e.getLabel().isBlank()) continue;          // 具名=结构面板
            if (intersByEl != null && intersByEl.containsKey(e.getId())
                    && !intersByEl.get(e.getId()).isEmpty()) continue;              // 可交互
            if (nz(e.getWidth()) * nz(e.getHeight()) < 2000) continue;              // 小碎屑直接无视
            decor.add(e);
        }
        if (decor.size() < 4) {
            return;                                                                 // 不够密集，不处理
        }
        // 载重保护
        java.util.Set<Long> protectedIds = new java.util.HashSet<>();
        for (Element d : decor) {
            for (Element o : renderList) {
                if (o == d) continue;
                String ty = o.getType() == null ? "" : o.getType();
                boolean content = "text".equals(ty) || "button".equals(ty) || "input".equals(ty)
                        || "search".equals(ty) || "switch".equals(ty) || "tabs".equals(ty)
                        || "rating".equals(ty) || "badge".equals(ty) || "icon".equals(ty)
                        || (intersByEl != null && intersByEl.containsKey(o.getId())
                                && !intersByEl.get(o.getId()).isEmpty());
                if (!content) continue;
                double cx = nz(o.getPositionX()) + nz(o.getWidth()) / 2;
                double cy = nz(o.getPositionY()) + nz(o.getHeight()) / 2;
                boolean centerIn = cx >= nz(d.getPositionX()) && cx <= nz(d.getPositionX()) + nz(d.getWidth())
                        && cy >= nz(d.getPositionY()) && cy <= nz(d.getPositionY()) + nz(d.getHeight());
                if (centerIn || overlapRatio(d, o) >= 0.5) {
                    protectedIds.add(d.getId());
                    break;
                }
            }
        }
        // 贪心非重叠坍缩
        List<Element> pure = new ArrayList<>(decor.stream()
                .filter(d -> !protectedIds.contains(d.getId())).toList());
        pure.sort(Comparator.comparingDouble((Element e) -> -nz(e.getWidth()) * nz(e.getHeight())));
        List<Element> kept = new ArrayList<>();
        java.util.Set<Long> drop = new java.util.HashSet<>();
        for (Element d : pure) {
            boolean clash = false;
            for (Element k : kept) {
                if (overlapRatio(d, k) >= 0.12) {
                    clash = true;
                    break;
                }
            }
            if (clash) {
                drop.add(d.getId());
            } else {
                kept.add(d);
            }
        }
        if (drop.isEmpty()) {
            return;
        }
        renderList.removeIf(e -> drop.contains(e.getId()));
    }

    /** 包含深度：有几个更大的块包含该块的中心。外层先画、内层后画 */
    private static int containDepth(Element e, List<Element> all) {
        double cx = nz(e.getPositionX()) + nz(e.getWidth()) / 2;
        double cy = nz(e.getPositionY()) + nz(e.getHeight()) / 2;
        double ea = nz(e.getWidth()) * nz(e.getHeight());
        int d = 0;
        for (Element o : all) {
            if (o == e) continue;
            if (nz(o.getWidth()) * nz(o.getHeight()) <= ea) continue;
            if (cx >= nz(o.getPositionX()) && cx <= nz(o.getPositionX()) + nz(o.getWidth())
                    && cy >= nz(o.getPositionY()) && cy <= nz(o.getPositionY()) + nz(o.getHeight())) {
                d++;
            }
        }
        return d;
    }

    /** 两个 bbox 外扩 pad 后是否相交 */
    private static boolean near(Element a, Element b, double pad) {
        return nz(a.getPositionX()) < nz(b.getPositionX()) + nz(b.getWidth()) + pad
                && nz(a.getPositionX()) + nz(a.getWidth()) + pad > nz(b.getPositionX())
                && nz(a.getPositionY()) < nz(b.getPositionY()) + nz(b.getHeight()) + pad
                && nz(a.getPositionY()) + nz(a.getHeight()) + pad > nz(b.getPositionY());
    }

    /** 交叠面积占较小 bbox 的比例 */
    private static double overlapRatio(Element a, Element b) {
        double ix = Math.max(0, Math.min(nz(a.getPositionX()) + nz(a.getWidth()),
                nz(b.getPositionX()) + nz(b.getWidth()))
                - Math.max(nz(a.getPositionX()), nz(b.getPositionX())));
        double iy = Math.max(0, Math.min(nz(a.getPositionY()) + nz(a.getHeight()),
                nz(b.getPositionY()) + nz(b.getHeight()))
                - Math.max(nz(a.getPositionY()), nz(b.getPositionY())));
        double inter = ix * iy;
        double aMin = Math.max(1, Math.min(nz(a.getWidth()) * nz(a.getHeight()),
                nz(b.getWidth()) * nz(b.getHeight())));
        return inter / aMin;
    }

    /* ================= 元素分型渲染 ================= */

    /** 从纯色或渐变背景表达式中安全提取主色 RGB */
    private static int parseBgRgb(String bg) {
        if (bg == null || bg.isBlank()) return 0xFFFFFF;
        var m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(bg);
        if (m.find()) {
            return DesignColorSampler.parseHex(m.group());
        }
        return DesignColorSampler.parseHex(bg);
    }

    /** 从纯色或渐变背景表达式中安全提取亮度，保障文字对比度决不断层 */
    private static int extractLuminance(String bg) {
        if (bg == null || bg.isBlank()) return 255;
        var m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(bg);
        if (m.find()) {
            return DesignColorSampler.luminance(m.group());
        }
        return DesignColorSampler.luminance(bg);
    }

    /** 判断元素实际承载背景的亮度（磨砂白卡片容器内为 245，否则为 pageBg 真实采样亮度） */
    private static int effectiveBackgroundLuminance(Element e, List<Element> containers, String pageBg) {
        double cx = nz(e.getPositionX()) + nz(e.getWidth()) / 2;
        double cy = nz(e.getPositionY()) + nz(e.getHeight()) / 2;
        if (containers != null) {
            for (Element c : containers) {
                double x0 = nz(c.getPositionX()), x1 = x0 + nz(c.getWidth());
                double y0 = nz(c.getPositionY()), y1 = y0 + nz(c.getHeight());
                if (cx >= x0 && cx <= x1 && cy >= y0 && cy <= y1) {
                    String label = c.getLabel() == null ? "" : c.getLabel();
                    if (label.equals("抽奖结果展示底板") || label.equals("限时兑换货架底板")) {
                        return extractLuminance(pageBg);
                    }
                    return 245; // 承载在 88% 磨砂白卡片容器上
                }
            }
        }
        return extractLuminance(pageBg);
    }

    private String renderElement(Page page, Element e,
                                 Map<Long, DesignColorSampler.ElemColors> colors,
                                 Map<Long, List<Interaction>> intersByEl,
                                 Map<Long, String> pageNameById,
                                 double canvasW, String accent,
                                 List<Element> containers, String pageBg, String semanticName, List<Element> all) {
        String type = e.getType() == null ? "other" : e.getType();
        String label = normalizeLabel(e.getLabel()).trim();
        String fill = col(colors, e.getId()).fill();
        String ink = col(colors, e.getId()).ink();

        String pos = String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;",
                nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), nz(e.getHeight()));
        String attr = elementAttrs(e, intersByEl, pageNameById);

        switch (type) {
            case "text": {
                int effectiveBgLum = effectiveBackgroundLuminance(e, containers, pageBg);
                if (e.getGroupKey() != null && e.getGroupKey().contains("-tab-") && isCurrentPageTab(label, page.getName())) {
                    return el(pos + textStyle(e, fill, ink, effectiveBgLum) + "color:" + accent + ";font-weight:600;", "wf-t wf-nav-active", attr, textInner(e));
                }
                return el(pos + textStyle(e, fill, ink, effectiveBgLum)+listTextAlignment(e,all,canvasW), "wf-t", attr, textInner(e));
            }
            case "button": {
                if(closeControl(e))return renderCloseControl(e,all,pos,attr);
                if (tabHit(e,all)) {
                    return el(pos+"z-index:35;background:none;border:0;box-shadow:none;", "wf-btn wf-tab-hit wf-act",attr,"");
                }
                if (label.contains("碎片") && (label.contains("可转化") || label.contains("可转换"))) {
                    return renderFragmentConversion(label, pos, attr);
                }
                if (switchInsideButton(e, all) != null) {
                    return el(pos + "z-index:29;display:flex;align-items:center;padding-left:8px;background:none;",
                            "wf-switch-label wf-act", attr,
                            "<span style='font-size:12px;color:#374151;line-height:1.2;'>" + esc(label) + "</span>");
                }
                if (LayoutNormalizer.isNavigationItem(e, page.getCanvasHeight() == null ? 812 : page.getCanvasHeight())) {
                    boolean active = isCurrentPageTab(label, page.getName());
                    String color = active ? accent : "#6B7280";
                    return el(pos + "background:#FFFFFF;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;",
                            "wf-nav-item" + (active ? " wf-nav-active" : ""), attr,
                            iconSvg(iconKey(label), color, 22) + "<span style='font-size:11px;color:" + color + ";'>" + esc(label) + "</span>");
                }
                if (label.matches("组件|按钮|图标") && nz(e.getWidth()) <= 32 && nz(e.getHeight()) <= 32
                        && all.stream().anyMatch(o -> o != e && "icon".equals(o.getType()) && overlapRatio(e,o) > .7)) {
                    // 用户添加的透明点击覆盖层保留 ID 和交互，结构名称不遮住下方图标。
                    return el(pos, "wf-act", attr, "");
                }
                boolean isSkeleton = page.getName() != null && (page.getName().contains("骨架") || page.getName().contains("加载"));
                if (isSkeleton && label.isEmpty() && nz(e.getPositionY()) >= 700) {
                    return el(pos + "background:#E5E7EB;border-radius:23px;box-sizing:border-box;", "wf-skeleton-bar", attr, "");
                }
                String radius = radius(fill, e.getHeight());
                String btnInk = ink!=null && Math.abs(extractLuminance(ink)-extractLuminance(fill))>=70
                        ? ink : DesignColorSampler.contrastInk(fill);
                double buttonW = nz(e.getWidth()), buttonH = nz(e.getHeight());
                String entryIcon = iconKey(label);
                if (label.matches("(每日)?任务|扭蛋机|其他收集") && buttonW >= 36 && buttonW <= 100
                        && buttonH >= 30 && buttonH <= 100) {
                    return renderIconShortcut(label, entryIcon, pos, attr, accent);
                }
                if (label.matches("手势.*|锁屏|充电.*|待机.*|来电|其他装扮|切换形象|编辑|自定义|表演|大表演|视频铃声|回到桌面|电量效果")
                        && buttonW>=30 && buttonW<=100 && buttonH>=30 && buttonH<=100) {
                    boolean dock=inBottomDock(e,all,canvasW,page.getCanvasHeight()==null?833:page.getCanvasHeight());
                    return renderIconShortcut(label,entryIcon==null?"image":entryIcon,pos,attr,dock&&!isVivid(fill)?"#6B7280":accent,dock);
                }
                boolean compactEntry = entryIcon != null && buttonW >= 36 && buttonW <= 88
                        && buttonH >= 36 && buttonW / buttonH >= .72 && buttonW / buttonH <= 1.4
                        && !label.startsWith("+") && !label.contains("\n") && !label.contains("\\n");
                if (compactEntry) {
                    return renderIllustratedEntry(e, label, entryIcon, pos, attr, fill, accent);
                }

                // 合并识别的奖励入口也拆成“上方图形/数值、下方原文”，不能整框刷成气泡。
                if (buttonW >= 36 && buttonW <= 130 && buttonH >= 44 && buttonH <= 160
                        && (label.contains("\n") || label.startsWith("+"))) {
                    String key = iconKey(label.replace("\n", ""));
                    return renderIllustratedEntry(e, label, key == null ? (label.startsWith("+")?"reward":"entry") : key, pos, attr, "#FFFFFF", accent);
                }

                // 侧边栏细长挂件按钮（右侧贴边悬浮条，如"规则说明"、"兑换记录"）：
                boolean isSideBtn = (nz(e.getPositionX()) >= 335 && nz(e.getWidth()) <= 32 && nz(e.getHeight()) >= 28)
                        || (nz(e.getHeight()) >= nz(e.getWidth()) * 1.4 && nz(e.getHeight()) >= 28 && nz(e.getWidth()) <= 32)
                        || "规则说明".equals(label) || "兑换记录".equals(label);

                if (isSideBtn && label.length() >= 2) {
                    double h = Math.max(54.0, nz(e.getHeight()));
                    double w = Math.max(18.0, nz(e.getWidth()));
                    double x = canvasW - w;
                    double y = nz(e.getPositionY());
                    String sidePos = String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;", x, y, w, h);
                    int sfs = 10;
                    String sideRadius = "border-radius:10px 0 0 10px;";
                    String bg = fill.equals("#F3F4F6") ? "rgba(0,0,0,0.40)" : fill;
                    String vBtnStyle = String.format("background:%s;color:%s;writing-mode:vertical-rl;text-orientation:upright;"
                            + "display:flex;align-items:center;justify-content:center;line-height:1.2;letter-spacing:2px;"
                            + "font-size:%dpx;font-weight:600;%sbox-shadow:0 1px 6px rgba(0,0,0,0.20);cursor:pointer;overflow:hidden;padding:4px 0;box-sizing:border-box;",
                            bg, "#FFFFFF", sfs, sideRadius);
                    return el(sidePos + vBtnStyle, attr.isEmpty() ? "wf-btn wf-side-pill" : "wf-btn wf-side-pill wf-act", attr, esc(label));
                }

                if ("预览".equals(label)) {
                    String previewPill = "background:rgba(255,255,255,0.88);color:#1F2937;border-radius:10px;"
                            + "border:1px solid rgba(255,255,255,0.9);box-shadow:0 1px 4px rgba(0,0,0,0.18);"
                            + "font-size:11px;font-weight:600;display:flex;align-items:center;justify-content:center;cursor:pointer;";
                    return el(pos + previewPill, attr.isEmpty() ? "wf-btn wf-pill" : "wf-btn wf-pill wf-act", attr, esc(label));
                }

                if ("单倍领取".equals(label)) {
                    String claimStyle = "background:#FF6B1A;color:#FFFFFF;"
                            + "font-size:14px;font-weight:600;border-radius:20px;box-shadow:none;"
                            + "display:flex;align-items:center;justify-content:center;cursor:pointer;";
                    return el(pos + claimStyle, attr.isEmpty() ? "wf-btn" : "wf-btn wf-act", attr, esc(label));
                }

                if ("一键入住".equals(label)) {
                    String btnInner = "<span class='wf-btn-label' style='color:#FFFFFF;font-size:16px;font-weight:bold;white-space:nowrap;'>一键入住</span>";
                    return el(pos + "background:#F85A18;border-radius:24px;display:flex;align-items:center;justify-content:center;box-shadow:none;cursor:pointer;", "wf-btn wf-act", attr, btnInner);
                }
                if ("等待".equals(label)) {
                    String btnInner = "<span class='wf-btn-label' style='color:#F87F2C;font-size:11px;font-weight:600;white-space:nowrap;'>等待</span>";
                    return el(pos + "background:rgba(255,255,255,0.9);border:1px solid #F87F2C;border-radius:15px;display:flex;align-items:center;justify-content:center;", "wf-btn", attr, btnInner);
                }
                if ("下载".equals(label)) {
                    String btnInner = "<span class='wf-btn-label' style='color:#FFFFFF;font-size:11px;font-weight:600;white-space:nowrap;'>下载</span>";
                    return el(pos + "background:#F87F2C;border-radius:15px;display:flex;align-items:center;justify-content:center;", "wf-btn", attr, btnInner);
                }
                if ("60%".equals(label)) {
                    String btnInner = "<span class='wf-btn-label' style='color:#F87F2C;font-size:11px;font-weight:600;white-space:nowrap;'>60%</span>";
                    return el(pos + "background:rgba(255,255,255,0.9);border:1px solid #F87F2C;border-radius:15px;display:flex;align-items:center;justify-content:center;", "wf-btn", attr, btnInner);
                }

                boolean hasPrefixIcon = label.matches(".*(?i)x\\d+.*")
                        || label.matches(".*\\d+兑换.*")
                        || label.matches(".*\\d+抽.*");
                double availW = hasPrefixIcon ? Math.max(20, nz(e.getWidth()) - 16) : Math.max(14, nz(e.getWidth()) - 4);
                int bfs = clamp((int) Math.floor((availW - 1) / Math.max(1, textUnits(label))), 8, 14);
                if (nz(e.getWidth()) <= 45 && bfs > 11) {
                    bfs = 11;
                }
                if (nz(e.getWidth()) <= 45 && label.length() >= 4) {
                    bfs = Math.min(8, bfs);
                }
                if (hasPrefixIcon && label.length() >= 4 && bfs > 10) {
                    bfs = 10;
                }
                int icSize = Math.min(10, Math.max(8, (int) (nz(e.getHeight()) * 0.40)));
                String iconBox = hasPrefixIcon
                        ? iconSvg(label.contains("抽") ? "ticket" : "gem", btnInk, icSize + 3) : "";
                String inner = iconBox + "<span class='wf-btn-label' style='color:" + btnInk
                        + ";font-size:" + bfs + "px;white-space:nowrap;line-height:1;'>" + esc(label) + "</span>";

                String btnBg = "background:" + fill + ";box-shadow:none;";
                return el(pos + btnBg + radius + ";display:flex;align-items:center;"
                        + "justify-content:center;padding:0 2px;overflow:hidden;",
                        attr.isEmpty() ? "wf-btn" : "wf-btn wf-act", attr, inner);
            }
            case "icon": {
                if(closeControl(e))return renderCloseControl(e,all,pos,attr);
                if(label.matches("收起|下拉|展开") && nz(e.getWidth())>=nz(e.getHeight())*2){
                    return el(pos+"z-index:46;display:flex;align-items:center;justify-content:center;color:#C7CBD1;", "wf-sheet-chevron wf-act",attr,
                            "<svg data-icon='chevron-down' width='100%' height='100%' viewBox='0 0 32 10' fill='none' stroke='currentColor' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'><path d='M3 2 16 8 29 2'/></svg>");
                }
                if ("下拉把手".equals(label)) {
                    return el(pos + "background:#D1D5DB;border-radius:999px;", "wf-sheet-handle", attr, "");
                }
                boolean light = effectiveBackgroundLuminance(e, containers, pageBg) >= 160;
                String name = semanticName == null ? label : semanticName;
                String key = iconKey(name);
                if (key == null) key = "image";
                boolean nav = e.getGroupKey() != null && e.getGroupKey().contains("-tab-");
                boolean active = nav && isCurrentPageTab(name, page.getName());
                String color = active ? accent : light ? "#6B7280" : "#F3F4F6";
                Element caption = nav ? null : overlappingIconCaption(e, all);
                if (caption != null) {
                    // 小入口的识别框可能包含下方独立文案；图形只使用文案上方的空间，点击范围仍保留原框。
                    int visualHeight = (int)(Math.round(nz(caption.getPositionY())) - Math.round(nz(e.getPositionY())) - 4);
                    int compactSize = Math.min(18, Math.min(visualHeight, (int)Math.round(nz(e.getWidth()))));
                    return el(pos + "align-items:flex-start;", "wf-ic wf-captioned-icon",
                            attr + " aria-label=\"" + esc(name) + "\"",
                            "<span class='wf-icon-graphic' style='width:100%;height:" + visualHeight
                                    + "px;flex-shrink:0;display:flex;align-items:center;justify-content:center;'>"
                                    + iconSvg(key,color,compactSize) + "</span>");
                }
                int size = (int)Math.max(10, Math.min(28, Math.min(nz(e.getWidth()), nz(e.getHeight())) * .78));
                String style = "display:flex;align-items:center;justify-content:center;";
                if (!nav && !key.equals("empty") && Math.min(nz(e.getWidth()),nz(e.getHeight())) >= 32) {
                    style += "background:" + (light ? "#FFFFFF" : "rgba(255,255,255,.10)")
                            + ";border:1px solid " + (light ? "#E5E7EB" : "rgba(255,255,255,.22)") + ";border-radius:50%;";
                }
                return el(pos + style, "wf-ic" + (active ? " wf-nav-active" : ""),
                        attr + " aria-label=\"" + esc(name) + "\"", iconSvg(key,color,size));
            }
            case "avatar": {
                double elW = nz(e.getWidth());
                double elH = nz(e.getHeight());
                int sz = (int) Math.max(14, Math.min(32, Math.min(elW, elH) * 0.55));
                String inner = String.format("<svg viewBox='0 0 24 24' width='%d' height='%d' fill='none' stroke='#9CA3AF' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2'/><circle cx='12' cy='7' r='4'/></svg>", sz, sz);
                return el(pos + "background:#F3F4F6;border:1.5px solid #E5E7EB;border-radius:50%;"
                        + "display:flex;align-items:center;justify-content:center;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.06);box-sizing:border-box;",
                        "wf-img wf-avatar", attr, inner);
            }
            case "background":
                if(attr.contains("data-action=\"back\"") || attr.contains("data-action=\"close\"")){
                    return el(pos+"z-index:1;background:transparent;border:0;cursor:pointer;", "wf-page-dismiss",attr,"");
                }
                // 全屏底图/大背景层：纯视觉衬底，绝不加任何"立绘/场景占位"文字，避免污染功能设置、网络提示等纯文字页面
                return "";
            case "image":
            case "banner": {
                double width = nz(e.getWidth()), height = nz(e.getHeight());
                double area = width * height / Math.max(1, canvasW * (page.getCanvasHeight() == null ? 812 : page.getCanvasHeight()));
                if (area >= .55 || label.matches(".*(房间|场景背景|房屋背景|整页背景).*")) return "";
                String key = iconKey(semanticName == null ? label : semanticName);
                boolean pet = label.matches(".*(宠物|奶龙|角色|精灵|生物|形象).*" );
                if (label.isBlank() && width >= 120 && height >= 120) {
                    pet = all.stream().anyMatch(t -> "text".equals(t.getType()) && t.getLabel() != null
                            && t.getLabel().matches(".*(饥饿|心情|健康)[：:].*") && t.getLabel().length() <= 16);
                }
                if (pet && width >= 90 && height >= 100) {
                    int size = (int)Math.min(width * .75, height * .65);
                    return el(pos + "background:#F3F4F6;border:1px solid #E5E7EB;border-radius:18px;"
                            + "display:flex;align-items:center;justify-content:center;overflow:hidden;",
                            "wf-img wf-hero-art", attr, iconSvg("pet", "#CBD0D8",size));
                }
                if (key != null) {
                    int size = (int)Math.max(10,Math.min(32,Math.min(width,height)*.75));
                    return el(pos + "display:flex;align-items:center;justify-content:center;", "wf-img wf-item-icon",attr,
                            iconSvg(key, iconColor(key), size));
                }
                int size = (int)Math.max(8,Math.min(28,Math.min(width,height)*.45));
                if (width<32 && height<48) {
                    return el(pos+"display:flex;align-items:center;justify-content:center;", "wf-img wf-compact-placeholder",attr,iconSvg("image","#AAB2BE",Math.max(12,size)));
                }
                return el(pos + "background:#F7F8FA;border:1px solid #E5E7EB;border-radius:"
                        + (width>80?12:6) + "px;display:flex;align-items:center;justify-content:center;overflow:hidden;",
                        "wf-img wf-image-placeholder", attr, iconSvg("image", "#C4CAD3",size));
            }
            case "navbar": {
                if (label.isBlank()) return "";
                // 顶部导航栏标题：在返回箭头右侧呈现规范的高保真页面标题文字（留出 15px 舒适呼吸间距）
                return el(pos + "display:flex;align-items:center;padding-left:58px;font-size:18px;font-weight:bold;color:#181818;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;",
                        "wf-t wf-navbar", attr, esc(label));
            }
            case "list":
                // 列表逻辑容器：本身为无边框的布局边界，内部子项已有分割线/卡槽呈现，不画多余的外层虚线框避免重叠压字
                return "";
            case "container": {
                boolean isSkeleton = page.getName() != null && (page.getName().contains("骨架") || page.getName().contains("加载"));
                if (isSkeleton) {
                    double ey = nz(e.getPositionY());
                    double ew = nz(e.getWidth());
                    double eh = nz(e.getHeight());
                    // 1. 骨架半屏抽屉/弹窗底板判定：
                    boolean isSheetBase = label.contains("抽屉") || label.contains("底板") || label.contains("面板") || "骨架弹窗底板".equals(label)
                            || (ew>=canvasW*.95 && eh>=100);
                    if (isSheetBase && ew >= canvasW*.95) {
                        double totalH = page.getCanvasHeight() == null ? 833 : page.getCanvasHeight();
                        double sheetH = Math.max(eh, totalH - ey);
                        String sheetPos = String.format("left:0px;top:%.0fpx;width:%.0fpx;height:%.0fpx;", ey, canvasW, sheetH);
                        return el(sheetPos + "background:#F3F4F6;border-radius:24px 24px 0 0;box-shadow:0 -2px 10px rgba(0,0,0,0.05);border:1px solid rgba(0,0,0,0.04);box-sizing:border-box;", "wf-card wf-bottom-sheet", attr, "");
                    }
                    // 2. 内部嵌套的大内容卡片：作为透明或白色内衬容器，绝不当做骨架条（防止变成巨型灰色方块盖住内部骨架条）
                    if (label.contains("卡片") || (ew >= 300 && eh >= 120)) {
                        return el(pos + "background:#FFFFFF;border-radius:16px;box-sizing:border-box;", "wf-card wf-skeleton-content", attr, "");
                    }
                    // 3. 骨架屏加载条：高度在常规条形范围（<= 90px），或者 label 包含占位/骨架/标题/内容/按钮/信息
                    if (eh <= 90 || label.contains("占位") || label.contains("骨架") || label.contains("信息") || label.contains("按钮") || label.contains("标题") || label.contains("内容")) {
                        return el(pos + "background:#E5E7EB;border-radius:8px;box-sizing:border-box;", "wf-skeleton-bar", attr, "");
                    }
                }
                // 专属业务卡片与面板优先匹配，防止被通用底板通配误伤：
                if (nz(e.getWidth())>=canvasW*.85 && nz(e.getHeight())<=90
                        && nz(e.getPositionY())>=(page.getCanvasHeight()==null?812:page.getCanvasHeight())*.85
                        && all.stream().anyMatch(child -> child!=e && kind(child).matches("button|tabs") && inside(frame(e),frame(child),6))) {
                    return el(pos+"z-index:45;background:#FFFFFF;border-top:1px solid #E5E7EB;border-radius:0;box-shadow:0 -2px 6px rgba(0,0,0,.04);", "wf-dock-bar",attr,"");
                }
                if ("奶龙专属道具卡片".equals(label) || "特殊道具卡片".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:16px;box-shadow:0 2px 10px rgba(0,0,0,0.05);border:1px solid #E5E7EB;box-sizing:border-box;", "wf-card", attr, "");
                }
                if ("宝箱奖励卡片".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:20px;box-shadow:0 4px 16px rgba(0,0,0,0.06);border:1px solid #E5E7EB;box-sizing:border-box;", "wf-card wf-modal-panel", attr, "");
                }
                if ("对话气泡".equals(label) || label.contains("气泡")) {
                    return el(pos + "background:#FFFFFF;border-radius:18px;box-shadow:0 3px 10px rgba(0,0,0,0.12);box-sizing:border-box;", "wf-card wf-bubble", attr, "");
                }
                if ("扭蛋机卡牌橱窗".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:16px;border:1px solid #E5E7EB;box-shadow:0 2px 6px rgba(0,0,0,0.04);box-sizing:border-box;", "wf-card wf-window-panel", attr, "");
                }
                if ("扭蛋卡片".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:8px;border:1px solid rgba(0,0,0,0.08);box-shadow:0 1px 3px rgba(0,0,0,0.04);box-sizing:border-box;", "wf-card wf-card-slot", attr, "");
                }
                if ("扭蛋机出货槽".equals(label)) {
                    return el(pos + "background:#F7F8FA;border-radius:12px;border:1px solid #E5E7EB;box-shadow:none;box-sizing:border-box;", "wf-card wf-slot-panel", attr, "");
                }
                if ("顶部导航栏底板".equals(label)) {
                    return el(pos + "background:rgba(255,255,255,0.85);border-bottom:1px solid #E5E7EB;box-sizing:border-box;", "wf-navbar-bar wf-el", attr, "");
                }
                if ("探险与属性说明底板".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:20px;box-shadow:0 4px 16px rgba(0,0,0,0.06);box-sizing:border-box;", "wf-card wf-modal-panel", attr, "");
                }
                if ("主题试用到期弹窗底板".equals(label) || "混搭模式弹窗底板".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:24px 24px 0 0;box-shadow:0 -2px 10px rgba(0,0,0,0.05);box-sizing:border-box;", "wf-card wf-bottom-sheet", attr, "");
                }
                if ("一键入住吸底栏".equals(label)) {
                    return el(pos + "background:#FFFFFF;box-shadow:0 -4px 16px rgba(0,0,0,0.06);box-sizing:border-box;", "wf-card wf-dock-bar", attr, "");
                }
                if ("任务列表白卡".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:18px;box-shadow:0 3px 14px rgba(0,0,0,0.06);box-sizing:border-box;", "wf-card wf-task-panel", attr, "");
                }
                if ("限时兑换货架底板".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:16px;border:1px solid #E5E7EB;box-shadow:0 2px 6px rgba(0,0,0,0.04);box-sizing:border-box;", "wf-card wf-shelf-panel", attr, "");
                }
                if ("抽奖结果展示底板".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:20px;border:1px solid #E5E7EB;box-shadow:0 4px 16px rgba(0,0,0,0.06);box-sizing:border-box;", "wf-card wf-lottery-panel", attr, "");
                }

                // 真正的半屏抽屉底板（背包抽屉底板、每日任务弹窗底板、骨架弹窗底板等，绝不能匹配任何普通卡片/列表容器）：
                boolean isBottomSheet = "背包抽屉底板".equals(label)
                        || "每日任务弹窗底板".equals(label)
                        || "骨架弹窗底板".equals(label)
                        || (!label.contains("卡片") && !label.contains("列表") && (label.contains("抽屉") || label.endsWith("半屏底板")));
                if (isBottomSheet) {
                    double totalH = page.getCanvasHeight() == null ? 833 : page.getCanvasHeight();
                    double ey = nz(e.getPositionY());
                    double sheetH = Math.max(nz(e.getHeight()), totalH - ey);
                    String sheetPos = String.format("left:0px;top:%.0fpx;width:%.0fpx;height:%.0fpx;", ey, canvasW, sheetH);
                    return el(sheetPos + "background:#FFFFFF;border-radius:24px 24px 0 0;box-shadow:0 -2px 10px rgba(0,0,0,0.05);box-sizing:border-box;", "wf-card wf-bottom-sheet", attr, "");
                }
                // 容器 label 是功能类别（"卡片""弹窗"等说明信息），不属于画面文字，
                // 一律不上画布——说明内容走 annotation（右侧说明框），避免视觉干扰。
                int rad = (nz(e.getWidth()) <= 70 || nz(e.getHeight()) <= 90) ? 8 : 16;
                return el(pos + "background:#FFFFFF;border-radius:" + rad + "px;"
                        + "box-shadow:0 2px 6px rgba(0,0,0,0.04);"
                        + "border:1px solid rgba(0,0,0,0.05);box-sizing:border-box;",
                        "wf-card", attr, "");
            }
            case "divider": {
                double divH = Math.min(2.0, Math.max(1.0, nz(e.getHeight())));
                String divPos = String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;",
                        nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), divH);
                // 使用统一的细分割线，避免列表里重复渐变造成视觉噪音。
                String divBg = effectiveBackgroundLuminance(e, containers, pageBg) >= 145
                        ? "#E9EDF2" : "rgba(255,255,255,0.18)";
                return el(divPos + "background:" + divBg + ";border-radius:1px;", "wf-div", "", "");
            }
            case "search": {
                String inner = "<span class='wf-search-in' style='color:#9CA3AF;font-size:13px;display:flex;align-items:center;gap:6px;'><span style='font-size:16px;'>\u2315</span> " + esc(label.isBlank() ? "搜索" : label) + "</span>";
                return el(pos + "border-radius:20px;background:#FFFFFF;box-shadow:0 2px 8px rgba(0,0,0,0.04);border:1px solid rgba(0,0,0,0.06);"
                        + "display:flex;align-items:center;padding-left:14px;white-space:nowrap;overflow:hidden;", "wf-search", attr, inner);
            }
            case "switch": {
                boolean on = switchOn(label);
                String caption = switchCaption(e, all);
                double sourceW = nz(e.getWidth()), sourceH = nz(e.getHeight());
                double trackW = Math.min(44, sourceW);
                if (!caption.isEmpty()) {
                    double captionSlot = textUnits(caption) * 12 + 12;
                    trackW = Math.min(32, Math.max(20, sourceW - captionSlot - 4));
                }
                double trackH = Math.min(Math.min(26, sourceH), trackW * .6);
                double trackX = nz(e.getPositionX()) + (caption.isEmpty() ? (sourceW - trackW) / 2 : sourceW - trackW - 4);
                double trackY = nz(e.getPositionY()) + (sourceH - trackH) / 2;
                String trackPos = String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;", trackX, trackY, trackW, trackH);
                String trackBg = on ? "#F87F2C" : "#DFE2E6";
                double knobSize = Math.max(6, Math.floor(trackH) - 4);
                String knobStyle = "width:" + (int)knobSize + "px;height:" + (int)knobSize + "px;"
                        + "position:absolute;top:2px;" + (on ? "right:2px;" : "left:2px;")
                        + "background:#FFFFFF;border-radius:50%;"
                        + "box-shadow:0 1px 2px rgba(0,0,0,0.12);";
                String trackShadow = "box-shadow:none;";
                return el(trackPos + "border-radius:999px;background:" + trackBg + ";" + trackShadow,
                        "wf-sw", attr + " data-state='" + (on ? "on" : "off") + "'", "<i class='wf-sw-k' style='" + knobStyle + "'></i>");
            }
            case "radio":
                return renderRadio(page,e,all,pos,attr);
            case "checkbox": {
                boolean checked = !label.matches(".*(未选|未勾|未启用|未开启|禁用).*|(?i)(关|关闭|false|0|off)")
                        && (label.contains("勾") || label.contains("选") || label.contains("已") || label.contains("单抽") || label.matches("(?i)(开|true|1|on)"));
                double boxSz = Math.max(13, Math.min(20, Math.min(nz(e.getWidth()), nz(e.getHeight()))));
                String checkedColor=isVivid(fill)?fill:THEME_ACCENT;
                String boxBg = checked ? checkedColor : "#FFFFFF";
                String box = "<i class='wf-ck-box' style='width:" + (int)boxSz + "px;height:" + (int)boxSz
                        + "px;border-radius:4px;border:1px solid " + (checked ? checkedColor : "#C7CDD5")
                        + ";background:" + boxBg + ";box-shadow:none;"
                        + ";display:inline-flex;align-items:center;justify-content:center;box-sizing:border-box;flex-shrink:0;'>"
                        + (checked ? "<svg viewBox='0 0 16 16' width='10' height='10' fill='none' stroke='#FFF' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'><path d='M13 4L6 11L3 8'/></svg>" : "")
                        + "</i>";
                // 状态词（"开"、"关"等）仅代表勾选状态，严禁当作文字标签输出，防止与右侧独立的"单抽"、"10抽"文本重叠压字
                boolean isStateWord = label.matches("(?i)(开|关|true|false|0|1|on|off)");
                String text = (label.isBlank() || isStateWord) ? "" : "<span class='wf-ck-label' style='font-size:12px;font-weight:500;color:#374151;margin-left:5px;white-space:nowrap;'>" + esc(label) + "</span>";
                return el(pos + "--wf-control-accent:"+checkedColor+";display:flex;align-items:center;cursor:pointer;", "wf-ck" + (checked ? " checked" : ""), attr, box + text);
            }
            case "badge": {
                if (label.matches("[0-9][0-9,.]*") && nz(e.getWidth()) >= nz(e.getHeight()) * 2) {
                    String key = iconKey(semanticName);
                    if (key == null) key = "currency";
                    return el(pos + "background:#FFFFFF;border:1px solid #E5E7EB;border-radius:999px;"
                            + "display:flex;align-items:center;justify-content:center;gap:5px;color:#374151;font-size:12px;font-weight:600;",
                            "wf-badge wf-resource", attr, iconSvg(key,iconColor(key),14) + "<span>" + esc(label) + "</span>");
                }
                boolean isPlus = label.contains("+") || label.contains("88");
                String badgeBg = isPlus ? "#F59E0B"
                        : (label.contains("新") || label.contains("领") || label.contains("热")
                           ? "#EF4444"
                           : (isVivid(fill) ? fill : accent));
                int bfs = clamp(fs(label, nz(e.getHeight()), nz(e.getWidth())), 9, 12);
                return el(pos + "background:" + badgeBg + ";color:#fff;border-radius:999px;display:flex;"
                        + "align-items:center;justify-content:center;white-space:nowrap;overflow:hidden;"
                        + "padding:1px 3px;box-shadow:none;"
                        + "font-size:" + bfs + "px;font-weight:700;line-height:1;border:1px solid rgba(255,255,255,0.25);",
                        "wf-badge", attr, esc(label));
            }
            case "progress": {
                // 分数和百分比保留原文，不能只剩没有数值的色块。
                double pct = 0;
                var fraction=java.util.regex.Pattern.compile("(\\d+)\\s*[/／]\\s*(\\d+)").matcher(label);
                if (fraction.find()) {
                    try { double total=Double.parseDouble(fraction.group(2));pct=total>0?Double.parseDouble(fraction.group(1))/total:0; } catch (NumberFormatException ignored) {}
                }
                if (label.contains("%")) {
                    try {
                        pct = Double.parseDouble(label.replaceAll("[^0-9.]", "")) / 100.0;
                    } catch (Exception ignored) {}
                }
                pct=Math.max(0,Math.min(1,pct));
                double barH = Math.max(label.isBlank()?6:14, Math.min(16, nz(e.getHeight())));
                String trackBg = effectiveBackgroundLuminance(e, containers, pageBg) >= 145
                        ? "rgba(0,0,0,0.08)" : "rgba(255,255,255,0.20)";
                String barFill = isVivid(fill) ? fill : accent;
                int innerW = (int) Math.round((nz(e.getWidth()) - 2) * pct);
                String innerBar = "<div style='width:" + innerW + "px;height:100%;border-radius:999px;"
                        + "background:"+barFill+";'></div>";
                if (!label.isBlank()) innerBar+="<span class='wf-progress-value' style='position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:10px;line-height:1;color:#374151;'>"+esc(label)+"</span>";
                return el(pos + "height:" + (int)barH + "px;border-radius:999px;background:" + trackBg + ";"
                        + "padding:1px;box-shadow:none;display:flex;align-items:center;",
                        "wf-progress", attr, innerBar);
            }
            case "tabs": {
                if (label.contains("动漫耀宝")) {
                    String tabInner = "<span style='color:#F85A18;font-size:15px;font-weight:bold;border-bottom:2.5px solid #F85A18;padding-bottom:2px;'>" + esc(label) + "</span>";
                    return el(pos + "display:flex;align-items:center;background:none;border-radius:0;", "wf-tab-underline", attr, tabInner);
                }
                String[] seg = label.split("[|/、,，]");
                boolean dock=inBottomDock(e,all,canvasW,page.getCanvasHeight()==null?833:page.getCanvasHeight());
                boolean pose=dock && seg.length==2 && java.util.Arrays.stream(seg).allMatch(s -> s.trim().matches("形象[一二12]"));
                double requiredCell=java.util.Arrays.stream(seg).mapToDouble(s -> textUnits(s.trim())*12+16).max().orElse(0);
                boolean vertical=!pose && seg.length>=2 && nz(e.getHeight())>=seg.length*22 && nz(e.getWidth())<requiredCell*seg.length;
                StringBuilder segs = new StringBuilder("<div class='wf-segs'>");
                for (int i = 0; i < seg.length; i++) {
                    String segName = seg[i].trim();
                    String navAttr = "";
                    String uid=tabSegmentUid(page,e,i);
                    var binding=tabSegmentBinding(uid,intersByEl);
                    if(binding!=null){
                        navAttr=" data-wf-element-id=\""+binding.getKey()+"\""+interactionAttrs(binding.getValue(),pageNameById);
                    } else {
                        Long targetPid = findTabSegmentTarget(segName, page.getId(), pageNameById);
                        if (targetPid != null && pageNameById.containsKey(targetPid))
                            navAttr = " data-nav=\"" + esc(pageNameById.get(targetPid)) + "\"";
                    }
                    boolean isOn = (i == 0 && navAttr.isEmpty()) || isCurrentPageTab(segName, page.getName());
                    segs.append("<span class='").append(isOn ? "on" : "").append(pose?" wf-pose-option":"")
                            .append("' data-wf-tab-segment='").append(i).append("' data-wf-uid='").append(uid).append("'").append(navAttr).append(">");
                    if(pose)segs.append("<i class='wf-pose-state'>").append(iconSvg(i==0?"lightbulb":"eye-off","currentColor",21))
                            .append("</i><b class='wf-pose-label'>").append(esc(segName)).append("</b>");
                    else segs.append(esc(segName));
                    segs.append("</span>");
                }
                segs.append("</div>");
                // 容器底色用设计稿采样色（等比提亮，避免硬编码 #F3F4F6 造成"灰容器+白药丸"的视觉塌缩）；
                // 激活段底色接入采样色并通过 CSS 变量下发，保证始终与设计稿观感一致。
                String track = vertical?"none":"#F3F4F6";
                String segBg = vertical?"#FFF1E4":"#FFFFFF";
                String segInk = accent;
                String segStyle = "--seg-bg:" + segBg + ";--seg-ink:" + segInk + ";";
                return el(pos +(dock?"z-index:46;":"")+ "background:" +(pose?"none":track)+ ";border-radius:10px;padding:"+(vertical?0:3)+"px;" + segStyle,
                        "wf-tabs"+(pose?" wf-pose-switch":"")+(vertical?" wf-tabs-vertical":""), attr, segs.toString());
            }
            case "effect":
                // 特效/光晕：氛围装饰，半透明垫底，绝不实心遮挡内容
                return el(pos + "background:" + DesignColorSampler.softTint(fill)
                        + ";opacity:.45;border-radius:14px;", "wf-fx", "", "");
            case "rating": {
                int stars = Math.max(3, (int) Math.round(nz(e.getWidth()) / Math.max(12, nz(e.getHeight()))));
                stars = Math.min(stars, 5);
                int activeStars = Math.max(1, stars - 1);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < stars; i++) {
                    String starColor = i < activeStars ? "#FBBF24" : "rgba(156,163,175,0.4)";
                    sb.append("<span style='color:").append(starColor).append(";text-shadow:0 1px 3px rgba(245,158,11,0.3);margin:0 1px;'>\u2605</span>");
                }
                return el(pos + "font-size:" + clamp((int) nz(e.getHeight()), 10, 22) + "px;display:flex;align-items:center;line-height:1;",
                        "wf-rate", attr, sb.toString());
            }
            default:
                if ((page.getBackgroundImage() != null && page.getBackgroundImage().contains("骨架"))
                        || (page.getName() != null && page.getName().contains("骨架"))) {
                    return el(pos + "background:#E5E7EB;border-radius:8px;box-sizing:border-box;", "wf-skeleton-bar", attr, "");
                }
                // other / effect 等：透明占位块，保持占位与可交互性
                return el(pos + "display:flex;align-items:center;justify-content:center;", "wf-ic",attr,
                        iconSvg("image", "#9CA3AF", (int)Math.max(10, Math.min(24, Math.min(nz(e.getWidth()),nz(e.getHeight()))*.6))));
        }
    }

    /** 常用 UI 图标的规范轻量矢量渲染，避免用设计稿位图切片导致锯齿或截断 */
    private static final String THEME_ACCENT = "#E96928";
    private record Frame(double x, double y, double w, double h) {
        double right() { return x + w; }
        double bottom() { return y + h; }
        double cx() { return x + w / 2; }
        double cy() { return y + h / 2; }
        String style() { return String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;", x,y,w,h); }
    }
    private record NavigationItem(Element anchor, String label, List<Element> parts) {}
    private record CardSlots(double imageX, double imageY, double imageW, double imageH, double titleY) {}
    private record ContentCard(Element host, List<Element> parts, Element frame, boolean illustrated, CardSlots slots) {}
    private record ProductAction(Element anchor, List<Element> parts, Frame frame) {}
    private record ProductData(Element title, Element quality, Element series, Element priceLabel, List<Element> prices, List<Element> extras) {}
    private record DetailRow(Element anchor, List<Element> parts, List<Element> copy, Element picture, Element control,
                             Element rewardIcon, Element rewardValue, Frame frame) {}
    private record DetailList(Element host, List<DetailRow> rows, List<Element> parts, Frame frame, boolean task) {}
    private record InventoryTile(Element host, Element picture, Element caption, List<Element> parts, Frame frame, double graphicHeight) {}
    private record DisplayTile(Element host, Element picture, Element caption, Element state, List<Element> parts, Element frame, double artHeight) {}
    private record PoseDock(Element host,Element tabs,List<Element> parts) {}
    private record CharacterRow(Element host, Element picture, List<Element> copy, List<Element> parts, Element frame) {}
    private record ServiceRow(Element anchor, String label, List<Element> parts) {}
    private record ServiceList(Element host, List<ServiceRow> rows) {}
    private record SpeechMessage(Element anchor, List<Element> parts, Frame frame, double tipX, boolean rightTip) {}
    private record ConversionEntry(Element anchor, Element text, List<Element> parts, Frame frame) {}
    private record RewardEntry(Element icon, Element number, List<Element> captions, List<Element> parts, Frame frame, double visualHeight) {}
    private record SideEntry(Element anchor, Element caption, String label, List<Element> parts, Frame frame) {}
    private record ShortcutEntry(Element anchor, Element caption, List<Element> parts, Frame frame) {}
    private static final class SemanticLayout {
        final java.util.Set<Element> claimed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final List<Element> status = new ArrayList<>();
        final List<NavigationItem> navigation = new ArrayList<>();
        final List<ContentCard> cards = new ArrayList<>();
        final List<ProductAction> productPurchases = new ArrayList<>();
        final List<ProductAction> productMore = new ArrayList<>();
        final List<DetailList> details = new ArrayList<>();
        final List<InventoryTile> inventory = new ArrayList<>();
        final List<DisplayTile> displayTiles = new ArrayList<>();
        final List<PoseDock> poseDocks = new ArrayList<>();
        final List<CharacterRow> characters = new ArrayList<>();
        final List<ServiceList> services = new ArrayList<>();
        final List<SpeechMessage> messages = new ArrayList<>();
        final List<ConversionEntry> conversions = new ArrayList<>();
        final List<RewardEntry> rewards = new ArrayList<>();
        final List<SideEntry> sideEntries = new ArrayList<>();
        final List<ShortcutEntry> shortcuts = new ArrayList<>();
        boolean statusBar;
        String activeNavigation = "";
    }

    /** 先建立组件归属，再画内部内容；旧的坐标修补不再改动已经归入组件的元素。 */
    private SemanticLayout collectSemanticLayout(Page page, List<Element> all, Map<Long,List<Interaction>> inters,
                                                  Map<Long,String> names, double cw, double ch, boolean fragment) {
        SemanticLayout layout = new SemanticLayout();
        Map<Long,List<Interaction>> safeInters = inters == null ? Map.of() : inters;
        String name = page.getName() == null ? "" : page.getName();
        boolean overlay = fragment || name.matches(".*(弹窗|浮层|遮罩|提醒|结果|试用已结束|背包).*" )
                || extractLuminance(pageBackground(page)) < 145;
        layout.statusBar = !overlay && !all.isEmpty() && cw <= 600 && ch >= cw * 1.2;
        if (layout.statusBar) {
            for (Element e : all) {
                Frame r = frame(e);
                boolean statusText = label(e).matches("(?i)(\\d{1,2}[:：]\\d{2}|[345]G|LTE|\\d{1,3}%|Wi-?Fi)")
                        || label(e).matches(".*(状态栏|网络|电量|信号|电池).*" );
                boolean statusGraphic = kind(e).matches("icon|image|other") && r.h<=26
                        && (statusText || r.x>=cw*.55);
                if (r.cy() <= 30 && r.h <= 35 && !hasAction(e,safeInters)
                        && (statusText || statusGraphic)) layout.status.add(e);
            }
            layout.claimed.addAll(layout.status);
        }
        if (!overlay) {
            collectPoseDocks(layout,all,cw,ch);
            collectNavigation(layout, all, safeInters, names, cw, ch);
            List<String> active=layout.navigation.stream().map(NavigationItem::label).filter(s -> isCurrentPageTab(s,name)).distinct().toList();
            if (active.size()==1) layout.activeNavigation=active.get(0);
            if (layout.activeNavigation.isEmpty()) {
                List<String> self=layout.navigation.stream().filter(item -> item.parts.stream().anyMatch(e -> safeInters.getOrDefault(e.getId(),List.of())
                                .stream().anyMatch(it -> page.getId()!=null && page.getId().equals(it.getTargetPageId()))))
                        .map(NavigationItem::label).distinct().toList();
                if (self.size()==1) layout.activeNavigation=self.get(0);
            }
            if (layout.activeNavigation.isEmpty()) {
                // 局部选项卡与底栏共享一项时，该底栏项代表这一组子页面。
                List<String> family=layout.navigation.stream().map(NavigationItem::label).filter(s -> all.stream()
                        .filter(e -> kind(e).equals("tabs")).map(TemplateHtmlRenderer::label)
                        .map(text -> java.util.Arrays.asList(text.split("[|/、,，]")))
                        .anyMatch(tabs -> tabs.contains(s) && tabs.stream().anyMatch(tab -> !tab.isBlank() && name.contains(tab))))
                        .distinct().toList();
                if (family.size()==1) layout.activeNavigation=family.get(0);
            }
        }
        collectConversionEntries(layout, all, safeInters, cw);
        collectDetailLists(layout,all,cw);
        collectInventoryTiles(layout,all,cw,ch);
        collectCharacterRows(layout,all,cw,ch);
        collectDisplayTiles(layout,all,cw,ch);
        if (!overlay) {
            collectShortcutEntries(layout, all, safeInters, cw);
            collectRewardEntries(layout, all);
        }
        collectContentCards(layout, all, cw, ch);
        collectProductActions(layout, all, safeInters, cw);
        collectServiceLists(layout, all, cw);
        if (!overlay) {
            collectSideEntries(layout, all, safeInters, cw);
            collectSpeechMessages(layout, all, cw);
        }
        return layout;
    }

    private static Frame bounds(List<Element> elements) {
        double x=elements.stream().mapToDouble(e -> frame(e).x).min().orElse(0),y=elements.stream().mapToDouble(e -> frame(e).y).min().orElse(0);
        return new Frame(x,y,elements.stream().mapToDouble(e -> frame(e).right()).max().orElse(x)-x,
                elements.stream().mapToDouble(e -> frame(e).bottom()).max().orElse(y)-y);
    }

    private static DetailRow detailRow(Element anchor, List<Element> parts, Element control, Frame box, boolean task) {
        List<Element> text=parts.stream().filter(e -> kind(e).matches("text|badge") && !label(e).isBlank())
                .sorted(Comparator.comparingDouble(e -> frame(e).y)).toList();
        Element reward=task?text.stream().filter(e -> label(e).matches("\\+\\s*\\d+.*")).findFirst().orElse(null):null;
        List<Element> copy=text.stream().filter(e -> e!=reward).toList();
        Element picture=parts.stream().filter(e -> kind(e).matches("image|icon") && frame(e).cx()<box.x+box.w*.3)
                .max(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
        Element currency=task?parts.stream().filter(e -> kind(e).equals("icon") && e!=picture && iconKey(label(e))!=null)
                .min(Comparator.comparingDouble(e -> reward==null?0:Math.abs(frame(e).cx()-frame(reward).x))).orElse(null):null;
        return new DetailRow(anchor,List.copyOf(parts),copy,picture,control,currency,reward,box);
    }

    /** 任务项和设置项按行组成组件，标题、说明、状态、操作始终归属于同一行。 */
    private static void collectDetailLists(SemanticLayout layout, List<Element> all, double cw) {
        List<DetailRow> taskRows=new ArrayList<>();
        for (Element host : all) {
            Frame h=frame(host);
            if (layout.claimed.contains(host) || !kind(host).equals("container") || h.w<cw*.6 || h.h<40 || h.h>110) continue;
            Element control=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("button")
                    && label(e).matches("领取|去完成|已完成") && inside(h,frame(e),2)).findFirst().orElse(null);
            if (control==null) continue;
            List<Element> parts=all.stream().filter(e -> e==host || !layout.claimed.contains(e) && kind(e).matches("text|badge|image|icon|button|divider")
                    && inside(h,frame(e),2)).toList();
            DetailRow row=detailRow(host,parts,control,h,true);
            if (row.copy.isEmpty()) continue;
            taskRows.add(row);layout.claimed.addAll(parts);
        }
        if (!taskRows.isEmpty()) {
            taskRows.sort(Comparator.comparingDouble(row -> row.frame.y));
            List<Element> anchors=taskRows.stream().map(DetailRow::anchor).toList();Frame taskBounds=bounds(anchors);
            Element host=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("list|container")
                            && inside(frame(e),taskBounds,3) && frame(e).h<=taskBounds.h+24 && frame(e).w<=cw)
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            Frame box=host==null?taskBounds:frame(host);
            Frame area=box;
            List<Element> parts=all.stream().filter(e -> e==host || taskRows.stream().anyMatch(row -> row.parts.contains(e))
                    || kind(e).equals("divider") && inside(area,frame(e),3)).toList();
            layout.details.add(new DetailList(host,List.copyOf(taskRows),parts,box,true));layout.claimed.addAll(parts);
        }
        Map<Element,List<Element>> groups=new LinkedHashMap<>();
        for (Element control : all) {
            if (layout.claimed.contains(control) || !kind(control).matches("switch|checkbox")) continue;
            Element owner=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("container") && frame(e).w>=cw*.6
                    && inside(frame(e),frame(control),2)
                    // 这里只收右侧独立控制项；卡片中部的抽奖选项不能把左边角色名收成设置行。
                    && frame(control).cx()>=frame(e).x+frame(e).w*.84)
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if (owner!=null) groups.computeIfAbsent(owner,k -> new ArrayList<>()).add(control);
        }
        for (var group : groups.entrySet()) {
            Element host=group.getKey();Frame h=frame(host);
            List<Element> controls=group.getValue();controls.sort(Comparator.comparingDouble(e -> frame(e).cy()));
            boolean vertical=true;
            for(int i=1;i<controls.size();i++)if(frame(controls.get(i)).cy()-frame(controls.get(i-1)).cy()<28)vertical=false;
            if(!vertical)continue;
            List<DetailRow> rows=new ArrayList<>();
            boolean dedicated=controls.stream().allMatch(e -> kind(e).equals("switch")) && h.h<=controls.size()*100;
            for (int i=0;i<controls.size();i++) {
                Element control=controls.get(i);Frame c=frame(control);
                double top=i==0?h.y:(frame(controls.get(i-1)).cy()+c.cy())/2;
                double bottom=i==controls.size()-1?h.bottom():(frame(controls.get(i+1)).cy()+c.cy())/2;
                Element title=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("text") && !label(e).isBlank()
                                && label(e).length()<=20 && inside(h,frame(e),2) && frame(e).right()<c.x+2
                                && Math.abs(frame(e).cy()-c.cy())<=22 && frame(e).y<=c.cy()+2)
                        .min(Comparator.comparingDouble(e -> Math.abs(frame(e).cy()-c.cy())+(label(e).length()>12?20:0))).orElse(null);
                if (title==null) continue;
                double rowTop=dedicated?top:Math.max(top,frame(title).y-8);
                List<Element> parts=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("text|icon|switch|checkbox|divider")
                        && inside(h,frame(e),2) && frame(e).cy()>=rowTop && frame(e).cy()<bottom).toList();
                double rowBottom=dedicated?bottom:Math.min(bottom,parts.stream().filter(e -> kind(e).equals("text"))
                        .mapToDouble(e -> frame(e).bottom()+8).max().orElse(bottom));
                Frame rowBox=new Frame(h.x,rowTop,h.w,Math.max(42,rowBottom-rowTop));
                DetailRow row=detailRow(title,parts,control,rowBox,false);
                rows.add(row);layout.claimed.addAll(parts);
            }
            if (rows.isEmpty()) continue;
            Frame box=dedicated?h:new Frame(h.x,rows.get(0).frame.y,h.w,rows.get(rows.size()-1).frame.bottom()-rows.get(0).frame.y);
            List<Element> parts=new ArrayList<>();rows.forEach(row -> parts.addAll(row.parts));
            if (dedicated) {parts.add(host);layout.claimed.add(host);}
            layout.details.add(new DetailList(dedicated?host:null,List.copyOf(rows),List.copyOf(parts),box,false));
        }
    }

    private static void collectInventoryTiles(SemanticLayout layout, List<Element> all, double cw, double ch) {
        for (Element host : all) {
            Frame h=frame(host);
            if (layout.claimed.contains(host) || !kind(host).equals("container") || h.w<36 || h.w>120 || h.h<36 || h.h>100
                    || h.w/h.h<.7 || h.w/h.h>1.4 || label(host).matches(".*(奖励|气泡|任务).*") ) continue;
            Element image=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("image") && inside(h,frame(e),2))
                    .max(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if (image==null) continue;
            List<Element> inside=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("text|badge|icon|image") && inside(h,frame(e),2)).toList();
            if (inside.stream().noneMatch(e -> label(e).matches("\\+?\\d+"))) continue;
            Element caption=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("text") && !label(e).isBlank()
                            && !label(e).matches("[+\\d\\s.,/%]+") && frame(e).y>=h.bottom()-2 && frame(e).y<=h.bottom()+30
                            && Math.abs(frame(e).cx()-h.cx())<=h.w*.35 && !label(e).matches(".*(下载|礼物|奖励).*"))
                    .min(Comparator.comparingDouble(e -> frame(e).y-h.bottom()+Math.abs(frame(e).cx()-h.cx()))).orElse(null);
            if (caption==null) continue;
            List<Element> parts=new ArrayList<>(inside);parts.add(host);parts.add(caption);
            layout.inventory.add(new InventoryTile(host,image,caption,List.copyOf(parts),new Frame(h.x,h.y,h.w,Math.max(h.h+24,frame(caption).bottom()-h.y)),h.h));
            layout.claimed.addAll(parts);
        }
    }

    /** 左图右文的角色选择卡保持横排，不能误套成上下结构后把图片区挤成细条。 */
    private static void collectCharacterRows(SemanticLayout layout,List<Element> all,double cw,double ch) {
        for(Element host:all){
            Frame box=frame(host);
            if(layout.claimed.contains(host) || !kind(host).equals("container") || box.w<130 || box.w>cw*.65 || box.h<60 || box.h>150 || box.w/box.h<1.4)continue;
            Element image=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("image") && inside(box,frame(e),3)
                            && frame(e).h>=box.h*.5 && frame(e).w<=box.w*.65 && frame(e).cx()<box.cx())
                    .max(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if(image==null)continue;
            List<Element> copy=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("text|badge") && !label(e).isBlank()
                            && inside(box,frame(e),4) && frame(e).x>=frame(image).right()-8)
                    .sorted(Comparator.comparingDouble(e -> frame(e).y)).toList();
            if(copy.isEmpty() || copy.stream().anyMatch(e -> productPriceText(label(e))))continue;
            List<Element> parts=new ArrayList<>(copy);parts.add(host);parts.add(image);
            all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("icon") && inside(box,frame(e),3)).forEach(parts::add);
            Element frame=new Element();frame.setPositionX(box.x);frame.setPositionY(box.y);frame.setWidth(box.w);frame.setHeight(box.h);
            layout.characters.add(new CharacterRow(host,image,copy,List.copyOf(parts),frame));layout.claimed.addAll(parts);
        }
        LayoutNormalizer.normalizeCardFrames(layout.characters.stream().map(CharacterRow::frame).toList(),cw,ch);
    }

    private static boolean displayStateIcon(Element e) {
        return kind(e).equals("icon") && frame(e).w<=30 && frame(e).h<=30
                && label(e).matches("灯光|灯泡|动效|隐藏|隐藏动效|已启用|未启用|已应用|未应用|锁定|关闭");
    }

    /** 分段切换与独立按钮两种识别结果，均收成同一个“状态图标 + 形象按钮”组件。 */
    private static void collectPoseDocks(SemanticLayout layout,List<Element> all,double cw,double ch) {
        for(Element host:all){
            Frame box=frame(host);
            if(layout.claimed.contains(host) || !kind(host).equals("container") || box.w<cw*.8 || box.y<ch*.85 || box.h>90)continue;
            List<Element> parts=all.stream().filter(e -> e!=host && !layout.claimed.contains(e) && kind(e).matches("icon|text|button|tabs")
                    && inside(box,frame(e),3)).toList();
            Element tabs=parts.stream().filter(e -> kind(e).equals("tabs") && label(e).replaceAll("\\s+","").matches("形象[一1][/|、]形象[二2]")).findFirst().orElse(null);
            boolean buttons=parts.stream().anyMatch(e -> kind(e).equals("button") && label(e).matches("形象[一1]"))
                    && parts.stream().anyMatch(e -> kind(e).equals("button") && label(e).matches("形象[二2]"));
            if(tabs==null && !buttons)continue;
            layout.poseDocks.add(new PoseDock(host,tabs,List.copyOf(parts)));
            layout.claimed.add(host);layout.claimed.addAll(parts);
        }
    }

    /** 动效与锁定预览不再把角标、图片、名称拆成多层独立框。 */
    private static void collectDisplayTiles(SemanticLayout layout, List<Element> all, double cw, double ch) {
        for (Element picture : all) {
            Frame image=frame(picture);
            if (layout.claimed.contains(picture) || !kind(picture).equals("image") || image.w<48 || image.w>170 || image.h<4 || image.h>200
                    || image.h<20 && image.y<ch*.85) continue;
            Element host=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("container") && frame(e).w<=cw*.48
                            && frame(e).h<=ch*.4 && inside(frame(e),image,6))
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(picture);
            Frame h=frame(host);
            if(image.h<80 && image.y<ch*.85 && (host==picture || h.h<80))continue;
            Element caption=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("text") && !label(e).isBlank()
                            && label(e).length()<=24 && Math.abs(frame(e).cx()-h.cx())<=h.w*.4
                            && frame(e).y>=image.y+image.h*.65 && frame(e).y<=Math.max(h.bottom(),image.bottom())+30
                            && !label(e).matches("[+\\d\\s.,/%]+"))
                    .min(Comparator.comparingDouble(e -> Math.abs(frame(e).y-image.bottom()))).orElse(null);
            Element state=all.stream().filter(e -> !layout.claimed.contains(e) && (displayStateIcon(e)
                            || kind(e).equals("badge") && label(e).isBlank() && frame(e).w<=30 && frame(e).h<=30
                            && caption!=null && label(caption).matches("[左右]滑.*")) && (inside(image,frame(e),12)
                            || caption!=null && label(caption).matches("[左右]滑.*") && frame(e).x>=image.x-12 && frame(e).x<=image.x+20
                            && frame(e).y>=image.y-Math.min(64,image.h*.6) && frame(e).y<=image.y+12))
                    .min(Comparator.comparingDouble(e -> frame(e).y)).orElse(null);
            boolean bottomSlice=image.y>=ch*.85 && image.h<80 && all.stream().filter(e -> kind(e).equals("image")
                    && frame(e).h>=80 && frame(e).h<=200 && Math.abs(frame(e).w/image.w-1)<.2).count()>=2;
            if (state==null && (caption==null || !label(caption).matches("[左右]滑.*")) && !bottomSlice) continue;
            List<Element> parts=new ArrayList<>();parts.add(host);if(host!=picture)parts.add(picture);
            if (state!=null)parts.add(state);if(caption!=null)parts.add(caption);
            double artHeight=image.h;
            Frame tileBounds=h;
            if(state!=null && caption!=null && label(caption).matches("[左右]滑.*") && frame(state).y<image.y-16){
                List<DisplayTile> peers=layout.displayTiles.stream().filter(tile -> tile.caption!=null && label(tile.caption).matches("[左右]滑.*")
                        && tile.state!=null && frame(tile.frame).w>=image.w*.9).toList();
                if(peers.size()>=2){
                    DisplayTile column=peers.stream().min(Comparator.comparingDouble(tile -> Math.abs(frame(tile.frame).x-image.x))).orElseThrow();
                    if(Math.abs(frame(column.frame).x-image.x)<=12){
                        double offset=median(peers.stream().map(tile -> frame(tile.frame).y-frame(tile.state).y).toList());
                        tileBounds=new Frame(frame(column.frame).x,frame(state).y+Math.max(0,Math.min(12,offset)),
                                median(peers.stream().map(tile -> frame(tile.frame).w).toList()),h.h);
                        artHeight=Math.max(image.h,median(peers.stream().map(DisplayTile::artHeight).toList()));
                    }
                }
            }
            if (artHeight<80 && image.y>=ch*.85) {
                List<Double> heights=all.stream().filter(e -> kind(e).equals("image") && frame(e).h>=80 && frame(e).h<=200
                        && Math.abs(frame(e).w/image.w-1)<.2).map(e -> frame(e).h).toList();
                artHeight=heights.isEmpty()?120:median(heights);
            }
            double captionHeight=caption==null?0:Math.max(16,Math.ceil(textUnits(label(caption))*9/Math.max(1,tileBounds.w-12))*14);
            Element box=new Element();box.setPositionX(tileBounds.x);box.setPositionY(tileBounds.y);box.setWidth(tileBounds.w);box.setHeight(artHeight+captionHeight+16);
            layout.displayTiles.add(new DisplayTile(host,picture,caption,state,List.copyOf(parts),box,artHeight));
            layout.claimed.addAll(parts);
        }
        for(Element host:all){
            Frame box=frame(host);
            if(layout.claimed.contains(host) || !kind(host).equals("container") || box.y<ch*.85 || box.h<12 || box.h>=80)continue;
            List<DisplayTile> peers=layout.displayTiles.stream().filter(tile -> Math.abs(frame(tile.frame).w/box.w-1)<.2).toList();
            if(peers.size()<2)continue;
            double height=median(peers.stream().map(DisplayTile::artHeight).toList());
            Element frame=new Element();frame.setPositionX(box.x);frame.setPositionY(box.y);frame.setWidth(box.w);frame.setHeight(height+16);
            layout.displayTiles.add(new DisplayTile(host,host,null,null,List.of(host),frame,height));layout.claimed.add(host);
        }
        java.util.Set<Element> normalized=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (DisplayTile seed : List.copyOf(layout.displayTiles)) {
            if(normalized.contains(seed.host))continue;
            List<DisplayTile> group=layout.displayTiles.stream().filter(tile -> !normalized.contains(tile.host)
                    && Math.abs(frame(tile.frame).w/frame(seed.frame).w-1)<=.2 && Math.abs(tile.artHeight/seed.artHeight-1)<=.3).toList();
            double height=Math.round(median(group.stream().map(DisplayTile::artHeight).toList())/4)*4;
            for(DisplayTile tile:group){
                tile.frame.setHeight(frame(tile.frame).h+height-tile.artHeight);
                layout.displayTiles.set(layout.displayTiles.indexOf(tile),new DisplayTile(tile.host,tile.picture,tile.caption,tile.state,tile.parts,tile.frame,height));
                normalized.add(tile.host);
            }
        }
        LayoutNormalizer.normalizeCardFrames(layout.displayTiles.stream().map(DisplayTile::frame).toList(),cw,ch);
    }

    private void collectNavigation(SemanticLayout layout, List<Element> all, Map<Long,List<Interaction>> inters,
                                   Map<Long,String> names, double cw, double ch) {
        Map<String,List<Element>> groups = new LinkedHashMap<>();
        for (Element e : all) {
            if (layout.claimed.contains(e)) continue;
            String group = e.getGroupKey() == null ? "" : e.getGroupKey();
            if (group.contains("-tab-") && frame(e).cy() >= ch * .78) {
                groups.computeIfAbsent(group,k -> new ArrayList<>()).add(e);
            } else if (kind(e).matches("button|text") && navLabel(label(e)) && frame(e).y >= ch * .88) {
                groups.computeIfAbsent("nav-" + e.getId(),k -> new ArrayList<>()).add(e);
            }
        }
        groups.entrySet().removeIf(entry -> !LayoutNormalizer.isNavigationGroup(entry.getValue(),all,ch));
        if (groups.size() < 2) return;
        List<List<Element>> items = new ArrayList<>(groups.values());
        items.sort(Comparator.comparingDouble(g -> g.stream().mapToDouble(e -> frame(e).cx()).average().orElse(0)));
        // 零散的导航点击覆盖层归入相应项目，保留原 ID 和跳转，不再盖一个“组件”按钮。
        for (Element e : all) {
            if (layout.claimed.contains(e) || items.stream().anyMatch(g -> g.contains(e))) continue;
            if (!kind(e).matches("icon|button|text") || frame(e).y < ch*.88 || frame(e).w > cw*.45) continue;
            if (!kind(e).equals("icon") && !label(e).matches("组件|按钮|图标|进入") && !navLabel(label(e))) continue;
            List<Element> nearest = items.stream().min(Comparator.comparingDouble(g -> Math.abs(frame(g.get(0)).cx()-frame(e).cx()))).orElse(null);
            if (nearest != null && Math.abs(frame(nearest.get(0)).cx()-frame(e).cx()) < cw/items.size()*.4) nearest.add(e);
        }
        for (List<Element> parts : items) {
            Element anchor = parts.stream().filter(e -> hasAction(e,inters) && !"member".equals(e.getGroupRole()))
                    .findFirst().orElseGet(() -> parts.stream().filter(e -> "anchor".equals(e.getGroupRole())).findFirst().orElse(parts.get(0)));
            String text = parts.stream().filter(e -> kind(e).equals("text") && !label(e).isEmpty()).map(TemplateHtmlRenderer::label).findFirst()
                    .orElseGet(() -> parts.stream().map(TemplateHtmlRenderer::label)
                            .filter(s -> !s.isEmpty() && !s.matches("组件|按钮|图标|进入")).findFirst().orElse(""));
            if (text.isEmpty()) {
                for (Interaction it : inters.getOrDefault(anchor.getId(),List.of())) {
                    if (it.getTargetPageId() != null) { text = names.getOrDefault(it.getTargetPageId(),""); break; }
                }
            }
            if (text.isEmpty()) text = "入口";
            layout.navigation.add(new NavigationItem(anchor,text,List.copyOf(parts)));
            layout.claimed.addAll(parts);
        }
        for (Element e : all) {
            Frame r=frame(e);
            if (kind(e).matches("background|navbar|container") && r.y >= ch*.88 && r.w >= cw*.85 && r.h <= 100
                    && !hasAction(e,inters)) layout.claimed.add(e);
        }
    }

    private void collectContentCards(SemanticLayout layout, List<Element> all, double cw, double ch) {
        List<Element> hosts = all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).equals("container"))
                .filter(e -> frame(e).w >= 16 && frame(e).w <= cw*.48 && frame(e).h >= 80 && frame(e).h <= ch*.4)
                .filter(e -> !label(e).matches(".*(气泡|开关|状态|信息|服务|任务|机身|橱窗).*") || hasPriceAndPurchase(e,all))
                .filter(e -> all.stream().anyMatch(p -> p!=e && kind(p).equals("image") && frame(p).w*frame(p).h < frame(e).w*frame(e).h
                        && inside(frame(e),frame(p),6))
                        || label(e).matches(".*(商品|图鉴|卡片).*" ) && all.stream().anyMatch(p -> p!=e && kind(p).matches("text|badge") && inside(frame(e),frame(p),6))
                        || hasPriceAndPurchase(e,all))
                .sorted(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).toList();
        Map<Element,List<Element>> members = new java.util.IdentityHashMap<>();
        for (Element host : hosts) members.put(host,new ArrayList<>());
        for (Element e : all) {
            if (layout.claimed.contains(e) || hosts.contains(e) || !kind(e).matches("text|badge|image|icon|progress|rating")) continue;
            Element best=null; double score=Double.MAX_VALUE;
            for (Element host : hosts) {
                Frame h=frame(host),r=frame(e);
                if (r.w > h.w+12 || r.h > h.h+12 || r.cx() < h.x-5 || r.cx() > h.right()+5) continue;
                boolean contained=inside(h,r,10);
                if (!contained) continue;
                double distance=Math.abs(r.cx()-h.cx()) + h.w*h.h*.0001;
                if (distance<score) { best=host;score=distance; }
            }
            if (best!=null) members.get(best).add(e);
        }
        List<Element> frames=new ArrayList<>();
        for (Element host : hosts) {
            List<Element> parts=members.get(host);
            if (parts.isEmpty()) continue;
            Frame h=frame(host);
            double bottom=parts.stream().mapToDouble(e -> frame(e).bottom()+8).max().orElse(h.bottom());
            boolean merchandise=label(host).contains("商品") || parts.stream().anyMatch(e -> label(e).matches(".*(券后价|¥|￥).*"));
            double height=Math.max(h.h,bottom-h.y);
            if (!merchandise) {
                double nextTop=hosts.stream().mapToDouble(e -> frame(e).y).filter(y -> y>h.y+25).min().orElse(Double.MAX_VALUE);
                if (nextTop<Double.MAX_VALUE) height=Math.max(height,Math.min(h.h*1.15,nextTop-h.y-8));
            }
            // 购买/预定是外部操作，卡片外框与其至少留 6px 间隙。
            double controlTop=all.stream().filter(e -> kind(e).equals("button") && label(e).matches(".*(购买|预定|订购|兑换).*"))
                    .filter(e -> frame(e).cx()>=h.x && frame(e).cx()<=h.right() && frame(e).y>=h.bottom()-8 && frame(e).y<=h.bottom()+48)
                    .mapToDouble(e -> frame(e).y).min().orElse(Double.MAX_VALUE);
            if (controlTop<Double.MAX_VALUE) height=Math.min(height,controlTop-h.y-6);
            Element box=new Element(); box.setId(host.getId());box.setPositionX(h.x);box.setPositionY(h.y);box.setWidth(h.w);
            box.setHeight(Math.min(height,ch-h.y));
            frames.add(box);
            layout.cards.add(new ContentCard(host,List.copyOf(parts),box,!merchandise,null));
            layout.claimed.add(host);layout.claimed.addAll(parts);
        }
        LayoutNormalizer.normalizeCardFrames(frames,cw,ch);
        constrainIllustratedPurchaseFrames(layout.cards,all);
        normalizeProductFrames(layout.cards,all,cw);
        normalizeCollectibleSlots(layout.cards);
    }

    private static boolean purchaseButton(Element e) {
        return kind(e).equals("button") && label(e).replaceAll("\\s+","").matches(".*(购买|预定|预订|订购|兑换|抢购).*");
    }

    private static boolean switchOn(String value) {
        return !value.trim().matches("(?i)(off|关|关闭|已关闭|false|0|未开启|未启用|禁用)");
    }

    private static boolean moreLabel(String text) {
        return text.replaceAll("\\s+","").matches("更多[>›»→＞]*");
    }

    private static boolean moreArrow(Element e) {
        String text=label(e).replaceAll("\\s+","");
        return kind(e).equals("icon") && (text.isEmpty() || text.equals("更多") || text.matches("[>›»→＞]+")
                || text.matches("(?i).*(箭头|向右|右侧|下一|next|chevron).*"))
                || kind(e).equals("text") && text.matches("[>›»→＞]+");
    }

    private static boolean tabHit(Element button, List<Element> all) {
        if (label(button).isBlank()) return false;
        return all.stream().filter(e -> kind(e).equals("tabs") && inside(frame(e),frame(button),8))
                .anyMatch(e -> java.util.Arrays.stream(label(e).split("[|/、,，]")).anyMatch(s -> s.trim().equals(label(button))));
    }

    private static double purchaseTop(Element host, List<Element> all) {
        Frame h=frame(host);
        return all.stream().filter(TemplateHtmlRenderer::purchaseButton)
                .filter(e -> frame(e).cx()>=h.x && frame(e).cx()<=h.right()
                        && frame(e).y>=h.bottom()-8 && frame(e).y<=h.bottom()+48)
                .mapToDouble(e -> frame(e).y).min().orElse(Double.MAX_VALUE);
    }

    /** 同款兑换卡先受外部按钮边界约束，再统一整组高度，不能被无按钮的“已拥有”卡反向拉长。 */
    private static void constrainIllustratedPurchaseFrames(List<ContentCard> cards,List<Element> all) {
        java.util.Set<Element> used=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(ContentCard seed:cards){
            if(!seed.illustrated || used.contains(seed.host) || frame(seed.host).w<48)continue;
            Frame s=frame(seed.host);
            List<ContentCard> peers=cards.stream().filter(c -> c.illustrated && !used.contains(c.host)
                    && Math.abs(frame(c.host).h/s.h-1)<=.25 && (Math.abs(frame(c.host).w/s.w-1)<=.2
                    || frame(c.host).w<s.w*.65 && cards.stream().anyMatch(full -> Math.abs(frame(full.host).y-frame(c.host).y)<=12
                    && Math.abs(frame(full.host).w/s.w-1)<=.2 && frame(c.host).x>frame(full.host).right()))).toList();
            double limit=peers.stream().filter(c -> purchaseTop(c.host,all)<Double.MAX_VALUE)
                    .mapToDouble(c -> purchaseTop(c.host,all)-frame(c.frame).y-6).min().orElse(Double.MAX_VALUE);
            if(limit==Double.MAX_VALUE)continue;
            double height=Math.max(48,Math.min(limit,median(peers.stream().map(c -> frame(c.host).h).toList())));
            for(ContentCard card:peers){card.frame.setHeight(height);used.add(card.host);}
        }
    }

    private static boolean hasPriceAndPurchase(Element host, List<Element> all) {
        return purchaseTop(host,all)<Double.MAX_VALUE && all.stream().anyMatch(e -> kind(e).matches("text|badge")
                && label(e).matches(".*[¥￥].*") && inside(frame(host),frame(e),10));
    }

    /** 横向裁切只裁完整卡片的可见部分，不把内部文字挤进窄条；外部购买按钮不移动。 */
    private static void normalizeProductFrames(List<ContentCard> cards, List<Element> all, double cw) {
        List<ContentCard> products=cards.stream().filter(c -> !c.illustrated).toList();
        for (ContentCard card : products) {
            Frame box=frame(card.frame);
            if (box.w<60 && box.right()>=cw-3) {
                List<Double> widths=products.stream().filter(c -> frame(c.frame).w>=60 && Math.abs(frame(c.frame).y-box.y)<12)
                        .map(c -> frame(c.frame).w).toList();
                if (widths.isEmpty()) widths=products.stream().filter(c -> frame(c.frame).w>=60).map(c -> frame(c.frame).w).toList();
                card.frame.setWidth(widths.isEmpty()?108:median(widths));
            }
            double control=purchaseTop(card.host,all);
            card.frame.setHeight(control==Double.MAX_VALUE?156:Math.min(156,Math.max(60,control-box.y-6)));
        }
    }

    /** 商品区的文字入口和外部操作单独成组，不再套通用的立体实心按钮。 */
    private static void collectProductActions(SemanticLayout layout, List<Element> all,
                                              Map<Long,List<Interaction>> inters, double cw) {
        List<ContentCard> products=layout.cards.stream().filter(c -> !c.illustrated).toList();
        if (products.isEmpty()) return;
        for (Element button : all) {
            Frame b=frame(button);
            boolean clipped=kind(button).equals("button") && label(button).isEmpty() && b.w<16 && b.right()>=cw-2;
            if (layout.claimed.contains(button) || (!purchaseButton(button) && !clipped)) continue;
            ContentCard card=products.stream().filter(c -> {
                Frame host=frame(c.host);
                return b.cx()>=host.x && b.cx()<=host.right() && b.y>=host.bottom()-8 && b.y<=host.bottom()+48;
            }).min(Comparator.comparingDouble(c -> Math.abs(b.y-frame(c.host).bottom()))).orElse(null);
            if (card==null) continue;
            double width=b.w;
            if (clipped) {
                List<Double> widths=all.stream().filter(TemplateHtmlRenderer::purchaseButton)
                        .filter(e -> frame(e).w>=36 && Math.abs(frame(e).y-b.y)<8).map(e -> frame(e).w).toList();
                width=widths.isEmpty()?b.h*2.4:median(widths);
            }
            Frame box=frame(card.frame);
            double gap=Math.max(6,Math.min(12,Math.round(b.y-frame(card.host).bottom())));
            List<Element> parts=new ArrayList<>();parts.add(button);
            all.stream().filter(e -> e!=button && !layout.claimed.contains(e) && kind(e).equals("text")
                    && label(e).equals(label(button)) && inside(b,frame(e),1) && !hasAction(e,inters)).forEach(parts::add);
            layout.productPurchases.add(new ProductAction(button,List.copyOf(parts),new Frame(box.cx()-width/2,box.bottom()+gap,width,b.h)));
            layout.claimed.addAll(parts);
        }
        List<Element> more=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("button|text")
                        && moreLabel(label(e)) && frame(e).x>=cw*.55 && frame(e).w<=120 && frame(e).h<=40)
                .sorted(Comparator.comparingInt(e -> kind(e).equals("button")?0:1)).toList();
        for (Element anchor : more) {
            if (layout.claimed.contains(anchor)) continue;
            Frame box=frame(anchor);
            Element caption=all.stream().filter(e -> e!=anchor && !layout.claimed.contains(e) && kind(e).equals("text")
                    && moreLabel(label(e)) && inside(box,frame(e),2)).findFirst().orElse(null);
            List<Element> arrows=all.stream().filter(e -> !layout.claimed.contains(e) && moreArrow(e)
                            && frame(e).w<=28 && frame(e).h<=28 && frame(e).cx()>=box.cx() && frame(e).x<=box.right()+24
                            && Math.abs(frame(e).cy()-box.cy())<=Math.max(10,box.h*.55)).toList();
            List<Element> parts=new ArrayList<>();parts.add(anchor);
            if (caption!=null) parts.add(caption);
            parts.addAll(arrows);
            double right=Math.max(box.right(),arrows.stream().mapToDouble(e -> frame(e).right()).max().orElse(box.right()));
            double width=Math.max(right-box.x,Math.min(48,cw-box.x));
            layout.productMore.add(new ProductAction(anchor,List.copyOf(parts),new Frame(box.x,box.y,width,Math.max(20,box.h))));
            layout.claimed.addAll(parts);
        }
    }

    /** 同尺寸角色卡共用内部槽位，单张图片识别偏矮或缺图不会再改变它的图片区高度。 */
    private static void normalizeCollectibleSlots(List<ContentCard> cards) {
        java.util.Set<Element> used=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ContentCard seed : List.copyOf(cards)) {
            if (!seed.illustrated || used.contains(seed.host)) continue;
            Frame seedBox=frame(seed.host);
            List<ContentCard> group=cards.stream().filter(c -> c.illustrated && !used.contains(c.host))
                    .filter(c -> Math.abs(frame(c.host).w/seedBox.w-1)<=.2 && Math.abs(frame(c.host).h/seedBox.h-1)<=.25).toList();
            List<Double> lefts=new ArrayList<>(),tops=new ArrayList<>(),heights=new ArrayList<>(),titles=new ArrayList<>();
            double width=group.stream().mapToDouble(c -> frame(c.frame).w).min().orElse(seedBox.w);
            double height=group.stream().mapToDouble(c -> frame(c.frame).h).min().orElse(seedBox.h);
            for (ContentCard card : group) {
                Frame host=frame(card.host);
                Element image=card.parts.stream().filter(e -> kind(e).equals("image"))
                        .max(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
                if (image!=null) {
                    lefts.add(frame(image).x-host.x);tops.add(frame(image).y-host.y);heights.add(frame(image).h);
                }
                Element title=collectibleTitle(card);
                if (title!=null) titles.add(frame(title).y-host.y);
            }
            double inset=Math.min(width*.2,Math.max(4,Math.min(12,Math.round(lefts.isEmpty()?8:median(lefts)))));
            double top=Math.max(10,Math.round(tops.isEmpty()?18:median(tops)));
            double titleTop=Math.max(top+24,Math.min(height-24,Math.round(titles.isEmpty()?height-28:median(titles))));
            double imageHeight=Math.max(20,Math.min(Math.round(heights.isEmpty()?seedBox.h*.74:median(heights)),titleTop-top-4));
            CardSlots slots=new CardSlots(inset,top,Math.max(12,width-inset*2),imageHeight,titleTop);
            for (ContentCard card : group) {
                int index=cards.indexOf(card);
                cards.set(index,new ContentCard(card.host,card.parts,card.frame,true,slots));
                used.add(card.host);
            }
        }
    }

    private static Element collectibleTitle(ContentCard card) {
        return card.parts.stream().filter(e -> kind(e).equals("text") && !label(e).isBlank()
                        && !label(e).matches(".*(拥有|试用|\\d+[/／]\\d+).*"))
                .min(Comparator.comparingDouble(e -> frame(e).y)).orElse(null);
    }

    private void collectServiceLists(SemanticLayout layout, List<Element> all, double cw) {
        for (Element heading : all) {
            if (!kind(heading).equals("text") || !label(heading).equals("我的服务")) continue;
            Element host=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("container|list"))
                    .filter(e -> frame(e).w >= cw*.65 && frame(e).y >= frame(heading).bottom()-4
                            && frame(e).y <= frame(heading).bottom()+55 && frame(e).h >= 40)
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if (host==null) continue;
            List<Element> parts=all.stream().filter(e -> e!=host && !layout.claimed.contains(e) && inside(frame(host),frame(e),6))
                    .filter(e -> kind(e).matches("button|icon|text|divider|line")).toList();
            List<Element> anchors=parts.stream().filter(e -> kind(e).equals("button") && frame(e).w >= frame(host).w*.65)
                    .sorted(Comparator.comparingDouble(e -> frame(e).y)).toList();
            if (anchors.isEmpty()) anchors=parts.stream().filter(e -> kind(e).equals("text") && !label(e).isBlank()
                            && label(e).length()<=20 && frame(e).h<=36 && !label(e).matches("[\\d+%.,/ ]+"))
                    .sorted(Comparator.comparingDouble(e -> frame(e).y)).toList();
            if (anchors.isEmpty()) continue;
            List<ServiceRow> rows=new ArrayList<>();
            for (Element anchor : anchors) {
                List<Element> row=parts.stream().filter(e -> Math.abs(frame(e).cy()-frame(anchor).cy()) <= 24).toList();
                rows.add(new ServiceRow(anchor,label(anchor),row));
            }
            layout.services.add(new ServiceList(host,rows));layout.claimed.add(host);layout.claimed.addAll(parts);
        }
    }

    /** 可见名称决定功能入口；单按钮、图标文字、容器图片文字三种识别结构共用组件。 */
    private void collectShortcutEntries(SemanticLayout layout, List<Element> all, Map<Long,List<Interaction>> inters, double cw) {
        for (Element caption : all) {
            if (layout.claimed.contains(caption) || !kind(caption).matches("button|text")
                    || !label(caption).matches("(每日)?任务|扭蛋机|其他收集")) continue;
            Frame r=frame(caption);
            Element host=kind(caption).equals("button") && r.w<=110 && r.h<=110 ? caption : all.stream()
                    .filter(e -> !layout.claimed.contains(e) && kind(e).equals("container") && frame(e).w<=110 && frame(e).h<=110
                            && inside(frame(e),r,5))
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if (host==null) host=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("icon|image")
                            && frame(e).w<=72 && frame(e).h<=72 && frame(e).bottom()<=r.y+6 && r.y-frame(e).bottom()<=32
                            && Math.abs(frame(e).cx()-r.cx())<=22)
                    .min(Comparator.comparingDouble(e -> Math.abs(frame(e).cx()-r.cx())+Math.abs(r.y-frame(e).bottom()))).orElse(null);
            if (host==null && (!kind(caption).equals("button") || r.w>110)) continue;
            Element owner=host==null?caption:host;
            Frame bounds=frame(owner);
            if (bounds.h<30 || owner!=caption && !kind(owner).equals("container")) {
                bounds=new Frame(Math.min(bounds.x,r.x),bounds.y,Math.max(bounds.right(),r.right())-Math.min(bounds.x,r.x),Math.max(bounds.bottom(),r.bottom())-bounds.y);
            }
            Frame area=new Frame(bounds.x-8,bounds.y-8,bounds.w+16,bounds.h+16);
            List<Element> parts=new ArrayList<>();parts.add(owner);if(owner!=caption)parts.add(caption);
            for (Element e : all) {
                if (parts.contains(e) || layout.claimed.contains(e) || !kind(e).matches("icon|image|text|badge|progress|effect")) continue;
                if (inside(area,frame(e),0)) parts.add(e);
            }
            Element anchor=parts.stream().filter(e -> hasAction(e,inters)).findFirst().orElse(owner);
            double width=60,x=Math.max(4,Math.min(cw-width-4,bounds.cx()-width/2));
            layout.shortcuts.add(new ShortcutEntry(anchor,caption,List.copyOf(parts),new Frame(x,bounds.y,width,54)));
            layout.claimed.addAll(parts);
        }
        java.util.Set<Element> used=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ShortcutEntry seed : List.copyOf(layout.shortcuts)) {
            if (used.contains(seed.anchor)) continue;
            List<ShortcutEntry> column=layout.shortcuts.stream().filter(e -> !used.contains(e.anchor) && Math.abs(e.frame.cx()-seed.frame.cx())<=18)
                    .sorted(Comparator.comparingDouble(e -> e.frame.y)).toList();
            if (column.size()<2) continue;
            double top=column.get(0).frame.y,step=Math.floor((column.get(column.size()-1).frame.y-top)/(column.size()-1));
            if (step<56 || step>140) continue;
            double x=median(column.stream().map(e -> e.frame.x).toList());
            for (int i=0;i<column.size();i++) {
                ShortcutEntry e=column.get(i);int index=layout.shortcuts.indexOf(e);
                layout.shortcuts.set(index,new ShortcutEntry(e.anchor,e.caption,e.parts,new Frame(x,top+i*step,60,54)));
                used.add(e.anchor);
            }
        }
    }

    /** 同列的功能入口不依赖 button/icon 类型和识别框长宽比，统一使用图标与外部文字。 */
    private void collectSideEntries(SemanticLayout layout, List<Element> all, Map<Long,List<Interaction>> inters, double cw) {
        List<SideEntry> candidates=new ArrayList<>();
        for (Element anchor : all) {
            Frame r=frame(anchor);
            if (layout.claimed.contains(anchor) || !kind(anchor).matches("button|icon|image")
                    || r.w<24 || r.w>100 || r.h<24 || r.h>100) continue;
            Element caption=kind(anchor).equals("button") && iconKey(label(anchor))!=null ? null : nearbyIconLabel(anchor,all);
            String text=caption==null?label(anchor):label(caption);
            if (!java.util.Set.of("monthly","backpack","message","game","support","sale","star").contains(
                    java.util.Objects.toString(iconKey(text),"")) || text.length()>12) continue;
            if (caption!=null && layout.claimed.contains(caption)) continue;
            List<Element> parts=new ArrayList<>();parts.add(anchor);
            if (caption!=null) parts.add(caption);
            for (Element e : all) {
                if (e==anchor || e==caption || layout.claimed.contains(e)) continue;
                boolean badge=kind(e).equals("badge") && frame(e).w<=42 && frame(e).h<=24
                        && inside(new Frame(r.x-8,r.y-10,r.w+20,r.h+20),frame(e),0);
                boolean backing=kind(e).equals("container") && frame(e).w<=100 && frame(e).h<=100
                        && inside(frame(e),r,3) && !hasIndependentText(e,anchor,caption,all);
                if (badge || backing) parts.add(e);
            }
            double bottom=caption==null?r.bottom():Math.max(r.bottom(),frame(caption).bottom());
            double left=caption==null?r.x:Math.min(r.x,frame(caption).x);
            double right=caption==null?r.right():Math.max(r.right(),frame(caption).right());
            Frame bounds=new Frame(left,r.y,right-left,bottom-r.y);
            SideEntry duplicate=candidates.stream().filter(e -> java.util.Objects.equals(iconKey(e.label),iconKey(text))
                            && Math.abs(e.frame.cx()-bounds.cx())<=12 && Math.abs(e.frame.y-bounds.y)<=16)
                    .findFirst().orElse(null);
            if (duplicate!=null) {
                List<Element> combined=new ArrayList<>(duplicate.parts);
                for (Element part : parts) if (!combined.contains(part)) combined.add(part);
                Element owner=hasAction(anchor,inters) && !hasAction(duplicate.anchor,inters) ? anchor : duplicate.anchor;
                Element copy=duplicate.caption==null?caption:duplicate.caption;
                double x=Math.min(bounds.x,duplicate.frame.x),y=Math.min(bounds.y,duplicate.frame.y);
                double rightEdge=Math.max(bounds.right(),duplicate.frame.right()),bottomEdge=Math.max(bounds.bottom(),duplicate.frame.bottom());
                candidates.set(candidates.indexOf(duplicate),new SideEntry(owner,copy,duplicate.label,List.copyOf(combined),new Frame(x,y,rightEdge-x,bottomEdge-y)));
            } else candidates.add(new SideEntry(anchor,caption,text,List.copyOf(parts),bounds));
        }
        java.util.Set<Element> used=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (SideEntry seed : candidates) {
            if (used.contains(seed.anchor)) continue;
            List<SideEntry> column=candidates.stream().filter(e -> !used.contains(e.anchor))
                    .filter(e -> Math.abs(e.frame.cx()-seed.frame.cx())<=16)
                    .sorted(Comparator.comparingDouble(e -> e.frame.y)).toList();
            if (column.size()<3) continue;
            double first=column.get(0).frame.y,last=column.get(column.size()-1).frame.y;
            double step=(last-first)/(column.size()-1);
            if (step<56 || step>130) continue;
            double width=Math.min(88,Math.max(56,column.stream().mapToDouble(e -> textUnits(e.label)*10+12).max().orElse(56)));
            double center=median(column.stream().map(e -> e.frame.cx()).toList());
            double x=Math.max(4,Math.min(cw-width-4,center-width/2));
            for (int i=0;i<column.size();i++) {
                SideEntry e=column.get(i);
                layout.sideEntries.add(new SideEntry(e.anchor,e.caption,e.label,e.parts,new Frame(x,first+i*step,width,54)));
                layout.claimed.addAll(e.parts);used.add(e.anchor);
            }
        }
    }

    private static boolean hasIndependentText(Element container, Element anchor, Element caption, List<Element> all) {
        return all.stream().anyMatch(e -> e!=anchor && e!=caption && kind(e).equals("text") && !label(e).isBlank()
                && inside(frame(container),frame(e),0));
    }

    /** 同一提示条可能是一个 button，也可能拆成容器、图标、正文、箭头；统一后者的归属。 */
    private void collectConversionEntries(SemanticLayout layout, List<Element> all, Map<Long,List<Interaction>> inters, double cw) {
        for (Element text : all) {
            String copy=label(text);
            if (layout.claimed.contains(text) || !kind(text).equals("text") || !copy.contains("碎片")
                    || !(copy.contains("可转化") || copy.contains("可转换"))) continue;
            Element host=all.stream().filter(e -> !layout.claimed.contains(e) && kind(e).matches("container|button"))
                    .filter(e -> frame(e).w>=cw*.5 && frame(e).h<=80 && inside(frame(e),frame(text),4))
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
            if (host==null) continue;
            List<Element> parts=all.stream().filter(e -> !layout.claimed.contains(e) &&
                    (e==host || kind(e).matches("text|icon|image|badge") && inside(frame(host),frame(e),4))).toList();
            Element anchor=parts.stream().filter(e -> hasAction(e,inters)).findFirst().orElse(host);
            layout.conversions.add(new ConversionEntry(anchor,text,parts,frame(host)));
            layout.claimed.addAll(parts);
        }
    }

    /** 以 +数值确认奖励：图形可以是小图标，也可以是一张包含该数值的完整气泡图片。 */
    private void collectRewardEntries(SemanticLayout layout, List<Element> all) {
        List<RewardEntry> candidates=new ArrayList<>();
        java.util.Set<Element> reserved=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Element number : all) {
            if (layout.claimed.contains(number) || reserved.contains(number) || !kind(number).matches("text|badge|progress|button")
                    || !label(number).matches("\\+\\d+(?:[.,]\\d+)?")) continue;
            Element icon=all.stream().filter(e -> !layout.claimed.contains(e) && !reserved.contains(e) && kind(e).matches("icon|image")
                            && frame(e).w>=10 && frame(e).h>=10 && frame(e).w<=72 && frame(e).h<=72
                            && bonusNumberFits(frame(e),frame(number)))
                    .min(Comparator.comparingDouble(e -> (iconKey(label(e))==null?0:-1000)+Math.abs(frame(e).cx()-frame(number).cx())
                            +Math.abs(frame(e).bottom()-frame(number).y)*.3+frame(e).w*frame(e).h*.0001)).orElse(null);
            if (icon==null) continue;
            Frame r=frame(icon);
            Element graphic=all.stream().filter(e -> !layout.claimed.contains(e) && !reserved.contains(e)
                            && (kind(e).equals("effect") || kind(e).equals("image") && frame(e).w<=72 && frame(e).h<=72
                                || kind(e).equals("container") && label(e).matches(".*(气泡|奖励).*")))
                    .filter(e -> frame(e).w>=24 && frame(e).w<=90 && frame(e).h>=24 && frame(e).h<=90
                            && inside(frame(e),r,4) && frame(number).y<=frame(e).bottom()+24
                            && Math.abs(frame(number).cx()-frame(e).cx())<=16)
                    .min(Comparator.comparingDouble(e -> frame(e).w*frame(e).h+(kind(e).equals("effect")?0:10000))).orElse(icon);
            List<Element> parts=new ArrayList<>(List.of(icon,number));if(graphic!=icon)parts.add(graphic);
            for (Element e : all) {
                if (parts.contains(e) || layout.claimed.contains(e) || reserved.contains(e)) continue;
                boolean wrapper=kind(e).equals("container") && label(e).matches(".*(气泡|奖励).*")
                        && frame(e).w<=100 && frame(e).h<=110 && inside(frame(e),r,4);
                boolean effect=kind(e).equals("effect") && inside(frame(graphic),frame(e),6);
                boolean duplicate=kind(e).matches("icon|image") && frame(e).w<=72 && frame(e).h<=72
                        && inside(frame(graphic),frame(e),4) && bonusNumberFits(frame(e),frame(number));
                if (wrapper || effect || duplicate) parts.add(e);
            }
            Frame box=frame(graphic);double visual=Math.max(36,Math.min(box.w,box.h)),width=Math.max(36,box.w);
            candidates.add(new RewardEntry(icon,number,List.of(),List.copyOf(parts),new Frame(box.cx()-width/2,box.y,width,visual),visual));reserved.addAll(parts);
        }
        Map<RewardEntry,List<Element>> captions=new java.util.IdentityHashMap<>();
        for (RewardEntry entry : candidates) captions.put(entry,new ArrayList<>());
        for (Element text : all) {
            if (layout.claimed.contains(text) || reserved.contains(text) || !kind(text).equals("text")
                    || !java.util.Arrays.stream(label(text).split("\\n")).allMatch(line -> line.trim().matches("下载完成|领取奖励|首开奖励|送个礼物"))) continue;
            RewardEntry best=candidates.stream().filter(entry -> frame(text).y>=entry.frame.bottom()-6
                            && frame(text).bottom()<=entry.frame.bottom()+54 && Math.abs(frame(text).cx()-entry.frame.cx())<=28)
                    .min(Comparator.comparingDouble(entry -> Math.abs(frame(text).cx()-entry.frame.cx()))).orElse(null);
            if (best!=null) captions.get(best).add(text);
        }
        for (RewardEntry entry : candidates) {
            List<Element> copy=captions.get(entry);copy.sort(Comparator.comparingDouble(e -> frame(e).y));
            List<Element> parts=new ArrayList<>(entry.parts);parts.addAll(copy);
            double left=entry.frame.x,right=entry.frame.right();
            for (Element text : copy) {left=Math.min(left,frame(text).x);right=Math.max(right,frame(text).right());}
            layout.rewards.add(new RewardEntry(entry.icon,entry.number,List.copyOf(copy),List.copyOf(parts),
                    new Frame(left,entry.frame.y,right-left,Math.max(36,entry.visualHeight)+(copy.isEmpty()?0:4+copy.stream().mapToInt(e -> label(e).split("\\n").length).sum()*14)),entry.visualHeight));
            layout.claimed.addAll(parts);
        }
    }

    private static boolean bonusNumberFits(Frame visual, Frame number) {
        return Math.abs(number.cx()-visual.cx())<=Math.max(12,Math.min(22,visual.w*.45))
                && number.y>=visual.y+visual.h*.25 && number.y<=visual.bottom()+28;
    }

    private void collectSpeechMessages(SemanticLayout layout, List<Element> all, double cw) {
        for (Element e : all) {
            if (layout.claimed.contains(e) || !kind(e).matches("button|text")) continue;
            String text=label(e);Frame r=frame(e);
            if (!text.matches("^(获得|已获得|解锁|已解锁|收到|新增).*") || text.length()>40 || r.w < r.h*1.7 || r.h>64) continue;
            Frame target=layout.shortcuts.stream().filter(a -> a.frame.bottom()>=r.y && a.frame.y>=r.y-72 && a.frame.y<=r.bottom()+48
                            && Math.max(0,Math.max(a.frame.x-r.right(),r.x-a.frame.right()))<=32)
                    .min(Comparator.comparingDouble(a -> Math.abs(a.frame.y-r.bottom()))).map(ShortcutEntry::frame).orElse(null);
            if (target==null) target=all.stream().filter(a -> a!=e && !layout.claimed.contains(a) && kind(a).equals("button"))
                    .filter(a -> frame(a).bottom()>=r.y && frame(a).y>=r.y-48 && frame(a).y<=r.bottom()+48
                            && Math.min(r.right(),frame(a).right())-Math.max(r.x,frame(a).x)>Math.min(r.w,frame(a).w)*.3)
                    .min(Comparator.comparingDouble(a -> Math.abs(frame(a).y-r.bottom()))).map(TemplateHtmlRenderer::frame).orElse(null);
            if (target==null) continue;
            double width=Math.min(160,Math.max(84,Math.ceil(textUnits(text)*11+16)));
            double height=textUnits(text)*11+16>160?38:24;
            boolean rightTip=target.x-width-10>=4;
            double x=rightTip?target.x-width-10:Math.max(4,Math.min(r.x,cw-width-4));
            double y=rightTip?target.y+18-height/2:Math.min(r.y,target.y-height-8);
            Frame message=new Frame(x,Math.max(32,y),width,height);
            List<Element> parts=new ArrayList<>();parts.add(e);
            for (Element p : all) if (!layout.claimed.contains(p) && kind(p).equals("container")
                    && label(p).matches(".*(气泡|提示|消息).*") && frame(p).h<=80 && frame(p).w<=r.w*2
                    && inside(frame(p),r,4)) parts.add(p);
            layout.messages.add(new SpeechMessage(e,parts,message,Math.max(12,Math.min(width-16,target.cx()-message.x)),rightTip));
            layout.claimed.addAll(parts);
        }
    }

    private String renderSemanticLayout(SemanticLayout layout, Page page, Map<Long,List<Interaction>> inters,
                                        Map<Long,String> names, double cw, double ch, String pageBg) {
        StringBuilder html=new StringBuilder();
        for(PoseDock dock:layout.poseDocks)html.append(renderPoseDock(dock,inters,names));
        for (DetailList list : layout.details) html.append(renderDetailList(list,page,cw,ch,inters,names));
        for (InventoryTile tile : layout.inventory) html.append(renderInventoryTile(tile,inters,names));
        for (CharacterRow row : layout.characters) html.append(renderCharacterRow(row,inters,names));
        for (DisplayTile tile : layout.displayTiles) html.append(renderDisplayTile(tile,inters,names));
        for (ContentCard card : layout.cards) html.append(renderContentCard(card,inters,names));
        for (ProductAction action : layout.productPurchases) html.append(renderProductPurchase(action,inters,names));
        for (ProductAction action : layout.productMore) html.append(renderProductMore(action,inters,names));
        for (ServiceList list : layout.services) html.append(renderServiceList(list,inters,names));
        for (SideEntry entry : layout.sideEntries) html.append(renderSideEntry(entry,inters,names));
        for (ShortcutEntry entry : layout.shortcuts) html.append(renderShortcutEntry(entry,inters,names));
        for (ConversionEntry entry : layout.conversions) {
            html.append(renderFragmentConversion(label(entry.text),entry.frame.style(),elementAttrs(entry.anchor,inters,names),
                    bindingAliases(entry.parts,entry.anchor,inters,names)));
        }
        for (RewardEntry entry : layout.rewards) html.append(renderRewardEntry(entry,inters,names));
        for (SpeechMessage message : layout.messages) {
            String aliases=bindingAliases(message.parts,message.anchor,inters,names);
            html.append(el(message.frame.style()+"z-index:40;background:#FFFFFF;border:1px solid #DDE2E8;border-radius:12px;"
                            +"display:flex;align-items:center;justify-content:center;padding:3px 7px;color:#374151;font-size:11px;line-height:14px;"
                            +"--tip-x:"+(int)message.tipX+"px;pointer-events:auto;"+(hasAction(message.anchor,inters)?"cursor:pointer;":"cursor:default;"),
                    "wf-message",elementAttrs(message.anchor,inters,names),esc(label(message.anchor))+aliases
                            +"<i aria-hidden='true' style='position:absolute;width:8px;height:8px;background:#FFFFFF;"
                            +(message.rightTip?"right:-5px;top:calc(50% - 4px);border-top:1px solid #DDE2E8;":"left:var(--tip-x);bottom:-5px;border-bottom:1px solid #DDE2E8;")
                            +"border-right:1px solid #DDE2E8;transform:rotate(45deg);'></i>"));
        }
        if (!layout.navigation.isEmpty()) {
            html.append("<nav class='wf-canonical-nav wf-el' aria-label='底部导航' style='left:0;top:").append((int)(ch-64))
                    .append("px;width:").append((int)cw).append("px;height:64px;z-index:60;display:flex;background:#FFFFFF;border-top:1px solid #E5E7EB;'>");
            for (NavigationItem item : layout.navigation) {
                boolean active=item.label.equals(layout.activeNavigation);String color=active?THEME_ACCENT:"#7B8492";
                Element text=item.parts.stream().filter(e -> kind(e).equals("text") && label(e).equals(item.label)).findFirst().orElse(null);
                Element icon=item.parts.stream().filter(e -> kind(e).equals("icon")).findFirst().orElse(null);
                List<Element> used=new ArrayList<>();used.add(item.anchor);
                String graphic=iconSvg(iconKey(item.label),color,24);
                if (icon!=null && icon!=item.anchor) {graphic="<span"+elementAttrs(icon,inters,names)+">"+graphic+"</span>";used.add(icon);}
                String caption="<span class='wf-nav-label'"+(text!=null && text!=item.anchor?elementAttrs(text,inters,names):"")
                        +" style='font-size:11px;line-height:14px;'>"+esc(item.label)+"</span>";
                if (text!=null) used.add(text);
                html.append("<div class='wf-nav-item").append(active?" wf-nav-active":"").append("'")
                        .append(elementAttrs(item.anchor,inters,names)).append(" style='position:relative;flex:1;min-width:0;display:flex;flex-direction:column;"
                                +"align-items:center;justify-content:center;gap:4px;cursor:pointer;color:").append(color).append(";'>")
                        .append("<span class='wf-nav-graphic'>").append(graphic).append("</span>").append(caption)
                        .append(bindingAliases(item.parts,used,inters,names)).append("</div>");
            }
            html.append("</nav>");
        }
        if (layout.statusBar) {
            Element time=layout.status.stream().filter(e -> label(e).matches("\\d{1,2}[:：]\\d{2}")).findFirst().orElse(null);
            String ink=extractLuminance(pageBg)<145?"#F3F4F6":"#374151";
            html.append("<div class='wf-statusbar wf-el' data-wf-statusbar='preset' aria-label='状态栏' style='left:0;top:0;width:")
                    .append((int)cw).append("px;height:30px;z-index:70;display:flex;align-items:center;justify-content:space-between;padding:0 24px;color:")
                    .append(ink).append(";font-size:12px;font-weight:600;'>")
                    .append("<span").append(time==null?"":elementAttrs(time,inters,names)).append(">")
                    .append(time==null?"09:41":esc(label(time))).append("</span>")
                    .append("<span style='display:flex;align-items:center;gap:5px;'>")
                    .append(iconSvg("signal",ink,16)).append(iconSvg("wifi",ink,16)).append(iconSvg("battery",ink,22)).append("</span>")
                    .append(bindingAliases(layout.status,time,inters,names)).append("</div>");
        }
        return html.toString();
    }

    private String renderDetailList(DetailList list, Page page, double cw, double ch,
                                    Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        Map<Long,double[]> boxes=new LinkedHashMap<>();
        for (DetailRow row : list.rows) if (kind(row.control).equals("checkbox")) {
            Frame c=frame(row.control);boxes.put(row.control.getId(),new double[]{c.x,c.y,c.w,c.h});
        }
        Map<Long,DesignColorSampler.ElemColors> colors=boxes.isEmpty()?Map.of():sampler.sample(readImage(page),boxes,cw,ch);
        List<Element> shown=new ArrayList<>();if(list.host!=null)shown.add(list.host);
        StringBuilder inner=new StringBuilder();
        for (int index=0;index<list.rows.size();index++) {
            DetailRow row=list.rows.get(index);List<Element> used=new ArrayList<>();used.add(row.anchor);
            inner.append("<div class='wf-detail-row'").append(elementAttrs(row.anchor,inters,names))
                    .append(" style='position:relative;display:flex;align-items:center;gap:10px;flex:").append(row.frame.h)
                    .append(" 1 0px;min-height:0;padding:8px 12px;").append(index==0?"":"border-top:1px solid #E9EDF2;").append("'>");
            if (row.picture!=null || list.task) {
                if(row.picture!=null)used.add(row.picture);
                String key=list.task && !row.copy.isEmpty()?iconKey(label(row.copy.get(0))):row.picture==null?null:iconKey(label(row.picture));
                if (key==null)key=list.task?"task":"settings";
                int size=list.task?34:22;
                inner.append("<span class='wf-detail-icon'").append(row.picture==null || row.picture==row.anchor?"":elementAttrs(row.picture,inters,names))
                        .append(" style='flex-shrink:0;display:flex;align-items:center;justify-content:center;width:").append(size).append("px;height:").append(size)
                        .append("px;").append(list.task?"border:1px solid #E5E7EB;border-radius:8px;background:#F7F8FA;":"")
                        .append("'>").append(iconSvg(key,"#7B8492",22)).append("</span>");
            }
            inner.append("<div class='wf-detail-copy' style='flex:1;min-width:0;'><div class='wf-detail-title-line' style='display:flex;align-items:center;gap:5px;min-width:0;'>");
            if (!row.copy.isEmpty()) {
                Element title=row.copy.get(0);used.add(title);
                inner.append("<span class='wf-detail-title'").append(title==row.anchor?"":elementAttrs(title,inters,names))
                        .append(" style='font-size:13px;line-height:18px;font-weight:500;color:#263244;'>").append(esc(label(title))).append("</span>");
            }
            if (row.rewardIcon!=null) {
                used.add(row.rewardIcon);String key=iconKey(label(row.rewardIcon));
                inner.append("<span class='wf-detail-reward-icon'").append(elementAttrs(row.rewardIcon,inters,names)).append(" style='display:flex;flex-shrink:0;'>")
                        .append(iconSvg(key==null?"reward":key,iconColor(key),12)).append("</span>");
            }
            if (row.rewardValue!=null) {
                used.add(row.rewardValue);
                inner.append("<span class='wf-detail-reward-value'").append(elementAttrs(row.rewardValue,inters,names))
                        .append(" style='font-size:11px;line-height:16px;color:").append(THEME_ACCENT).append(";white-space:nowrap;'>").append(esc(label(row.rewardValue))).append("</span>");
            }
            inner.append("</div>");
            for (int n=1;n<row.copy.size();n++) {
                Element text=row.copy.get(n);used.add(text);
                inner.append("<div class='wf-detail-description'").append(text==row.anchor?"":elementAttrs(text,inters,names))
                        .append(" style='margin-top:3px;font-size:11px;line-height:16px;color:#7B8492;overflow-wrap:anywhere;'>")
                        .append(esc(label(text))).append("</div>");
            }
            inner.append("</div>");used.add(row.control);
            if (list.task) {
                String text=label(row.control);boolean claim=text.equals("领取"),done=text.equals("已完成");
                inner.append("<span class='wf-btn wf-task-control'").append(elementAttrs(row.control,inters,names))
                        .append(" style='position:relative;flex-shrink:0;display:flex;align-items:center;justify-content:center;width:62px;height:28px;border:0;border-radius:999px;box-shadow:none;font-size:12px;line-height:16px;background:")
                        .append(claim?THEME_ACCENT:"#F3F4F6").append(";color:").append(claim?"#FFFFFF":done?"#9CA3AF":THEME_ACCENT).append(";'>")
                        .append(esc(text)).append("</span>");
            } else if (kind(row.control).equals("switch")) {
                boolean on=switchOn(label(row.control));
                inner.append("<span class='wf-sw'").append(elementAttrs(row.control,inters,names)).append(" data-state='").append(on?"on":"off")
                        .append("' style='position:relative;flex-shrink:0;width:36px;height:22px;border-radius:999px;box-shadow:none;background:").append(on?"#F87F2C":"#DFE2E6")
                        .append(";'><i class='wf-sw-k' style='position:absolute;top:2px;").append(on?"right:2px;":"left:2px;")
                        .append("width:18px;height:18px;border-radius:50%;background:#FFFFFF;box-shadow:0 1px 2px rgba(0,0,0,.12);'></i></span>");
            } else {
                boolean checked=label(row.control).matches(".*(已选|已勾|已启用).*|(?i)(开|true|1|on)");
                String sampled=col(colors,row.control.getId()).fill(),color=isVivid(sampled)?sampled:THEME_ACCENT;
                inner.append("<span class='wf-ck").append(checked?" checked":"").append("'").append(elementAttrs(row.control,inters,names))
                        .append(" style='position:relative;flex-shrink:0;display:flex;align-items:center;justify-content:center;width:20px;height:20px;--wf-control-accent:").append(color).append(";'>")
                        .append("<i class='wf-ck-box' style='display:flex;align-items:center;justify-content:center;width:20px;height:20px;border-radius:4px;border:1px solid ")
                        .append(checked?color:"#C7CDD5").append(";background:").append(checked?color:"#FFFFFF").append(";'>")
                        .append(checked?iconSvg("check","#FFFFFF",14):"").append("</i></span>");
            }
            for (Element extra : row.parts) if (!used.contains(extra) && hasAction(extra,inters) && kind(extra).equals("icon")) {
                inner.append("<span").append(elementAttrs(extra,inters,names)).append(" style='display:flex;flex-shrink:0;'>").append(iconSvg(iconKey(label(extra)),"#7B8492",16)).append("</span>");used.add(extra);
            }
            inner.append(bindingAliases(row.parts,used,inters,names)).append("</div>");shown.addAll(row.parts);
        }
        inner.append(bindingAliases(list.parts,shown,inters,names));
        boolean panel=list.host!=null;
        return el(list.frame.style()+"z-index:24;display:flex;flex-direction:column;overflow:hidden;"
                        +(panel?"background:#FFFFFF;border:1px solid #E5E7EB;border-radius:16px;box-shadow:0 2px 6px rgba(0,0,0,.04);":"background:none;border:0;box-shadow:none;"),
                "wf-detail-list "+(list.task?"wf-task-list":"wf-settings-list"),list.host==null?"":elementAttrs(list.host,inters,names),inner.toString());
    }

    private String renderInventoryTile(InventoryTile tile, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        String key=iconKey(label(tile.caption));if(key==null)key="gift";
        List<Element> shown=new ArrayList<>(List.of(tile.host,tile.picture,tile.caption));
        StringBuilder inner=new StringBuilder("<div class='wf-inventory-graphic' style='position:relative;width:100%;height:").append((int)Math.round(tile.graphicHeight))
                .append("px;flex-shrink:0;display:flex;align-items:center;justify-content:center;background:#F7F8FA;border:1px solid #E5E7EB;border-radius:12px;'>")
                .append("<span").append(elementAttrs(tile.picture,inters,names)).append(" style='display:flex;'>").append(iconSvg(key,"#9AA3AF",28)).append("</span>");
        for (Element value : tile.parts) if (!shown.contains(value) && kind(value).matches("text|badge") && !label(value).isBlank()) {
            boolean count=label(value).matches("\\d+");shown.add(value);
            inner.append("<span class='").append(count?"wf-inventory-count":"wf-inventory-bonus").append("'").append(elementAttrs(value,inters,names))
                    .append(" style='position:absolute;right:5px;").append(count?"top:5px;min-width:18px;height:17px;padding:1px 4px;border-radius:5px;background:#FFFFFF;color:#667085;":"bottom:4px;color:"+THEME_ACCENT+";")
                    .append("font-size:10px;line-height:15px;text-align:center;white-space:nowrap;'>").append(esc(label(value))).append("</span>");
        }
        inner.append("</div><span class='wf-inventory-caption'").append(elementAttrs(tile.caption,inters,names))
                .append(" style='display:block;width:100%;font-size:11px;line-height:16px;text-align:center;color:#374151;'>")
                .append(esc(label(tile.caption))).append("</span>").append(bindingAliases(tile.parts,shown,inters,names));
        return el(tile.frame.style()+"z-index:24;display:flex;flex-direction:column;align-items:center;gap:6px;background:none;border:0;box-shadow:none;",
                "wf-inventory-tile",elementAttrs(tile.host,inters,names),inner.toString());
    }

    private String renderCharacterRow(CharacterRow row, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        Frame box=frame(row.frame);double size=Math.max(32,Math.min(box.h-12,box.w*.44));
        List<Element> shown=new ArrayList<>(List.of(row.host,row.picture));
        StringBuilder inner=new StringBuilder("<span class='wf-character-thumbnail'").append(elementAttrs(row.picture,inters,names))
                .append(" style='display:flex;align-items:center;justify-content:center;flex-shrink:0;width:").append((int)Math.round(size))
                .append("px;height:").append((int)Math.round(size)).append("px;background:#F7F8FA;border:1px solid #E5E7EB;border-radius:10px;'>")
                .append(iconSvg("image","#C4CAD3",26)).append("</span><div class='wf-character-copy' style='flex:1;min-width:0;display:flex;flex-direction:column;gap:5px;'>");
        boolean titleShown=false;
        for(Element text:row.copy){
            shown.add(text);boolean title=!titleShown && kind(text).equals("text");if(title)titleShown=true;
            inner.append("<span").append(elementAttrs(text,inters,names)).append(" style='display:block;font-size:").append(title?12:9)
                    .append("px;line-height:").append(title?18:13).append("px;font-weight:").append(title?500:400)
                    .append(";color:").append(title?"#263244":"#7B8492").append(";overflow-wrap:anywhere;'>").append(esc(label(text))).append("</span>");
        }
        for(Element icon:row.parts)if(!shown.contains(icon)&&hasAction(icon,inters)){
            shown.add(icon);inner.append("<span").append(elementAttrs(icon,inters,names)).append(" style='display:flex;'>").append(iconSvg(iconKey(label(icon)),"#7B8492",14)).append("</span>");
        }
        inner.append("</div>").append(bindingAliases(row.parts,shown,inters,names));
        return el(box.style()+"z-index:20;display:flex;align-items:center;gap:8px;padding:6px;background:#FFFFFF;border:1px solid rgba(0,0,0,.05);border-radius:12px;box-shadow:0 2px 6px rgba(0,0,0,.04);overflow:hidden;",
                "wf-card wf-character-row",elementAttrs(row.host,inters,names),inner.toString());
    }

    private String renderPoseDock(PoseDock dock,Map<Long,List<Interaction>> inters,Map<Long,String> names) {
        Frame original=frame(dock.host);double height=Math.max(48,Math.min(80,Math.round(original.h/8)*8));
        Frame box=new Frame(original.x,original.bottom()-height,original.w,height);
        List<Element> shown=new ArrayList<>();shown.add(dock.host);if(dock.tabs!=null)shown.add(dock.tabs);
        StringBuilder inner=new StringBuilder("<div class='wf-tabs wf-pose-switch'").append(dock.tabs==null?"":elementAttrs(dock.tabs,inters,names))
                .append(" style='position:absolute;left:0;top:0;width:100%;height:100%;z-index:46;padding:6px 24px;box-sizing:border-box;background:none;--seg-ink:")
                .append(THEME_ACCENT).append(";'><div class='wf-segs'>");
        String[] labels=dock.tabs==null?new String[]{"形象一","形象二"}:label(dock.tabs).split("[/|、]");
        for(int i=0;i<2;i++){
            String pattern=i==0?"形象[一1]":"形象[二2]";double center=box.x+box.w*(i+.5)/2;
            Element button=dock.parts.stream().filter(e -> kind(e).equals("button") && label(e).matches(pattern)).findFirst().orElse(null);
            Element caption=dock.parts.stream().filter(e -> kind(e).equals("text") && label(e).matches(pattern)).findFirst().orElse(null);
            Element icon=dock.parts.stream().filter(e -> kind(e).equals("icon") && (frame(e).cx()<box.cx())==(center<box.cx()))
                    .min(Comparator.comparingDouble(e -> Math.abs(frame(e).cx()-center))).orElse(null);
            inner.append("<span class='wf-pose-option").append(i==0?" on":"").append("'").append(button==null?"":elementAttrs(button,inters,names))
                    .append("><i class='wf-pose-state'").append(icon==null?"":elementAttrs(icon,inters,names)).append(">")
                    .append(iconSvg(i==0?"lightbulb":"eye-off","currentColor",21)).append("</i><b class='wf-pose-label'")
                    .append(caption==null?"":elementAttrs(caption,inters,names)).append(">")
                    .append(esc(caption!=null?label(caption):button!=null?label(button):labels[i].trim())).append("</b></span>");
            if(button!=null)shown.add(button);if(caption!=null)shown.add(caption);if(icon!=null)shown.add(icon);
        }
        inner.append("</div>").append(bindingAliases(dock.parts,shown,inters,names)).append("</div>");
        return el(box.style()+"z-index:45;background:#FFFFFF;border-top:1px solid #E5E7EB;box-shadow:0 -2px 6px rgba(0,0,0,.04);",
                "wf-dock-bar wf-pose-dock",elementAttrs(dock.host,inters,names),inner.toString());
    }

    private String renderDisplayTile(DisplayTile tile, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        Frame box=frame(tile.frame);List<Element> shown=new ArrayList<>(List.of(tile.host,tile.picture));
        StringBuilder inner=new StringBuilder("<div class='wf-display-art'").append(tile.picture==tile.host?"":elementAttrs(tile.picture,inters,names))
                .append(" style='position:relative;width:100%;height:").append((int)Math.round(tile.artHeight))
                .append("px;flex-shrink:0;display:flex;align-items:center;justify-content:center;background:#F7F8FA;border:1px solid #E5E7EB;border-radius:10px;'>")
                .append(iconSvg("image","#C4CAD3",26));
        if (tile.state!=null) {
            shown.add(tile.state);String state=label(tile.state);boolean inactive=state.matches("未.*|关闭|锁定|隐藏.*");
            String key=state.matches("关闭|隐藏.*|未.*")?"eye-off":state.equals("锁定")?"lock":"lightbulb";
            inner.append("<span class='wf-display-state'").append(elementAttrs(tile.state,inters,names))
                    .append(" data-state='").append(state.isBlank()?"unknown":inactive?"inactive":"active")
                    .append("' style='position:absolute;top:4px;left:4px;display:flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:4px;background:#FFFFFF;'>")
                    .append(iconSvg(key,"#7B8492",13)).append("</span>");
        }
        inner.append("</div>");
        if (tile.caption!=null) {
            shown.add(tile.caption);int font=clamp((int)Math.floor((box.w-12)/textUnits(label(tile.caption))),9,12);
            inner.append("<span class='wf-display-caption'").append(elementAttrs(tile.caption,inters,names))
                    .append(" style='display:block;width:100%;font-size:").append(font)
                    .append("px;line-height:14px;text-align:center;color:#667085;overflow-wrap:anywhere;'>").append(esc(label(tile.caption))).append("</span>");
        }
        inner.append(bindingAliases(tile.parts,shown,inters,names));
        return el(box.style()+"z-index:20;display:flex;flex-direction:column;gap:4px;padding:6px;background:#FFFFFF;border:1px solid rgba(0,0,0,.05);border-radius:12px;box-shadow:0 2px 6px rgba(0,0,0,.04);overflow:hidden;",
                "wf-card wf-display-card",elementAttrs(tile.host,inters,names),inner.toString());
    }

    private String renderProductPurchase(ProductAction action, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        String text=label(action.anchor);
        int font=clamp((int)Math.floor((action.frame.w-8)/Math.max(1,textUnits(text))),10,14);
        return el(action.frame.style()+"z-index:30;background:#F3E5DF;color:#FF6B16;border:0;box-shadow:none;border-radius:999px;"
                        +"display:flex;align-items:center;justify-content:center;padding:0 4px;overflow:hidden;",
                "wf-btn wf-product-purchase wf-act",elementAttrs(action.anchor,inters,names),
                "<span class='wf-btn-label wf-product-purchase-label' style='font-size:"+font+"px;font-weight:500;line-height:1;color:#FF6B16;white-space:nowrap;'>"
                        +esc(text)+"</span>"+bindingAliases(action.parts,action.anchor,inters,names));
    }

    private String renderProductMore(ProductAction action, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        Element caption=action.parts.stream().filter(e -> e!=action.anchor && kind(e).equals("text") && moreLabel(label(e))).findFirst().orElse(null);
        Element arrow=action.parts.stream().filter(TemplateHtmlRenderer::moreArrow)
                .sorted(Comparator.comparingInt(e -> hasAction(e,inters)?0:1)).findFirst().orElse(null);
        List<Element> shown=new ArrayList<>();shown.add(action.anchor);
        if (caption!=null) shown.add(caption);
        if (arrow!=null) shown.add(arrow);
        return el(action.frame.style()+"z-index:30;display:flex;align-items:center;justify-content:flex-end;gap:4px;"
                        +"background:none;border:0;border-radius:0;box-shadow:none;padding:0;",
                "wf-btn wf-more-entry wf-act",elementAttrs(action.anchor,inters,names),
                "<span class='wf-more-label'"+(caption==null?"":elementAttrs(caption,inters,names))
                        +" style='font-size:14px;line-height:20px;font-weight:400;color:#6B7280;white-space:nowrap;'>更多</span>"
                        +"<span class='wf-more-arrow'"+(arrow==null?"":elementAttrs(arrow,inters,names))
                        +" style='display:flex;align-items:center;flex-shrink:0;'>"+iconSvg("next","#B8BBC1",16)+"</span>"
                        +bindingAliases(action.parts,shown,inters,names));
    }

    private String renderContentCard(ContentCard card, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        if (card.illustrated) return renderCollectibleCard(card,inters,names);
        return renderProductCard(card,inters,names);
    }

    private static boolean productQuality(String text) {
        return text.matches("典藏|臻藏|灵动|轻典|至臻|至臻典藏|传说|史诗|稀有|普通|限定|预售");
    }

    private static boolean productPriceText(String text) {
        return text.matches(".*(¥|￥|券后价|售价|价格|原价|到手价|折后价).*");
    }

    private static boolean productStatus(String text) {
        return text.matches("NEW|已拥有|未拥有|售罄|预售|即将上线|限时试用|敬请期待");
    }

    /** 从现有语义与相对位置提取槽位；不改变识别结果，也不补造商品名或金额。 */
    private static ProductData productData(ContentCard card) {
        List<Element> texts=card.parts.stream().filter(e -> kind(e).matches("text|badge|progress") && !label(e).isBlank())
                .sorted(Comparator.comparingDouble((Element e) -> frame(e).y).thenComparingDouble(e -> frame(e).x)).toList();
        Element quality=texts.stream().filter(e -> productQuality(label(e))).findFirst().orElse(null);
        Element priceLabel=texts.stream().filter(e -> label(e).matches("券后价|售价|价格|到手价|折后价")).findFirst().orElse(null);
        Element amount=texts.stream().filter(e -> label(e).matches("(?s).*[¥￥].*"))
                .min(Comparator.comparingDouble(e -> priceLabel==null?-frame(e).y:Math.abs(frame(e).cy()-frame(priceLabel).cy()))).orElse(null);
        List<Element> prices=new ArrayList<>();
        if (amount!=null) {
            prices.add(amount);
            if (label(amount).matches("[¥￥]")) texts.stream().filter(e -> e!=amount && label(e).matches("[\\d.,]+")
                            && frame(e).x>=frame(amount).x && frame(e).x<=frame(amount).right()+40 && Math.abs(frame(e).cy()-frame(amount).cy())<=12)
                    .min(Comparator.comparingDouble(e -> frame(e).x)).ifPresent(prices::add);
        } else if (priceLabel!=null) {
            texts.stream().filter(e -> label(e).matches("[\\d.,]+") && Math.abs(frame(e).cy()-frame(priceLabel).cy())<=12)
                    .min(Comparator.comparingDouble(e -> Math.abs(frame(e).x-frame(priceLabel).right()))).ifPresent(prices::add);
        }
        Element series=texts.stream().filter(e -> kind(e).equals("badge") && e!=quality && e!=priceLabel && !prices.contains(e)
                && !productStatus(label(e)) && !productPriceText(label(e))).findFirst().orElse(null);
        if (series==null) {
            List<Element> candidates=texts.stream().filter(e -> kind(e).equals("text") && e!=quality && e!=priceLabel && !prices.contains(e)
                    && !productStatus(label(e)) && !productPriceText(label(e)) && !label(e).matches("[\\d.,/]+")).toList();
            if (candidates.size()>1 && frame(candidates.get(0)).y<=frame(card.host).y+Math.min(40,frame(card.host).h*.3)
                    && frame(candidates.get(candidates.size()-1)).y-frame(candidates.get(0)).y>12) series=candidates.get(0);
        }
        Element selectedSeries=series;
        Element title=texts.stream().filter(e -> kind(e).equals("text") && e!=quality && e!=priceLabel && e!=selectedSeries && !prices.contains(e)
                        && !productStatus(label(e)) && !productPriceText(label(e)) && !label(e).matches("[\\d.,/]+")
                        && (amount==null || frame(e).y<=frame(amount).y+2))
                .max(Comparator.comparingDouble(e -> frame(e).y)).orElse(null);
        if (title==null) title=texts.stream().filter(e -> kind(e).equals("text") && e!=quality && e!=priceLabel
                && e!=selectedSeries && !prices.contains(e) && !productPriceText(label(e))).findFirst().orElse(null);
        Element selectedTitle=title;
        List<Element> extras=texts.stream().filter(e -> e!=selectedTitle && e!=quality && e!=selectedSeries && e!=priceLabel && !prices.contains(e)).toList();
        return new ProductData(title,quality,series,priceLabel,List.copyOf(prices),extras);
    }

    private String renderProductCard(ContentCard card, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        ProductData data=productData(card);Frame box=frame(card.frame);
        String title=data.title==null?"":label(data.title),series=data.series==null?"":label(data.series);
        String state=data.quality==null?(title.equals("即将上线")?"预售":""):label(data.quality);
        double heroHeight=Math.max(0,Math.min(96,box.h-60)),infoHeight=box.h-heroHeight;
        List<Element> shown=new ArrayList<>();shown.add(card.host);
        Element image=card.parts.stream().filter(e -> kind(e).equals("image")).max(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
        if (image!=null) shown.add(image);
        StringBuilder inner=new StringBuilder("<div class='wf-card-hero'").append(image==null?"":elementAttrs(image,inters,names))
                .append(" style='position:relative;box-sizing:border-box;width:100%;height:").append((int)heroHeight)
                .append("px;min-height:").append((int)heroHeight).append("px;flex-shrink:0;padding:10px 8px 0;display:flex;'>")
                .append("<div class='wf-image-placeholder wf-product-art' style='position:relative;box-sizing:border-box;width:100%;height:100%;min-width:0;background:#F7F8FA;border:1px solid #E5E7EB;border-radius:12px;display:flex;align-items:center;justify-content:center;overflow:hidden;'>")
                .append(iconSvg("image","#C4CAD3",28));
        if (!state.isEmpty()) {
            inner.append("<span class='wf-card-quality'").append(data.quality==null?"":elementAttrs(data.quality,inters,names))
                    .append(" style='position:absolute;left:6px;top:6px;font-size:9px;line-height:12px;font-weight:400;color:#667085;white-space:nowrap;'>")
                    .append(esc(state)).append("</span>");
            if (data.quality!=null) shown.add(data.quality);
        }
        java.util.Set<String> seen=new java.util.HashSet<>(List.of(title,series,state));
        if (!data.extras.isEmpty()) {
            inner.append("<div class='wf-product-notes' style='position:absolute;left:7px;right:7px;bottom:5px;display:flex;flex-wrap:wrap;gap:2px;font-size:9px;line-height:12px;'>");
            for (Element e : data.extras) if (seen.add(label(e)) || hasAction(e,inters)) {
                inner.append("<span").append(elementAttrs(e,inters,names)).append(" style='padding:1px 3px;border-radius:3px;background:rgba(255,255,255,.82);color:#667085;'>").append(esc(label(e))).append("</span>");shown.add(e);
            }
            inner.append("</div>");
        }
        inner.append("</div></div><div class='wf-card-info' style='box-sizing:border-box;width:100%;height:").append((int)infoHeight)
                .append("px;min-height:").append((int)infoHeight).append("px;padding:7px;display:flex;flex-direction:column;justify-content:center;gap:5px;background:#FFFFFF;'>")
                .append("<div class='wf-card-identity' style='display:flex;align-items:center;justify-content:space-between;min-width:0;gap:4px;min-height:18px;'>");
        if (data.title!=null) {
            inner.append("<strong class='wf-card-name wf-product-name'").append(elementAttrs(data.title,inters,names)).append(" title=\"").append(esc(title))
                    .append("\" style='min-width:0;font-size:13px;font-weight:500;line-height:18px;color:#1F2937;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'>").append(esc(title)).append("</strong>");shown.add(data.title);
        }
        if (data.series!=null) {
            inner.append("<span class='wf-product-series'").append(elementAttrs(data.series,inters,names)).append(" title=\"").append(esc(series))
                    .append("\" style='flex-shrink:0;color:#667085;font-size:9px;line-height:12px;font-weight:400;white-space:nowrap;'>").append(esc(series)).append("</span>");shown.add(data.series);
        }
        inner.append("</div>");
        if (!data.prices.isEmpty() || data.priceLabel!=null) {
            inner.append("<div class='wf-card-price-row' style='display:flex;align-items:center;justify-content:space-between;min-width:0;gap:4px;min-height:17px;'><span class='wf-card-price-group' style='display:inline-flex;align-items:baseline;gap:3px;white-space:nowrap;'>");
            if (data.priceLabel!=null) {
                inner.append("<span class='wf-price-label'").append(elementAttrs(data.priceLabel,inters,names)).append(" style='font-size:9px;line-height:12px;font-weight:400;color:#9297A0;'>").append(esc(label(data.priceLabel))).append("</span>");shown.add(data.priceLabel);
            }
            inner.append("<strong class='wf-product-price' style='font-size:12.5px;line-height:17px;font-weight:700;color:#D76B3E;letter-spacing:-.15px;'>");
            for (Element price : data.prices) {
                inner.append("<span").append(elementAttrs(price,inters,names)).append(">");
                var match=java.util.regex.Pattern.compile("^(.*?)([¥￥]\\s*[\\d.,]+)(.*)$").matcher(label(price));
                if (match.matches() && (!match.group(1).isBlank() || !match.group(3).isBlank())) {
                    inner.append("<span class='wf-price-label' style='font-size:9px;line-height:12px;font-weight:400;color:#9297A0;'>").append(esc(match.group(1))).append("</span>")
                            .append("<span>").append(esc(match.group(2))).append("</span><span style='font-size:9px;font-weight:400;color:#9297A0;'>").append(esc(match.group(3))).append("</span>");
                } else inner.append(esc(label(price)));
                inner.append("</span>");shown.add(price);
            }
            inner.append("</strong></span></div>");
        }
        inner.append("</div>").append(bindingAliases(card.parts,shown,inters,names));
        return el(box.style()+"z-index:10;box-sizing:border-box;background:#FFFFFF;border:1px solid rgba(0,0,0,.05);border-radius:16px;box-shadow:0 4px 16px -2px rgba(0,0,0,.06),0 2px 6px -1px rgba(0,0,0,.04);padding:0;display:flex;flex-direction:column;gap:0;overflow:hidden;min-width:0;",
                "wf-content-card wf-product-card",elementAttrs(card.host,inters,names),inner.toString());
    }

    /** 角色卡恢复“图像区域 + 下方名称”的框架，系列和状态仍约束在这张卡内部。 */
    private String renderCollectibleCard(ContentCard card, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        Frame host=frame(card.host),box=frame(card.frame);
        List<Element> texts=card.parts.stream().filter(e -> kind(e).matches("text|badge|progress") && !label(e).isBlank())
                .sorted(Comparator.comparingDouble(e -> frame(e).y)).toList();
        Element title=collectibleTitle(card);
        Element image=card.parts.stream().filter(e -> kind(e).equals("image"))
                .max(Comparator.comparingDouble(e -> frame(e).w*frame(e).h)).orElse(null);
        double titleTop=title==null?box.h-28:Math.max(16,Math.min(box.h-24,frame(title).y-host.y));
        double imageX=image==null?8:Math.max(6,frame(image).x-host.x);
        double imageY=image==null?18:Math.max(10,frame(image).y-host.y);
        double imageW=Math.max(12,Math.min(image==null?box.w-16:frame(image).w,box.w-imageX-6));
        double imageH=Math.max(20,Math.min(image==null?host.h*.74:frame(image).h,titleTop-imageY-1));
        if (card.slots!=null) {
            imageX=card.slots.imageX;imageY=card.slots.imageY;imageW=card.slots.imageW;imageH=card.slots.imageH;titleTop=card.slots.titleY;
        }
        List<Element> shown=new ArrayList<>();shown.add(card.host);if(title!=null)shown.add(title);if(image!=null)shown.add(image);
        java.util.Set<String> seen=new java.util.HashSet<>();if(title!=null)seen.add(label(title));
        StringBuilder top=new StringBuilder(),bottom=new StringBuilder(),fragments=new StringBuilder();
        for (Element e : texts) {
            if (e==title || !seen.add(label(e)) && !hasAction(e,inters)) continue;
            FragmentCount fragmentsCount = parseFragmentCount(label(e));
            if (fragmentsCount != null) {
                fragments.append(renderFragmentCount(e, fragmentsCount, inters, names));
                shown.add(e);
                continue;
            }
            String text="<span"+elementAttrs(e,inters,names)+" style='font-size:9px;line-height:12px;color:#667085;'>"+esc(label(e))+"</span>";
            if (label(e).matches(".*(拥有|试用|\\d+[/／]\\d+).*" ) || kind(e).equals("progress")) bottom.append(text);
            else top.append(text);
            shown.add(e);
        }
        for (Element e : card.parts) if (!shown.contains(e) && !texts.contains(e) && hasAction(e,inters)) {
            top.append("<span").append(elementAttrs(e,inters,names)).append(" style='cursor:pointer;'>")
                    .append(iconSvg(iconKey(label(e)),"#7B8492",16)).append("</span>");shown.add(e);
        }
        String fragmentSlots=fragments.isEmpty()?"":"<div class='wf-collectible-fragments' style='display:flex;justify-content:center;flex-wrap:wrap;gap:4px;'>"+fragments+"</div>";
        String art="<div class='wf-image-placeholder wf-collectible-art'"+(image==null?"":elementAttrs(image,inters,names))
                +" style='position:absolute;left:"+(int)Math.round(imageX)+"px;top:"+(int)Math.round(imageY)+"px;width:"+(int)Math.round(imageW)
                +"px;height:"+(int)Math.round(imageH)+"px;background:#F7F8FA;border:1px solid #E5E7EB;border-radius:12px;"
                +"display:flex;align-items:center;justify-content:center;overflow:hidden;'>"
                +iconSvg("image","#C4CAD3",28)
                +"<div class='wf-collectible-tags' style='position:absolute;left:6px;right:6px;top:6px;display:flex;flex-wrap:wrap;gap:3px;'>"+top+"</div>"
                +"<div class='wf-collectible-state' style='position:absolute;left:6px;right:6px;bottom:6px;display:flex;flex-direction:column;gap:3px;'>"+bottom+fragmentSlots+"</div></div>";
        String caption=title==null?"":"<span class='wf-card-name'"+elementAttrs(title,inters,names)
                +" style='position:absolute;left:8px;right:8px;top:"+(int)Math.round(titleTop)+"px;height:20px;"
                +"display:flex;align-items:center;justify-content:center;white-space:nowrap;font-size:"+clamp((int)Math.floor((box.w-16)/Math.max(1,textUnits(label(title)))),8,13)+"px;line-height:18px;font-weight:500;color:#1F2937;'>"
                +esc(label(title))+"</span>";
        return el(box.style()+"z-index:10;background:#FFFFFF;border-radius:16px;border:1px solid rgba(0,0,0,.05);"
                        +"box-shadow:0 4px 16px -2px rgba(0,0,0,.06),0 2px 6px -1px rgba(0,0,0,.04);overflow:hidden;",
                "wf-content-card wf-collectible-card",elementAttrs(card.host,inters,names),art+caption+bindingAliases(card.parts,shown,inters,names));
    }

    private record FragmentCount(java.math.BigInteger current, java.math.BigInteger required) {
        boolean complete() { return required.signum()>0 && current.compareTo(required)>=0; }
    }

    private static FragmentCount parseFragmentCount(String text) {
        var matcher=java.util.regex.Pattern.compile("^(\\d+)\\s*[/／]\\s*(\\d+)$").matcher(text.trim());
        if (!matcher.matches()) return null;
        return new FragmentCount(new java.math.BigInteger(matcher.group(1)),new java.math.BigInteger(matcher.group(2)));
    }

    private String renderFragmentCount(Element e, FragmentCount count, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        boolean ready=count.complete();
        String background=ready?"#FFF1E4":"#F3F4F6";
        String border=ready?"#FDBA74":"#D9DEE6";
        String ink=ready?"#9A4C12":"#667085";
        return "<span class='wf-fragment-progress "+(ready?"wf-fragment-ready":"wf-fragment-pending")+"'"
                +elementAttrs(e,inters,names)+" data-ready='"+ready+"' aria-label='碎片 "+esc(label(e))+(ready?"，已集齐":"，未集齐")
                +"' style='display:inline-flex;align-items:center;justify-content:center;min-width:34px;max-width:100%;height:18px;"
                +"padding:1px 6px;border:1px solid "+border+";border-radius:5px;background:"+background+";color:"+ink+";'>"
                +"<span class='wf-fragment-value' style='font-size:10px;line-height:12px;font-weight:600;white-space:nowrap;'>"+esc(label(e))+"</span></span>";
    }

    private String renderFragmentConversion(String label, String pos, String attr) {
        return renderFragmentConversion(label,pos,attr,"");
    }

    private String renderFragmentConversion(String label, String pos, String attr, String aliases) {
        return el(pos+"z-index:30;display:flex;align-items:center;gap:10px;padding:0 14px;background:#FFFFFF;"
                        +"border:1px solid #E3E7ED;border-radius:12px;cursor:pointer;",
                "wf-conversion-entry wf-act",attr,
                "<span class='wf-conversion-icon' style='display:flex;flex-shrink:0;'>"+iconSvg("fragment",THEME_ACCENT,22)+"</span>"
                        +"<span class='wf-conversion-label' style='flex:1;min-width:0;font-size:13px;line-height:18px;color:#374151;text-align:left;'>"
                        +esc(label)+"</span><span class='wf-conversion-arrow' style='display:flex;flex-shrink:0;'>"+iconSvg("next","#9AA3AF",18)+"</span>"+aliases);
    }

    private String renderShortcutEntry(ShortcutEntry entry, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        List<Element> shown=new ArrayList<>();shown.add(entry.anchor);
        StringBuilder inner=new StringBuilder("<span class='wf-entry-graphic' style='width:22px;height:22px;flex-shrink:0;display:flex;align-items:center;justify-content:center;'>")
                .append(iconSvg(iconKey(label(entry.caption)),THEME_ACCENT,22)).append("</span><span class='wf-entry-caption'");
        if (entry.caption!=entry.anchor) {inner.append(elementAttrs(entry.caption,inters,names));shown.add(entry.caption);}
        inner.append(" style='font-size:10px;line-height:14px;color:#374151;white-space:nowrap;'>").append(esc(label(entry.caption))).append("</span>");
        for (Element extra : entry.parts) {
            if (shown.contains(extra) || !kind(extra).matches("text|badge|progress") || label(extra).isBlank() || label(extra).equals(label(entry.caption))) continue;
            boolean count=label(extra).matches("\\d+[/／]\\d+");
            inner.append("<span class='").append(count?"wf-shortcut-progress":"wf-entry-badge").append("'").append(elementAttrs(extra,inters,names))
                    .append(" style='position:absolute;").append(count?"top:41px;left:0;width:100%;color:#8A5A22;":"top:-6px;right:0;padding:0 3px;border-radius:7px;background:#F06449;color:#FFFFFF;")
                    .append("font-size:9px;line-height:12px;text-align:center;white-space:nowrap;'>").append(esc(label(extra))).append("</span>");shown.add(extra);
        }
        inner.append(bindingAliases(entry.parts,shown,inters,names));
        return el(entry.frame.style()+"z-index:30;display:flex;flex-direction:column;align-items:center;gap:4px;background:none;border:0;box-shadow:none;",
                "wf-entry wf-shortcut",elementAttrs(entry.anchor,inters,names),inner.toString());
    }

    private String renderSideEntry(SideEntry entry, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        List<Element> shown=new ArrayList<>();shown.add(entry.anchor);
        StringBuilder inner=new StringBuilder("<span class='wf-entry-graphic' style='box-sizing:border-box;width:36px;height:36px;flex-shrink:0;")
                .append("border-radius:50%;border:1px solid #E5E7EB;background:#FFFFFF;display:flex;align-items:center;justify-content:center;'>")
                .append(iconSvg(iconKey(entry.label),THEME_ACCENT,22)).append("</span>")
                .append("<span class='wf-entry-caption'");
        if (entry.caption!=null) {inner.append(elementAttrs(entry.caption,inters,names));shown.add(entry.caption);}
        inner.append(" style='font-size:10px;line-height:14px;color:#374151;white-space:nowrap;'>").append(esc(entry.label)).append("</span>");
        for (Element badge : entry.parts) {
            if (badge==entry.caption || !kind(badge).equals("badge") || label(badge).isBlank()) continue;
            inner.append("<span class='wf-entry-badge'").append(elementAttrs(badge,inters,names))
                    .append(" style='position:absolute;top:-3px;right:3px;min-width:14px;height:14px;padding:0 3px;border-radius:7px;")
                    .append("background:#F06449;color:#FFFFFF;font-size:9px;line-height:14px;text-align:center;'>")
                    .append(esc(label(badge))).append("</span>");shown.add(badge);
        }
        inner.append(bindingAliases(entry.parts,shown,inters,names));
        return el(entry.frame.style()+"z-index:30;display:flex;flex-direction:column;align-items:center;gap:4px;background:none;border:0;box-shadow:none;",
                "wf-entry "+(java.util.Set.of("sale","star").contains(iconKey(entry.label))?"wf-promotion-entry":"wf-side-entry"),elementAttrs(entry.anchor,inters,names),inner.toString());
    }

    private String renderRewardEntry(RewardEntry entry, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        String key=iconKey(label(entry.icon));if(key==null)key="reward";boolean energy="energy".equals(key);
        String ink=energy?"#B88918":"#468DC8",fill=energy?"#FFF7E1":"#F2F8FE",stroke=energy?"#E8CE87":"#C8DEF0";
        double visual=Math.max(36,Math.min(56,entry.visualHeight));
        StringBuilder inner=new StringBuilder("<span class='wf-entry-visual-slot' style='height:")
                .append((int)visual).append("px;flex-shrink:0;display:flex;align-items:center;justify-content:center;'>")
                .append("<span class='wf-entry-graphic' style='box-sizing:border-box;width:36px;height:36px;border-radius:50%;display:flex;")
                .append("flex-direction:column;align-items:center;justify-content:center;background:").append(fill)
                .append(";border:1px solid ").append(stroke).append(";color:").append(ink).append(";'>")
                .append(iconSvg(key,ink,14)).append("<span").append(elementAttrs(entry.number,inters,names))
                .append(" style='font-size:10px;line-height:12px;'>").append(esc(label(entry.number))).append("</span></span></span>");
        List<Element> shown=new ArrayList<>(List.of(entry.icon,entry.number));
        if (!entry.captions.isEmpty()) {
            inner.append("<span class='wf-entry-caption' style='display:flex;flex-direction:column;flex-shrink:0;max-width:100%;font-size:10px;line-height:14px;color:#374151;text-align:center;white-space:nowrap;'>");
            for (Element text : entry.captions) {inner.append("<span").append(elementAttrs(text,inters,names)).append(">").append(textInner(text)).append("</span>");shown.add(text);}
            inner.append("</span>");
        }
        inner.append(bindingAliases(entry.parts,shown,inters,names));
        return el(entry.frame.style()+"z-index:30;display:flex;flex-direction:column;align-items:center;gap:4px;background:none;border:0;",
                "wf-entry wf-reward-entry",elementAttrs(entry.icon,inters,names),inner.toString());
    }

    private String renderServiceList(ServiceList list, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        StringBuilder inner=new StringBuilder();List<Element> shown=new ArrayList<>();shown.add(list.host);
        for (int i=0;i<list.rows.size();i++) {
            ServiceRow row=list.rows.get(i);shown.add(row.anchor);
            Element hit=hasAction(row.anchor,inters)?row.anchor:row.parts.stream().filter(e -> hasAction(e,inters)).findFirst().orElse(row.anchor);
            Element title=kind(row.anchor).equals("text")?row.anchor:row.parts.stream().filter(e -> kind(e).equals("text") && label(e).equals(row.label)).findFirst().orElse(null);
            Element left=row.parts.stream().filter(e -> kind(e).equals("icon") && frame(e).cx()<frame(list.host).cx()).findFirst().orElse(null);
            Element arrow=row.parts.stream().filter(e -> kind(e).equals("icon") && frame(e).cx()>frame(list.host).cx()).findFirst().orElse(null);
            List<Element> rowShown=new ArrayList<>();rowShown.add(hit);
            inner.append("<div class='wf-service-row'").append(elementAttrs(hit,inters,names))
                    .append(" style='display:flex;align-items:center;gap:12px;flex:1;min-height:40px;padding:8px 12px;background:none;")
                    .append(i==0?"":"border-top:1px solid #EEF0F3;").append("'>")
                    .append("<span class='wf-service-icon'").append(left==null || left==hit?"":elementAttrs(left,inters,names)).append(">").append(iconSvg(iconKey(row.label),"#7B8492",22)).append("</span>")
                    .append("<span class='wf-service-copy' style='flex:1;min-width:0;'><span class='wf-service-title'").append(title==null || title==hit?"":elementAttrs(title,inters,names))
                    .append(" style='display:block;font-size:14px;line-height:20px;color:#374151;'>")
                    .append(esc(row.label)).append("</span>");
            if(left!=null)rowShown.add(left);if(title!=null)rowShown.add(title);if(arrow!=null)rowShown.add(arrow);
            for (Element text : row.parts) if (text!=row.anchor && kind(text).equals("text") && !label(text).equals(row.label)) {
                inner.append("<span").append(elementAttrs(text,inters,names)).append(" style='display:block;font-size:11px;line-height:16px;color:#7B8492;'>")
                        .append(esc(label(text))).append("</span>");rowShown.add(text);
            }
            inner.append("</span><span class='wf-service-arrow'").append(arrow==null || arrow==hit?"":elementAttrs(arrow,inters,names)).append(">").append(iconSvg("next","#9AA3AF",18)).append("</span>")
                    .append(bindingAliases(row.parts,rowShown,inters,names)).append("</div>");
            shown.addAll(row.parts);
        }
        return el(frame(list.host).style()+"z-index:20;background:none;border:0;box-shadow:none;display:flex;flex-direction:column;",
                "wf-service-list",elementAttrs(list.host,inters,names),inner.toString());
    }

    private String bindingAliases(List<Element> all, Element used, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        return bindingAliases(all,used==null?List.of():List.of(used),inters,names);
    }
    private String bindingAliases(List<Element> all, List<Element> used, Map<Long,List<Interaction>> inters, Map<Long,String> names) {
        StringBuilder html=new StringBuilder();
        for (Element e : all) if (!used.contains(e)) html.append("<span class='wf-binding-alias'")
                .append(elementAttrs(e,inters,names)).append(" aria-hidden='true' style='position:absolute;width:0;height:0;overflow:hidden;'></span>");
        return html.toString();
    }
    private static boolean hasAction(Element e, Map<Long,List<Interaction>> inters) { return inters!=null && !inters.getOrDefault(e.getId(),List.of()).isEmpty(); }
    private static boolean navLabel(String text) { return text.matches("家园|首页|收集|扭蛋|商城|我的"); }
    private static String kind(Element e) { return e.getType()==null?"":e.getType(); }
    private static String label(Element e) { return normalizeLabel(e.getLabel()).trim(); }
    private static Frame frame(Element e) { return new Frame(nz(e.getPositionX()),nz(e.getPositionY()),nz(e.getWidth()),nz(e.getHeight())); }
    private static boolean inside(Frame a, Frame b, double pad) { return b.x>=a.x-pad && b.y>=a.y-pad && b.right()<=a.right()+pad && b.bottom()<=a.bottom()+pad; }

    /** 整块仍是同一个点击入口，只有图形部分有底色，说明位于图形外。 */
    private String renderIllustratedEntry(Element e, String label, String key, String pos, String attr, String fill, String accent) {
        List<String> lines = new ArrayList<>();
        String value = "";
        for (String line : label.split("\n")) {
            String text = line.trim();
            if (text.isEmpty()) continue;
            if (lines.isEmpty() && value.isEmpty() && text.matches("\\+\\d+(?:[.,]\\d+)?")) value = text;
            else lines.add(text);
        }
        double width = nz(e.getWidth()), height = nz(e.getHeight());
        double maxUnits = lines.stream().mapToDouble(TemplateHtmlRenderer::textUnits).max().orElse(1);
        int font = clamp((int)Math.floor((width - 4) / Math.max(1, maxUnits)), 9, 11);
        int lineHeight = font + 3;
        double captionH = lines.size() * lineHeight;
        double visualSlot = Math.max(12, Math.min(width - 2, height - captionH - (lines.isEmpty() ? 0 : 4)));
        boolean reward = label.matches("(?s).*(礼物|礼包|下载完成|领取|奖励).*" ) || label.startsWith("+");
        double visualSize = reward ? Math.min(36,visualSlot) : visualSlot;
        boolean colored = !reward && isVivid(fill);
        String surface = colored ? fill : "#FFFFFF";
        String graphicInk = colored ? DesignColorSampler.contrastInk(fill) : reward ? iconColor(key) : accent;
        int iconSize = (int)Math.min(28, visualSize * (value.isEmpty() ? .6 : .42));
        String number = value.isEmpty() ? "" : "<span style='font-size:10px;line-height:12px;color:" + graphicInk + ";'>" + esc(value) + "</span>";
        String visual = "<span class='wf-entry-graphic' style='width:" + (int)visualSize + "px;height:" + (int)visualSize
                + "px;flex-shrink:0;border-radius:50%;border:1px solid #E5E7EB;background:" + surface
                + ";display:flex;flex-direction:column;align-items:center;justify-content:center;'>"
                + iconSvg(key, graphicInk, iconSize) + number + "</span>";
        String caption = lines.isEmpty() ? "" : "<span class='wf-entry-caption' style='display:block;flex-shrink:0;max-width:100%;"
                + "font-size:" + font + "px;line-height:" + lineHeight + "px;color:#374151;text-align:center;white-space:nowrap;'>"
                + lines.stream().map(TemplateHtmlRenderer::esc).collect(java.util.stream.Collectors.joining("<br>")) + "</span>";
        String graphicSlot="<span class='wf-entry-visual-slot' style='height:"+(int)visualSlot+"px;flex-shrink:0;display:flex;align-items:center;justify-content:center;'>"+visual+"</span>";
        return el(pos + "z-index:30;display:flex;flex-direction:column;align-items:center;justify-content:flex-start;gap:4px;"
                + "background:none;border:0;box-shadow:none;cursor:pointer;", "wf-entry wf-act", attr, graphicSlot + caption);
    }

    /** 同类功能入口使用相同的图标尺寸和图文间距，不能因识别框较扁就变回实心按钮。 */
    private String renderIconShortcut(String label, String key, String pos, String attr, String accent) {
        return renderIconShortcut(label,key,pos,attr,accent,false);
    }

    private String renderIconShortcut(String label, String key, String pos, String attr, String accent,boolean dock) {
        return el(pos+"z-index:"+(dock?46:30)+";display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;"
                        +"background:none;border:0;box-shadow:none;cursor:pointer;", "wf-entry wf-shortcut wf-act",attr,
                "<span class='wf-entry-graphic' style='width:22px;height:22px;display:flex;align-items:center;justify-content:center;'>"
                        +iconSvg(key,accent,22)+"</span><span class='wf-entry-caption' style='font-size:10px;line-height:12px;color:#374151;white-space:nowrap;'>"
                        +esc(label)+"</span>");
    }

    private static Element switchInsideButton(Element button, List<Element> all) {
        String label = button.getLabel() == null ? "" : button.getLabel().trim();
        if (!"button".equals(button.getType()) || label.isEmpty() || label.length() > 6 || nz(button.getHeight()) > 60) return null;
        return all.stream().filter(e -> "switch".equals(e.getType()) && overlapRatio(button, e) >= .75
                && Math.abs(nz(button.getPositionY()) - nz(e.getPositionY())) <= 8).findFirst().orElse(null);
    }

    private static String switchCaption(Element sw, List<Element> all) {
        for (Element e : all) {
            if (switchInsideButton(e, all) == sw) return e.getLabel().trim();
        }
        return all.stream().filter(e -> "text".equals(e.getType()) && e.getLabel() != null
                        && e.getLabel().length() <= 6 && !e.getLabel().isBlank()
                        && !e.getLabel().matches("[\\d+%., ]+")
                        && nz(e.getPositionX()) >= nz(sw.getPositionX())
                        && nz(e.getPositionX()) + nz(e.getWidth()) <= nz(sw.getPositionX()) + nz(sw.getWidth()) * .75
                        && Math.abs(nz(e.getPositionY()) + nz(e.getHeight()) / 2 - nz(sw.getPositionY()) - nz(sw.getHeight()) / 2) <= nz(sw.getHeight()) / 2)
                .map(Element::getLabel).findFirst().orElse("");
    }

    // Existing bundled icons plus Lucide presets (ISC): https://github.com/lucide-icons/lucide
    private static final Map<String, String> ICON_PRESETS = Map.ofEntries(
            Map.entry("fragment", "<path d=\"M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z\"/>"),
            Map.entry("signal", "<path d=\"M2 20h.01\"/><path d=\"M7 20v-4\"/><path d=\"M12 20v-8\"/><path d=\"M17 20V8\"/><path d=\"M22 4v16\"/>"),
            Map.entry("home", "<path d=\"m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"></path> <polyline points=\"9 22 9 12 15 12 15 22\"></polyline>"),
            Map.entry("collection", "<path d=\"M12 3v18\" /> <path d=\"M3 12h18\" /> <rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\" />"),
            Map.entry("ticket", "<path d=\"M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z\" /> <path d=\"M13 5v2\" /> <path d=\"M13 17v2\" /> <path d=\"M13 11v2\" />"),
            Map.entry("shop", "<path d=\"M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z\"></path>"),
            Map.entry("user", "<path d=\"M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2\"></path> <circle cx=\"12\" cy=\"7\" r=\"4\"></circle>"),
            Map.entry("backpack", "<path d=\"M4 10a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z\" /> <path d=\"M8 10h8\" /> <path d=\"M8 18h8\" /> <path d=\"M8 22v-6a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v6\" /> <path d=\"M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2\" />"),
            Map.entry("message", "<path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"></path>"),
            Map.entry("support", "<path d=\"M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3\" />"),
            Map.entry("game", "<line x1=\"6\" x2=\"10\" y1=\"11\" y2=\"11\" /> <line x1=\"8\" x2=\"8\" y1=\"9\" y2=\"13\" /> <line x1=\"15\" x2=\"15.01\" y1=\"12\" y2=\"12\" /> <line x1=\"18\" x2=\"18.01\" y1=\"10\" y2=\"10\" /> <path d=\"M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z\" />"),
            Map.entry("monthly", "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\" ry=\"2\"></rect> <line x1=\"3\" y1=\"9\" x2=\"21\" y2=\"9\"></line> <line x1=\"9\" y1=\"21\" x2=\"9\" y2=\"9\"></line>"),
            Map.entry("gift", "<path d=\"M12 7v14\" /> <path d=\"M20 11v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8\" /> <path d=\"M7.5 7a1 1 0 0 1 0-5A4.8 8 0 0 1 12 7a4.8 8 0 0 1 4.5-5 1 1 0 0 1 0 5\" /> <rect x=\"3\" y=\"7\" width=\"18\" height=\"4\" rx=\"1\" />"),
            Map.entry("task", "<rect width=\"8\" height=\"4\" x=\"8\" y=\"2\" rx=\"1\" ry=\"1\" /> <path d=\"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2\" /> <path d=\"M12 11h4\" /> <path d=\"M12 16h4\" /> <path d=\"M8 11h.01\" /> <path d=\"M8 16h.01\" />"),
            Map.entry("gem", "<path d=\"M10.5 3 8 9l4 13 4-13-2.5-6\" /> <path d=\"M17 3a2 2 0 0 1 1.6.8l3 4a2 2 0 0 1 .013 2.382l-7.99 10.986a2 2 0 0 1-3.247 0l-7.99-10.986A2 2 0 0 1 2.4 7.8l2.998-3.997A2 2 0 0 1 7 3z\" /> <path d=\"M2 9h20\" />"),
            Map.entry("energy", "<path d=\"M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z\" />"),
            Map.entry("outfit", "<path d=\"M20.38 3.46 16 2a4 4 0 0 1-8 0L3.62 3.46a2 2 0 0 0-1.34 2.23l.58 3.47a1 1 0 0 0 .99.84H6v10c0 1.1.9 2 2 2h8a2 2 0 0 0 2-2V10h2.15a1 1 0 0 0 .99-.84l.58-3.47a2 2 0 0 0-1.34-2.23z\" />"),
            Map.entry("explore", "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z\" />"),
            Map.entry("reward", "<path d=\"M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2\" /> <path d=\"M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2\" /> <path d=\"M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3\" /> <path d=\"M4 22h16\" /> <path d=\"M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z\" /> <path d=\"M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3\" />"),
            Map.entry("back", "<polyline points=\"15 18 9 12 15 6\"></polyline>"),
            Map.entry("next", "<polyline points=\"9 18 15 12 9 6\"></polyline>"),
            Map.entry("close", "<line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"></line> <line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"></line>"),
            Map.entry("search", "<circle cx=\"11\" cy=\"11\" r=\"8\"></circle> <path d=\"m21 21-4.3-4.3\"></path>"),
            Map.entry("settings", "<path d=\"M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z\"></path> <circle cx=\"12\" cy=\"12\" r=\"3\"></circle>"),
            Map.entry("more", "<line x1=\"3\" y1=\"12\" x2=\"21\" y2=\"12\"></line> <line x1=\"3\" y1=\"6\" x2=\"21\" y2=\"6\"></line> <line x1=\"3\" y1=\"18\" x2=\"21\" y2=\"18\"></line>"),
            Map.entry("add", "<line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line> <line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line>"),
            Map.entry("heart", "<path d=\"M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z\"></path>"),
            Map.entry("star", "<polygon points=\"12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2\"></polygon>"),
            Map.entry("info", "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle> <line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"></line> <line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"></line>"),
            Map.entry("refresh", "<path d=\"M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8\"></path> <path d=\"M21 3v5h-5\"></path>"),
            Map.entry("download", "<path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"></path> <polyline points=\"7 10 12 15 17 10\"></polyline> <line x1=\"12\" y1=\"15\" x2=\"12\" y2=\"3\"></line>"),
            Map.entry("share", "<path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"></path> <polyline points=\"17 8 12 3 7 8\"></polyline> <line x1=\"12\" y1=\"3\" x2=\"12\" y2=\"15\"></line>"),
            Map.entry("entry", "<path d=\"M12 2L2 7l10 5 10-5-10-5z\"></path> <path d=\"M2 17l10 5 10-5\"></path> <path d=\"M2 12l10 5 10-5\"></path>"),
            Map.entry("image", "<rect width=\"18\" height=\"18\" x=\"3\" y=\"3\" rx=\"2\" ry=\"2\" /> <circle cx=\"9\" cy=\"9\" r=\"2\" /> <path d=\"m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21\" />"),
            Map.entry("pet", "<path d=\"M13 16a3 3 0 0 1 2.24 5\" /> <path d=\"M18 12h.01\" /> <path d=\"M18 21h-8a4 4 0 0 1-4-4 7 7 0 0 1 7-7h.2L9.6 6.4a1 1 0 1 1 2.8-2.8L15.8 7h.2c3.3 0 6 2.7 6 6v1a2 2 0 0 1-2 2h-1a3 3 0 0 0-3 3\" /> <path d=\"M20 8.54V4a2 2 0 1 0-4 0v3\" /> <path d=\"M7.612 12.524a3 3 0 1 0-1.6 4.3\" />"),
            Map.entry("sale", "<path d=\"M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z\" /> <circle cx=\"7.5\" cy=\"7.5\" r=\".5\" fill=\"currentColor\" />"),
            Map.entry("currency", "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8\" /> <path d=\"M12 18V6\" />"),
            Map.entry("clock", "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle> <polyline points=\"12 6 12 12 16 14\"></polyline>"),
            Map.entry("battery", "<path d=\"M 22 14 L 22 10\" /> <rect x=\"2\" y=\"6\" width=\"16\" height=\"12\" rx=\"2\" />"),
            Map.entry("wifi", "<path d=\"M12 20h.01\" /> <path d=\"M2 8.82a15 15 0 0 1 20 0\" /> <path d=\"M5 12.859a10 10 0 0 1 14 0\" /> <path d=\"M8.5 16.429a5 5 0 0 1 7 0\" />"),
            Map.entry("lock", "<rect x=\"4\" y=\"10\" width=\"16\" height=\"12\" rx=\"2\"/><path d=\"M8 10V6a4 4 0 0 1 8 0v4\"/><path d=\"M12 15v2\"/>"),
            Map.entry("hand", "<path d=\"M8 12V5a2 2 0 0 1 4 0v6l1-1a2 2 0 0 1 3 0l1 1a2 2 0 0 1 3 1v4a6 6 0 0 1-6 6h-2a6 6 0 0 1-4-2l-5-5a2 2 0 0 1 3-3l2 2\"/>"),
            Map.entry("phone", "<path d=\"M22 16v3a2 2 0 0 1-2 2 19 19 0 0 1-17-17 2 2 0 0 1 2-2h3l2 5-3 2a15 15 0 0 0 8 8l2-3 5 2Z\"/>"),
            Map.entry("desktop", "<rect x=\"2\" y=\"3\" width=\"20\" height=\"14\" rx=\"2\"/><path d=\"M8 21h8M12 17v4\"/>"),
            Map.entry("moon", "<path d=\"M21 13a9 9 0 0 1-10-10A9 9 0 1 0 21 13Z\"/>"),
            Map.entry("play", "<circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"m10 8 6 4-6 4Z\"/>"),
            Map.entry("lightbulb", "<path d=\"M9 18h6M9 21h6M8 14a6 6 0 1 1 8 0l-1 2H9l-1-2Z\"/>"),
            Map.entry("eye-off", "<path d=\"m3 3 18 18M10 5c5-1 9 3 12 7-1 2-2 3-4 4M14 19C8 20 4 16 2 12l3-4M9 9a4 4 0 0 0 6 6\"/>"),
            Map.entry("check", "<path d=\"m5 12 4 4L20 5\"/>"),
            Map.entry("food", "<path d=\"M4 2v7a3 3 0 0 0 6 0V2M7 2v20M20 22V2c-4 2-4 10 0 10\"/>"),
            Map.entry("fruit", "<path d=\"M12 6c-5-4-12 2-8 10 2 5 5 6 8 4 3 2 6 1 8-4 4-8-3-14-8-10ZM12 6V3M12 3c2-3 5-2 6-1-1 2-3 3-6 1\"/>"),
            Map.entry("icecream", "<path d=\"M6 12h12l-6 10-6-10ZM6 12a4 4 0 0 1 1-7 5 5 0 0 1 10 0 4 4 0 0 1 1 7\"/>"),
            Map.entry("glasses", "<circle cx=\"6\" cy=\"14\" r=\"4\"/><circle cx=\"18\" cy=\"14\" r=\"4\"/><path d=\"M10 14h4M2 14l2-9h3M22 14l-2-9h-3\"/>"),
            Map.entry("music", "<path d=\"M9 18V5l12-2v13M9 8l12-2\"/><ellipse cx=\"6\" cy=\"18\" rx=\"3\" ry=\"3\"/><ellipse cx=\"18\" cy=\"16\" rx=\"3\" ry=\"3\"/>"),
            Map.entry("book", "<path d=\"M4 3h16v18H4a2 2 0 0 1 0-4h16M4 3a2 2 0 0 0-2 2v14M7 7h9M7 11h6\"/>"),
            Map.entry("empty", "<path d=\"M3 12 6 3h12l3 9v8H3v-8ZM3 12h5l2 4h4l2-4h5\"/>"),
            Map.entry("edit", "<path d=\"m16 3 5 5-12 12-7 2 2-7L16 3ZM13 6l5 5\"/>"));

    private static String iconSvg(String key, String color, int size) {
        if (key == null) key = "image";
        String body = ICON_PRESETS.getOrDefault(key, ICON_PRESETS.get("image"));
        String fill = "pet".equals(key) ? color : "none";
        return "<svg data-icon='" + esc(key) + "' aria-hidden='true' viewBox='0 0 24 24' width='" + size
                + "' height='" + size + "' fill='" + fill + "' stroke='" + color
                + "' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round' style='flex-shrink:0;max-width:100%;max-height:100%;'>"
                + body + "</svg>";
    }

    private static String iconColor(String key) {
        return switch (key) {
            case "gem" -> "#32A6D9";
            case "energy", "currency", "reward" -> "#D89B21";
            case "gift", "heart" -> "#E56581";
            default -> "#6B7280";
        };
    }

    private static String resolveIconName(Element e, List<Element> all) {
        String own = e.getLabel() == null ? "" : e.getLabel().trim();
        if (iconKey(own) != null) return own;
        if (kind(e).matches("image|icon") && frame(e).w<=36 && frame(e).h<=36 && all.stream().anyMatch(c -> kind(c).equals("container")
                && label(c).matches(".*(货币|余额|资产).*") && frame(c).h<=60 && inside(frame(c),frame(e),4))) return "货币";
        Element text = nearbyIconLabel(e, all);
        return text == null ? own : text.getLabel();
    }

    /** 功能图片的文字可能与图形下缘重叠，也可能是按钮文案；只在紧邻的小图形范围内关联。 */
    private static Element nearbyIconLabel(Element icon, List<Element> all) {
        Element grouped=LayoutNormalizer.associatedLabel(icon,all);
        if (grouped!=null && iconKey(label(grouped))!=null) return grouped;
        Frame r=frame(icon);
        if (r.w>100 || r.h>110) return grouped;
        return all.stream().filter(e -> e!=icon && kind(e).matches("text|badge|button") && iconKey(label(e))!=null
                        && label(e).length()<=16 && frame(e).w<=130 && frame(e).h<=30
                        && frame(e).y>=r.y+r.h*.3 && frame(e).y<=r.bottom()+32
                        && Math.abs(frame(e).cx()-r.cx())<=Math.max(12,Math.min(36,r.w*.7)))
                .min(Comparator.comparingDouble(e -> Math.abs(frame(e).cx()-r.cx())+Math.abs(frame(e).y-r.bottom())))
                .orElse(grouped);
    }

    /** 只压缩确实覆盖到下方短文案的小图标，右侧标签、独立图标和导航项仍走原有布局。 */
    private static Element overlappingIconCaption(Element icon, List<Element> all) {
        Frame r=frame(icon);
        if(r.w>64 || r.h>64)return null;
        Element caption=nearbyIconLabel(icon,all);
        if(caption==null || !kind(caption).equals("text") || label(caption).length()>8)return null;
        Frame t=frame(caption);
        double available=Math.round(t.y)-Math.round(r.y)-4;
        return t.y>=r.y+r.h*.35 && t.y<r.bottom() && t.w<=r.w+12
                && Math.abs(t.cx()-r.cx())<=Math.max(4,r.w*.35) && available>=10 ? caption : null;
    }

    /** 先匹配明确语义，禁止“心”“加”等单字子串吞掉游戏功能名。 */
    private static String iconKey(String label) {
        if (label == null || label.isBlank()) return null;
        String s = label.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_\\-]", "");
        if (s.length() > 20 || s.contains("\n") || s.contains("\\n")) return null;
        if (s.matches(".*(返回|后退|arrowleft|chevronleft).*")) return "back";
        if (s.matches(".*(关闭|close).*" ) || s.matches("[x×✕]")) return "close";
        if (s.matches(".*(下一步|右箭头|chevronright).*")) return "next";
        if (s.matches(".*(搜索|search).*")) return "search";
        if (s.matches(".*(未启用|未应用|隐藏|不可见|eyeoff).*")) return "eye-off";
        if (s.matches(".*(已启用|已应用|选中|check).*")) return "check";
        if (s.matches(".*(锁屏|锁定|锁头|lock).*")) return "lock";
        if (s.matches(".*(手势|手指|hand).*")) return "hand";
        if (s.matches(".*(待机|熄屏|息屏|休眠|moon).*")) return "moon";
        if (s.matches(".*(回到桌面|桌面挂件|desktop).*")) return "desktop";
        if (s.matches(".*(表演|播放|play).*")) return "play";
        if (s.matches(".*(灯光|灯泡|lightbulb).*")) return "lightbulb";
        if (s.matches(".*(电话|来电|视频铃声|phone).*")) return "phone";
        if (s.matches(".*(壁纸|照片|桌面和壁纸|image).*")) return "image";
        if (s.matches(".*(纪念品|秘箱|book).*")) return "book";
        if (s.matches(".*(护目镜|眼镜|glasses).*")) return "glasses";
        if (s.matches(".*(冰淇淋|冰激凌|icecream).*")) return "icecream";
        if (s.matches(".*(苹果|水果|fruit).*")) return "fruit";
        if (s.matches(".*(香蕉|食物|喂养|food).*")) return "food";
        if (s.matches(".*(玩偶|公仔).*")) return "pet";
        if (s.matches(".*(电子道具|音乐|music).*")) return "music";
        if (s.matches(".*(编辑|自定义|edit).*")) return "edit";
        if (s.matches(".*(切换形象).*")) return "user";
        if (s.matches(".*(充电|电量效果).*")) return "battery";
        if (s.matches(".*(控制中心).*")) return "settings";
        if (s.matches(".*(暂无|空状态|无记录|无任务|empty).*")) return "empty";
        if (s.matches(".*(发现).*")) return "explore";
        if (s.matches(".*(客服|帮助|反馈|support|headphone).*")) return "support";
        if (s.matches(".*(游戏中心|游戏|gamepad).*")) return "game";
        if (s.matches(".*(月卡|皇冠|会员|monthly|crown).*")) return "monthly";
        if (s.matches(".*(每日任务|任务|task|clipboard).*")) return "task";
        if (s.matches(".*(扭蛋|抽奖|gacha|ticket).*")) return "ticket";
        if (s.matches(".*(背包|道具包|backpack).*")) return "backpack";
        if (s.matches(".*(钻石|宝石|蓝钻|水晶|gem|diamond).*")) return "gem";
        if (s.matches(".*(能量|体力|闪电|energy|zap).*")) return "energy";
        if (s.matches(".*(货币|余额|资产|currency|coin).*")) return "currency";
        if (s.matches(".*(礼物|礼包|宝箱|gift).*" ) || s.equals("奖池")) return "gift";
        if (s.matches(".*(领奖|奖励|奖杯|reward|trophy).*")) return "reward";
        if (s.matches(".*(探险|探索|compass|explore).*")) return "explore";
        if (s.matches(".*(揉揉|揉脸|抚摸|摸摸).*")) return "pet";
        if (s.matches(".*(装扮|服饰|衣服|shirt|outfit).*")) return "outfit";
        if (s.matches(".*(低价|特价|折扣|sale|discount).*")) return "sale";
        if (s.matches(".*(上新|耀宝上新|新品).*")) return "star";
        if (s.matches(".*(家园|首页|主页|home).*")) return "home";
        if (s.matches(".*(收集|图鉴|collection).*")) return "collection";
        if (s.matches(".*(商城|商店|购物|shop|cart).*")) return "shop";
        if (s.matches(".*(我的|个人|用户|profile|user).*")) return "user";
        if (s.matches(".*(消息|通知|邮件|message|bell|mail).*")) return "message";
        if (s.matches(".*(设置|setting).*")) return "settings";
        if (s.matches(".*(说明|信息|提示|info).*")) return "info";
        if (s.matches(".*(记录|历史|时间|clock|history).*")) return "clock";
        if (s.matches(".*(刷新|refresh|sync).*")) return "refresh";
        if (s.matches(".*(下载|download).*")) return "download";
        if (s.matches(".*(分享|share).*")) return "share";
        if (s.matches(".*(收藏|心愿|爱心|heart|like).*")) return "heart";
        if (s.matches(".*(星星|star).*")) return "star";
        if (s.matches(".*(更多|菜单|more|menu).*")) return "more";
        if (s.matches(".*(添加|新增|add).*" ) || s.equals("+")) return "add";
        if (s.matches(".*(电量|battery).*")) return "battery";
        if (s.matches(".*(网络|wifi|信号).*")) return "wifi";
        return null;
    }

    private String getIconSvg(String label, boolean onLight, double minDim) {
        String key = iconKey(label);
        return iconSvg(key == null ? "image" : key, onLight ? "#6B7280" : "#F3F4F6",
                (int)Math.max(10,Math.min(26,minDim*.78)));
    }

    /* ================= 样式细节 ================= */

    /** label 归一化：字面 "\n"（反斜杠+n）与真实换行符统一为 \n，去掉 \r */
    private static String normalizeLabel(String s) {
        if (s == null) return "";
        return s.replace("\\r", "").replace("\\\\n", "\n").replace("\r", "").replace("\n\n", "\n");
    }

    /** 文本内层 HTML：清洗 AI 识图硬切断的 \n，保持规范分点 */
    private static String textInner(Element e) {
        String norm = normalizeLabel(e.getLabel()).trim();
        if (!norm.contains("\n")) {
            return esc(norm);
        }
        // 如果文本是规则说明列表（含数字序号 1. 2. 3. 等），只在新的分点前换行，消除单句内误切的断句
        if (norm.contains("1.") || norm.contains("2.") || norm.length() >= 60) {
            String[] rawLines = norm.split("\n", -1);
            StringBuilder clean = new StringBuilder();
            for (String line : rawLines) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                boolean isNewPoint = t.matches("^(\\d+[\\.、]|·|\\([0-9]\\)).*");
                if (clean.length() > 0) {
                    clean.append(isNewPoint ? "<br>" : " ");
                }
                clean.append(esc(t));
            }
            return clean.toString();
        }
        String[] segs = norm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) sb.append("<br>");
            sb.append(esc(segs[i].trim()));
        }
        return sb.toString();
    }

    /** label 是否带显式换行（设计稿多行的最可靠信号） */
    private static boolean hasExplicitBreak(Element e) {
        return normalizeLabel(e.getLabel()).contains("\n");
    }

    /**
     * 行数估计：
     *  - 高度 < 26px 绝对为单行；
     *  - 显式 \n → 按行数计；
     *  - 长句（>=14字符）且高度>=28px 或字数较密集 → 视为多行段落；
     *  - 其余按 bbox 高度 ÷（宽度拟合字号 × 1.45）。
     */
    private static int estLineCount(Element e) {
        double w = nz(e.getWidth()), h = nz(e.getHeight());
        // 高度不足 26px（如 16px、19px 的横幅文案），物理空间根本容纳不下多行，必须单行自适应排版
        if (h < 26) {
            return 1;
        }
        String norm = normalizeLabel(e.getLabel());
        if (norm.contains("\n")) {
            return Math.max(2, Math.min(3, norm.split("\n", -1).length));
        }
        String text = norm.trim();
        double units = textUnits(text);
        double fsW = (w - 4) / units;
        // 只有当高度充裕（h >= 28）且文本为长提示句（>=14字符且包含标点或字数密集）时才走多行折行
        if (h >= 28 && text.length() >= 14 && (text.contains("？") || text.contains("?") || text.contains("，") || units > w / 14.0)) {
            return 2;
        }
        boolean cantFitOneLine = units > w / 8.0;      // 8px 地板字号都放不进一行
        if (!cantFitOneLine) {
            double singleLineMinH = 8 * 1.45;
            if (h > singleLineMinH * 2 && fsW <= 12) {
                double refFs = Math.max(8, fsW);
                int lines = (int) Math.round(h / (refFs * 1.45));
                return Math.max(2, Math.min(3, lines));
            }
            return 1;                                   // 松散盒/短文本：单行
        }
        double refFs = Math.max(8, fsW);
        int lines = (int) Math.round(h / (refFs * 1.45));
        return Math.max(2, Math.min(3, lines));
    }

    /** 盒高是否远超字号（松散盒）：单行文字应顶部锚定而非垂直居中，避免中点坍缩堆叠 */
    private static boolean looseBox(Element e, int fpx) {
        return nz(e.getHeight()) > fpx * 2.2;
    }

    private static String textStyle(Element e, String fill, String ink, int effectiveBgLum) {
        double w = nz(e.getWidth()), h = nz(e.getHeight());
        String text = normalizeLabel(e.getLabel()).replace("\n", "").trim();
        double units = textUnits(text);
        double fsW = (w - 4) / units;
        boolean leftAlign = text.length() > 14 && w > 180;

        if (text.contains("恭喜获得")) {
            return "color:" + (effectiveBgLum < 145 ? "#FFE066" : "#B77916") + ";font-size:20px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:flex;align-items:center;justify-content:center;";
        }

        // 文字颜色高对比度保证：检测真实承载底色亮度，防止浅卡片出浅字或深背景出深字
        String color;
        if (effectiveBgLum >= 145) {
            // 承载在浅色卡片/浅底上：必须使用高对比深色文字（#1F2937 或 #374151）
            if (ink != null && DesignColorSampler.luminance(ink) < 110) {
                color = ink;
            } else {
                color = "#1F2937";
            }
        } else {
            // 承载在深色/暗色大背景上：必须使用高对比清晰白字
            if (ink != null && DesignColorSampler.luminance(ink) > 175) {
                color = ink;
            } else {
                color = "#FFFFFF";
            }
        }
        String textShadow = color.equalsIgnoreCase("#FFFFFF") && effectiveBgLum < 145
                ? "text-shadow:0 1px 2px rgba(0,0,0,0.5);" : "";

        int fpx;
        String layout;
        int estLines = estLineCount(e);
        boolean isSideTag = nz(e.getPositionX()) >= 335 && w <= 32 && h >= 28;
        boolean isVertical = (h >= w * 1.4 && h >= 28 && w <= 32) || (isSideTag && text.length() >= 2);
        if (isVertical) {
            fpx = clamp((int) Math.min(w * 0.65, (h - 2) / Math.max(1, text.length())), 8, 14);
            layout = "writing-mode:vertical-rl;text-orientation:mixed;"
                    + "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;"
                    + "display:flex;align-items:center;justify-content:center;"
                    + "line-height:1.1;letter-spacing:1px;";
        } else if (estLines >= 2) {
            // 多行文本：保持设计稿字号（该大则大），盒内自动换行、垂直居中，绝不溢出外框
            fpx = clamp((int) Math.round(fsW), 8, 24);
            if (hasExplicitBreak(e)) {
                String[] segs = normalizeLabel(e.getLabel()).split("\n", -1);
                double maxU = 1;
                for (String s : segs) {
                    maxU = Math.max(maxU, textUnits(s.trim()));
                }
                fpx = clamp((int) Math.min((h - 4) / (segs.length * 1.35), (w - 4) / maxU), 8, 24);
            } else if (fsW <= 12) {
                // 修复：窄高盒子的短文本多行布局，据估算行数计算更大字号
                double charsPerLine = Math.max(1, Math.ceil(units / estLines));
                double wFs = (w - 4) / charsPerLine;
                double hFs = (h - 4) / (estLines * 1.35);
                fpx = clamp((int) Math.round(Math.min(wFs, hFs)), 8, 24);
            }
            // 长段落规则说明（字数 >= 30），字号保底 11px，必须顶部对齐绝不能垂直居中截断
            boolean isLongDoc = text.length() >= 30;
            if (isLongDoc && fpx < 11) {
                fpx = 11;
            }
            if (text.matches("^[1-7][.、].*") || text.contains("每天7:00") || text.contains("能量主要")
                    || text.contains("探险可以") || text.contains("体力、心情") || text.contains("领取每日")
                    || text.contains("非月卡") || text.contains("月卡可以")) {
                fpx = 12;
            }
            String vAlign = isLongDoc ? "justify-content:flex-start;" : "justify-content:center;";
            String overflowStyle = isLongDoc ? "overflow:hidden;padding-right:2px;" : "overflow:hidden;text-overflow:ellipsis;";
            layout = "white-space:normal;word-break:break-word;display:flex;flex-direction:column;"
                    + vAlign + "text-align:" + (leftAlign || isLongDoc ? "left" : "center")
                    + ";line-height:1.45;" + overflowStyle;
        } else {
            // 单行文本：宽度拟合字号（该小则小，按可用宽度精准计算）
            fpx = fs(text, h, w);
            if (text.matches("^[1-7][.、].*") || text.contains("每天7:00") || text.contains("能量主要")
                    || text.contains("探险可以") || text.contains("体力、心情") || text.contains("领取每日")
                    || text.contains("非月卡") || text.contains("月卡可以")) {
                fpx = 12;
            }
            // 松散盒里的短标签（气泡说明、侧栏条目等）：设计稿通常 12~16px，封顶避免过大
            if (looseBox(e, fpx) && units <= 6 && fpx > 16) {
                fpx = 16;
            }
            // 高盒短字（侧边栏长条等）：顶部锚定，禁止垂直居中导致的中点坍缩
            String vAlign = looseBox(e, fpx)
                    ? "align-items:flex-start;padding-top:2px;"
                    : "align-items:center;";
            layout = "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.3;display:flex;"
                    + vAlign
                    + (leftAlign ? "padding-left:2px;padding-right:2px;"
                                 : "justify-content:center;");
        }
        // 字重与主次层级分级：大标题(>=18px)粗壮、区块标题(14~17px)饱满、正文(12~13px)适中、小字(<=11px)规整
        String fontWeight;
        String letterSpacing = "";
        if (fpx >= 18) {
            fontWeight = "font-weight:700;";
            letterSpacing = "letter-spacing:-0.2px;";
        } else if (fpx >= 14) {
            fontWeight = "font-weight:600;";
        } else if (fpx >= 12) {
            fontWeight = "font-weight:500;";
        } else {
            fontWeight = "font-weight:400;";
            if (color.equals("#1F2937") && fpx <= 11) {
                color = "#4B5563"; // 浅底上的次级辅助小字略显柔和深灰，拉开视觉主次
            } else if (color.equalsIgnoreCase("#FFFFFF") && fpx <= 11) {
                color = "rgba(255,255,255,0.85)";
            }
        }
        return "color:" + color + ";font-size:" + fpx + "px;" + fontWeight + letterSpacing + textShadow + layout;
    }

    /**
     * 字号估算：字符宽度单位模型——设计稿放得下的文字，按盒子宽度反推出的字号必然也放得下。
     */
    private static int fs(String text, double h, double w) {
        double byH = h * 0.72;
        double fit = (w - 2) / textUnits(text);
        return clamp((int) Math.floor(Math.min(byH, fit)), 8, 24);
    }

    /** 文本渲染宽度（em 单位）：CJK/全角≈1.0，字母数字≈0.56，空格 0.32，其余标点 0.5 */
    private static double textUnits(String s) {
        if (s == null || s.isEmpty()) return 1;
        double u = 0;
        for (char c : s.toCharArray()) {
            if (c >= 0x2E80 || c == '　') {
                u += 1.0;                                   // CJK 与全角
            } else if (Character.isLetterOrDigit(c)) {
                u += 0.56;                                  // 半角字母数字
            } else if (c == ' ') {
                u += 0.32;
            } else {
                u += 0.5;                                   // 半角标点
            }
        }
        return Math.max(u, 1);
    }

    /**
     * 文字几何自适应：若当前 bbox 宽度不足以按"高度约束字号"放下完整文案
     * （即 AI 把 bbox 提窄了，设计稿实际放得下），向空闲侧扩展盒子——
     * 左对齐长句向右扩展；短居中文案对称扩展。边界避让已放置元素与画布右缘。
     * 调整后的宽度会让 fs() 反推出与设计稿一致、且必然放得下的字号。
     */
    private static void fitTextGeometry(Element e, List<Element> obstacles, double canvasW) {
        if (estLineCount(e) >= 2) {
            return; // 多行文本在盒内换行即可，不需要扩盒
        }
        String label = e.getLabel() == null ? "" : e.getLabel().trim();
        if (label.isEmpty()) return;
        double units = textUnits(label);
        double w = nz(e.getWidth()), x = nz(e.getPositionX());
        double desiredFs = clamp((int) Math.round(nz(e.getHeight()) * 0.72), 8, 24);
        // 优惠/原价/说明类辅助次级文本合理限高（避免按背景横幅整体高度误算为 21px 巨字导致盲目暴涨）
        if (label.contains("原价") || label.contains("直降") || label.contains("特价") || label.contains("限时") || label.contains("说明")) {
            desiredFs = Math.min(desiredFs, 12);
        }
        double needW = desiredFs * units + 4;
        if (needW <= w + 2) {
            return; // 现宽足够
        }
        double leftLimit = 4.0;
        double rightLimit = canvasW - 6.0;

        // 若文本原本位于右侧功能栏（x >= 250），左边界不可向左穿透进入左侧卡牌区（保护橱窗）
        if (x >= 250.0) {
            leftLimit = Math.max(leftLimit, 252.0);
        }
        // 若文本原本位于左侧卡牌区（x <= 245），右边界不可向右穿透进入右侧功能栏
        if (x + w <= 248.0) {
            rightLimit = Math.min(rightLimit, 248.0);
        }

        double aE = w * nz(e.getHeight());
        double cxE = x + w / 2.0;
        double cyE = nz(e.getPositionY()) + nz(e.getHeight()) / 2.0;

        for (Element p : obstacles) {
            if (p == e) continue;
            boolean vOverlap = nz(e.getPositionY()) < nz(p.getPositionY()) + nz(p.getHeight()) - 2
                    && nz(e.getPositionY()) + nz(e.getHeight()) > nz(p.getPositionY()) + 2;
            if (!vOverlap) continue;

            // 区分底衬与平级冲突：
            // 若 p 面积显著大于 e，且 e 的中心落在 p 内部，说明 p 是 e 的底衬背景（如大图标/底板/气泡），不作为并列避让障碍
            double aP = nz(p.getWidth()) * nz(p.getHeight());
            boolean isUnderlay = aP >= aE * 1.3
                    && cxE >= nz(p.getPositionX()) && cxE <= nz(p.getPositionX()) + nz(p.getWidth())
                    && cyE >= nz(p.getPositionY()) && cyE <= nz(p.getPositionY()) + nz(p.getHeight());
            if (isUnderlay) continue;

            double px = nz(p.getPositionX());
            double pw = nz(p.getWidth());
            if (px > x + 6 && px - 4 < rightLimit) {
                rightLimit = px - 4;
            }
            if (px + pw <= x + 4 && px + pw + 4 > leftLimit) {
                leftLimit = px + pw + 4;
            }
        }

        boolean leftAlign = units >= 10 || w >= 180;
        if (leftAlign) {
            double newW = Math.min(needW, rightLimit - x);
            if (newW > w) {
                e.setWidth(newW);
            }
        } else {
            double maxSpan = Math.max(w, rightLimit - leftLimit);
            double newW = Math.min(needW, maxSpan);
            double newX = Math.max(leftLimit, x - (newW - w) / 2.0);
            if (newX + newW > rightLimit) {
                newX = Math.max(leftLimit, rightLimit - newW);
            }
            if (newW > w && newX >= leftLimit) {
                e.setPositionX(newX);
                e.setWidth(newW);
            }
        }
    }

    private static String boxStyle(String fill, boolean onLight, int rad) {
        int safeRad = Math.max(3, Math.min(12, rad));
        if (onLight) {
            // 置于浅色/白色卡片内部：微冷灰凹槽底色 + 细腻灰阶边框 + 微内阴影（内凹插槽感，彻底杜绝白叠白）
            return "background:#F3F4F6;border:1px solid #D1D5DB;border-radius:" + safeRad + "px;"
                    + "box-shadow:inset 0 1px 2px rgba(0,0,0,0.05);display:flex;align-items:center;justify-content:center;box-sizing:border-box;";
        }
        // 置于深色背景上：柔白半透明微卡 + 细腻微边框与立体外阴影
        return "background:rgba(255,255,255,0.85);border:1px solid rgba(255,255,255,0.50);border-radius:" + safeRad + "px;"
                + "box-shadow:0 2px 8px rgba(0,0,0,0.12), inset 0 1px 0 rgba(255,255,255,0.80);display:flex;align-items:center;justify-content:center;box-sizing:border-box;";
    }

    private static String boxStyle(String fill, boolean onLight) {
        return boxStyle(fill, onLight, 8);
    }

    private static String boxStyle(String fill) {
        return boxStyle(fill, true, 8);
    }

    private static String navBarStyle(String fill) {
        return "background:#FFFFFF;border-radius:0;display:flex;align-items:center;"
                + "justify-content:center;border-bottom:1px solid rgba(0,0,0,0.06);box-shadow:0 1px 3px rgba(0,0,0,0.03);";
    }

    /** 近白色容器改为半透明白+细边框，避免大块死白盖住底下的分隔线 */
    private static String rgbaIfWhite(String hex) {
        int rgb = DesignColorSampler.parseHex(hex);
        if (rgb < 0) return "#FFFFFF";
        int lum = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        if (lum >= 245) return "#FFFFFF";
        return hex;
    }

    private static boolean isVivid(String hex) {
        int rgb = DesignColorSampler.parseHex(hex);
        if (rgb < 0) return false;
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return hsb[1] > 0.45f;
    }

    private static String radius(String fill, Double h) {
        return "border-radius:" + clamp((int) (nz(h) * 0.25), 6, 24) + "px";
    }

    /* ================= 交互属性 ================= */

    /** 一组只给 anchor 打跳转；member 只带组编号，点击时再找到代表。 */
    private String elementAttrs(Element e, Map<Long, List<Interaction>> intersByEl, Map<Long, String> pageNameById) {
        StringBuilder sb = new StringBuilder();
        String key = e.getGroupKey() == null ? "" : e.getGroupKey().trim();
        sb.append(" data-wf-element-id=\"").append(e.getId()).append("\"");
        if (!key.isEmpty()) {
            sb.append(" data-group-key=\"").append(esc(key)).append("\"");
            String role = e.getGroupRole() == null ? "" : e.getGroupRole().trim();
            if (!role.isEmpty()) sb.append(" data-group-role=\"").append(esc(role)).append("\"");
        }
        if (!"member".equals(e.getGroupRole())) {
            sb.append(interactionAttrs(intersByEl == null ? null : intersByEl.get(e.getId()), pageNameById));
        }
        return sb.toString();
    }

    private String interactionAttrs(List<Interaction> inters, Map<Long, String> pageNameById) {
        if (inters == null || inters.isEmpty()) return "";
        for (Interaction it : inters) {
            String act = it.getActionType() == null ? "" : it.getActionType();
            if ("navigate".equals(act) && it.getTargetPageId() != null) {
                String name = pageNameById.get(it.getTargetPageId());
                if (name != null && !name.isBlank()) {
                    return " data-nav=\"" + esc(name) + "\"";
                }
            } else if ("back".equals(act) || "close".equals(act)) {
                return " data-action=\""+act+"\"";
            } else if (("modal".equals(act) || "popup".equals(act))
                    && it.getTargetPageId() != null && pageNameById.containsKey(it.getTargetPageId())) {
                return " data-modal=\"" + it.getTargetPageId() + "\"";
            } else if ("tab_switch".equals(act) || "tab".equals(act)) {
                return " data-action=\"tab\"";
            }
        }
        return "";
    }

    private static String tabSegmentUid(Page page,Element tab,int index) {
        return "wf-tab-p"+page.getId()+"-e"+tab.getId()+"-"+index;
    }

    private static boolean hasReturnAction(Element element,Map<Long,List<Interaction>> inters) {
        List<Interaction> actions=inters==null?null:inters.get(element.getId());
        return actions!=null && actions.stream().anyMatch(it -> "back".equals(it.getActionType()) || "close".equals(it.getActionType()));
    }

    private String interactionDomUid(Interaction it) {
        if(it==null || it.getParams()==null)return "";
        try{return objectMapper.readTree(it.getParams()).path("domUid").asText("");}catch(Exception ignored){return "";}
    }

    private Map.Entry<Long,List<Interaction>> tabSegmentBinding(String uid,Map<Long,List<Interaction>> inters) {
        if(inters==null)return null;
        return inters.entrySet().stream().filter(entry -> entry.getValue()!=null && entry.getValue().stream()
                .anyMatch(it -> uid.equals(interactionDomUid(it)))).findFirst().orElse(null);
    }

    private Long findTabSegmentTarget(String segName, Long curPageId, Map<Long, String> pageNameById) {
        if (segName == null || segName.isBlank() || pageNameById == null) return null;
        for (Map.Entry<Long, String> entry : pageNameById.entrySet()) {
            if (entry.getKey().equals(curPageId)) continue;
            String pName = entry.getValue();
            if (pName == null) continue;
            if (segName.equals("收集") && pName.equals("收集_次态")) {
                return entry.getKey();
            }
            if (segName.equals("扭蛋") && pName.equals("扭蛋抽奖页")) {
                return entry.getKey();
            }
            if (segName.equals("纪念品") && pName.equals("其他装扮_纪念品")) {
                return entry.getKey();
            }
            if (segName.equals("照片壁纸") && pName.equals("其他装扮")) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isCurrentPageTab(String segName, String curPageName) {
        if (segName == null || curPageName == null) return false;
        if (segName.equals("家园") && curPageName.contains("家园")) return true;
        if (segName.equals("我的") && curPageName.contains("我的")) return true;
        if (segName.equals("商城") && curPageName.contains("商城")) return true;
        if (segName.equals("扭蛋") && curPageName.contains("扭蛋")) return true;
        if (segName.equals("收集") && curPageName.contains("收集")) return true;
        if (segName.equals("照片壁纸") && (curPageName.equals("其他装扮") || !curPageName.contains("纪念品"))) return true;
        if (segName.equals("纪念品") && curPageName.contains("纪念品")) return true;
        return false;
    }

    /* ================= 文档骨架 ================= */

    private String doc(String title, int canvasW, int canvasH, String body, String pageBg) {
        String safeTitle = esc(title == null || title.isBlank() ? "WireForge 原型" : title);
        String bg = pageBg == null || pageBg.isBlank() ? "#FFFFFF" : pageBg;
        return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="wf-template-revision" content="2026-10-30-background-7">
                <title>%s</title>
                <style>
                  * { box-sizing: border-box; }
                  html,body{margin:0;padding:0}
                  body{width:%dpx;height:%dpx;position:relative;background:%s;overflow:hidden;
                       font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Helvetica Neue","Microsoft YaHei",sans-serif;
                       -webkit-font-smoothing:antialiased;}
                  .wf-el{position:absolute;box-sizing:border-box}
                  /* 系统性 z-index 层次体系（严格按视觉层级分层，彻底解决遮挡问题） */
                  .wf-fx{z-index:2}
                  .wf-card{z-index:10;border-radius:16px;box-shadow:0 4px 16px -2px rgba(0,0,0,0.06), 0 2px 6px -1px rgba(0,0,0,0.04);border:1px solid rgba(0,0,0,0.05);overflow:hidden}
                  .wf-img{z-index:15}
                  .wf-avatar{z-index:16}
                  .wf-div{z-index:20}
                  .wf-ic{z-index:22;display:flex;align-items:center;justify-content:center;box-sizing:border-box}
                  .wf-t{z-index:25;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%%;line-height:1.25}
                  .wf-skeleton-bar{z-index:25;background:#E5E7EB;border-radius:8px}
                  .wf-btn,.wf-sw,.wf-ck,.wf-search,.wf-tabs,.wf-rate,.wf-progress{z-index:30}
                  .wf-btn{cursor:pointer;border:1px solid rgba(0,0,0,0.06);box-shadow:none;transition:transform 0.1s ease, filter 0.15s ease}
                  .wf-btn:hover{filter:brightness(1.02);box-shadow:none}
                  .wf-btn:active{transform:scale(0.97);filter:brightness(0.95)}
                  .wf-btn-label{color:#FFF;font-weight:600;font-size:%dpx;white-space:nowrap;
                       overflow:hidden;text-overflow:ellipsis;max-width:100%%;letter-spacing:-0.2px;text-shadow:none}
                  .wf-act,.wf-ic[data-nav],.wf-img[data-nav],.wf-card[data-nav]{cursor:pointer}
                  .wf-badge,.wf-side-pill{z-index:35}
                  .wf-navbar{z-index:45}
                  .wf-segs{display:flex;width:100%%;height:100%%;box-sizing:border-box}
                  .wf-segs span{flex:1;display:flex;align-items:center;justify-content:center;
                       font-size:13px;color:#6B7280;border-radius:8px;min-width:0;
                       white-space:nowrap;overflow:hidden}
                  .wf-segs span.on{background:var(--seg-bg, #FFF);color:var(--seg-ink, %s);box-shadow:0 1px 3px rgba(0,0,0,.12);font-weight:600}
                  .wf-tabs-vertical .wf-segs{flex-direction:column;gap:7px}
                  .wf-tabs-vertical .wf-segs span{font-size:12px;line-height:16px;border-radius:999px;background:#F3F4F6;min-height:0}
                  .wf-tabs-vertical .wf-segs span.on{background:var(--seg-bg);box-shadow:none}
                  .wf-pose-switch .wf-pose-option,.wf-pose-switch .wf-pose-option.on{gap:7px;background:none;box-shadow:none;overflow:visible}
                  .wf-pose-state{width:31px;height:31px;flex-shrink:0;display:flex;align-items:center;justify-content:center;border-radius:50%%;background:#F0F1F4;color:#7B8492}
                  .wf-pose-label{padding:5px 12px;border-radius:999px;background:#F0F1F4;font-size:12px;font-weight:500;color:#667085}
                  .wf-pose-option.on .wf-pose-state,.wf-pose-option.on .wf-pose-label{background:#FFF1E4;color:var(--seg-ink)}
                  .wf-sw{display:block;cursor:pointer;transition:background 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, filter 0.15s ease}
                  .wf-sw-k{transition:left 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), right 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), background-color 0.2s ease}
                  .wf-ck{cursor:pointer;transition:opacity 0.15s ease}
                  .wf-ck:active{opacity:0.8}
                  .wf-radio-input + .wf-radio-box svg{opacity:0}
                  .wf-radio-input:checked + .wf-radio-box{border-color:#E96928;background:#FFF6ED}
                  .wf-radio-input:checked + .wf-radio-box svg{opacity:1}
                  .wf-radio-input:focus-visible + .wf-radio-box{outline:2px solid #FDBA74;outline-offset:2px}
                  body.wf-edit-mode .wf-radio-option{pointer-events:none}
                  body.wf-interactive .wf-radio-option{pointer-events:auto}
                  .wf-rate{user-select:none}
                  .wf-progress{box-sizing:border-box}
                  .wf-search-in{font-size:14px;user-select:none}
                  .wf-modal{position:absolute;inset:0;z-index:999;display:none;background:rgba(15,18,26,.55);backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px)}
                  .wf-modal.wf-show{display:block}
                  .wf-modal-dismiss{position:absolute;inset:0;background:none;border:none;padding:0;margin:0;cursor:pointer}
                  .wf-tabbar{display:flex;z-index:50;background:rgba(255,255,255,0.94);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);box-shadow:0 -2px 10px rgba(0,0,0,.05);border-top:1px solid rgba(0,0,0,0.04)}
                  .wf-tabit{position:absolute;top:0;height:100%%;display:flex;flex-direction:column;
                       align-items:center;justify-content:center;gap:3px;cursor:pointer;min-width:0}
                  .wf-tabic{width:22px;height:22px;display:flex;align-items:center;justify-content:center}
                  .wf-tabit span{font-size:10px;color:rgba(107,68,35,.75);max-width:92%%;white-space:nowrap;
                       overflow:hidden;text-overflow:ellipsis}
                  .wf-tabit.on .wf-tabic{color:%s}
                  .wf-tabit.on span{color:%s;font-weight:bold}
                </style></head>
                <body data-wf-canvas="%dx%d">%s</body></html>
                """.formatted(safeTitle, canvasW, canvasH, bg, buttonFontSizeDefault(), "#ff9800",
                "#ff9800", "#ff9800", canvasW, canvasH, body);
    }

    private static int buttonFontSizeDefault() { return 15; }

    /* ================= 工具方法 ================= */

    /** 生成元素 div：class 与额外属性（data-nav 等）分开拼接，杜绝引号吞并 */
    private static String el(String inlineStyle, String cls, String extraAttrs, String inner) {
        return "<div class=\"" + cls + " wf-el\""
                + (extraAttrs == null ? "" : extraAttrs)
                + " style=\"" + inlineStyle + "\">" + inner + "</div>";
    }

    private DesignColorSampler.ElemColors col(Map<Long, DesignColorSampler.ElemColors> colors, Long id) {
        DesignColorSampler.ElemColors c = colors.get(id);
        return c != null ? c : new DesignColorSampler.ElemColors("#F3F4F6", null);
    }

    private static BufferedImage readImage(Page page) {
        try {
            if (page.getBackgroundImage() != null && !page.getBackgroundImage().isBlank()) {
                Path p = Path.of(page.getBackgroundImage());
                if (Files.exists(p)) {
                    return javax.imageio.ImageIO.read(p.toFile());
                }
                String norm = page.getBackgroundImage().replace('\\', '/');
                String fn = norm.substring(norm.lastIndexOf('/') + 1);
                Path inDesigns = Path.of("designs", fn);
                if (Files.exists(inDesigns)) {
                    return javax.imageio.ImageIO.read(inDesigns.toFile());
                }
            }
        } catch (Exception e) {
            log.warn("读取设计稿用于采样失败 [{}]: {}", page.getName(), e.getMessage());
        }
        return null;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    private static double nz(double v) {
        return v;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
