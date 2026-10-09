package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.*;
import com.wireforge.model.AutowirePlan.*;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only navigation semantics. A strip proves an entry, never its destination. */
public final class NavigationPlanner {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Mapping(String familyKey, String itemKey, String label, long targetPageId,
                          String origin, Long sourceInteractionId, boolean active) {}
    public record Entry(Page page, Element anchor, List<Element> members, String label, String region,
                        String familyKey, String itemKey, double x, double y, double width, double height) {
        public String stableKey() { return page.getId() + ":nav:" + familyKey + ":" + itemKey; }
        public List<Long> ids() { return members.stream().map(Element::getId).distinct().toList(); }
    }
    public record Result(List<Item> items, List<NavigationDiagnostic> diagnostics, List<Entry> entries) {}

    public static List<Entry> detect(List<Page> pages, List<Element> elements) {
        List<Entry> out = new ArrayList<>();
        for (Page page : pages) {
            List<Element> siblings = elements.stream().filter(e -> page.getId().equals(e.getPageId()) && e.getId() != null).toList();
            double h = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight(), w = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
            List<ClickGrouper.Group> groups = ClickGrouper.group(siblings, h, page.getId());
            for (String region : List.of("bottom", "top", "side")) {
                List<List<Element>> units = new ArrayList<>(); Set<Long> used = new HashSet<>();
                for (ClickGrouper.Group group : groups) {
                    if (!"tab".equals(group.kind) || !"bottom".equals(region)) continue;
                    List<Element> members = new ArrayList<>(); members.add(group.anchor); members.addAll(group.members);
                    if (!label(members).isBlank() && !commercial(label(members)) && !insideListing(group.anchor,siblings)) { units.add(members); members.forEach(e -> used.add(e.getId())); }
                }
                for (Element e : siblings) {
                    if (used.contains(e.getId()) || "member".equals(e.getGroupRole())) continue;
                    String hint = hint(e, "region"), type = AutowirePlanner.safe(e.getType()).toLowerCase();
                    boolean explicit = region.equals(hint);
                    boolean bottomButton = "bottom".equals(region) && "button".equals(type) && cy(e) >= h * .80;
                    boolean namedEdge = Set.of("button","icon","text","tab").contains(type) && AutowirePlanner.safe(e.getLabel()).matches("(?:首页|家园|分类|商城|购物车|我的|个人中心|发现|消息|社区|背包|设置)")
                            && (("top".equals(region) && cy(e) <= h*.20 && cy(e) >= h*.025) || ("side".equals(region) && cx(e) <= w*.18 && cy(e) > h*.20 && cy(e) < h*.80));
                    if (!explicit && !bottomButton && !namedEdge) continue;
                    if (!Set.of("button", "icon", "text", "tab", "tabs", "nav_item").contains(type) || ClickGrouper.skip(e)) continue;
                    List<Element> members = new ArrayList<>(); members.add(e);
                    if (e.getGroupKey() != null && !e.getGroupKey().isBlank()) siblings.stream().filter(s -> !s.getId().equals(e.getId()) && e.getGroupKey().equals(s.getGroupKey())).forEach(members::add);
                    else if ("button".equals(type)) siblings.stream().filter(s -> !s.getId().equals(e.getId()) && Set.of("icon", "text").contains(s.getType()) && AutowirePlanner.contains(e, s)).forEach(members::add);
                    if (label(members).isBlank() || commercial(label(members)) || insideListing(e,siblings)) continue;
                    members.forEach(s -> used.add(s.getId())); units.add(members);
                }
                if (units.size() < 2) continue;
                boolean side = "side".equals(region);
                units.sort(Comparator.comparingDouble(unit -> side ? cy(unit.get(0)) : cx(unit.get(0))));
                // A navigation strip must be aligned and span an appreciable part of its edge.
                double low = units.stream().mapToDouble(u -> side ? cx(u.get(0)) : cy(u.get(0))).min().orElse(0);
                double high = units.stream().mapToDouble(u -> side ? cx(u.get(0)) : cy(u.get(0))).max().orElse(0);
                if (high - low > (side ? w : h) * .075) continue;
                double span = (side ? cy(units.get(units.size()-1).get(0)) - cy(units.get(0).get(0)) : cx(units.get(units.size()-1).get(0)) - cx(units.get(0).get(0)));
                if (span < (side ? h : w) * .30) continue;
                List<String> labels = units.stream().map(NavigationPlanner::label).toList();
                String family = AutowirePlanner.sha("nav-v1|" + region + "|" + (side ? "vertical" : "horizontal") + "|" + labels.stream().map(AutowirePlanner::normalize).collect(Collectors.joining("|")));
                double top = units.stream().flatMap(List::stream).mapToDouble(e -> nz(e.getPositionY())).min().orElse(0);
                double bottom = units.stream().flatMap(List::stream).mapToDouble(e -> nz(e.getPositionY()) + nz(e.getHeight())).max().orElse(h);
                double left = units.stream().flatMap(List::stream).mapToDouble(e -> nz(e.getPositionX())).min().orElse(0);
                double right = units.stream().flatMap(List::stream).mapToDouble(e -> nz(e.getPositionX()) + nz(e.getWidth())).max().orElse(w);
                Map<String,Integer> occurrences = new HashMap<>();
                for (int i = 0; i < units.size(); i++) {
                    List<Element> members = units.get(i); Element anchor = members.get(0); String name = labels.get(i);
                    String normalized = AutowirePlanner.normalize(name); int occurrence = occurrences.merge(normalized, 1, Integer::sum);
                    String itemKey = AutowirePlanner.sha(normalized + "|" + occurrence);
                    double x = side ? Math.max(0,left-6) : i == 0 ? span > w*.55 ? 0 : Math.max(0,left-6) : (cx(units.get(i-1).get(0)) + cx(anchor))/2;
                    double endX = side ? Math.min(w,right+6) : i == units.size()-1 ? span > w*.55 ? w : Math.min(w,right+6) : (cx(anchor)+cx(units.get(i+1).get(0)))/2;
                    double y = side ? i == 0 ? Math.max(0,top-6) : (cy(units.get(i-1).get(0))+cy(anchor))/2 : Math.max(0,top-6);
                    double endY = side ? i == units.size()-1 ? Math.min(h,bottom+6) : (cy(anchor)+cy(units.get(i+1).get(0)))/2 : Math.min(h,bottom+6);
                    out.add(new Entry(page,anchor,List.copyOf(members),name,region,family,itemKey,x,y,Math.max(1,endX-x),Math.max(1,endY-y)));
                }
            }
        }
        return out;
    }

    public Result plan(List<Page> pages, List<Element> elements, List<Interaction> lines,
                       List<Annotation> annotations, List<InteractionExclusion> exclusions,
                       List<Mapping> mappings, Map<String,Long> overrides) {
        List<Entry> entries = detect(pages,elements); List<Item> items = new ArrayList<>(); List<NavigationDiagnostic> diagnostics = new ArrayList<>();
        Map<Long,Page> targets = pages.stream().collect(Collectors.toMap(Page::getId,p -> p));
        for (Page page : pages) if (entries.stream().noneMatch(e -> e.page().getId().equals(page.getId()))) {
            long htmlItems = java.util.regex.Pattern.compile("(?i)<(?:div|a|button|li)[^>]*class=[\"'][^\"']*(?:nav-item|wf-tabit|tab-item)[^\"']*[\"']").matcher(AutowirePlanner.safe(page.getHtmlContent())).results().count();
            if (htmlItems >= 2 || elements.stream().anyMatch(e -> page.getId().equals(e.getPageId()) && !hint(e,"region").isBlank()))
                diagnostics.add(new NavigationDiagnostic(page.getId(),page.getName(),"导航区域","unbound","页面含导航标记，但没有完整可绑定的导航分组；请重新识别或补齐导航元素"));
        }
        for (Entry entry : entries) {
            List<Element> siblings = elements.stream().filter(e -> entry.page().getId().equals(e.getPageId())).toList();
            List<Interaction> current = lines.stream().filter(i -> entry.ids().contains(i.getElementId())).toList();
            if (current.stream().anyMatch(i -> "user".equals(i.getSource()) || !AutowirePlanner.automatic(i))) { diagnostics.add(diag(entry,"protected","人工关系或来源不明的旧关系已保留")); continue; }
            if (entry.members().stream().anyMatch(e -> AutowirePlanner.excluded(e,siblings,"*",exclusions)) || annotations.stream().anyMatch(a -> entry.page().getId().equals(a.getPageId()) && entry.ids().contains(a.getElementId()) && AutowirePlanner.safe(a.getText()).matches("(?s).*(不(?:需要|可|能|支持)?(?:点击|跳转|连线)|仅(?:展示|显示)|无需(?:交互|跳转)).*"))) { diagnostics.add(diag(entry,"excluded","该导航项已排除或明确要求不跳转")); continue; }
            if (current.size() > 1) { items.add(proposal(entry,null,null,"conflict","导航项存在多条关系，需要先确认保留哪条",false,pages)); continue; }
            Interaction old = current.isEmpty() ? null : current.get(0);
            if ("local".equals(hint(entry.anchor(),"kind")) || localLabel(entry.label())) { diagnostics.add(diag(entry,"local_tab","保留页面内的内容切换")); continue; }
            Long chosen = overrides.get(entry.stableKey()); String basis = chosen == null ? "" : "user_choice";
            if (chosen == null && old != null && "navigate".equals(old.getActionType()) && targets.containsKey(old.getTargetPageId())) { chosen = old.getTargetPageId(); basis = "existing_target"; }
            if (chosen == null && entries.stream().filter(e -> e.page().getId().equals(entry.page().getId()) && e.familyKey().equals(entry.familyKey()) && AutowirePlanner.normalize(e.label()).equals(AutowirePlanner.normalize(entry.label()))).count() > 1) {
                items.add(proposal(entry,old,null,"ambiguous","同组存在重复文案，需分别确认目标",false,pages)); continue;
            }
            if (mappings.stream().anyMatch(m -> m.active() && m.familyKey().equals(entry.familyKey()) && m.itemKey().equals(entry.itemKey()) && !targets.containsKey(m.targetPageId())))
                diagnostics.add(diag(entry,"missing_target","已保存的导航目标页已不存在，旧映射不参与推导"));
            Set<Long> confirmed = new LinkedHashSet<>();
            for (Mapping mapping : mappings) if (mapping.active() && mapping.familyKey().equals(entry.familyKey()) && mapping.itemKey().equals(entry.itemKey()) && targets.containsKey(mapping.targetPageId())
                    && (mapping.sourceInteractionId() == null || lines.stream().anyMatch(i -> mapping.sourceInteractionId().equals(i.getId()) && "navigate".equals(i.getActionType()) && mapping.targetPageId() == Objects.requireNonNullElse(i.getTargetPageId(),-1L) && Set.of("user","autowire_review").contains(AutowirePlanner.safe(i.getSource()))
                    && entries.stream().anyMatch(peer -> peer.familyKey().equals(mapping.familyKey()) && peer.itemKey().equals(mapping.itemKey()) && peer.ids().contains(i.getElementId()))))) confirmed.add(mapping.targetPageId());
            for (Entry peer : entries) if (peer.familyKey().equals(entry.familyKey()) && peer.itemKey().equals(entry.itemKey()))
                for (Interaction i : lines) if (peer.ids().contains(i.getElementId()) && "navigate".equals(i.getActionType()) && targets.containsKey(i.getTargetPageId()) && Set.of("user","autowire_review").contains(AutowirePlanner.safe(i.getSource()))) {
                    List<Element> peerSiblings = elements.stream().filter(e -> peer.page().getId().equals(e.getPageId())).toList();
                    if (peer.members().stream().noneMatch(e -> AutowirePlanner.excluded(e,peerSiblings,AutowirePlanner.relationKey(i.getTriggerType(),"navigate",i.getTargetPageId()),exclusions))) confirmed.add(i.getTargetPageId());
                }
            if (chosen == null && confirmed.size() > 1) { items.add(proposal(entry,old,null,"conflict","同组已确认导航指向多个页面，不能自动合并",false,pages)); continue; }
            if (chosen == null && confirmed.size() == 1) { chosen = confirmed.iterator().next(); basis = "confirmed_mapping"; }
            List<Page> exact = pages.stream().filter(p -> AutowirePlanner.normalize(p.getName()).equals(AutowirePlanner.normalize(entry.label()))).toList();
            if (chosen == null && exact.size() == 1) { chosen = exact.get(0).getId(); basis = "exact_name"; }
            if (chosen != null) {
                Long finalTarget = chosen;
                if (entry.members().stream().anyMatch(e -> AutowirePlanner.excluded(e,siblings,AutowirePlanner.relationKey(old == null ? "click" : old.getTriggerType(),"navigate",finalTarget),exclusions))) {
                    if ("user_choice".equals(basis)) throw new IllegalStateException("该目标关系已被排除，请先恢复排除");
                    diagnostics.add(diag(entry,"excluded","该导航关系已被排除")); continue;
                }
                if (chosen.equals(entry.page().getId())) {
                    if (old == null || ("navigate".equals(old.getActionType()) && chosen.equals(old.getTargetPageId()))) diagnostics.add(diag(entry,"current_page","当前页导航，无需新增跨页连线"));
                    else items.add(proposal(entry,old,null,"conflict","导航映射为当前页，但已有动作不同，需要人工确认",false,pages));
                    continue;
                }
                if (old != null && "navigate".equals(old.getActionType()) && chosen.equals(old.getTargetPageId())) { diagnostics.add(diag(entry,"correct","已有可用导航目标，已保留且无需重复建立关系")); continue; }
                if (old != null && !Set.of("navigate","tab_switch","tab").contains(AutowirePlanner.safe(old.getActionType()))) { diagnostics.add(diag(entry,"local_tab","保留现有独立操作，未转换为跨页导航")); continue; }
                boolean defaultSelected = !"user_choice".equals(basis) && (old == null || ("navigate".equals(old.getActionType()) && old.getTargetPageId() == null));
                items.add(proposal(entry,old,targets.get(chosen),basis,old == null ? "补齐导航入口的目标" : "navigate".equals(old.getActionType()) ? "补全或修正导航目标" : "建议将页内切换转换为跨页导航",defaultSelected,pages));
            } else items.add(proposal(entry,old,null,exact.size() > 1 ? "ambiguous" : "unresolved",exact.size() > 1 ? "多个同名目标页面，需要人工确认" : "已识别导航入口，但目标尚未确定；可选择目标后重新预检",false,pages));
        }
        return new Result(List.copyOf(items),List.copyOf(diagnostics),entries);
    }

    public static Item proposal(Entry entry, Interaction old, Page target, String basis, String reason, boolean selected, List<Page> pages) {
        boolean applicable = target != null && !"conflict".equals(basis);
        String category = applicable ? old == null ? "add" : "complete" : "uncertain";
        String stable = entry.stableKey() + ":" + (old == null ? "new" : old.getId());
        List<String> refs = List.of("navigation:" + entry.anchor().getId(), "nav_basis:" + basis);
        String fingerprint = AutowirePlanner.sha(stable + "|" + category + "|navigate|" + (target == null ? null : target.getId()) + "|" + basis + "|" + (old == null ? "" : old.getActionType()+":"+old.getTargetPageId()) + "|" + entry.ids().stream().map(id -> entry.members().stream().filter(e -> e.getId().equals(id)).map(AutowirePlanner::elementKey).findFirst().orElse("")).toList());
        NavigationInfo info = new NavigationInfo(entry.familyKey(),entry.itemKey(),entry.label(),entry.region(),entry.ids(),entry.x(),entry.y(),entry.width(),entry.height(),applicable ? "resolved" : basis,basis,old == null ? null : old.getActionType(),old == null ? null : old.getTargetPageId(),pages.stream().filter(p -> !p.getId().equals(entry.page().getId()) && !AutowirePlanner.safe(p.getName()).matches(".*(弹窗|遮罩|提示弹框|确认弹框).*" )).map(p -> new TargetOption(p.getId(),p.getName())).toList());
        return new Item("nav:"+AutowirePlanner.sha(stable).substring(0,24),category,entry.page().getId(),entry.page().getName(),old == null ? entry.anchor().getId() : old.getElementId(),entry.label(),old == null ? null : old.getId(),old == null ? "click" : old.getTriggerType(),"navigate",target == null ? null : target.getId(),target == null ? "未确定" : target.getName(),"ai_suggestion".equals(basis) ? "ai_inferred" : "autowire_review",reason,refs,applicable,selected,stable,fingerprint,info);
    }
    public static NavigationDiagnostic diag(Entry entry,String status,String reason) { return new NavigationDiagnostic(entry.page().getId(),entry.page().getName(),entry.label(),status,reason); }
    public static String label(List<Element> members) {
        String visible = members.stream().filter(e -> "text".equals(e.getType()) && !AutowirePlanner.safe(e.getLabel()).isBlank()).sorted(Comparator.comparingDouble(NavigationPlanner::cy)).map(e -> e.getLabel().trim()).collect(Collectors.joining(" "));
        return !visible.isBlank() ? visible : !hint(members.get(0),"label").isBlank() ? hint(members.get(0),"label") : AutowirePlanner.safe(members.get(0).getLabel()).trim();
    }
    public static String hint(Element e,String field) { try { return JSON.readTree(AutowirePlanner.safe(e.getStyle())).path("navigation").path(field).asText(""); } catch(Exception ignored) { return ""; } }
    private static boolean commercial(String label) { return label.matches(".*(限购|库存|剩余|￥|¥|\\d+[元个份]).*|(?:立即|马上|确认|去)?(?:购买|兑换|领取)(?:$|[\\s\\d].*)"); }
    private static boolean insideListing(Element anchor,List<Element> siblings) {
        return siblings.stream().filter(e -> Set.of("container","card").contains(e.getType()) && AutowirePlanner.contains(e,anchor))
                .anyMatch(outer -> siblings.stream().filter(e -> AutowirePlanner.contains(outer,e)).anyMatch(e -> commercial(AutowirePlanner.safe(e.getLabel()))));
    }
    public static boolean localLabel(String label) { return label.matches("(?:全部|进行中|已完成|未完成|待付款|待收货|已取消|最新|热门)(?:[/、| ].*)?"); }
    private static double nz(Double n) { return n == null ? 0 : n; }
    private static double cx(Element e) { return nz(e.getPositionX()) + nz(e.getWidth())/2; }
    private static double cy(Element e) { return nz(e.getPositionY()) + nz(e.getHeight())/2; }
}
