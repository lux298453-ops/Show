package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wireforge.entity.UserComponent;
import com.wireforge.mapper.UserComponentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserComponentService {
    static final int MAX_NAME = 40;
    static final int MAX_HTML = 400_000;

    private final UserComponentMapper mapper;

    public List<Map<String, Object>> listMine(String userId) {
        return mapper.selectList(Wrappers.<UserComponent>lambdaQuery()
                        .eq(UserComponent::getUserId, userId)
                        .orderByDesc(UserComponent::getUpdatedAt)
                        .orderByDesc(UserComponent::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    public Map<String, Object> create(String userId, String name, String html, Integer width, Integer height) {
        String cleanName = cleanName(name);
        String cleanHtml = cleanHtml(html);
        int w = cleanSize(width);
        int h = cleanSize(height);
        LocalDateTime now = LocalDateTime.now();
        UserComponent row = UserComponent.builder()
                .userId(userId)
                .name(cleanName)
                .html(cleanHtml)
                .width(w)
                .height(h)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mapper.insert(row);
        return toView(row);
    }

    public Map<String, Object> rename(String userId, Long id, String name) {
        UserComponent row = own(userId, id);
        row.setName(cleanName(name));
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        return toView(row);
    }

    public void delete(String userId, Long id) {
        own(userId, id);
        mapper.delete(Wrappers.<UserComponent>lambdaQuery()
                .eq(UserComponent::getId, id)
                .eq(UserComponent::getUserId, userId));
    }

    private UserComponent own(String userId, Long id) {
        if (id == null) throw new IllegalStateException("找不到这个组件");
        UserComponent row = mapper.selectOne(Wrappers.<UserComponent>lambdaQuery()
                .eq(UserComponent::getId, id)
                .eq(UserComponent::getUserId, userId)
                .last("LIMIT 1"));
        if (row == null) throw new IllegalStateException("找不到这个组件");
        return row;
    }

    private Map<String, Object> toView(UserComponent row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("name", row.getName());
        view.put("html", row.getHtml());
        view.put("width", row.getWidth());
        view.put("height", row.getHeight());
        view.put("updatedAt", row.getUpdatedAt());
        return view;
    }

    static String cleanName(String name) {
        String text = name == null ? "" : name.trim();
        if (!StringUtils.hasText(text)) throw new IllegalStateException("请填写组件名字");
        if (text.length() > MAX_NAME) text = text.substring(0, MAX_NAME);
        return text;
    }

    static String cleanHtml(String html) {
        String text = html == null ? "" : html.trim();
        if (!StringUtils.hasText(text)) throw new IllegalStateException("没有可保存的内容");
        text = stripScripts(text).trim();
        if (!StringUtils.hasText(text)) throw new IllegalStateException("没有可保存的内容");
        if (text.length() > MAX_HTML) throw new IllegalStateException("这份内容太大，存不下");
        return text;
    }

    static int cleanSize(Integer value) {
        if (value == null || value < 1) return 1;
        return Math.min(value, 4000);
    }

    static String stripScripts(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            int start = lower.indexOf("<script", i);
            if (start < 0) {
                out.append(html, i, html.length());
                break;
            }
            out.append(html, i, start);
            int end = lower.indexOf("</script>", start);
            if (end < 0) break;
            i = end + "</script>".length();
        }
        return out.toString();
    }
}
