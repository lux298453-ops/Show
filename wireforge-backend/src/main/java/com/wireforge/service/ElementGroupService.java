package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.InteractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 元素落库之后、自动布线之前：写成组，一组只在 anchor 上留一条线。
 * 人手动保存的线（source=user）保留，并改挂到该组的 anchor。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElementGroupService {

    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;

    @Transactional
    public int groupPage(Page page) {
        if (page == null || page.getId() == null) return 0;
        List<Element> elements = elementMapper.selectList(
                Wrappers.<Element>lambdaQuery().eq(Element::getPageId, page.getId()));
        if (elements.isEmpty()) return 0;

        elementMapper.update(null, Wrappers.<Element>lambdaUpdate()
                .eq(Element::getPageId, page.getId())
                .set(Element::getGroupKey, "")
                .set(Element::getGroupRole, ""));

        double canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();
        List<ClickGrouper.Group> groups = ClickGrouper.group(elements, canvasH, page.getId());

        Map<Long, List<Interaction>> byElement = new HashMap<>();
        List<Long> ids = elements.stream().map(Element::getId).toList();
        if (!ids.isEmpty()) {
            for (Interaction it : interactionMapper.selectList(
                    Wrappers.<Interaction>lambdaQuery().in(Interaction::getElementId, ids))) {
                byElement.computeIfAbsent(it.getElementId(), k -> new ArrayList<>()).add(it);
            }
        }

        Map<Long, Element> byId = new HashMap<>();
        for (Element e : elements) byId.put(e.getId(), e);

        int tabIndex = 0;
        for (ClickGrouper.Group group : groups) {
            int index = "tab".equals(group.kind) ? tabIndex++ : 0;
            String key = ClickGrouper.keyFor(page.getId(), group, index);
            mark(group.anchor, key, "anchor");
            for (Element member : group.members) mark(member, key, "member");
            keepOneLine(group, byElement, byId);
        }
        log.info("页面 [{}] 点击分组 {} 组", page.getName(), groups.size());
        return groups.size();
    }

    private void mark(Element element, String key, String role) {
        element.setGroupKey(key);
        element.setGroupRole(role);
        elementMapper.updateById(element);
    }

    private void keepOneLine(ClickGrouper.Group group,
                             Map<Long, List<Interaction>> byElement,
                             Map<Long, Element> byId) {
        List<Interaction> all = new ArrayList<>();
        collect(all, byElement.get(group.anchor.getId()));
        for (Element member : group.members) collect(all, byElement.get(member.getId()));
        if (all.isEmpty()) return;

        List<Interaction> users = new ArrayList<>();
        List<Interaction> autos = new ArrayList<>();
        for (Interaction it : all) {
            if ("user".equals(it.getSource())) users.add(it);
            else autos.add(it);
        }

        if (!users.isEmpty()) {
            for (Interaction userLine : users) moveToAnchor(userLine, group.anchor.getId());
            for (Interaction auto : autos) interactionMapper.deleteById(auto.getId());
            return;
        }

        Interaction keep = pickAuto(autos, byId);
        for (Interaction auto : autos) {
            if (keep != null && auto.getId().equals(keep.getId())) continue;
            interactionMapper.deleteById(auto.getId());
        }
        if (keep != null) moveToAnchor(keep, group.anchor.getId());
    }

    private static void collect(List<Interaction> out, List<Interaction> src) {
        if (src != null) out.addAll(src);
    }

    private void moveToAnchor(Interaction it, Long anchorId) {
        if (anchorId.equals(it.getElementId())) return;
        it.setElementId(anchorId);
        interactionMapper.updateById(it);
    }

    /** 目标一致就留一条。目标不一致时，按钮或图标上的线优先于纯文字。仍然分不出就一条都不留。 */
    private static Interaction pickAuto(List<Interaction> autos, Map<Long, Element> byId) {
        if (autos.isEmpty()) return null;
        String first = signature(autos.get(0));
        boolean same = true;
        for (Interaction it : autos) {
            if (!signature(it).equals(first)) {
                same = false;
                break;
            }
        }
        if (same) return autos.get(0);

        int best = Integer.MIN_VALUE;
        List<Interaction> top = new ArrayList<>();
        for (Interaction it : autos) {
            int score = score(byId.get(it.getElementId()));
            if (score > best) {
                best = score;
                top.clear();
                top.add(it);
            } else if (score == best) {
                top.add(it);
            }
        }
        if (top.size() == 1) return top.get(0);
        String topSig = signature(top.get(0));
        for (Interaction it : top) {
            if (!signature(it).equals(topSig)) return null;
        }
        return top.get(0);
    }

    private static int score(Element e) {
        String t = e == null || e.getType() == null ? "" : e.getType().toLowerCase();
        return switch (t) {
            case "button" -> 3;
            case "icon" -> 2;
            case "container" -> 1;
            default -> 0;
        };
    }

    private static String signature(Interaction it) {
        String action = it.getActionType() == null ? "" : it.getActionType();
        String target = it.getTargetPageId() == null ? "" : String.valueOf(it.getTargetPageId());
        String params = it.getParams() == null ? "" : it.getParams();
        return action + "|" + target + "|" + params;
    }
}
