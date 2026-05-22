
package com.tradingx.controller;

import com.tradingx.model.prediction.ModelVersionEntity;
import com.tradingx.model.prediction.TrainingTaskEntity;
import com.tradingx.service.prediction.TrainingService;
import com.tradingx.model.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @PostMapping("/submit")
    public R<TrainingTaskEntity> submitTraining(@RequestBody TrainingService.TrainingRequest request) {
        try {
            TrainingTaskEntity task = trainingService.submitTraining(request);
            return R.ok(task);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/submit-all")
    public R<TrainingTaskEntity> submitAllTraining(@RequestBody TrainingService.TrainingRequest request) {
        try {
            request.setSymbol(null);
            request.setTrainAll(true);
            TrainingTaskEntity task = trainingService.submitTraining(request);
            return R.ok(task);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/tasks")
    public R<List<TrainingTaskEntity>> getAllTasks() {
        return R.ok(trainingService.getAllTasks());
    }

    @GetMapping("/tasks/{id}")
    public R<TrainingTaskEntity> getTask(@PathVariable Long id) {
        TrainingTaskEntity task = trainingService.getTask(id);
        if (task != null) {
            return R.ok(task);
        }
        return R.fail("Task not found");
    }

    @PostMapping("/tasks/{id}/cancel")
    public R<Boolean> cancelTask(@PathVariable Long id) {
        boolean success = trainingService.cancelTask(id);
        return R.ok(success);
    }

    @DeleteMapping("/tasks/{id}")
    public R<Boolean> deleteTask(@PathVariable Long id) {
        boolean success = trainingService.deleteTask(id);
        if (!success) {
            return R.fail("无法删除：任务正在运行中或不存在");
        }
        return R.ok(true);
    }

    @GetMapping("/tasks/{id}/output")
    public R<String> getTaskOutput(@PathVariable Long id) {
        String output = trainingService.getTaskOutput(id);
        return R.ok(output);
    }

    @GetMapping("/models")
    public R<List<ModelVersionEntity>> getModels() {
        return R.ok(trainingService.getModelVersions());
    }
}

