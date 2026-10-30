package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.InteractionMapper;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PageBackgroundActionService {
    private final ProjectService projects;
    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;

    /** Save behavior atomically without regenerating or replacing the user's page HTML. */
    @Transactional
    public Element configure(Long projectId,Long pageId,boolean enabled,String clientId) {
        projects.getProject(projectId);
        projectMapper.selectOne(Wrappers.<Project>lambdaQuery().eq(Project::getId,projectId).last("FOR UPDATE"));
        Page page=pageMapper.selectOne(Wrappers.<Page>lambdaQuery().eq(Page::getId,pageId).last("FOR UPDATE"));
        if(page==null || !projectId.equals(page.getProjectId()) || Integer.valueOf(1).equals(page.getIsDeleted()))
            throw new IllegalArgumentException("页面不存在或不属于该项目");
        if(Boolean.TRUE.equals(projects.getPageEditingStatus(pageId,clientId).get("conflict")))
            throw new IllegalStateException("其他成员正在编辑此页面，请稍后设置");
        var existing=elementMapper.selectList(Wrappers.<Element>lambdaQuery().eq(Element::getPageId,pageId).orderByAsc(Element::getId));
        Element element=existing.stream().filter(PageBackgroundAction::persistent).findFirst().orElse(null);
        boolean insert=element==null;
        if(insert){
            element=new Element();element.setPageId(pageId);element.setCreatedBy("manual");element.setCreatedAt(LocalDateTime.now());
        }
        element.setType("background");element.setLabel("页面空白区域");element.setStyle(PageBackgroundAction.STYLE);
        element.setPositionX(0d);element.setPositionY(0d);
        element.setWidth((double)(page.getCanvasWidth()==null?375:page.getCanvasWidth()));
        element.setHeight((double)(page.getCanvasHeight()==null?812:page.getCanvasHeight()));
        if(insert)elementMapper.insert(element);else elementMapper.updateById(element);
        var actions=interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId,element.getId()).orderByAsc(Interaction::getId));
        Interaction action=actions.isEmpty()?new Interaction():actions.get(0);
        action.setElementId(element.getId());action.setTriggerType("click");action.setActionType(enabled?"back":"none");
        action.setTargetPageId(null);action.setParams(null);action.setSource("user");
        if(actions.isEmpty())interactionMapper.insert(action);else interactionMapper.updateById(action);
        for(int i=1;i<actions.size();i++)interactionMapper.deleteById(actions.get(i).getId());
        return element;
    }
}
