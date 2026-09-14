package com.wireforge.ai;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.List;

/**
 * 无头 Chromium 渲染器：把整页 HTML 渲染成 PNG 截图，供"对比原稿→回修"闭环使用。
 *
 * 需要先安装浏览器二进制（首次部署执行一次）：
 *   mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
 * 若未安装或启动失败，本组件以"不可用"状态运行，analyzePage 会自动跳过回修（不阻断主流程）。
 */
@Slf4j
@Component
public class HtmlRenderer {

    private Playwright playwright;
    private Browser browser;
    private final int deviceScaleFactor;

    public HtmlRenderer(@Value("${wireforge.ai.render-scale:2}") int deviceScaleFactor) {
        this.deviceScaleFactor = deviceScaleFactor;
    }

    @PostConstruct
    public void init() {
        try {
            this.playwright = Playwright.create();
            this.browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setArgs(java.util.List.of("--no-sandbox", "--disable-dev-shm-usage")));
            log.info("HtmlRenderer 无头 Chromium 初始化完成（用于回修截图，scale={}）", deviceScaleFactor);
        } catch (Exception e) {
            log.error("HtmlRenderer 初始化失败，回修功能将不可用（请确认已执行 'playwright install chromium'）：{}",
                    e.getMessage());
            this.browser = null;
        }
    }

    /** 渲染器是否可用（浏览器成功启动才可用）。 */
    public boolean isAvailable() {
        return browser != null;
    }

    /**
     * 把 HTML 渲染为 PNG 截图字节。
     *
     * @param html  完整 HTML 文档
     * @param width 逻辑宽度（如 375），决定布局与截图宽度
     */
    public byte[] screenshot(String html, int width) {
        if (browser == null) {
            throw new IllegalStateException("HtmlRenderer 不可用（Chromium 未初始化）");
        }
        int w = width > 0 ? width : 375;
        try (Page page = browser.newPage(new Browser.NewPageOptions()
                .setViewportSize(w, 800)
                .setDeviceScaleFactor(deviceScaleFactor))) {
            page.setContent(html);
            // 等 CSS/字体/图片基本就位；固定等待避免依赖网络空闲导致卡顿
            page.waitForTimeout(500);
            return page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true));
        } catch (Exception e) {
            throw new RuntimeException("HTML 渲染截图失败: " + e.getMessage(), e);
        }
    }

    /** 单条交互检查结果 */
    public record CheckResult(String page, String kind, String target, boolean ok, String note) {}

    /**
     * 交互 runtime（与前端 PageCanvas.vue 的注入脚本保持一致；前端会在预览时注入同款）。
     * 存库的 html_content 不含 runtime（由前端注入），因此无头验证前必须先补上。
     * 修改两者之一时务必同步另一份。
     */
    private static final String WF_RUNTIME =
            "<script data-wf-inject>(function(){"
            + "function openModal(id){var m=document.getElementById('wf-modal-'+id);if(m)m.classList.add('wf-show');}"
            + "function closeModal(m){if(m)m.classList.remove('wf-show');}"
            + "document.addEventListener('click',function(e){"
            + "var t=e.target;var el=t&&t.closest?t.closest('[data-nav],[data-modal],[data-action]'):null;"
            + "if(!el)return;e.preventDefault();e.stopPropagation();"
            + "if(el.hasAttribute('data-modal')){openModal(el.getAttribute('data-modal'));return;}"
            + "var a=el.getAttribute('data-action')||'';"
            + "if(a==='back'||a==='close'){var inModal=el.closest('.wf-modal');"
            + "if(inModal){closeModal(inModal);return;}parent.postMessage({type:'wf-back'},'*');return;}"
            + "if(a==='tab'){var segs=el.querySelectorAll('.wf-segs span');"
            + "if(segs.length){var cur=el.querySelector('.wf-segs span.on')||segs[0];"
            + "var idx=Array.prototype.indexOf.call(segs,cur);"
            + "for(var i=0;i<segs.length;i++)segs[i].classList.remove('on');"
            + "segs[(idx+1)%segs.length].classList.add('on');}return;}"
            + "var nav=el.getAttribute('data-nav');"
            + "if(nav)parent.postMessage({type:'wf-nav',page:nav},'*');"
            + "},true);})();</script>";

    /**
     * 交互自动验证：在无头浏览器里真实点击页面上的所有交互元素并断言行为。
     *  - [data-nav]：应向父窗口发出 {type:'wf-nav', page:目标名}（顶层加载时 parent==自身，可监听 message）
     *  - [data-modal]：点击后对应 .wf-modal 应出现 wf-show；再点其内部 back 应关闭
     *  - [data-action=back]（浮层外）：应发出 {type:'wf-back'}
     * 返回逐条检查结果，全部通过返回空失败列表（结果包含 ok 项便于统计）。
     */
    public List<CheckResult> verifyInteractions(String pageName, String html) {
        List<CheckResult> out = new java.util.ArrayList<>();
        if (browser == null) {
            return out;
        }
        try (Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(375, 812))) {
            String withRuntime = html.contains("</body>")
                    ? html.replace("</body>", WF_RUNTIME + "</body>")
                    : html + WF_RUNTIME;
            page.setContent(withRuntime);
            page.waitForTimeout(400);
            page.evaluate("""
                    () => { window.__wf = [];
                      window.addEventListener('message', e => {
                        try { if (e && e.data && e.data.type) window.__wf.push(e.data); } catch (_) {}
                      });
                    }
                    """);

            // ---- navigate ----
            int navCount = (Integer) page.evaluate(
                    "() => document.querySelectorAll('[data-nav]').length");
            for (int i = 0; i < navCount; i++) {
                String expect = (String) page.evaluate(
                        "(idx) => { const els=[...document.querySelectorAll('[data-nav]')];"
                                + " const el=els[idx]; if(!el) return '';"
                                + " el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));"
                                + " return el.getAttribute('data-nav'); }", i);
                page.waitForTimeout(40);
                boolean sent = (Boolean) page.evaluate(
                        "(exp) => { const ms=(window.__wf||[]).filter(m=>m.type==='wf-nav');"
                                + " const last=ms[ms.length-1];"
                                + " return !!last && last.page === exp; }", expect);
                out.add(new CheckResult(pageName, "navigate", expect, sent,
                        sent ? "" : "点击后未收到 wf-nav 消息（可能被遮挡或 runtime 缺失）"));
            }

            // ---- modal（弹层开 + 内部 back 关）----
            int modCount = (Integer) page.evaluate(
                    "() => document.querySelectorAll('[data-modal]').length");
            for (int i = 0; i < modCount; i++) {
                Object r = page.evaluate(
                        "(idx) => { const els=[...document.querySelectorAll('[data-modal]')];"
                                + " const el=els[idx]; if(!el) return ['',''];"
                                + " el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));"
                                + " const id=el.getAttribute('data-modal');"
                                + " const m=document.getElementById('wf-modal-'+id);"
                                + " return [id, m && m.classList.contains('wf-show')]; }", i);
                @SuppressWarnings("unchecked")
                java.util.List<Object> rr = (java.util.List<Object>) r;
                String id = String.valueOf(rr.get(0));
                boolean opened = Boolean.TRUE.equals(rr.get(1));
                if (!opened) {
                    out.add(new CheckResult(pageName, "modal", id, false, "点击后浮层未显示（wf-modal 层缺失或样式异常）"));
                    continue;
                }
                boolean closed = (Boolean) page.evaluate(
                        "(mid) => { const m=document.getElementById('wf-modal-'+mid); if(!m) return false;"
                                + " const back=m.querySelector('[data-action=\"back\"]');"
                                + " if(back) back.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));"
                                + " return !m.classList.contains('wf-show'); }", id);
                out.add(new CheckResult(pageName, "modal", id, closed,
                        closed ? "" : "浮层能打开但 back 未关闭"));
            }

            // ---- back（浮层外，如顶栏返回箭头）----
            int backCount = (Integer) page.evaluate(
                    "() => [...document.querySelectorAll('[data-action=\"back\"]')]"
                            + ".filter(e=>!e.closest('.wf-modal')).length");
            for (int i = 0; i < backCount; i++) {
                page.evaluate("(idx) => { window.__wf.length=0;"
                        + " const els=[...document.querySelectorAll('[data-action=\"back\"]')]"
                        + ".filter(e=>!e.closest('.wf-modal'));"
                        + " const el=els[idx]; if(el) el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true})); }", i);
                page.waitForTimeout(40);
                boolean sent = (Boolean) page.evaluate(
                        "() => (window.__wf||[]).some(m=>m.type==='wf-back')");
                out.add(new CheckResult(pageName, "back", "#" + i, sent,
                        sent ? "" : "点击后未收到 wf-back 消息"));
            }
        } catch (Exception e) {
            out.add(new CheckResult(pageName, "runtime", "", false, "验证执行异常: " + e.getMessage()));
        }
        return out;
    }

    @PreDestroy
    public void destroy() {
        try {
            if (browser != null) browser.close();
        } catch (Exception ignore) {
        }
        try {
            if (playwright != null) playwright.close();
        } catch (Exception ignore) {
        }
    }
}
