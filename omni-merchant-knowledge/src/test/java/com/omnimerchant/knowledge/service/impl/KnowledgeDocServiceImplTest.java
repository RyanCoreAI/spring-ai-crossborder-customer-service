package com.omnimerchant.knowledge.service.impl;

import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.knowledge.dto.KnowledgeDocCreateDTO;
import com.omnimerchant.knowledge.entity.KnowledgeDoc;
import com.omnimerchant.knowledge.mapper.KnowledgeDocMapper;
import com.omnimerchant.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocServiceImplTest {

    @Mock
    private KnowledgeDocMapper mapper;

    private KnowledgeDocServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createShouldUseVerifiedTenantContext() {
        TenantContextHolder.set(1L);
        when(mapper.insert(any(KnowledgeDoc.class))).thenReturn(1);
        var dto = validDto(1L);

        service.create(dto);

        var captor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
    }

    @Test
    void createShouldRejectBodyTenantMismatch() {
        TenantContextHolder.set(1L);
        var dto = validDto(2L);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
        verifyNoInteractions(mapper);
    }

    private KnowledgeDocCreateDTO validDto(Long tenantId) {
        var dto = new KnowledgeDocCreateDTO();
        dto.setTenantId(tenantId);
        dto.setDocType("refund");
        dto.setTitle("Refund policy");
        dto.setLanguage("en");
        dto.setRawContent("Refund within 30 days.");
        return dto;
    }
}
