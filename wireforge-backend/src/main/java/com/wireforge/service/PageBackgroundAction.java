package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;

/** Explicit page behavior, stored separately from elements reconstructed by vision. */
final class PageBackgroundAction {
    static final String STYLE="{\"wfPageAction\":\"background-return\"}";
    private static final ObjectMapper JSON=new ObjectMapper();

    static boolean managed(Element element) {
        if(element==null || !"background".equals(element.getType()) || element.getStyle()==null)return false;
        try{return "background-return".equals(JSON.readTree(element.getStyle()).path("wfPageAction").asText());}
        catch(Exception ignored){return false;}
    }

    static boolean persistent(Element element) {
        return managed(element) && "manual".equals(element.getCreatedBy());
    }
}
