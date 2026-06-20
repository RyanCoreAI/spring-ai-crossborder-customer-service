package com.omnimerchant.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.omnimerchant.common.dto.R;
import com.omnimerchant.knowledge.dto.KnowledgeDocCreateDTO;
import com.omnimerchant.knowledge.dto.KnowledgeDocVO;
import com.omnimerchant.knowledge.service.KnowledgeDocService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocService knowledgeDocService;

    @GetMapping("/docs")
    public R<IPage<KnowledgeDocVO>> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String docType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(knowledgeDocService.listDocs(tenantId, docType, page, size));
    }

    @GetMapping("/docs/{docUuid}")
    public R<KnowledgeDocVO> getByUuid(@PathVariable String docUuid) {
        return R.ok(knowledgeDocService.getByUuid(docUuid));
    }

    @PostMapping("/docs")
    public R<KnowledgeDocVO> create(@Valid @RequestBody KnowledgeDocCreateDTO dto) {
        return R.ok(knowledgeDocService.create(dto));
    }

    @PutMapping("/docs/{docUuid}")
    public R<KnowledgeDocVO> update(@PathVariable String docUuid,
                                     @Valid @RequestBody KnowledgeDocCreateDTO dto) {
        return R.ok(knowledgeDocService.update(docUuid, dto));
    }

    @DeleteMapping("/docs/{docUuid}")
    public R<Void> delete(@PathVariable String docUuid) {
        knowledgeDocService.deleteByUuid(docUuid);
        return R.ok();
    }
}
