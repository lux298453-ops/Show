package com.wireforge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设计稿颜色采样器：直接读取原始设计稿图片的像素，为每个元素区域采样出
 * 真实的填充色（背景/按钮底色）与文字色。纯确定性算法，不经过任何模型猜测。
 *
 * 坐标约定：元素坐标是画布坐标（宽 375），需要乘以 imgW/canvasW 映射回原图像素。
 */
@Slf4j
@Service
public class DesignColorSampler {

    /** 单元素采样结果：fill=填充色(HEX)，ink=文字色(HEX)。ink 可能为 null（未检测到明显前景） */
    public record ElemColors(String fill, String ink) {}

    /**
     * 对一页的所有元素采样颜色。
     *
     * @param img       原始设计稿图像
     * @param boxes     元素列表，key=元素id，value=[x,y,w,h] 画布坐标
     * @param canvasW   画布宽（=375）
     * @param canvasH   画布高
     */
    public Map<Long, ElemColors> sample(BufferedImage img, Map<Long, double[]> boxes,
                                        double canvasW, double canvasH) {
        Map<Long, ElemColors> out = new LinkedHashMap<>();
        if (img == null || boxes == null || boxes.isEmpty()) {
            return out;
        }
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        double sx = canvasW > 0 ? imgW / canvasW : 1;
        double sy = canvasH > 0 ? imgH / canvasH : sx;

        for (var entry : boxes.entrySet()) {
            double[] b = entry.getValue();
            if (b == null || b.length < 4 || b[2] <= 0 || b[3] <= 0) {
                continue;
            }
            // 画布坐标 → 原图像素坐标，并夹紧到图内
            int x0 = clamp((int) Math.floor(b[0] * sx), 0, imgW - 1);
            int y0 = clamp((int) Math.floor(b[1] * sy), 0, imgH - 1);
            int bw = clamp((int) Math.ceil(b[2] * sx), 1, imgW - x0);
            int bh = clamp((int) Math.ceil(b[3] * sy), 1, imgH - y0);
            try {
                out.put(entry.getKey(), sampleBox(img, x0, y0, bw, bh));
            } catch (Exception e) {
                log.debug("元素 [{}] 采样失败: {}", entry.getKey(), e.getMessage());
            }
        }
        return out;
    }

    /** 单个盒子采样：边缘带取主色当填充色，中心区找离填充色最远的像素簇当文字色 */
    private ElemColors sampleBox(BufferedImage img, int x0, int y0, int bw, int bh) {
        // 太小的盒子（<6px）只有一个候选像素，直接返回均值
        if (bw < 6 || bh < 6) {
            int rgb = avg(img, x0, y0, bw, bh);
            String c = hex(rgb);
            return new ElemColors(c, null);
        }

        // ---- 1) 边缘带采样填充色：外圈 18% 厚度的边带（去掉四角），通常是容器/按钮自己的底色 ----
        int t = Math.max(1, (int) (Math.min(bw, bh) * 0.18));
        long rs = 0, gs = 0, bs = 0;
        int n = 0;
        // 上边带与下边带（横贯整宽）
        for (int y = y0; y < y0 + t && y < y0 + bh; y++) {
            for (int x = x0; x < x0 + bw; x++) {
                int p = img.getRGB(x, y);
                rs += (p >> 16) & 0xFF; gs += (p >> 8) & 0xFF; bs += p & 0xFF; n++;
            }
        }
        int yBottom = y0 + bh - t;
        for (int y = Math.max(yBottom, y0); y < y0 + bh; y++) {
            for (int x = x0; x < x0 + bw; x++) {
                int p = img.getRGB(x, y);
                rs += (p >> 16) & 0xFF; gs += (p >> 8) & 0xFF; bs += p & 0xFF; n++;
            }
        }
        // 左右两条竖带（中间段即可）
        int yMidA = y0 + t, yMidB = y0 + bh - t;
        for (int x = x0; x < x0 + t; x++) {
            for (int y = yMidA; y < yMidB; y++) {
                int p = img.getRGB(x, y);
                rs += (p >> 16) & 0xFF; gs += (p >> 8) & 0xFF; bs += p & 0xFF; n++;
            }
        }
        int xRight = x0 + bw - t;
        for (int x = Math.max(xRight, x0); x < x0 + bw; x++) {
            for (int y = yMidA; y < yMidB; y++) {
                int p = img.getRGB(x, y);
                rs += (p >> 16) & 0xFF; gs += (p >> 8) & 0xFF; bs += p & 0xFF; n++;
            }
        }
        if (n == 0) {
            return new ElemColors("#F3F4F6", null);
        }
        int fr = (int) (rs / n), fg = (int) (gs / n), fb = (int) (bs / n);
        String fill = hex((fr << 16) | (fg << 8) | fb);

        // ---- 2) 中心区 50% 范围内找文字色：取与填充色亮度差最大的前 12% 像素求均值 ----
        int cx0 = x0 + bw / 4, cy0 = y0 + bh / 4;
        int cw = bw / 2, ch = bh / 2;
        boolean lightBg = ((fr * 299 + fg * 587 + fb * 114) / 1000) >= 140;
        record Cand(int d, int rgb) {}
        List<Cand> cand = new java.util.ArrayList<>();
        for (int y = cy0; y < cy0 + ch; y++) {
            for (int x = cx0; x < cx0 + cw; x++) {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, bl = p & 0xFF;
                int lum = (r * 299 + g * 587 + bl * 114) / 1000;
                int flum = (fr * 299 + fg * 587 + fb * 114) / 1000;
                cand.add(new Cand(lightBg ? flum - lum : lum - flum, p));
            }
        }
        cand.sort((a, b2) -> Integer.compare(b2.d, a.d));
        int take = Math.max(1, cand.size() * 12 / 100);
        if (cand.get(0).d < 45) {
            // 中心与边缘对比度不足：没有可辨识的文字/图标前景
            return new ElemColors(fill, null);
        }
        long ir = 0, ig = 0, ib = 0;
        for (int i = 0; i < take; i++) {
            int p = cand.get(i).rgb();
            ir += (p >> 16) & 0xFF; ig += (p >> 8) & 0xFF; ib += p & 0xFF;
        }
        String ink = hex(((int) (ir / take) << 16) | ((int) (ig / take) << 8) | (int) (ib / take));
        return new ElemColors(fill, ink);
    }

    private static int avg(BufferedImage img, int x0, int y0, int bw, int bh) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = y0; y < y0 + bh; y++) {
            for (int x = x0; x < x0 + bw; x++) {
                int p = img.getRGB(x, y);
                r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
            }
        }
        if (n == 0) return 0xFFFFFFFF;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** 页面主题色：收集所有按钮采出的填充色，选饱和度最高且出现次数加权最大的一个 */
    public String pickAccent(Map<String, Integer> fillColorCounts) {
        String best = null;
        double bestScore = -1;
        for (var e : fillColorCounts.entrySet()) {
            int rgb = parseHex(e.getKey());
            if (rgb < 0) continue;
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            double score = hsb[1] * e.getValue(); // 饱和度 × 出现次数
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return bestScore > 0 ? best : "#4F46E5";
    }

    /** 把任意颜色转成低饱和浅色调（图片占位块用）：s×0.32、明度抬到 88~93% */
    public static String softTint(String hex) {
        int rgb = parseHex(hex);
        if (rgb < 0) return "#F3F4F6";
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        int tinted = java.awt.Color.HSBtoRGB(hsb[0], Math.min(hsb[1] * 0.32f, 0.22f), 0.90f);
        return hex(tinted);
    }

    /** 原色与白按比例混合（weight=白色权重）。大面积图块占位用，比 softTint 更"实"，避免隐身 */
    public static String mixWhite(String hex, double weight) {
        int rgb = parseHex(hex);
        if (rgb < 0) return "#F3F4F6";
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = (int) Math.round(r * (1 - weight) + 255 * weight);
        g = (int) Math.round(g * (1 - weight) + 255 * weight);
        b = (int) Math.round(b * (1 - weight) + 255 * weight);
        return hex((r << 16) | (g << 8) | b);
    }

    /** 原色与黑按比例混合（weight=黑色权重）。用于产生自然下沉色调与按钮立体渐变 */
    public static String mixBlack(String hex, double weight) {
        int rgb = parseHex(hex);
        if (rgb < 0) return "#1F2937";
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = (int) Math.round(r * (1 - weight));
        g = (int) Math.round(g * (1 - weight));
        b = (int) Math.round(b * (1 - weight));
        return hex((r << 16) | (g << 8) | b);
    }

    /** 根据底色挑选可读的前景色（深字/白字） */
    public static String contrastInk(String hexBg) {
        int lum = luminance(hexBg);
        return lum >= 150 ? "#333333" : "#FFFFFF";
    }

    public static int luminance(String hex) {
        int rgb = parseHex(hex);
        if (rgb < 0) return 255;
        return (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static int parseHex(String hex) {
        if (hex == null || hex.isBlank()) return -1;
        String s = hex.trim().replace("#", "");
        if (s.length() != 6) return -1;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
