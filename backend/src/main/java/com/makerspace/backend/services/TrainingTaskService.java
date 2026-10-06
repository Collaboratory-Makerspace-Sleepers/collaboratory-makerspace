package com.makerspace.backend.services;

import com.makerspace.backend.model.TrainingTask;
import com.makerspace.backend.repository.TrainingTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrainingTaskService {

    @Autowired private TrainingTaskRepository trainingTaskRepository;

    @Transactional(readOnly = true)
    public List<TrainingTask> findByUser(Long userId) {
        return trainingTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public TrainingTask markVideoCompleted(Long taskId, Long userId) {
        TrainingTask task = findOwnedTask(taskId, userId);
        if (task.getVideoCompletedAt() == null) {
            task.setVideoCompletedAt(LocalDateTime.now());
        }
        return trainingTaskRepository.save(task);
    }

    @Transactional
    public TrainingTask markCompleted(Long taskId, Long userId) {
        TrainingTask task = findOwnedTask(taskId, userId);
        if (task.getVideoCompletedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Complete the training video before completing this task");
        }
        if (task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        }
        return trainingTaskRepository.save(task);
    }

    private TrainingTask findOwnedTask(Long taskId, Long userId) {
        TrainingTask task = trainingTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Training task not found"));
        if (!task.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return task;
    }
}