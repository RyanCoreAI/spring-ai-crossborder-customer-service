package com.omnimerchant.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.omnimerchant.knowledge.dto.KnowledgeDocCreateDTO;
import com.omnimerchant.knowledge.dto.KnowledgeDocVO;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;

public interface KnowledgeDocService extends IService<KnowledgeDoc> {

    IPage<KnowledgeDocVO> listDocs(Long tenantId, String docType, int page, int size);

    KnowledgeDocVO getByUuid(String docUuid);

    KnowledgeDocVO create(KnowledgeDocCreateDTO dto);

    KnowledgeDocVO update(String docUuid, KnowledgeDocCreateDTO dto);

    void deleteByUuid(String docUuid);
}
