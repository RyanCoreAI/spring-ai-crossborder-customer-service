package com.omnimerchant.agent.service;

import com.omnimerchant.agent.dto.HelpdeskDtos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omnimerchant.agent.dto.CommerceDtos;
import com.omnimerchant.agent.entity.QaReviewQueue;
import com.omnimerchant.agent.mapper.QaReviewQueueMapper;
import com.omnimerchant.agent.mapper.SupportIdentityLookupMapper;
import com.omnimerchant.common.exception.BusinessException;
import com.omnimerchant.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportQualityService {

    private final QaReviewQueueMapper qaReviewQueueMapper;
    private final SupportIdentityLookupMapper identityLookupMapper;
    private final SupportAuditService auditService;

    public CommerceDtos.PageResult<HelpdeskDtos.QaReviewItemVO> queue(String status, int page, int size) {
        var wrapper = new LambdaQueryWrapper<QaReviewQueue>()
                .eq(status != null && !status.isBlank(), QaReviewQueue::getStatus, status)
                .orderByDesc(QaReviewQueue::getCreatedAt);
        var result = qaReviewQueueMapper.selectPage(new Page<>(page, clamp(size)), wrapper);
        return new CommerceDtos.PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toView).toList());
    }

    public HelpdeskDtos.QaSummaryVO summary() {
        var rows = qaReviewQueueMapper.selectList(new LambdaQueryWrapper<QaReviewQueue>()
                .orderByDesc(QaReviewQueue::getCreatedAt).last("LIMIT 2000"));
        var pending = rows.stream().filter(row -> "PENDING".equalsIgnoreCase(row.getStatus())).count();
        var reviewed = rows.stream().filter(row -> "REVIEWED".equalsIgnoreCase(row.getStatus())).count();
        return new HelpdeskDtos.QaSummaryVO(rows.size(), pending, reviewed,
                averageScore(rows.stream().map(QaReviewQueue::getAutoScore).toList()),
                averageScore(rows.stream().map(QaReviewQueue::getReviewerScore).toList()));
    }

    @Transactional
    public HelpdeskDtos.QaReviewItemVO review(Long id, HelpdeskDtos.QaReviewRequest request) {
        var row = qaReviewQueueMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "质检任务不存在");
        }
        var reviewerId = request == null ? null : request.reviewerId();
        if (reviewerId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少操作者身份");
        }
        row.setStatus("REVIEWED");
        row.setReviewerId(reviewerId);
        row.setReviewerScore(request.score());
        row.setFindings(request.findings());
        row.setActionItems(request.actionItems());
        row.setReviewedAt(LocalDateTime.now());
        qaReviewQueueMapper.updateById(row);
        auditService.record(reviewerId, "SUPPORT_QA", "REVIEW_QA", "QA_REVIEW_QUEUE",
                String.valueOf(id), "完成客服质检复核", "MEDIUM", row.getTicketNo());
        return toView(row);
    }

    private HelpdeskDtos.QaReviewItemVO toView(QaReviewQueue row) {
        return new HelpdeskDtos.QaReviewItemVO(row.getId(), row.getSourceType(), row.getSourceId(),
                row.getConversationUuid(), row.getTicketNo(), row.getStatus(), row.getAutoScore(),
                row.getReviewerScore(), row.getReviewFlags(), row.getFindings(), row.getActionItems(),
                row.getReviewerId(), displayName(row.getReviewerId()), row.getReviewedAt(), row.getCreatedAt());
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        var name = identityLookupMapper.findDisplayName(userId);
        return name == null || name.isBlank() ? "用户 #" + userId : name;
    }

    private BigDecimal averageScore(List<Integer> scores) {
        var values = scores.stream().filter(value -> value != null && value > 0).toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).average().orElse(0.0))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int clamp(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
