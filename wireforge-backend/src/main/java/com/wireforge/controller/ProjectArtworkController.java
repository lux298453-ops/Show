package com.wireforge.controller;

import com.wireforge.service.ProjectArtworkService;
import com.wireforge.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProjectArtworkController {
    private final ProjectArtworkService artwork;
    private final ProjectService projects;

    @GetMapping("/api/assets/artwork/{projectId}/{digest}.png")
    public ResponseEntity<Resource> image(@PathVariable long projectId, @PathVariable String digest) {
        projects.getProject(projectId);
        Resource image = artwork.image(projectId, digest);
        return image.exists() ? ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image) : ResponseEntity.notFound().build();
    }
}
