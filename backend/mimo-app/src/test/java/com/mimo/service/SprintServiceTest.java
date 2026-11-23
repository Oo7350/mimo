package com.mimo.service;

import com.mimo.common.BusinessException;
import com.mimo.dto.SprintDTO;
import com.mimo.dto.SprintDTO.UpdateRequest;
import com.mimo.entity.Sprint;
import com.mimo.mapper.BoardColumnMapper;
import com.mimo.mapper.BurndownSnapshotMapper;
import com.mimo.mapper.IssueMapper;
import com.mimo.mapper.ProjectMapper;
import com.mimo.mapper.SprintMapper;
import com.mimo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KNOWN-009：Sprint 以前建完只能启动/完成，名字和起止日期都改不了。
 * 补更新端点时守住两条底线：日期不能倒挂，已结束的 Sprint 不许改。
 */
@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock private SprintMapper sprintMapper;
    @Mock private IssueMapper issueMapper;
    @Mock private BurndownSnapshotMapper snapshotMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private BoardColumnMapper boardColumnMapper;
    @Mock private UserMapper userMapper;

    private SprintService service() {
        return new SprintService(sprintMapper, issueMapper, snapshotMapper,
                projectMapper, boardColumnMapper, userMapper);
    }

    private Sprint sprint(String status) {
        Sprint s = new Sprint();
        s.setId(4L);
        s.setProjectId(1L);
        s.setName("Sprint 1");
        s.setGoal("旧目标");
        s.setStartDate(LocalDate.of(2026, 11, 1));
        s.setEndDate(LocalDate.of(2026, 11, 14));
        s.setIsActive(0);
        s.setStatus(status);
        return s;
    }

    private UpdateRequest req(LocalDate start, LocalDate end) {
        UpdateRequest r = new UpdateRequest();
        r.setName("Sprint 1 改名");
        r.setGoal("新目标");
        r.setStartDate(start);
        r.setEndDate(end);
        return r;
    }

    @Test
    void shouldUpdateNameGoalAndDates() {
        when(sprintMapper.selectById(4L)).thenReturn(sprint("PLANNING"));

        SprintDTO.SprintVO vo = service().updateSprint(4L,
                req(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 20)));

        assertThat(vo.getName()).isEqualTo("Sprint 1 改名");
        assertThat(vo.getGoal()).isEqualTo("新目标");
        assertThat(vo.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(vo.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        verify(sprintMapper).updateById(any(Sprint.class));
    }

    @Test
    void shouldRejectReversedDates() {
        when(sprintMapper.selectById(4L)).thenReturn(sprint("PLANNING"));

        assertThatThrownBy(() -> service().updateSprint(4L,
                req(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 3))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");

        verify(sprintMapper, never()).updateById(any(Sprint.class));
    }

    @Test
    void shouldRejectCompletedSprint() {
        when(sprintMapper.selectById(4L)).thenReturn(sprint("COMPLETED"));

        assertThatThrownBy(() -> service().updateSprint(4L,
                req(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 20))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");

        verify(sprintMapper, never()).updateById(any(Sprint.class));
    }

    @Test
    void shouldRejectMissingSprint() {
        when(sprintMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service().updateSprint(404L,
                req(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 20))))
                .isInstanceOf(BusinessException.class);
    }
}
