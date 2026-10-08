package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.*;
import com.wireforge.model.AutowirePlan.Item;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Pure planning: no mapper, network, rendering or writes. Geometry alone never proves a link. */
@Component
public class AutowirePlanner {
    private static final Set<String> AUTOMATIC = Set.of("ai", "autowire", "ai_inferred", "autowire_review");
    private static final Pattern INTENT = Pattern.compile("(?:点击|点按|轻触|选择).{0,32}(?:进入|跳转|打开|弹出|展示.{0,3}(?:弹窗|详情))|(?:跳转至|跳转到|进入.{1,30}(?:页|详情)|打开.{1,30}(?:页|弹窗)|弹出)");
    private static final Pattern NEGATIVE = Pattern.compile("不(?:需要|可|能|支持)?(?:点击|跳转|连线)|仅(?:展示|显示)|无需(?:交互|跳转)|不可点击");
    private final ObjectMapper json = new ObjectMapper();

    public record Evidence(List<String> refs, String text, List<Page> targets, boolean wholeCard) {}
    public record Planned(List<Item> items, int protectedCount) {}

    public Planned plan(List<Page> pages, List<Element> elements, List<Interaction> lines,
                        List<Annotation> annotations, List<InteractionExclusion> exclusions) {
        Map<Long, Page> pageMap = pages.stream().collect(Collectors.toMap(Page::getId, p -> p));
        Map<Long, List<Element>> byPage = elements.stream().collect(Collectors.groupingBy(Element::getPageId));
        Map<Long, List<Interaction>> byElement = lines.stream().collect(Collectors.groupingBy(Interaction::getElementId));
        List<Item> items = new ArrayList<>();
        int protectedCount = 0;
        for (Element e : elements) {
            Page page = pageMap.get(e.getPageId());
            if (page == null) continue;
            List<Element> siblings = byPage.get(e.getPageId());
            List<Interaction> current = byElement.getOrDefault(e.getId(), List.of());
            if (current.stream().anyMatch(i -> "user".equals(i.getSource()))) {
                protectedCount += current.size();
                continue;
            }
            Evidence evidence = evidence(e, page, siblings, current, annotations, pages);
            String blocked = blockedReason(e, siblings, evidence);
            Set<String> seen = new HashSet<>();
            for (Interaction old : current) {
                if (!automatic(old)) {
                    items.add(item("uncertain", page, e, old, old.getActionType(), old.getTargetPageId(), pageMap,
                            "旧关系来源不明，保留并等待人工确认", List.of(), false));
                    continue;
                }
                // Existing local actions are not cross-page candidates.
                if (!crossPage(old) && !"back".equals(old.getActionType())) continue;
                String signature = relationKey(old.getTriggerType(), old.getActionType(), old.getTargetPageId());
                String reason = null;
                boolean uncertain = false;
                if (!seen.add(signature)) reason = "同一元素上重复的自动关系";
                else if (old.getTargetPageId() != null && !pageMap.containsKey(old.getTargetPageId())) reason = "目标页面已不存在";
                else if (excluded(e, siblings, signature, exclusions)) reason = "该自动关系已被排除";
                // Existing return/close behavior is local; missing navigation evidence must not remove it.
                else if ("back".equals(old.getActionType()) && !container(e) && !NEGATIVE.matcher(evidence.text()).find()) continue;
                else if (blocked != null) {
                    reason = blocked;
                    uncertain = evidence.refs().isEmpty() && !container(e) && !ClickGrouper.skip(e)
                            && !"member".equals(e.getGroupRole()) && !Set.of("image", "avatar", "badge").contains(safe(e.getType()).toLowerCase());
                }
                else if (old.getTargetPageId() != null && !evidence.targets().stream().anyMatch(p -> p.getId().equals(old.getTargetPageId()))) {
                    reason = evidence.targets().isEmpty() ? "没有明确业务依据支持此自动跳转" : "现有目标与业务依据不一致";
                    uncertain = evidence.targets().isEmpty();
                }
                if (reason != null) {
                    items.add(item(uncertain ? "uncertain" : "remove", page, e, old, old.getActionType(), old.getTargetPageId(), pageMap, reason, evidence.refs(), false));
                } else if (old.getTargetPageId() == null && crossPage(old)) {
                    if (evidence.targets().size() == 1) {
                        Page target = evidence.targets().get(0);
                        String action = crossAction(old.getActionType(), target, evidence.text());
                        if (!excluded(e, siblings, relationKey(old.getTriggerType(), action, target.getId()), exclusions))
                            items.add(item("complete", page, e, old, action, target.getId(), pageMap, "补全已有交互的明确目标", evidence.refs(), true));
                    } else items.add(item("uncertain", page, e, old, old.getActionType(), null, pageMap,
                            evidence.targets().isEmpty() ? "已有交互意图，但没有唯一目标" : "存在多个可能目标，需要确认", evidence.refs(), false));
                }
            }
            if (!current.isEmpty() || blocked != null || evidence.refs().isEmpty()) continue;
            if (elementExcluded(e, siblings, exclusions)) continue;
            if (isNamedBack(e)) {
                if (!excluded(e, siblings, relationKey("click", "back", null), exclusions))
                    items.add(item("add", page, e, null, "back", null, pageMap, "明确的返回控件", List.of("control:" + e.getId()), true));
            } else if (evidence.targets().size() == 1) {
                Page target = evidence.targets().get(0);
                String action = crossAction(null, target, evidence.text());
                if (!excluded(e, siblings, relationKey("click", action, target.getId()), exclusions))
                    items.add(item("add", page, e, null, action, target.getId(), pageMap, "依据明确的业务关系关联目标", evidence.refs(), true));
            } else {
                items.add(item("uncertain", page, e, null, "navigate", null, pageMap,
                        evidence.targets().isEmpty() ? "业务说明有交互意图，但目标尚不明确" : "业务说明对应多个页面", evidence.refs(), false));
            }
        }
        return new Planned(List.copyOf(items), protectedCount);
    }

    public Evidence evidence(Element e, Page page, List<Element> siblings, List<Interaction> current,
                             List<Annotation> annotations, List<Page> pages) {
        List<String> refs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        List<Page> targets = new ArrayList<>();
        for (Interaction i : current) {
            String name = targetName(i);
            if (!name.isBlank()) {
                refs.add("interaction:" + i.getId()); text.append("目标：").append(name).append('\n');
                targets.addAll(exactTargets(name, page.getId(), pages));
            }
        }
        boolean whole = false;
        for (Annotation a : annotations) {
            if (!page.getId().equals(a.getPageId()) || a.getElementId() == null) continue;
            Element owner = siblings.stream().filter(s -> a.getElementId().equals(s.getId())).findFirst().orElse(null);
            boolean sameGroup = owner != null && "anchor".equals(e.getGroupRole()) && "member".equals(owner.getGroupRole())
                    && e.getGroupKey() != null && e.getGroupKey().equals(owner.getGroupKey());
            if (!e.getId().equals(a.getElementId()) && !sameGroup) continue;
            String value = safe(a.getText());
            if (!INTENT.matcher(value).find() && !NEGATIVE.matcher(value).find()) continue;
            refs.add("annotation:" + a.getId()); text.append(value).append('\n');
            whole |= value.matches("(?s).*(整卡|整个卡片|整张卡片|点击卡片).*?(进入|打开|跳转).*");
            if (!NEGATIVE.matcher(value).find()) {
                // Match page names only in clauses that explicitly describe a navigation action.
                for (String clause : value.split("[。；;\\n]")) {
                    if (!INTENT.matcher(clause).find()) continue;
                    List<Page> hits = pages.stream().filter(p -> !p.getId().equals(page.getId()))
                            .filter(p -> !safe(p.getName()).isBlank() && normalize(clause).contains(normalize(p.getName())))
                            .toList();
                    // Do not arbitrarily choose the shorter page when a name contains another name.
                    targets.addAll(hits);
                }
            }
        }
        if (isNamedBack(e)) refs.add("control:" + e.getId());
        // Exact names are accepted for explicit navigation controls, never for product cards or purchase controls.
        if (refs.isEmpty() && navigationControl(e)) {
            List<Page> exact = exactTargets(e.getLabel(), page.getId(), pages);
            if (!exact.isEmpty()) { refs.add("control:" + e.getId()); targets.addAll(exact); text.append("明确入口：").append(safe(e.getLabel())); }
        }
        return new Evidence(List.copyOf(refs), text.toString(), targets.stream().distinct().toList(), whole);
    }

    private Item item(String category, Page page, Element e, Interaction old, String action, Long target,
                      Map<Long, Page> pages, String reason, List<String> refs, boolean selected) {
        String key = category + ":" + e.getId() + ":" + (old == null ? "new" : old.getId());
        return new Item(key, category, page.getId(), page.getName(), e.getId(), safe(e.getLabel()),
                old == null ? null : old.getId(), old == null ? "click" : safeTrigger(old.getTriggerType()), action, target,
                target == null ? ("back".equals(action) ? "返回上一页" : "未确定") : pages.containsKey(target) ? pages.get(target).getName() : "目标已不存在",
                old == null ? "autowire_review" : safe(old.getSource()), reason, refs, !"uncertain".equals(category), selected);
    }

    public String blockedReason(Element e, List<Element> siblings, Evidence evidence) {
        if (NEGATIVE.matcher(evidence.text()).find()) return "业务标注明确要求不跳转或仅展示";
        if (ClickGrouper.skip(e)) return "展示或输入元素不应自动建立跨页关系";
        if ("member".equals(e.getGroupRole())) return "组内装饰或文字应由实际操作控件承担交互";
        if (container(e)) {
            if (!evidence.wholeCard()) return listing(e, siblings) ? "陈列型展示卡片没有整卡跳转依据" : "普通卡片没有明确的整卡跳转依据";
            if (siblings.stream().anyMatch(s -> contains(e, s) && ("button".equals(s.getType())
                    || safe(s.getLabel()).matches(".*(兑换|购买|去完成|查看).*"))))
                return "卡片内已有独立操作控件，由操作控件承担交互";
            return null;
        }
        if (Set.of("image", "avatar", "badge").contains(safe(e.getType()).toLowerCase())) return "图片或角标没有独立入口依据";
        if (evidence.refs().isEmpty()) return "没有明确的交互依据，不能仅凭组件类型或名称相似创建跳转";
        return null;
    }

    public boolean listing(Element e, List<Element> siblings) {
        if (!container(e)) return false;
        long repeated = siblings.stream().filter(s -> !s.getId().equals(e.getId()) && container(s))
                .filter(s -> Math.abs(nz(s.getWidth()) - nz(e.getWidth())) <= Math.max(8, nz(e.getWidth()) * .12)
                        && Math.abs(nz(s.getHeight()) - nz(e.getHeight())) <= Math.max(8, nz(e.getHeight()) * .12))
                .filter(s -> !contains(e, s) && !contains(s, e)).count();
        String contents = siblings.stream().filter(s -> contains(e, s)).map(s -> safe(s.getLabel())).collect(Collectors.joining(" "));
        boolean priced = contents.matches("(?s).*(?:金币|钻石|积分|￥|¥|库存|限购|剩余|\\d+元).*"), action = contents.matches("(?s).*(兑换|购买|领取).*" );
        return repeated >= 1 || (priced && action);
    }

    public static boolean contains(Element outer, Element inner) {
        if (outer.getId().equals(inner.getId())) return false;
        return nz(outer.getWidth()) * nz(outer.getHeight()) > nz(inner.getWidth()) * nz(inner.getHeight()) * 1.2
                && nz(inner.getPositionX()) >= nz(outer.getPositionX()) - 2 && nz(inner.getPositionY()) >= nz(outer.getPositionY()) - 2
                && nz(inner.getPositionX()) + nz(inner.getWidth()) <= nz(outer.getPositionX()) + nz(outer.getWidth()) + 2
                && nz(inner.getPositionY()) + nz(inner.getHeight()) <= nz(outer.getPositionY()) + nz(outer.getHeight()) + 2;
    }
    private static boolean container(Element e) { return Set.of("container", "card").contains(safe(e.getType()).toLowerCase()); }
    private boolean navigationControl(Element e) {
        String label = safe(e.getLabel()).trim();
        if (!label.contains("记录") && label.matches(".*(兑换|购买|领取|抽奖|消耗|金币|钻石|积分).*")) return false;
        return "tab".equals(e.getType()) || safe(e.getGroupKey()).contains("-tab-")
                || (("button".equals(e.getType()) || "icon".equals(e.getType()) || "anchor".equals(e.getGroupRole()))
                && label.matches(".*(记录|规则|说明|设置|背包|商城|详情|更多).*"));
    }
    private static boolean isNamedBack(Element e) {
        return Set.of("返回", "后退", "back", "返回上一页").contains(safe(e.getLabel()).trim().toLowerCase())
                && Set.of("button", "icon", "text").contains(safe(e.getType()).toLowerCase());
    }
    public static boolean automatic(Interaction i) { return AUTOMATIC.contains(safe(i.getSource())); }
    public static boolean crossPage(Interaction i) { return Set.of("navigate", "popup", "modal").contains(safe(i.getActionType())); }
    public String targetName(Interaction i) {
        try { return json.readTree(safe(i.getParams())).path("target_name").asText(""); }
        catch (Exception ignored) { return ""; }
    }
    public static String crossAction(String existing, Page target, String evidence) {
        if (Set.of("navigate", "popup", "modal").contains(safe(existing))) return existing;
        return safe(target.getName()).matches(".*(弹窗|遮罩|确认弹框|提示弹框).*" ) || evidence.matches("(?s).*(弹出|弹窗|浮层).*" ) ? "popup" : "navigate";
    }
    private static List<Page> exactTargets(String name, Long self, List<Page> pages) {
        return pages.stream().filter(p -> !p.getId().equals(self) && !normalize(name).isBlank() && normalize(p.getName()).equals(normalize(name))).toList();
    }
    public static String relationKey(String trigger, String action, Long target) { return safeTrigger(trigger) + "|" + safe(action) + "|" + (target == null ? "none" : target); }
    public static String elementKey(Element e) {
        // Conservative exact fingerprint: ID-independent but position-sensitive; ambiguous repetitions never inherit.
        return sha(e.getPageId() + "|" + safe(e.getType()) + "|" + normalize(e.getLabel()) + "|" + safe(e.getAssetId()) + "|"
                + Math.round(nz(e.getPositionX())) + ":" + Math.round(nz(e.getPositionY())) + ":" + Math.round(nz(e.getWidth())) + ":" + Math.round(nz(e.getHeight())));
    }
    public static boolean matches(InteractionExclusion x, Element e, List<Element> siblings) {
        if (!Boolean.TRUE.equals(x.getActive()) || !e.getPageId().equals(x.getPageId())) return false;
        if (e.getId().equals(x.getElementId())) return true;
        return elementKey(e).equals(x.getElementKey()) && siblings.stream().filter(s -> elementKey(s).equals(x.getElementKey())).count() == 1;
    }
    public static boolean excluded(Element e, List<Element> siblings, String signature, List<InteractionExclusion> exclusions) {
        return exclusions.stream().anyMatch(x -> matches(x, e, siblings) && ("element".equals(x.getScope()) || signature.equals(x.getRelationKey())));
    }
    private static boolean elementExcluded(Element e, List<Element> siblings, List<InteractionExclusion> exclusions) {
        return exclusions.stream().anyMatch(x -> "element".equals(x.getScope()) && matches(x, e, siblings));
    }
    public static String normalize(String s) { return safe(s).replaceAll("[\\s\\p{P}\\p{S}]+", "").toLowerCase(); }
    public static String safe(String s) { return s == null ? "" : s; }
    private static String safeTrigger(String s) { return safe(s).isBlank() ? "click" : s; }
    private static double nz(Double n) { return n == null ? 0 : n; }
    public static String sha(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
