package com.omnimerchant.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import com.omnimerchant.knowledge.dto.KnowledgeDocCreateDTO;
import com.omnimerchant.knowledge.dto.KnowledgeDocVO;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;
import com.omnimerchant.knowledge.mapper.KnowledgeDocMapper;
import com.omnimerchant.knowledge.service.KnowledgeDocService;
import com.omnimerchant.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc>
        implements KnowledgeDocService {

    @Override
    public IPage<KnowledgeDocVO> listDocs(Long tenantId, String docType, int page, int size) {
        var contextTenantId = requireTenantContext();
        assertTenantParamMatchesContext(tenantId);
        var wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, contextTenantId)
                .eq(docType != null && !docType.isBlank(), KnowledgeDoc::getDocType, docType)
                .orderByDesc(KnowledgeDoc::getCreatedAt);
        var result = page(new Page<>(page, size), wrapper);
        return result.convert(this::toVO);
    }

    @Override
    public KnowledgeDocVO getByUuid(String docUuid) {
        var doc = getOne(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDocUuid, docUuid));
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识文档不存在");
        }
        return toVO(doc);
    }

    @Override
    @Transactional
    public KnowledgeDocVO create(KnowledgeDocCreateDTO dto) {
        var contextTenantId = requireTenantContext();
        assertTenantParamMatchesContext(dto.getTenantId());
        var doc = new KnowledgeDoc();
        BeanUtils.copyProperties(dto, doc);
        doc.setTenantId(contextTenantId);
        doc.setDocUuid(UUID.randomUUID().toString().replace("-", ""));
        doc.setCharCount(dto.getRawContent() != null ? dto.getRawContent().length() : 0);
        doc.setChunkSize(dto.getChunkSize() != null ? dto.getChunkSize() : 500);
        doc.setChunkOverlap(dto.getChunkOverlap() != null ? dto.getChunkOverlap() : 50);
        doc.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        doc.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        save(doc);
        log.info("知识文档创建成功: docUuid={}, title={}", doc.getDocUuid(), doc.getTitle());
        return toVO(doc);
    }

    @Override
    @Transactional
    public KnowledgeDocVO update(String docUuid, KnowledgeDocCreateDTO dto) {
        var doc = getOne(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDocUuid, docUuid));
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识文档不存在");
        }
        BeanUtils.copyProperties(dto, doc, "tenantId");
        if (dto.getRawContent() != null) {
            doc.setCharCount(dto.getRawContent().length());
        }
        updateById(doc);
        return toVO(doc);
    }

    @Override
    @Transactional
    public void deleteByUuid(String docUuid) {
        var doc = getOne(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getDocUuid, docUuid));
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识文档不存在");
        }
        removeById(doc.getId());
        log.info("知识文档已删除: docUuid={}", docUuid);
    }

    private KnowledgeDocVO toVO(KnowledgeDoc doc) {
        var vo = new KnowledgeDocVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }

    private Long requireTenantContext() {
        var tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_MISSING, "缺少租户上下文");
        }
        return tenantId;
    }

    private void assertTenantParamMatchesContext(Long tenantId) {
        var contextTenantId = requireTenantContext();
        if (tenantId != null && !tenantId.equals(contextTenantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求租户与认证租户不一致");
        }
    }
}
