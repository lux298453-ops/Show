package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererNavigationEditingTest {
    @ParameterizedTest @ValueSource(ints={3,5,6,7})
    void alignedNavigationKeepsEditablePartsAndIndependentSourceBindings(int count) throws Exception {
        Page p=new Page();p.setId(1L);p.setName("角色详情");p.setCanvasWidth(375);p.setCanvasHeight(833);
        List<Element> elements=new ArrayList<>();Map<Long,List<Interaction>> actions=new HashMap<>();
        String[] names={"家园","收集","商城","我的","锁屏","主题","装扮"};
        for(int i=0;i<count;i++) {
            for(int j=0;j<2;j++) {
                Element e=new Element();e.setId(10L+i*2+j);e.setPageId(1L);e.setType(j==0?"icon":"text");
                e.setLabel(names[i]);e.setPositionX(375.0/count*i+4);e.setPositionY(j==0?775.0:806.0);
                e.setWidth(j==0?24.0:40.0);e.setHeight(j==0?24.0:14.0);e.setGroupKey("test-tab-"+i);
                e.setGroupRole(j==0?"anchor":"member");elements.add(e);
                if(j==0){Interaction action=new Interaction();action.setElementId(e.getId());action.setActionType("navigate");action.setTargetPageId(2L);actions.put(e.getId(),List.of(action));}
            }
        }
        ObjectMapper mapper=new ObjectMapper();String before=mapper.writeValueAsString(elements);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundles=Map.of(1L,new TemplateHtmlRenderer.PageBundle(p,elements,actions));
        String html=renderer.render(p,bundles,Map.of(1L,"角色详情",2L,"目标页"),p.getName(),null);
        assertEquals(count,Pattern.compile("data-wf-edit-role='navigation-item'").matcher(html).results().count());
        assertEquals(count,Pattern.compile("data-wf-edit-role='navigation-icon'").matcher(html).results().count());
        assertEquals(count,Pattern.compile("data-wf-edit-role='navigation-label'").matcher(html).results().count());
        var uids=Pattern.compile("data-wf-uid='([^']+)'").matcher(html);Set<String> seen=new HashSet<>();
        while(uids.find())assertTrue(seen.add(uids.group(1)),"Duplicate identity "+uids.group(1));
        for(Element e:elements)assertEquals(1,Pattern.compile("data-wf-element-id=\""+e.getId()+"\"").matcher(html).results().count());
        assertEquals(count,Pattern.compile("data-nav=\"目标页\"").matcher(html).results().count());
        assertTrue(html.contains("height:64px;z-index:60;display:flex"));
        assertEquals(before,mapper.writeValueAsString(elements));
        assertEquals(html,renderer.render(p,bundles,Map.of(1L,"角色详情",2L,"目标页"),p.getName(),null));
    }
}
