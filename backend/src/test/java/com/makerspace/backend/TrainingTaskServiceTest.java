package com.makerspace.backend;

import com.makerspace.backend.model.TrainingTask;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.TrainingTaskRepository;
import com.makerspace.backend.services.TrainingTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingTaskServiceTest {

    @Mock private TrainingTaskRepository trainingTaskRepository;

    @InjectMocks
    private TrainingTaskService trainingTaskService;

    private TrainingTask taskForUser(Long userId) {
        User user = new User();
        user.setId(userId);
        TrainingTask task = new TrainingTask();
        task.setUser(user);
        return task;
    }

    @Test
    void markCompleted_rejectsTaskUntilVideoIsCompleted() {
        when(trainingTaskRepository.findById(3L)).thenReturn(Optional.of(taskForUser(7L)));

        assertThatThrownBy(() -> trainingTaskService.markCompleted(3L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(trainingTaskRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markCompleted_succeedsAfterVideoCompletion() {
        TrainingTask task = taskForUser(7L);
        task.setVideoCompletedAt(LocalDateTime.now().minusMinutes(1));
        when(trainingTaskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(trainingTaskRepository.save(task)).thenReturn(task);

        TrainingTask result = trainingTaskService.markCompleted(3L, 7L);

        assertThat(result.getCompletedAt()).isNotNull();
        verify(trainingTaskRepository).save(task);
    }
}