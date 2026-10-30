package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Page;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.PageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyzeServiceVariantAlignmentTest {
    private final PageMapper pages=mock(PageMapper.class);
    private final ElementMapper elements=mock(ElementMapper.class);
    private final AnalyzeService service=new AnalyzeService(null,null,pages,elements,null,null,new ObjectMapper(),null,null,null,null,null,null);

    private static Page page(long id,String name,String hash){
        Page p=new Page();p.setId(id);p.setName(name);p.setImageHash(hash);p.setCanvasWidth(375);p.setCanvasHeight(833);return p;
    }
    private static Element e(long id,String type,String label,double x,double y,double w,double h){
        Element e=new Element();e.setId(id);e.setType(type);e.setLabel(label);e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }
    private void sources(Page base,Page variant,List<Element> baseElements,List<Element> variantElements){
        when(pages.selectList(any())).thenReturn(List.of(base,variant));
        when(elements.selectList(any())).thenReturn(baseElements,variantElements);
    }

    @Test
    void differentCostumeScreensCannotSnapMotionThirdRowToSouvenirPictures(){
        Page souvenir=page(1,"其他装扮","a".repeat(64)),motion=page(2,"个性装扮动效页","b".repeat(64));
        Element picture=e(20,"image","",15.4,560.7,80.2,127.3);
        sources(souvenir,motion,List.of(e(10,"image","",17,608.6,64.8,104.9)),List.of(picture));
        service.alignVariantPages(7);
        verifyNoInteractions(elements);assertEquals(560.7,picture.getPositionY().doubleValue());
    }

    @Test
    void legacyPagesWithoutFingerprintsCannotBeGroupedBySharedWords(){
        when(pages.selectList(any())).thenReturn(List.of(page(1,"个性装扮预览页",null),page(2,"个性装扮动效页",null)));
        service.alignVariantPages(7);verifyNoInteractions(elements);
    }

    @Test
    void identicalDraftCanCorrectSmallJitterWithoutChangingIdentityOrCopy(){
        Page base=page(1,"界面","a".repeat(64)),variant=page(2,"界面副本","a".repeat(64));
        Element expected=e(10,"image","",15,195,80,128),target=e(20,"image","",18,198,81,127);target.setPageId(2L);target.setStyle("{\"owned\":true}");
        sources(base,variant,List.of(expected),List.of(target));service.alignVariantPages(7);
        verify(elements).updateById(target);assertEquals(15,target.getPositionX().doubleValue());assertEquals(195,target.getPositionY().doubleValue());
        assertEquals(20L,target.getId());assertEquals(2L,target.getPageId());assertEquals("",target.getLabel());assertEquals("{\"owned\":true}",target.getStyle());
    }

    @Test
    void croppedImageCannotReplaceTheWholeSlotEvenForTheSameDraft(){
        Element target=e(20,"image","",15.4,560.7,80.2,127.3);
        sources(page(1,"界面","a".repeat(64)),page(2,"界面副本","a".repeat(64)),
                List.of(e(10,"image","",17,608.6,64.8,104.9)),List.of(target));
        service.alignVariantPages(7);verify(elements,never()).updateById(any(Element.class));
        assertEquals(560.7,target.getPositionY().doubleValue());assertEquals(80.2,target.getWidth().doubleValue());
    }

    @Test
    void emptyReferenceLabelCannotMatchEveryNamedTopIcon(){
        sources(page(1,"界面","a".repeat(64)),page(2,"界面副本","a".repeat(64)),
                List.of(e(10,"icon","",20,40,20,20)),List.of(e(20,"icon","信息",24,42,20,20)));
        service.alignVariantPages(7);verify(elements,never()).updateById(any(Element.class));
    }

    @Test
    void referenceElementCannotAbsorbTwoIndependentElements(){
        Element one=e(20,"text","提示",23,102,60,18),two=e(21,"text","提示",25,103,60,18);
        sources(page(1,"界面","a".repeat(64)),page(2,"界面副本","a".repeat(64)),
                List.of(e(10,"text","提示",20,100,60,18)),List.of(one,two));
        service.alignVariantPages(7);verify(elements,times(1)).updateById(any(Element.class));
        assertEquals(25,two.getPositionX().doubleValue());
    }

    @Test
    void changedFileAndDifferentCanvasCannotUseAnOldSharedFingerprint(){
        Page base=page(1,"界面","a".repeat(64)),variant=page(2,"界面副本","a".repeat(64));
        when(pages.selectList(any())).thenReturn(List.of(base,variant));
        AnalyzeService checking=spy(service);doReturn("b".repeat(64)).when(checking).currentImageFingerprint(base);
        checking.alignVariantPages(7);verifyNoInteractions(elements);
        variant.setCanvasWidth(414);service.alignVariantPages(7);verifyNoInteractions(elements);
    }
}
