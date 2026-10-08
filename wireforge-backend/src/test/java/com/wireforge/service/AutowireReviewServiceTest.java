package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import com.wireforge.model.AutowirePlan.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AutowireReviewServiceTest {
    PageMapper pages=mock(PageMapper.class);ElementMapper elements=mock(ElementMapper.class);InteractionMapper lines=mock(InteractionMapper.class);
    AnnotationMapper annotations=mock(AnnotationMapper.class);ProjectMapper projects=mock(ProjectMapper.class);InteractionExclusionMapper exclusions=mock(InteractionExclusionMapper.class);
    AiClient ai=mock(AiClient.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    AutowireRenderService render=mock(AutowireRenderService.class);ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    Page page=AutowirePlannerTest.page(1,"积分商城"), target=AutowirePlannerTest.page(3,"兑换记录");Element button=AutowirePlannerTest.el(10,"button","兑换记录",10,100,100,30);
    InteractionAutowireService service;
    @BeforeEach void setup() {
        Project p=new Project();p.setId(1L);when(projects.selectOne(any())).thenReturn(p);when(pages.selectList(any())).thenReturn(List.of(page,target));
        when(elements.selectList(any())).thenReturn(List.of(button));when(lines.selectList(any())).thenReturn(List.of());when(annotations.selectList(any())).thenReturn(List.of());when(exclusions.selectList(any())).thenReturn(List.of());
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(render.bindings(anyList(),anyList(),anyList())).thenReturn(List.of());
        service=new InteractionAutowireService(pages,elements,lines,annotations,projects,exclusions,ai,json,new AutowirePlanner(),jdbc,tx,render);
    }
    ApplyRequest request(String token,List<String> selected) {return new ApplyRequest(token,"review-key-1234",selected,List.of(),List.of());}
    @Test void previewNeverWritesOrRenders() {
        var plan=service.preview(1L);assertEquals(1,plan.items().size());
        verifyNoInteractions(jdbc,render,tx);verify(lines,never()).insert(any(Interaction.class));verify(lines,never()).deleteById(anyLong());verifyNoInteractions(ai);
    }
    @Test void stalePlanRollsBackWithoutChangingRelations() {
        var plan=service.preview(1L);button.setLabel("同时编辑后的入口");
        assertThrows(ResponseStatusException.class,()->service.apply(1L,request(plan.previewId(),List.of(plan.items().get(0).id()))));
        verify(tx).rollback(any());verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(render);
    }
    @Test void forgedItemOrOtherProjectTokenCannotBeApplied() {
        var plan=service.preview(1L);
        assertThrows(IllegalStateException.class,()->service.apply(1L,request(plan.previewId(),List.of("forged"))));
        assertThrows(ResponseStatusException.class,()->service.apply(2L,request(plan.previewId(),List.of())));
        verify(lines,never()).insert(any(Interaction.class));
    }
    @Test void uncheckedItemDoesNotBecomeAnExclusion() {
        var plan=service.preview(1L);var result=service.apply(1L,request(plan.previewId(),List.of()));
        assertEquals(0,result.added());assertEquals(0,result.excluded());verify(lines,never()).insert(any(Interaction.class));
        verify(jdbc,never()).update(startsWith("INSERT INTO interaction_exclusion"),any(Object[].class));verify(tx).commit(any());
    }
    @Test void idempotencyReturnsOriginalResultWithoutInsertingAgain() throws Exception {
        var plan=service.preview(1L);ApplyRequest req=request(plan.previewId(),List.of(plan.items().get(0).id()));
        String hash=AutowirePlanner.sha(json.writeValueAsString(req));var saved=new ApplyResult("old-application",1,0,0,0,"done",null);
        when(jdbc.queryForList(startsWith("SELECT request_hash"),eq(1L),eq("review-key-1234"))).thenReturn(List.of(Map.of("request_hash",hash,"result_json",json.writeValueAsString(saved))));
        assertEquals(saved,service.apply(1L,req));verify(lines,never()).insert(any(Interaction.class));verify(tx).commit(any());
    }
    @Test void applyUsesOnlyServerProposalAndCommitsBeforeRendering() {
        var plan=service.preview(1L);var result=service.apply(1L,request(plan.previewId(),List.of(plan.items().get(0).id())));
        assertEquals(1,result.added());verify(lines).insert(argThat((Interaction i)->i.getTargetPageId()==3L&&"autowire_review".equals(i.getSource())));
        var order=inOrder(tx,render);order.verify(tx).commit(any());order.verify(render).process(result.applicationId());
    }
}
