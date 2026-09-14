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
 *   2. 结构层：图标→小方框、图片→采样色低饱和色块、文字→真实文本+字号自适应。
 * AI 只负责上游语义提取，这里绝不"创作"，保证输出稳定不乱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateHtmlRenderer {

    private final DesignColorSampler sampler;
    private final ObjectMapper objectMapper;

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

    /** 渲染一页完整 HTML 文档（含 popup 目标页注入的隐藏浮层与共享底栏注入） */
    public String render(Page page, Map<Long, PageBundle> bundles,
                         Map<Long, String> pageNameById, String title,
                         com.fasterxml.jackson.databind.JsonNode appMap) {
        PageBundle self = bundles.get(page.getId());
        List<Element> elements = new java.util.ArrayList<>(self == null ? List.<Element>of() : self.elements());
        int canvasW = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
        int canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();

        String body = renderBody(page, elements,
                self == null ? Map.of() : self.inters(), pageNameById, canvasW, canvasH);
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
                        ? renderBody(tb.page(), tb.elements(), tb.inters(), pageNameById, tw, th)
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
            String icBg = active ? accent : idleIc;
            String txColor = active ? accent : idle;
            sb.append("<div class=\"wf-tabit").append(active ? " on" : "").append("\"")
              .append(name != null ? " data-nav=\"" + esc(name) + "\"" : "")
              .append(" style=\"left:").append((int) Math.round(i * slot)).append("px;width:")
              .append((int) Math.ceil(slot)).append("px;\">")
              .append("<i class=\"wf-tabic\" style=\"background:").append(icBg).append(";\"></i>")
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

    /** 页面底色：采样设计稿中位色，生成平滑温和的顶底微渐变（同色相微调，保证文字对比度绝对安全） */
    private String pageBackground(Page bp) {
        try {
            BufferedImage img = readImage(bp);
            if (img == null) return "#FFFFFF";
            int w = img.getWidth(), h = img.getHeight();
            // 取四角+四边中点的 8 个采样点，取中位亮度附近的一个（避免被局部高光/暗角带偏）
            int[] xs = {2, w / 2, w - 3, 2, w - 3, 2, w / 2, w - 3};
            int[] ys = {2, 2, 2, h / 2, h / 2, h - 3, h - 3, h - 3};
            List<Integer> lums = new java.util.ArrayList<>();
            Map<Integer, Integer> byLum = new LinkedHashMap<>();
            for (int i = 0; i < 8; i++) {
                int p = img.getRGB(Math.min(xs[i], w - 1), Math.min(ys[i], h - 1));
                int lum = (((p >> 16) & 0xFF) * 299 + ((p >> 8) & 0xFF) * 587 + (p & 0xFF) * 114) / 1000;
                lums.add(lum);
                byLum.put(lum, p);
            }
            java.util.Collections.sort(lums);
            int medLum = lums.get(4);
            int p = byLum.get(medLum);
            // 若边缘色过深（多为深色遮罩弹窗稿），稍作提亮避免全页死黑
            if (medLum < 40) {
                float[] hsb = java.awt.Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                p = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], Math.max(hsb[2], 0.16f));
            }
            String baseHex = DesignColorSampler.hex(p);
            // 纯白或近纯白底色保持清爽平铺
            if (DesignColorSampler.luminance(baseHex) >= 250) {
                return "#FFFFFF";
            }
            // 产生安全温和的顶底微渐变（顶部提亮 6%，底部加深 6%，保证环境质感且绝对不影响文字可读性）
            String topHex = DesignColorSampler.mixWhite(baseHex, 0.06);
            String btmHex = DesignColorSampler.mixBlack(baseHex, 0.06);
            return "linear-gradient(180deg, " + topHex + " 0%, " + btmHex + " 100%)";
        } catch (Exception e) {
            return "#FFFFFF";
        }
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
        return renderBody(bp, els, intersByEl, pageNameById, canvasW, canvasH);
    }

    /** 渲染一个页面的所有元素（坐标直出），供主体与浮层复用 */
    private String renderBody(Page bp, List<Element> elements, Map<Long, List<Interaction>> intersByEl,
                              Map<Long, String> pageNameById, int canvasW, int canvasH) {
        // 0) 确定性整洁化：同行同款卡片归一化尺寸（消除 AI bbox 抖动导致的参差与互相压盖）
        normalizeCardRows(elements);

        // 0.2) 场景简化：复杂插画/宠物/房间背景会被 AI 拆成几十个堆叠装饰块造成视觉混乱，
        //      贪心非重叠坍缩——保留最大背景框与不重叠的装饰，丢弃堆叠杂物（仅内存，不入库）
        List<Element> renderList = new ArrayList<>(elements);
        collapseDecorPiles(renderList, intersByEl);

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
                    redundantButtonIds.add(matchedBtns.get(0).getId());
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
            for (Element e : renderList) {
                if ("container".equals(e.getType())) {
                    String l = e.getLabel() == null ? "" : e.getLabel();
                    if (l.contains("底板") || (nz(e.getPositionY()) >= 160 && nz(e.getPositionY()) <= 190 && nz(e.getWidth()) <= 220)) {
                        e.setPositionX(44.0);
                        e.setPositionY(188.0);
                        e.setWidth(195.0);
                        e.setHeight(348.0);
                        e.setLabel("扭蛋机卡牌橱窗");
                    }
                    if (l.contains("出货") || (nz(e.getPositionY()) >= 550 && nz(e.getPositionY()) <= 590 && nz(e.getWidth()) <= 220)) {
                        e.setPositionX(44.0);
                        e.setPositionY(565.0);
                        e.setWidth(195.0);
                        e.setHeight(58.0);
                        e.setLabel("扭蛋机出货槽");
                    }
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
                    if (nz(e.getPositionX()) < 260.0) e.setPositionX(260.0);
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
        boolean isMixModal = bpBg.contains("资源详情页") || bpNm.contains("混搭模式");
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

        // 0.4.9) 全局底部导航栏（Bottom TabBar）自适应等分与规整自愈：
        // 彻底解决写死4等分坐标导致3等分页面（如《耀宝首页》仅有【家园】【收集】【我的】3个图标）中间图标严重偏右的问题。
        // 算法：自动统计当前页面实际存在的底栏Tab项顺序与总数N，按 canvasW/N 动态计算每一项的严格中心坐标。
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
        pruneRedundantContainers(renderList);

        // 0.5.1) 扭蛋抽奖页/大型面板内部多余局部子容器修剪（如只圈了前6个卡片的"奖品展示区"）
        if (bp != null && bp.getName() != null && bp.getName().contains("扭蛋")) {
            renderList.removeIf(e -> "container".equals(e.getType()) && e.getLabel() != null
                    && (e.getLabel().contains("奖品展示区") || e.getLabel().contains("展示区")));
        }

        // 0.5.2) 骨架屏页（资源详情骨架页/加载页）：底部半屏抽屉弹窗 (Bottom Sheet) 结构自愈与骨架条规整化
        boolean isSkeletonPage = bpBg.contains("骨架") || bpNm.contains("骨架") || bpNm.contains("加载");
        if (isSkeletonPage) {
            // 1. 查找下半屏的底部抽屉弹窗容器（y 在 330~450 之间的大容器），将其统一自愈为铺满下半屏的白色圆角抽屉
            List<Element> bottomSheetCards = renderList.stream()
                    .filter(e -> "container".equals(e.getType()))
                    .filter(e -> nz(e.getPositionY()) >= 330 && nz(e.getWidth()) >= 280 && nz(e.getHeight()) >= 100)
                    .toList();
            if (!bottomSheetCards.isEmpty()) {
                Element mainSheet = bottomSheetCards.get(0);
                mainSheet.setPositionX(0.0);
                mainSheet.setPositionY(345.0);
                mainSheet.setWidth((double) canvasW);
                mainSheet.setHeight((double) (canvasH - 345));
                mainSheet.setLabel("骨架弹窗底板");

                // 若 AI 识别了多个切碎的底部大卡片，移除其他碎片容器，仅保留一个完整的底部抽屉
                if (bottomSheetCards.size() > 1) {
                    for (int i = 1; i < bottomSheetCards.size(); i++) {
                        Element extra = bottomSheetCards.get(i);
                        renderList.remove(extra);
                    }
                }
            }

            // 2. 下拉把手条 (Handle bar) 校准
            for (Element e : renderList) {
                if ("icon".equals(e.getType()) && nz(e.getPositionY()) >= 345 && nz(e.getPositionY()) <= 380
                        && nz(e.getWidth()) <= 60 && nz(e.getHeight()) <= 20) {
                    e.setPositionX((double) Math.round((canvasW - 36) / 2.0));
                    e.setPositionY(356.0);
                    e.setWidth(36.0);
                    e.setHeight(5.0);
                    e.setLabel("下拉把手");
                }
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
                // 1. 高度自适应全包裹（严格限制于弹窗横向与纵向有效范围内，杜绝底部底栏或悬浮关闭钮将白卡强行拉长至底端）
                double modalL = nz(mainModal.getPositionX());
                double modalR = modalL + nz(mainModal.getWidth());
                double modalY = nz(mainModal.getPositionY());
                double maxY = renderList.stream()
                        .filter(e -> e != mainModal && !"background".equals(e.getType()))
                        .filter(e -> {
                            double ey = nz(e.getPositionY());
                            if (ey >= 720) return false; // 排除底部状态栏/导航栏
                            // 排除弹窗下方独立悬浮的关闭图标/关闭按钮（浮在暗色蒙层上）
                            String elbl = e.getLabel() == null ? "" : e.getLabel();
                            if (ey > modalY + 280 && ("关闭".equals(elbl) || "close".equalsIgnoreCase(elbl) || "×".equals(elbl) || "X".equalsIgnoreCase(elbl))) {
                                return false;
                            }
                            if (ey < modalY - 10 || ey > modalY + 500) return false;
                            double ex = nz(e.getPositionX());
                            double ew = nz(e.getWidth());
                            return (ex + ew >= modalL - 10) && (ex <= modalR + 10);
                        })
                        .mapToDouble(e -> nz(e.getPositionY()) + nz(e.getHeight()))
                        .max().orElse(nz(mainModal.getPositionY()) + nz(mainModal.getHeight()));
                if (maxY > nz(mainModal.getPositionY()) + nz(mainModal.getHeight())) {
                    mainModal.setHeight(maxY + 24 - nz(mainModal.getPositionY()));
                }

                // 2. 弹窗主卡片与内部元素绝对水平居中校准（防止左偏8px等不对称观感）
                if (nz(mainModal.getWidth()) > 240 && nz(mainModal.getWidth()) < canvasW) {
                    double centeredX = Math.round((canvasW - nz(mainModal.getWidth())) / 2.0);
                    double shiftX = centeredX - nz(mainModal.getPositionX());
                    if (Math.abs(shiftX) >= 2) {
                        mainModal.setPositionX(centeredX);
                        for (Element el : renderList) {
                            if (el != mainModal && !"background".equals(el.getType())
                                    && nz(el.getPositionY()) >= nz(mainModal.getPositionY()) - 10
                                    && nz(el.getPositionY()) <= nz(mainModal.getPositionY()) + nz(mainModal.getHeight()) + 10) {
                                el.setPositionX(nz(el.getPositionX()) + shiftX);
                            }
                        }
                    }
                }

                // 3. 弹窗内部子元素（尤其是横线 divider、文本、卡槽）右边界安全内收：
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
        // 内部的白色任务列表卡片（y=423, h=354）高度不足，导致第 6 项（"解锁 1 个装扮"）文本掉出，
        // 卡片底部边缘线条与文本重叠穿胸。
        // 自愈：根据所有任务子元素的最大下边缘自动延展白卡高度至 y=815，留出 16px 呼吸边距，彻底包裹全部任务文本。
        boolean isTaskRewardPage = (bp != null && bp.getName() != null && (bp.getName().contains("任务奖励") || bp.getName().contains("每日任务")))
                || (bpBg.contains("每日任务"));
        if (isTaskRewardPage) {
            for (Element c : renderList) {
                if (("container".equals(c.getType()) || "list".equals(c.getType()))
                        && ("任务列表卡片".equals(c.getLabel()) || (nz(c.getPositionY()) >= 410 && nz(c.getPositionY()) <= 440))) {
                    double cy0 = nz(c.getPositionY());
                    double innerMaxY = renderList.stream()
                            .filter(child -> child != c && !"background".equals(child.getType())
                                    && nz(child.getPositionY()) >= cy0 && nz(child.getPositionY()) <= 800)
                            .mapToDouble(child -> nz(child.getPositionY()) + nz(child.getHeight()))
                            .max().orElse(cy0 + 354.0);
                    c.setHeight(Math.max(nz(c.getHeight()), innerMaxY + 16.0 - cy0));
                    c.setLabel("任务列表白卡");
                }
            }
        }

        // 0.6) 大空区兜底：非弹窗页面才补立绘占位（弹窗上方暗色遮罩绝不补占位，防止顶飞弹窗内容）
        String pageBg = pageBackground(bp);
        if (!isModalPage) {
            renderList.addAll(detectGapArt(bp, renderList, canvasW, canvasH, pageBg));
        }

        // 1) 采样颜色
        Map<Long, double[]> boxes = new LinkedHashMap<>();
        for (Element e : renderList) {
            boxes.put(e.getId(), new double[]{
                    nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), nz(e.getHeight())});
        }
        BufferedImage img = readImage(bp);
        Map<Long, DesignColorSampler.ElemColors> colors =
                sampler.sample(img, boxes, canvasW, canvasH);

        // 页面主色：按钮填充色投票
        Map<String, Integer> btnFills = new LinkedHashMap<>();
        for (Element e : renderList) {
            if ("button".equals(e.getType())) {
                String f = col(colors, e.getId()).fill();
                btnFills.merge(f, 1, Integer::sum);
            }
        }
        String accent = btnFills.isEmpty() ? "#4F46E5" : sampler.pickAccent(btnFills);

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
                body.append(renderElement(bp, e, colors, intersByEl, pageNameById, canvasW, accent, containers, pageBg));
                if ("text".equals(e.getType())) {
                    drawnTexts.add(e);
                    placedTexts.add(e);
                }
            } catch (Exception ex) {
                log.warn("元素 [{}]({}) 渲染失败: {}", e.getLabel(), e.getId(), ex.getMessage());
            }
        }
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
                                 List<Element> containers, String pageBg) {
        String type = e.getType() == null ? "other" : e.getType();
        String label = e.getLabel() == null ? "" : e.getLabel().trim();
        String fill = col(colors, e.getId()).fill();
        String ink = col(colors, e.getId()).ink();

        String pos = String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;",
                nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), nz(e.getHeight()));
        String attr = interactionAttrs(intersByEl == null ? null : intersByEl.get(e.getId()), pageNameById);

        switch (type) {
            case "text": {
                int effectiveBgLum = effectiveBackgroundLuminance(e, containers, pageBg);
                return el(pos + textStyle(e, fill, ink, effectiveBgLum), "wf-t", "", textInner(e));
            }
            case "button": {
                boolean isSkeleton = page.getName() != null && (page.getName().contains("骨架") || page.getName().contains("加载"));
                if (isSkeleton && label.isEmpty() && nz(e.getPositionY()) >= 700) {
                    return el(pos + "background:#E5E7EB;border-radius:23px;box-sizing:border-box;", "wf-skeleton-bar", attr, "");
                }
                String radius = radius(fill, e.getHeight());
                String btnInk = DesignColorSampler.contrastInk(fill);

                // 多行气泡复合按钮（如家园顶部的 "+88\n送个礼物"、"+88\n下载完成\n领取奖励"）：
                if (label.contains("\n") || (label.startsWith("+") && label.length() >= 4)) {
                    String[] lines = label.split("\n", -1);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (line.isEmpty()) continue;
                        if (i == 0 && line.startsWith("+")) {
                            sb.append("<span style='background:#FF7A00;color:#FFF;font-size:9px;font-weight:bold;padding:1px 4px;border-radius:6px;line-height:1;margin-bottom:2px;'>")
                              .append(esc(line))
                              .append("</span>");
                        } else {
                            sb.append("<span style='color:").append(btnInk).append(";font-size:9px;line-height:1.1;white-space:nowrap;'>")
                              .append(esc(line))
                              .append("</span>");
                        }
                    }
                    return el(pos + "background:" + fill + ";" + radius + ";display:flex;flex-direction:column;align-items:center;"
                            + "justify-content:center;padding:2px 2px;overflow:hidden;box-shadow:0 2px 6px rgba(0,0,0,0.08);",
                            attr.isEmpty() ? "wf-btn" : "wf-btn wf-act", attr, sb.toString());
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

                if ("✕".equals(label) || ("关闭".equals(label) && nz(e.getWidth()) <= 45 && nz(e.getHeight()) <= 45 && nz(e.getPositionY()) >= 500)) {
                    String closeCircle = "background:rgba(30,30,30,0.65);color:#FFFFFF;border-radius:50%;"
                            + "font-size:18px;font-weight:bold;display:flex;align-items:center;justify-content:center;"
                            + "border:1.5px solid rgba(255,255,255,0.4);cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,0.2);";
                    return el(pos + closeCircle, "wf-btn wf-close-circle wf-act", attr, "✕");
                }

                if ("单倍领取".equals(label)) {
                    String claimStyle = "background:linear-gradient(180deg, #FF7D26 0%, #FF5500 100%);color:#FFFFFF;"
                            + "font-size:16px;font-weight:bold;border-radius:20px;box-shadow:0 4px 14px rgba(255,85,0,0.38);"
                            + "display:flex;align-items:center;justify-content:center;cursor:pointer;";
                    return el(pos + claimStyle, attr.isEmpty() ? "wf-btn" : "wf-btn wf-act", attr, esc(label));
                }

                if ("一键入住".equals(label)) {
                    String btnInner = "<span class='wf-btn-label' style='color:#FFFFFF;font-size:16px;font-weight:bold;white-space:nowrap;'>一键入住</span>";
                    return el(pos + "background:#F85A18;border-radius:24px;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 12px rgba(248,90,24,0.3);cursor:pointer;", "wf-btn wf-act", attr, btnInner);
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
                        ? "<i class='wf-btn-icon' style='width:" + icSize + "px;height:" + icSize + "px;background:"
                            + (btnInk.equals("#FFFFFF") ? "rgba(255,255,255,0.45)" : "rgba(0,0,0,0.15)")
                            + ";border:1px solid "
                            + (btnInk.equals("#FFFFFF") ? "rgba(255,255,255,0.85)" : "rgba(0,0,0,0.3)")
                            + ";border-radius:2px;margin-right:3px;display:inline-block;flex-shrink:0;'></i>"
                        : "";
                String inner = iconBox + "<span class='wf-btn-label' style='color:" + btnInk
                        + ";font-size:" + bfs + "px;white-space:nowrap;line-height:1;'>" + esc(label) + "</span>";

                String btnBg;
                if (fill.startsWith("#") && fill.length() == 7) {
                    String topFill = DesignColorSampler.mixWhite(fill, 0.12);
                    String btmFill = DesignColorSampler.mixBlack(fill, 0.08);
                    btnBg = "background:linear-gradient(180deg, " + topFill + " 0%, " + btmFill + " 100%);";
                } else {
                    btnBg = "background:" + fill + ";";
                }
                return el(pos + btnBg + radius + ";display:flex;align-items:center;"
                        + "justify-content:center;padding:0 2px;overflow:hidden;",
                        attr.isEmpty() ? "wf-btn" : "wf-btn wf-act", attr, inner);
            }
            case "icon": {
                if ("下拉把手".equals(label) || (nz(e.getWidth()) <= 60 && nz(e.getHeight()) <= 12 && (nz(e.getPositionY()) >= 190 && nz(e.getPositionY()) <= 380))) {
                    return el(pos + "background:#D1D5DB;border-radius:9999px;box-sizing:border-box;", "wf-sheet-handle", attr, "");
                }
                boolean onLight = effectiveBackgroundLuminance(e, containers, pageBg) >= 160;
                double minDim = Math.min(nz(e.getWidth()), nz(e.getHeight()));
                int rad = Math.max(4, Math.min(8, (int)(minDim * 0.28)));
                int glyphPct = minDim <= 26 ? 48 : 36;
                String glyphColor = onLight ? "rgba(107,114,128,0.55)" : "rgba(255,255,255,0.75)";
                String glyph = "<i class='wf-ic-glyph' style='width:" + glyphPct + "%;height:" + glyphPct + "%;background:" + glyphColor + ";border-radius:2px;display:inline-block;'></i>";
                return el(pos + boxStyle(fill, onLight, rad), "wf-ic", attr, glyph);
            }
            case "avatar":
                int avFs = clamp((int)(Math.min(nz(e.getWidth()), nz(e.getHeight())) * 0.52), 12, 28);
                return el(pos + "background:rgba(255,255,255,0.85);border:1.5px solid rgba(255,255,255,0.95);"
                        + "border-radius:50%;box-shadow:0 0 0 1px rgba(0,0,0,0.08), 0 3px 10px rgba(0,0,0,0.12);"
                        + "display:flex;align-items:center;justify-content:center;overflow:hidden;", "wf-img wf-avatar", attr,
                        "<span style='font-size:" + avFs + "px;'>👤</span>");
            case "background":
                // 全屏底图/大背景层：纯视觉衬底，绝不加任何"立绘/场景占位"文字，避免污染功能设置、网络提示等纯文字页面
                return "";
            case "image":
            case "banner": {
                boolean isSkeleton = page.getName() != null && (page.getName().contains("骨架") || page.getName().contains("加载"));
                // 骨架页上半部分的壁纸/场景卡片：
                if (isSkeleton && nz(e.getPositionY()) >= 50 && nz(e.getPositionY()) <= 350) {
                    String title = nz(e.getPositionX()) < 150 ? "锁屏壁纸" : "桌面组件";
                    String inner = "<span style='color:#9CA3AF;font-size:12px;font-weight:600;'>[ " + title + " ]</span>";
                    return el(pos + "background:#FFFFFF;border-radius:16px;border:1px solid #E5E7EB;box-shadow:0 4px 14px rgba(0,0,0,0.05);display:flex;align-items:center;justify-content:center;box-sizing:border-box;",
                            "wf-img wf-wallpaper-card", attr, inner);
                }

                if (page.getName() != null && page.getName().contains("宝箱")) {
                    if (nz(e.getPositionY()) <= 260 && nz(e.getPositionX()) <= 80) {
                        String inner = "<span style='color:rgba(107,68,35,0.85);font-size:11px;font-weight:bold;'>[ 挂件立绘 ]</span>";
                        return el(pos + "background:rgba(255,255,255,0.85);border:1.5px dashed rgba(201,155,107,0.75);"
                                + "border-radius:16px;box-shadow:0 3px 10px rgba(0,0,0,0.1);display:flex;align-items:center;justify-content:center;box-sizing:border-box;",
                                "wf-img wf-pendant-art", attr, inner);
                    }
                    if (nz(e.getPositionY()) >= 320 && nz(e.getPositionY()) <= 410) {
                        String inner = "<span style='color:#F59E0B;font-size:12px;font-weight:bold;'>\uD83D\uDC8E 钻石奖励</span>";
                        return el(pos + "background:radial-gradient(circle, rgba(255,251,235,0.95) 0%, rgba(254,243,199,0.5) 100%);"
                                + "border-radius:14px;border:1px solid rgba(252,211,77,0.6);display:flex;align-items:center;justify-content:center;box-sizing:border-box;box-shadow:0 2px 8px rgba(245,158,11,0.15);",
                                "wf-img wf-reward-icon", attr, inner);
                    }
                }

                double canvasArea = canvasW * (double) (page.getCanvasHeight() == null ? 812 : page.getCanvasHeight());
                double area = nz(e.getWidth()) * nz(e.getHeight());
                double areaRatio = area / Math.max(1, canvasArea);

                // 全屏或近全屏背景大图（面积 >= 55% 画布）：属于场景底衬，绝不生成占位大字
                if (areaRatio >= 0.55 || (nz(e.getWidth()) >= canvasW * 0.88 && nz(e.getHeight()) >= 500)) {
                    return "";
                }

                // 纯工具类、设置类、文本列表类页面，界面以文字+开关+图标为主，绝不生成"立绘/场景占位"
                String pName = page.getName() == null ? "" : page.getName();
                boolean isToolPage = pName.contains("设置") || pName.contains("异常") || pName.contains("提示")
                        || pName.contains("说明") || pName.contains("规则") || pName.contains("我的")
                        || pName.contains("关于") || pName.contains("记录") || pName.contains("加载") || pName.contains("骨架");

                // 真正的主视觉立绘/大场景插画（面积介于 8% ~ 55% 之间，且不在工具页面）：才显示占位框
                boolean isHeroArt = !isToolPage && areaRatio >= 0.08 && nz(e.getWidth()) >= 120 && nz(e.getHeight()) >= 120;
                if (isHeroArt) {
                    String placeholderText = label.isBlank() ? "立绘 / 场景占位" : label;
                    String inner = "<span style='color:rgba(107,68,35,0.85);font-size:13px;font-weight:bold;text-align:center;padding:0 8px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'>[ " + esc(placeholderText) + " ]</span>";
                    return el(pos + "background:rgba(255,255,255,0.28);border:2px dashed rgba(176,141,110,0.65);"
                            + "border-radius:18px;display:flex;align-items:center;justify-content:center;box-sizing:border-box;",
                            "wf-img wf-hero-art", attr, inner);
                } else {
                    // 小尺寸图片/道具槽位/设置项配图：严格按照原始坐标渲染规整线框，不加干扰性大字
                    String r = nz(e.getWidth()) > 80 ? "10px;" : "6px;";
                    return el(pos + "background:rgba(255,255,255,0.65);border:1px dashed rgba(201,155,107,0.55);"
                            + "border-radius:" + r + "box-sizing:border-box;", "wf-img", attr, "");
                }
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
                            || (ey >= 330 && ew >= 340 && (eh >= 100 || ey <= 365));
                    if (isSheetBase && ew >= 340) {
                        double totalH = page.getCanvasHeight() == null ? 833 : page.getCanvasHeight();
                        double sheetH = Math.max(eh, totalH - ey);
                        String sheetPos = String.format("left:0px;top:%.0fpx;width:%.0fpx;height:%.0fpx;", ey, canvasW, sheetH);
                        return el(sheetPos + "background:#FFFFFF;border-radius:24px 24px 0 0;box-shadow:0 -8px 32px rgba(0,0,0,0.12);border:1px solid rgba(0,0,0,0.04);box-sizing:border-box;", "wf-card wf-bottom-sheet", attr, "");
                    }
                    // 2. 内部嵌套的大内容卡片：作为透明或白色内衬容器，绝不当做骨架条（防止变成巨型灰色方块盖住内部骨架条）
                    if (label.contains("卡片") || (ew >= 300 && eh >= 120)) {
                        return el(pos + "background:rgba(255,255,255,0.6);border-radius:16px;box-sizing:border-box;", "wf-card", attr, "");
                    }
                    // 3. 骨架屏加载条：高度在常规条形范围（<= 90px），或者 label 包含占位/骨架/标题/内容/按钮/信息
                    if (eh <= 90 || label.contains("占位") || label.contains("骨架") || label.contains("信息") || label.contains("按钮") || label.contains("标题") || label.contains("内容")) {
                        return el(pos + "background:#E5E7EB;border-radius:8px;box-sizing:border-box;", "wf-skeleton-bar", attr, "");
                    }
                }
                // 专属业务卡片与面板优先匹配，防止被通用底板通配误伤：
                if ("奶龙专属道具卡片".equals(label) || "特殊道具卡片".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:16px;box-shadow:0 2px 10px rgba(0,0,0,0.05);border:1px solid #E5E7EB;box-sizing:border-box;", "wf-card", attr, "");
                }
                if ("宝箱奖励卡片".equals(label)) {
                    return el(pos + "background:#FFFDF8;border-radius:24px;box-shadow:0 12px 36px rgba(0,0,0,0.22);border:1px solid rgba(255,255,255,0.9);box-sizing:border-box;", "wf-card wf-modal-panel", attr, "");
                }
                if ("对话气泡".equals(label) || label.contains("气泡")) {
                    return el(pos + "background:#FFFFFF;border-radius:18px;box-shadow:0 3px 10px rgba(0,0,0,0.12);box-sizing:border-box;", "wf-card wf-bubble", attr, "");
                }
                if ("扭蛋机卡牌橱窗".equals(label)) {
                    return el(pos + "background:rgba(255,255,255,0.45);border-radius:18px;border:2px solid rgba(255,255,255,0.85);box-shadow:inset 0 2px 10px rgba(0,0,0,0.06), 0 4px 16px rgba(0,0,0,0.04);box-sizing:border-box;", "wf-card wf-window-panel", attr, "");
                }
                if ("扭蛋机出货槽".equals(label)) {
                    return el(pos + "background:rgba(215,108,138,0.22);border-radius:14px;border:1.5px solid rgba(215,108,138,0.35);box-shadow:inset 0 3px 8px rgba(0,0,0,0.1);box-sizing:border-box;", "wf-card wf-slot-panel", attr, "");
                }
                if ("顶部导航栏底板".equals(label)) {
                    return el(pos + "background:rgba(255,255,255,0.85);border-bottom:1px dashed rgba(201,155,107,0.45);box-sizing:border-box;", "wf-navbar-bar wf-el", attr, "");
                }
                if ("探险与属性说明底板".equals(label)) {
                    return el(pos + "background:#FFFDF8;border-radius:24px;box-shadow:0 12px 36px rgba(0,0,0,0.22);box-sizing:border-box;", "wf-card wf-modal-panel", attr, "");
                }
                if ("主题试用到期弹窗底板".equals(label) || "混搭模式弹窗底板".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:32px 32px 0 0;box-shadow:0 -8px 32px rgba(0,0,0,0.18);box-sizing:border-box;", "wf-card wf-bottom-sheet", attr, "");
                }
                if ("一键入住吸底栏".equals(label)) {
                    return el(pos + "background:#FFFFFF;box-shadow:0 -4px 16px rgba(0,0,0,0.06);box-sizing:border-box;", "wf-card wf-dock-bar", attr, "");
                }
                if ("任务列表白卡".equals(label)) {
                    return el(pos + "background:#FFFFFF;border-radius:18px;box-shadow:0 3px 14px rgba(0,0,0,0.06);box-sizing:border-box;", "wf-card wf-task-panel", attr, "");
                }
                if ("限时兑换货架底板".equals(label)) {
                    return el(pos + "background:rgba(85,45,18,0.30);border-radius:18px;border:1.5px solid rgba(201,155,107,0.35);box-shadow:inset 0 2px 10px rgba(0,0,0,0.20);box-sizing:border-box;", "wf-card wf-shelf-panel", attr, "");
                }
                if ("抽奖结果展示底板".equals(label)) {
                    return el(pos + "background:rgba(255,255,255,0.10);backdrop-filter:blur(12px);border-radius:24px;border:1.5px solid rgba(255,224,102,0.40);box-shadow:0 8px 32px rgba(0,0,0,0.35), 0 0 20px rgba(255,224,102,0.15);box-sizing:border-box;", "wf-card wf-lottery-panel", attr, "");
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
                    String bg = "背包抽屉底板".equals(label) ? "#ECEAE4" : "#FFFFFF";
                    return el(sheetPos + "background:" + bg + ";border-radius:24px 24px 0 0;box-shadow:0 -8px 32px rgba(0,0,0,0.18);box-sizing:border-box;", "wf-card wf-bottom-sheet", attr, "");
                }
                // 容器 label 是功能类别（"卡片""弹窗"等说明信息），不属于画面文字，
                // 一律不上画布——说明内容走 annotation（右侧说明框），避免视觉干扰。
                return el(pos + "background:rgba(255,255,255,0.92);border-radius:16px;"
                        + "box-shadow:0 4px 16px -2px rgba(0,0,0,0.06), 0 2px 6px -1px rgba(0,0,0,0.04), inset 0 1px 0 rgba(255,255,255,0.85);"
                        + "border:1px solid rgba(0,0,0,0.05);box-sizing:border-box;",
                        "wf-card", attr, "");
            }
            case "divider": {
                double divH = Math.min(2.0, Math.max(1.0, nz(e.getHeight())));
                String divPos = String.format("left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;",
                        nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), divH);
                // 两端渐隐的柔和线性分割线
                String divBg = effectiveBackgroundLuminance(e, containers, pageBg) >= 145
                        ? "linear-gradient(90deg, transparent, rgba(0,0,0,0.08) 20%, rgba(0,0,0,0.08) 80%, transparent)"
                        : "linear-gradient(90deg, transparent, rgba(255,255,255,0.18) 20%, rgba(255,255,255,0.18) 80%, transparent)";
                return el(divPos + "background:" + divBg + ";border-radius:1px;", "wf-div", "", "");
            }
            case "search": {
                String inner = "<span class='wf-search-in' style='color:#9CA3AF;font-size:13px;display:flex;align-items:center;gap:6px;'><span style='font-size:16px;'>\u2315</span> " + esc(label.isBlank() ? "搜索" : label) + "</span>";
                return el(pos + "border-radius:20px;background:#FFFFFF;box-shadow:0 2px 8px rgba(0,0,0,0.04);border:1px solid rgba(0,0,0,0.06);"
                        + "display:flex;align-items:center;padding-left:14px;white-space:nowrap;overflow:hidden;", "wf-search", attr, inner);
            }
            case "switch": {
                boolean on = !label.matches("(?i)(off|关|false|0)");
                String trackBg = on
                        ? "linear-gradient(180deg, #FF8336 0%, #FA6B19 100%)"
                        : "linear-gradient(180deg, #E5E7EB 0%, #D1D5DB 100%)";
                double knobSize = Math.max(12, nz(e.getHeight()) - 4);
                String knobStyle = "width:" + (int)knobSize + "px;height:" + (int)knobSize + "px;"
                        + "position:absolute;top:2px;" + (on ? "right:2px;" : "left:2px;")
                        + "background:#FFFFFF;border-radius:50%;"
                        + "box-shadow:0 2px 5px rgba(0,0,0,0.25), inset 0 1px 0 rgba(255,255,255,0.9);";
                String trackShadow = on
                        ? "box-shadow:inset 0 1px 3px rgba(0,0,0,0.15), 0 2px 6px rgba(250,107,25,0.28);"
                        : "box-shadow:inset 0 1px 3px rgba(0,0,0,0.15), 0 1px 3px rgba(0,0,0,0.06);";
                return el(pos + "border-radius:999px;background:" + trackBg + ";" + trackShadow,
                        "wf-sw", attr, "<i class='wf-sw-k' style='" + knobStyle + "'></i>");
            }
            case "checkbox": {
                boolean checked = label.contains("勾") || label.contains("选") || label.contains("已") || label.contains("单抽") || label.matches("(?i)(开|true|1|on)");
                double boxSz = Math.max(13, Math.min(20, Math.min(nz(e.getWidth()), nz(e.getHeight()))));
                String boxBg = checked ? "linear-gradient(180deg, #FF7043 0%, #F4511E 100%)" : "#FFFFFF";
                String box = "<i class='wf-ck-box' style='width:" + (int)boxSz + "px;height:" + (int)boxSz
                        + "px;border-radius:4px;border:1.5px solid " + (checked ? "#F4511E" : "rgba(0,0,0,0.25)")
                        + ";background:" + boxBg + ";box-shadow:0 1px 3px rgba(0,0,0,0.10);"
                        + ";display:inline-flex;align-items:center;justify-content:center;box-sizing:border-box;flex-shrink:0;'>"
                        + (checked ? "<svg viewBox='0 0 16 16' width='10' height='10' fill='none' stroke='#FFF' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'><path d='M13 4L6 11L3 8'/></svg>" : "")
                        + "</i>";
                // 状态词（"开"、"关"等）仅代表勾选状态，严禁当作文字标签输出，防止与右侧独立的"单抽"、"10抽"文本重叠压字
                boolean isStateWord = label.matches("(?i)(开|关|true|false|0|1|on|off)");
                String text = (label.isBlank() || isStateWord) ? "" : "<span class='wf-ck-label' style='font-size:12px;font-weight:500;color:#374151;margin-left:5px;white-space:nowrap;'>" + esc(label) + "</span>";
                return el(pos + "display:flex;align-items:center;cursor:pointer;", "wf-ck" + (checked ? " checked" : ""), attr, box + text);
            }
            case "badge": {
                boolean isPlus = label.contains("+") || label.contains("88");
                String badgeBg = isPlus ? "linear-gradient(180deg, #FFA000 0%, #FF6F00 100%)"
                        : (label.contains("新") || label.contains("领") || label.contains("热")
                           ? "linear-gradient(180deg, #EF4444 0%, #DC2626 100%)"
                           : (isVivid(fill) ? fill : accent));
                int bfs = clamp(fs(label, nz(e.getHeight()), nz(e.getWidth())), 9, 12);
                return el(pos + "background:" + badgeBg + ";color:#fff;border-radius:999px;display:flex;"
                        + "align-items:center;justify-content:center;white-space:nowrap;overflow:hidden;"
                        + "padding:1px 6px;box-shadow:0 2px 6px rgba(220,38,38,0.28), inset 0 1px 0 rgba(255,255,255,0.35);"
                        + "font-size:" + bfs + "px;font-weight:700;line-height:1;border:1px solid rgba(255,255,255,0.25);",
                        "wf-badge", attr, esc(label));
            }
            case "progress": {
                // 进度条：圆角滑槽底色（带内阴影） + 主题色高光渐变填充条
                double pct = 0.65;
                if (label.contains("%")) {
                    try {
                        pct = Double.parseDouble(label.replaceAll("[^0-9.]", "")) / 100.0;
                        pct = Math.max(0.05, Math.min(1.0, pct));
                    } catch (Exception ignored) {}
                }
                double barH = Math.max(6, Math.min(16, nz(e.getHeight())));
                String trackBg = effectiveBackgroundLuminance(e, containers, pageBg) >= 145
                        ? "rgba(0,0,0,0.08)" : "rgba(255,255,255,0.20)";
                String barFill = isVivid(fill) ? fill : accent;
                String barTop = DesignColorSampler.mixWhite(barFill, 0.25);
                int innerW = (int) Math.round((nz(e.getWidth()) - 2) * pct);
                String innerBar = "<div style='width:" + innerW + "px;height:100%;border-radius:999px;"
                        + "background:linear-gradient(180deg, " + barTop + " 0%, " + barFill + " 100%);"
                        + "box-shadow:0 1px 3px rgba(0,0,0,0.2), inset 0 1px 0 rgba(255,255,255,0.4);'></div>";
                return el(pos + "height:" + (int)barH + "px;border-radius:999px;background:" + trackBg + ";"
                        + "padding:1px;box-shadow:inset 0 1px 3px rgba(0,0,0,0.15);display:flex;align-items:center;",
                        "wf-progress", attr, innerBar);
            }
            case "tabs": {
                if (label.contains("动漫耀宝")) {
                    String tabInner = "<span style='color:#F85A18;font-size:15px;font-weight:bold;border-bottom:2.5px solid #F85A18;padding-bottom:2px;'>" + esc(label) + "</span>";
                    return el(pos + "display:flex;align-items:center;background:none;border-radius:0;", "wf-tab-underline", attr, tabInner);
                }
                String[] seg = label.split("[|/、,，]");
                StringBuilder segs = new StringBuilder("<div class='wf-segs'>");
                for (int i = 0; i < seg.length; i++) {
                    String segName = seg[i].trim();
                    String navAttr = "";
                    Long targetPid = findTabSegmentTarget(segName, page.getId(), pageNameById);
                    if (targetPid != null && pageNameById.containsKey(targetPid)) {
                        navAttr = " data-nav=\"" + esc(pageNameById.get(targetPid)) + "\" style=\"cursor:pointer;\"";
                    }
                    boolean isOn = (i == 0 && navAttr.isEmpty()) || isCurrentPageTab(segName, page.getName());
                    segs.append("<span class='").append(isOn ? "on" : "").append("'").append(navAttr).append(">")
                            .append(esc(segName)).append("</span>");
                }
                segs.append("</div>");
                // 容器底色用设计稿采样色（等比提亮，避免硬编码 #F3F4F6 造成"灰容器+白药丸"的视觉塌缩）；
                // 激活段底色接入采样色并通过 CSS 变量下发，保证始终与设计稿观感一致。
                String track = DesignColorSampler.mixWhite(fill, 0.25);
                String segBg = isVivid(fill) || DesignColorSampler.contrastInk(fill).equals("#FFFFFF")
                        ? fill : DesignColorSampler.mixWhite(fill, 0.55);
                String segInk = DesignColorSampler.contrastInk(segBg).equals("#FFFFFF") ? "#FFFFFF" : "#4F46E5";
                String segStyle = "--seg-bg:" + segBg + ";--seg-ink:" + segInk + ";";
                return el(pos + "background:" + track + ";border-radius:10px;padding:3px;" + segStyle,
                        "wf-tabs", "", segs.toString());
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
                boolean onLightDef = effectiveBackgroundLuminance(e, containers, pageBg) >= 160;
                String glyphColorDef = onLightDef ? "rgba(156,163,175,0.50)" : "rgba(107,114,128,0.35)";
                String glyphDef = "<i class='wf-ic-glyph' style='width:36%;height:36%;background:" + glyphColorDef + ";border-radius:2px;display:inline-block;'></i>";
                return el(pos + boxStyle(fill, onLightDef), "wf-ic", attr, glyphDef);
        }
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
            return "color:#FFE066;font-size:20px;font-weight:bold;text-shadow:0 2px 8px rgba(0,0,0,0.6), 0 0 12px rgba(255,224,102,0.8);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:flex;align-items:center;justify-content:center;letter-spacing:1px;";
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
        return "background:rgba(255,255,255,0.92);border-radius:0;display:flex;align-items:center;"
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

    private String interactionAttrs(List<Interaction> inters, Map<Long, String> pageNameById) {
        if (inters == null || inters.isEmpty()) return "";
        for (Interaction it : inters) {
            String act = it.getActionType() == null ? "" : it.getActionType();
            if ("navigate".equals(act) && it.getTargetPageId() != null) {
                String name = pageNameById.get(it.getTargetPageId());
                if (name != null && !name.isBlank()) {
                    return " data-nav=\"" + esc(name) + "\"";
                }
            } else if ("back".equals(act)) {
                return " data-action=\"back\"";
            } else if (("modal".equals(act) || "popup".equals(act))
                    && it.getTargetPageId() != null && pageNameById.containsKey(it.getTargetPageId())) {
                return " data-modal=\"" + it.getTargetPageId() + "\"";
            } else if ("tab_switch".equals(act) || "tab".equals(act)) {
                return " data-action=\"tab\"";
            }
        }
        return "";
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
                  .wf-btn{cursor:pointer;border:1px solid rgba(255,255,255,0.25);box-shadow:0 3px 10px rgba(0,0,0,0.12), inset 0 1px 0 rgba(255,255,255,0.35);transition:transform 0.1s ease, filter 0.15s ease}
                  .wf-btn:hover{filter:brightness(1.05);box-shadow:0 4px 14px rgba(0,0,0,0.16), inset 0 1px 0 rgba(255,255,255,0.45)}
                  .wf-btn:active{transform:scale(0.97);filter:brightness(0.95)}
                  .wf-btn-label{color:#FFF;font-weight:600;font-size:%dpx;white-space:nowrap;
                       overflow:hidden;text-overflow:ellipsis;max-width:100%%;letter-spacing:-0.2px;text-shadow:0 1px 2px rgba(0,0,0,0.20)}
                  .wf-act,.wf-ic[data-nav],.wf-img[data-nav],.wf-card[data-nav]{cursor:pointer}
                  .wf-badge,.wf-side-pill{z-index:35}
                  .wf-navbar{z-index:45}
                  .wf-segs{display:flex;width:100%%;height:100%%;box-sizing:border-box}
                  .wf-segs span{flex:1;display:flex;align-items:center;justify-content:center;
                       font-size:13px;color:#6B7280;border-radius:8px;min-width:0;
                       white-space:nowrap;overflow:hidden}
                  .wf-segs span.on{background:var(--seg-bg, #FFF);color:var(--seg-ink, %s);box-shadow:0 1px 3px rgba(0,0,0,.12);font-weight:600}
                  .wf-sw{display:block;cursor:pointer;transition:all 0.2s ease}
                  .wf-sw-k{transition:all 0.22s cubic-bezier(0.34, 1.56, 0.64, 1)}
                  .wf-ck{cursor:pointer;transition:opacity 0.15s ease}
                  .wf-ck:active{opacity:0.8}
                  .wf-rate{user-select:none}
                  .wf-progress{box-sizing:border-box}
                  .wf-search-in{font-size:14px;user-select:none}
                  .wf-modal{position:absolute;inset:0;z-index:999;display:none;background:rgba(15,18,26,.55);backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px)}
                  .wf-modal.wf-show{display:block}
                  .wf-modal-dismiss{position:absolute;inset:0;background:none;border:none;padding:0;margin:0;cursor:pointer}
                  .wf-tabbar{display:flex;z-index:50;background:rgba(255,255,255,0.94);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);box-shadow:0 -2px 10px rgba(0,0,0,.05);border-top:1px solid rgba(0,0,0,0.04)}
                  .wf-tabit{position:absolute;top:0;height:100%%;display:flex;flex-direction:column;
                       align-items:center;justify-content:center;gap:3px;cursor:pointer;min-width:0}
                  .wf-tabic{width:22px;height:22px;border-radius:6px;background:rgba(176,141,110,.25);
                       border:1px dashed rgba(201,155,107,0.4)}
                  .wf-tabit span{font-size:10px;color:rgba(107,68,35,.75);max-width:92%%;white-space:nowrap;
                       overflow:hidden;text-overflow:ellipsis}
                  .wf-tabit.on .wf-tabic{background:%s}
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