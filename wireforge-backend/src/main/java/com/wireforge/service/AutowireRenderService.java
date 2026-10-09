package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import com.wireforge.model.AutowirePlan.NavigationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.regex.Pattern;

/** Durable post-commit jobs. Patch interaction bindings, preserving user-edited HTML and layout. */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class AutowireRenderService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;
    private final AppMapService appMapService;
    private final PlatformTransactionManager transactionManager;

    public record Binding(long pageId, long elementId, String label, String type, String group,
                          String domUid, double x, double y, double width, double height,
                          String action, Long targetId, String targetName, NavigationInfo navigation) {
        public Binding(long pageId, long elementId, String label, String type, String group,
                       String domUid, double x, double y, double width, double height,
                       String action, Long targetId, String targetName) {
            this(pageId,elementId,label,type,group,domUid,x,y,width,height,action,targetId,targetName,null);
        }
    }

    private NavigationInfo navigationMetadata(Interaction line, List<Interaction> fallback, long elementId) {
        List<Interaction> sources = new ArrayList<>(); if (line != null) sources.add(line);
        fallback.stream().filter(i -> i.getElementId().equals(elementId)).forEach(sources::add);
        for (Interaction source : sources) try {
            var node = json.readTree(AutowirePlanner.safe(source.getParams()));
            if (node != null && node.path("navigation").isObject()) return json.treeToValue(node.path("navigation"),NavigationInfo.class);
        } catch(Exception ignored) {}
        return null;
    }

    public List<Binding> bindings(List<Element> changed, List<Interaction> oldLines, List<Page> pages) {
        List<Binding> result = new ArrayList<>();
        for (Element e : changed) {
            List<Interaction> current = interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId, e.getId()).orderByAsc(Interaction::getId));
            Interaction line = current.stream().filter(i -> "user".equals(i.getSource())).findFirst().orElse(current.isEmpty() ? null : current.get(0));
            String uid = "";
            for (Interaction old : oldLines) if (old.getElementId().equals(e.getId())) {
                try { var p = json.readTree(AutowirePlanner.safe(old.getParams())); uid = p.path("domUid").asText(p.path("dom_uid").asText("")); } catch (Exception ignored) {}
                if (!uid.isBlank()) break;
            }
            if (line != null) {
                try { var p = json.readTree(AutowirePlanner.safe(line.getParams())); String currentUid = p.path("domUid").asText(p.path("dom_uid").asText("")); if (!currentUid.isBlank()) uid = currentUid; } catch (Exception ignored) {}
            }
            Long target = line == null ? null : line.getTargetPageId();
            String name = pages.stream().filter(p -> p.getId().equals(target)).map(Page::getName).findFirst().orElse("");
            result.add(new Binding(e.getPageId(), e.getId(), AutowirePlanner.safe(e.getLabel()), AutowirePlanner.safe(e.getType()), AutowirePlanner.safe(e.getGroupKey()), uid,
                    nz(e.getPositionX()), nz(e.getPositionY()), nz(e.getWidth()), nz(e.getHeight()), line == null ? "none" : line.getActionType(), target, name,navigationMetadata(line,oldLines,e.getId())));
        }
        return result;
    }

    /** Called inside the manual-edit transaction; the scheduler runs it only after commit. */
    public void enqueueManual(long projectId, Element element, List<Interaction> oldLines) {
        List<Page> pages = pageMapper.selectList(Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, projectId));
        String jobId = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO autowire_render_job(id,project_id,page_ids,bindings_json,status) VALUES(?,?,?,?, 'pending')",
                    jobId, projectId, json.writeValueAsString(List.of(element.getPageId())), json.writeValueAsString(bindings(List.of(element), oldLines, pages)));
        } catch (Exception ex) { throw new IllegalStateException("交互预览任务保存失败", ex); }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    TransactionTemplate postCommit = new TransactionTemplate(transactionManager);
                    postCommit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    try { postCommit.executeWithoutResult(s -> process(jobId)); }
                    catch (Exception ex) { log.warn("Manual interaction preview job {} remains recoverable: {}", jobId, ex.getMessage()); }
                }
            });
        }
    }

    public synchronized void process(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM autowire_render_job WHERE id=?", id);
        if (rows.isEmpty() || !"pending".equals(rows.get(0).get("status"))) return;
        if (jdbc.update("UPDATE autowire_render_job SET status='running',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'", id) != 1) return;
        Map<String, Object> job = rows.get(0);
        try {
            List<Binding> changes = json.readValue(job.get("bindings_json").toString(), new TypeReference<>() {});
            long projectId = ((Number) job.get("project_id")).longValue();
            List<Page> pages = pageMapper.selectList(Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, projectId));
            // Refresh persisted binding commands from current relations so a delayed retry cannot restore stale lines.
            List<Binding> refreshed = new ArrayList<>();
            Map<Long,List<NavigationPlanner.Entry>> navigationByPage = new HashMap<>();
            for (Binding b : changes) {
                Element element = elementMapper.selectById(b.elementId());
                if (element == null || element.getPageId() != b.pageId()) continue;
                List<Interaction> current = interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId, b.elementId()).orderByAsc(Interaction::getId));
                Interaction line = current.stream().filter(i -> "user".equals(i.getSource())).findFirst().orElse(current.isEmpty() ? null : current.get(0));
                Long target = line == null ? null : line.getTargetPageId();
                String name = pages.stream().filter(p -> p.getId().equals(target)).map(Page::getName).findFirst().orElse("");
                NavigationInfo nav = navigationMetadata(line,List.of(),b.elementId()); if (nav == null) nav = b.navigation();
                if (nav != null) {
                    Page owner = pages.stream().filter(p -> p.getId() == b.pageId()).findFirst().orElseThrow();
                    var entry = navigationByPage.computeIfAbsent(b.pageId(),pageId -> NavigationPlanner.detect(List.of(owner),elementMapper.selectList(Wrappers.<Element>lambdaQuery().eq(Element::getPageId,pageId)))).stream().filter(e -> e.ids().contains(b.elementId())).findFirst().orElse(null);
                    if (entry != null) nav = new NavigationInfo(entry.familyKey(),entry.itemKey(),entry.label(),entry.region(),entry.ids(),entry.x(),entry.y(),entry.width(),entry.height(),nav.status(),nav.basis(),nav.previousAction(),nav.previousTargetPageId(),List.of());
                    else nav = null; // Layout changed: retain the exact node binding, never guess a stale hot area.
                }
                refreshed.add(new Binding(b.pageId(), b.elementId(), AutowirePlanner.safe(element.getLabel()), AutowirePlanner.safe(element.getType()), AutowirePlanner.safe(element.getGroupKey()), b.domUid(), nz(element.getPositionX()), nz(element.getPositionY()), nz(element.getWidth()), nz(element.getHeight()), line == null ? "none" : line.getActionType(), target, name,nav));
            }
            String beforeMap = jdbc.queryForObject("SELECT COALESCE(app_map,'') FROM project WHERE id=?", String.class, projectId);
            if (appMapService.buildAppMap(projectId) == null) jdbc.update("UPDATE project SET app_map=NULL WHERE id=?", projectId);
            String afterMap = jdbc.queryForObject("SELECT COALESCE(app_map,'') FROM project WHERE id=?", String.class, projectId);
            boolean sharedChanged = !Objects.equals(beforeMap, afterMap);
            com.fasterxml.jackson.databind.node.ObjectNode mapPayload = json.createObjectNode();
            if (afterMap != null && !afterMap.isBlank()) mapPayload.set("map", json.readTree(afterMap));
            mapPayload.set("pageNames", json.valueToTree(pages.stream().collect(java.util.stream.Collectors.toMap(p -> String.valueOf(p.getId()), Page::getName))));
            afterMap = json.writeValueAsString(mapPayload);
            Set<Long> affected = new LinkedHashSet<>(refreshed.stream().map(Binding::pageId).toList());
            if (sharedChanged) {
                // Shared map consumers are refreshed without recreating their page layout.
                for (Page page : pages) if (page.getHtmlContent() != null && page.getHtmlContent().contains("wf-tabbar"))
                    affected.add(page.getId());
            }
            for (long pageId : affected) {
                Page page = pageMapper.selectById(pageId);
                if (page != null) patchPage(page, refreshed.stream().filter(b -> b.pageId() == pageId).toList(), afterMap);
            }
            jdbc.update("UPDATE autowire_render_job SET status='done',error=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        } catch (Exception e) {
            log.warn("Autowire render job {} failed: {}", id, e.getMessage());
            jdbc.update("UPDATE autowire_render_job SET status='failed',error=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", "关系已保存，但交互预览更新失败：" + e.getMessage(), id);
        }
    }

    private void patchPage(Page page, List<Binding> additions, String map) throws Exception {
        if (page.getHtmlContent() == null || page.getHtmlContent().isBlank()) throw new IllegalStateException("画板没有可更新的 HTML，请先生成页面预览");
        String old = page.getHtmlContent();
        List<Binding> combined = new ArrayList<>();
        var prior = Pattern.compile("(?s)<script id=\"wf-autowire-bindings\" type=\"application/json\">(.*?)</script>").matcher(old);
        if (prior.find()) combined.addAll(json.readValue(prior.group(1), new TypeReference<List<Binding>>() {}));
        Set<Long> newIds = new HashSet<>(); additions.forEach(b -> newIds.add(b.elementId()));
        combined.removeIf(b -> newIds.contains(b.elementId())); combined.addAll(additions);
        // Include all surviving popup bindings, including those from earlier applications.
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(map);
        var modalPages = json.createObjectNode();
        for (Long targetId : combined.stream().filter(b -> "popup".equals(b.action()) || "modal".equals(b.action())).map(Binding::targetId).filter(Objects::nonNull).distinct().toList()) {
            Page target = pageMapper.selectById(targetId);
            if (target != null && page.getProjectId().equals(target.getProjectId()) && target.getHtmlContent() != null)
                modalPages.put(String.valueOf(targetId), target.getHtmlContent().replaceAll("(?s)<!-- wf-autowire-start -->.*?<!-- wf-autowire-end -->", ""));
        }
        payload.set("modalPages", modalPages);
        String patched = patchHtml(old, json.writeValueAsString(combined), json.writeValueAsString(payload));
        // Compare-and-set avoids overwriting HTML saved while the job was preparing.
        Integer updated = new TransactionTemplate(transactionManager).execute(s -> jdbc.update("UPDATE page SET html_content=? WHERE id=? AND html_content=?", patched, page.getId(), old));
        if (updated == null || updated != 1) throw new IllegalStateException("画板被同时编辑，请重试预览更新");
    }

    public static String patchHtml(String html, String bindingsJson, String appMapJson) {
        String clean = html.replaceAll("(?s)<!-- wf-autowire-start -->.*?<!-- wf-autowire-end -->", "");
        String payload = bindingsJson.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
        String map = AutowirePlanner.safe(appMapJson).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
        String script = "<!-- wf-autowire-start --><script id=\"wf-autowire-bindings\" type=\"application/json\">" + payload + "</script>\n" +
                "<script id=\"wf-autowire-map\" type=\"application/json\">" + (map.isBlank() ? "null" : map) + "</script>\n" + RUNTIME + "<!-- wf-autowire-end -->";
        int body = clean.toLowerCase().lastIndexOf("</body>");
        return body < 0 ? clean + script : clean.substring(0, body) + script + clean.substring(body);
    }

    private static final String RUNTIME = """
        <script>(function(){
          function apply(){
            var rows=JSON.parse(document.getElementById('wf-autowire-bindings').textContent||'[]');
            var missing=[];
            function norm(s){return (s||'').replace(/\\s+/g,'').trim();}
            function find(b){
              var exact=Array.from(document.querySelectorAll('[data-wf-element-id]')).filter(function(n){return n.getAttribute('data-wf-element-id')===String(b.elementId);});
              if(exact.length===1)return exact[0];
              if(b.domUid){exact=Array.from(document.querySelectorAll('[data-wf-uid]')).filter(function(n){return n.getAttribute('data-wf-uid')===b.domUid;});if(exact.length===1)return exact[0];}
              if(b.group){exact=Array.from(document.querySelectorAll('[data-group-key][data-group-role="anchor"]')).filter(function(n){return n.getAttribute('data-group-key')===b.group;});if(exact.length===1)return exact[0];}
              if(b.navigation){
                var ids=(b.navigation.memberElementIds||[]).map(String);
                exact=Array.from(document.querySelectorAll('[data-wf-element-id]')).filter(function(n){return ids.indexOf(n.getAttribute('data-wf-element-id'))>=0;});
                if(exact.length===1)return exact[0];
              }
              // Conservative legacy fallback: exact label AND close geometry AND one unambiguous match.
              var body=document.body.getBoundingClientRect();
              exact=Array.from(document.querySelectorAll('.wf-el,.wf-card,.wf-btn,.wf-act,.wf-ic,.wf-t,.wf-tabit,[data-nav],[data-modal],[data-action]')).filter(function(n){
                if(!b.label||norm(n.textContent)!==norm(b.label))return false;
                var r=n.getBoundingClientRect();
                return Math.abs(r.left-body.left-b.x)<=12&&Math.abs(r.top-body.top-b.y)<=12&&Math.abs(r.width-b.width)<=16&&Math.abs(r.height-b.height)<=16;
              });
              if(exact.length===1)return exact[0];
              if(b.navigation){
                var nav=b.navigation;
                exact=Array.from(document.querySelectorAll('[data-nav-item],.nav-item,.wf-tabit,.tab-item')).filter(function(n){
                  if(n.hasAttribute('data-wf-element-id')||norm(n.textContent)!==norm(nav.label))return false;
                  var r=n.getBoundingClientRect();return r.left>=body.left+nav.x-16&&r.right<=body.left+nav.x+nav.width+16&&r.top>=body.top+nav.y-16&&r.bottom<=body.top+nav.y+nav.height+16;
                });
                if(exact.length===1)return exact[0];
              }
              return null;
            }
            function navigationNode(n,b){
              var nav=b.navigation;if(!nav)return n;
              // Deletion clears only its recorded owner; surviving member actions must remain intact.
              if(b.action==='none')return n;
              var ids=(nav.memberElementIds||[]).map(String),body=document.body.getBoundingClientRect();
              var sx=body.width/(document.body.offsetWidth||body.width||1),sy=sx;
              function owned(el){var own=el.closest&&el.closest('[data-wf-element-id]');return own&&ids.indexOf(own.getAttribute('data-wf-element-id'))>=0;}
              function inSlot(el){var r=el.getBoundingClientRect();return r.left>=body.left+(nav.x-16)*sx&&r.right<=body.left+(nav.x+nav.width+16)*sx&&r.top>=body.top+(nav.y-32)*sy&&r.bottom<=body.top+(nav.y+nav.height+32)*sy;}
              var wrapper=n.closest('[data-nav-item],.nav-item,.wf-tabit,.tab-item,button,a,[role="button"]');
              if(wrapper&&wrapper!==document.body&&wrapper!==document.documentElement&&inSlot(wrapper)){
                var foreign=Array.from(wrapper.querySelectorAll('button,a,input,textarea,select,[role="button"],[data-nav],[data-modal],[data-action]')).some(function(el){return el!==n&&ids.indexOf(el.getAttribute('data-wf-element-id'))<0;});
                if(wrapper!==n&&wrapper.hasAttribute('data-wf-element-id')&&!owned(wrapper)&&(wrapper.hasAttribute('data-nav')||wrapper.hasAttribute('data-modal')||wrapper.hasAttribute('data-action')))foreign=true;
                var otherItem=Array.from(wrapper.querySelectorAll('[data-wf-element-id]')).some(function(el){return ids.indexOf(el.getAttribute('data-wf-element-id'))<0;});
                if(!foreign&&!otherItem){
                  wrapper.setAttribute('data-wf-nav-owner',String(b.elementId));
                  // Only clear members of this reviewed item; independent child controls keep their behavior.
                  wrapper.querySelectorAll('[data-nav]').forEach(function(el){if(owned(el))el.removeAttribute('data-nav');});
                  return wrapper;
                }
              }
              var nr=n.getBoundingClientRect();
              if(nr.left<body.left+(nav.x-16)*sx||nr.right>body.left+(nav.x+nav.width+16)*sx||nr.top<body.top+(nav.y-16)*sy||nr.bottom>body.top+(nav.y+nav.height+16)*sy)return null;
              var foreign=Array.from(document.querySelectorAll('button,a,input,textarea,select,[data-modal],[data-action]')).some(function(el){
                if(el===n||ids.indexOf(el.getAttribute('data-wf-element-id'))>=0||el.classList.contains('wf-nav-hotspot'))return false;
                var r=el.getBoundingClientRect();return r.right>body.left+nav.x*sx&&r.left<body.left+(nav.x+nav.width)*sx&&r.bottom>body.top+nav.y*sy&&r.top<body.top+(nav.y+nav.height)*sy;
              });
              if(foreign)return null;
              var area=document.createElement('button');area.type='button';area.className='wf-nav-hotspot';
              area.setAttribute('data-wf-nav-owner',String(b.elementId));area.setAttribute('aria-label',nav.label||b.label||'导航');
              area.style.cssText='position:absolute;left:'+nav.x+'px;top:'+nav.y+'px;width:'+nav.width+'px;height:'+nav.height+'px;background:transparent;border:0;padding:0;z-index:90;cursor:pointer';
              document.body.appendChild(area);return area;
            }
            if(!document.getElementById('wf-nav-hotspot-style')){
              var style=document.createElement('style');style.id='wf-nav-hotspot-style';style.textContent='body:not(.wf-interactive) .wf-nav-hotspot{display:none}.wf-nav-hotspot:focus-visible{outline:2px solid #0d99ff;outline-offset:-2px}';document.head.appendChild(style);
            }
            rows.forEach(function(b){
              var n=find(b);if(!n){missing.push(b.elementId);return;}
              if(!n.hasAttribute('data-wf-element-id'))n.setAttribute('data-wf-element-id',String(b.elementId));
              document.querySelectorAll('[data-wf-nav-owner]').forEach(function(el){if(el.getAttribute('data-wf-nav-owner')===String(b.elementId)){el.removeAttribute('data-nav');if(el.classList.contains('wf-nav-hotspot'))el.remove();}});
              n.removeAttribute('data-nav');n.removeAttribute('data-modal');
              if(n.getAttribute('data-action')==='back')n.removeAttribute('data-action');
              if(b.navigation){n=navigationNode(n,b);if(!n){missing.push(b.elementId);return;}}
              if(b.action==='navigate'&&b.targetName)n.setAttribute('data-nav',b.targetName);
              else if((b.action==='popup'||b.action==='modal')&&b.targetId)n.setAttribute('data-modal',String(b.targetId));
              else if(b.action==='back')n.setAttribute('data-action','back');
            });
            if(missing.length)parent.postMessage({type:'wf-autowire-unmapped',elements:missing},'*');
            var shared=JSON.parse(document.getElementById('wf-autowire-map').textContent||'null');
            if(shared&&shared.modalPages){
              var modalStyle=document.createElement('style');
              modalStyle.textContent='.wf-autowire-modal{display:none;position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,.35)}.wf-autowire-modal.wf-show{display:flex}.wf-autowire-modal iframe{width:100%;height:100%;border:0}';
              document.head.appendChild(modalStyle);
              Object.keys(shared.modalPages).forEach(function(id){
                if(document.getElementById('wf-modal-'+id))return;
                var modal=document.createElement('div');modal.id='wf-modal-'+id;modal.className='wf-modal wf-autowire-modal';
                var frame=document.createElement('iframe');frame.title='交互弹窗';
                var close=document.createElement('button');close.textContent='关闭';close.setAttribute('aria-label','关闭弹窗');close.setAttribute('data-action','back');close.style.cssText='position:absolute;right:8px;top:8px;z-index:2';
                close.onclick=function(){modal.classList.remove('wf-show');};
                frame.srcdoc=shared.modalPages[id];
                frame.addEventListener('load',function(){
                  try{frame.contentDocument.addEventListener('click',function(event){
                    var n=event.target.closest('[data-nav],[data-action]');if(!n)return;
                    if(n.getAttribute('data-action')==='back'||n.getAttribute('data-action')==='close'){event.preventDefault();event.stopImmediatePropagation();modal.classList.remove('wf-show');}
                    else if(n.hasAttribute('data-nav')){event.preventDefault();event.stopImmediatePropagation();parent.postMessage({type:'wf-nav',page:n.getAttribute('data-nav')},'*');}
                  },true);}catch(ignore){}
                });
                modal.appendChild(frame);modal.appendChild(close);document.body.appendChild(modal);
              });
            }
            if(shared&&shared.map&&shared.map.tab_bar){
              var items=shared.map.tab_bar.items||[];
              document.querySelectorAll('.wf-tabbar .wf-tabit').forEach(function(n){
                // Per-element reviewed or manually deleted bindings take priority over shared defaults.
                if(rows.some(function(b){return n.getAttribute('data-wf-element-id')===String(b.elementId);}))return;
                if(!n.hasAttribute('data-nav')||n.hasAttribute('data-wf-nav-owner'))return;
                var matching=items.filter(function(i){return norm(i.label)===norm(n.textContent);});
                if(matching.length===1&&shared.pageNames[String(matching[0].target)])n.setAttribute('data-nav',shared.pageNames[String(matching[0].target)]);
              });
            }
          }
          if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply,{once:true});else apply();
        })();</script>
        """;
    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void resumePending() {
        jdbc.update("UPDATE autowire_render_job SET status='pending' WHERE status='running' AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)");
        // Pending jobs are recoverable after a crash. Failed jobs wait for an explicit retry.
        for (String id : jdbc.query("SELECT id FROM autowire_render_job WHERE status='pending' ORDER BY created_at LIMIT 10", (rs, n) -> rs.getString(1))) process(id);
    }
    public void retry(String id) { jdbc.update("UPDATE autowire_render_job SET status='pending',error=NULL WHERE id=? AND status='failed'", id); process(id); }
    private static double nz(Double n) { return n == null ? 0 : n; }
}
