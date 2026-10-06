package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.TrainingTaskDTO;
import com.makerspace.backend.services.TrainingTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training-tasks")
public class TrainingTaskController {

    @Autowired private TrainingTaskService trainingTaskService;
    @Autowired private UserSecurity userSecurity;

    @GetMapping("/me")
    public List<TrainingTaskDTO> myTasks(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        return trainingTaskService.findByUser(userId).stream().map(TrainingTaskDTO::from).toList();
    }

    @PostMapping("/{id}/video-completed")
    public TrainingTaskDTO markVideoCompleted(@PathVariable Long id, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        return TrainingTaskDTO.from(trainingTaskService.markVideoCompleted(id, userId));
    }

    @PostMapping("/{id}/complete")
    public TrainingTaskDTO markCompleted(@PathVariable Long id, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        return TrainingTaskDTO.from(trainingTaskService.markCompleted(id, userId));
    }
}