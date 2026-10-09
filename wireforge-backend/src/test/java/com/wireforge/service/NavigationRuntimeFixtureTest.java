package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.model.AutowirePlan.NavigationInfo;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Also exports the exact backend runtime for the frontend's real iframe browser regression. */
class NavigationRuntimeFixtureTest {
    @Test void interactionPatchPreservesLayoutAndExportsBrowserFixture() throws Exception {
        String original="""
            <!doctype html><html><head><meta charset="utf-8"><style>
            body{position:relative;margin:0;width:375px;height:812px;background:#f4f4f4;font:14px sans-serif}
            .nav-item{position:absolute;left:0;top:730px;width:125px;height:76px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;background:white}
            .nav-item svg{width:24px;height:24px}.wf-ic,.wf-t{position:absolute}
            </style></head><body><h1>保留用户编辑的内容</h1>
            <div class="nav-item"><svg data-wf-element-id="10"><circle cx="12" cy="12" r="10" fill="blue"/></svg><span data-wf-element-id="11">首页</span></div>
            <svg class="wf-ic" data-wf-element-id="20" style="left:175px;top:744px;width:24px;height:24px"><circle cx="12" cy="12" r="10" fill="green"/></svg>
            <span class="wf-t" data-wf-element-id="21" style="left:175px;top:774px">我的</span>
            <div class="nav-item" style="left:250px"><svg data-wf-element-id="30"><circle cx="12" cy="12" r="10"/></svg><span data-wf-element-id="31">设置</span><button data-wf-element-id="99" data-action="toggle">独立操作</button></div>
            </body></html>
            """;
        List<AutowireRenderService.Binding> bindings=new ArrayList<>();
        for(int i=0;i<3;i++) {
            long id=(i+1)*10;String name=List.of("首页","我的","设置").get(i);
            var nav=new NavigationInfo("family","item"+i,name,"bottom",List.of(id,id+1),i*125,730,125,76,"resolved","user_choice",null,null,List.of());
            bindings.add(new AutowireRenderService.Binding(1,id,"图标","icon","","",i*125+50,744,24,24,"navigate",2L,name,nav));
        }
        var json=new ObjectMapper();String patched=AutowireRenderService.patchHtml(original,json.writeValueAsString(bindings),"{}");
        assertTrue(patched.contains("<h1>保留用户编辑的内容</h1>"));assertTrue(patched.contains("data-wf-element-id=\"99\" data-action=\"toggle\""));
        assertEquals(1,patched.split("<!-- wf-autowire-start -->",-1).length-1);
        assertEquals(patched,AutowireRenderService.patchHtml(patched,json.writeValueAsString(bindings),"{}"));
        Files.writeString(Path.of("target/navigation-runtime-fixture.html"),patched);
    }
}
