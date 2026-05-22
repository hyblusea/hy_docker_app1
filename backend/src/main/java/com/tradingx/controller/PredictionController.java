
package com.tradingx.controller;

import com.tradingx.model.KlineDailyEntity;
import com.tradingx.model.prediction.PredictionEntity;
import com.tradingx.model.R;
import com.tradingx.repository.prediction.PredictionRepository;
import com.tradingx.service.prediction.PredictionService;
import com.tradingx.service.prediction.data.DataPrepareService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prediction")
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionRepository predictionRepository;
    private final DataPrepareService dataPrepareService;

    public PredictionController(
            PredictionService predictionService,
            PredictionRepository predictionRepository,
            DataPrepareService dataPrepareService
    ) {
        this.predictionService = predictionService;
        this.predictionRepository = predictionRepository;
        this.dataPrepareService = dataPrepareService;
    }

    @PostMapping("/predict/{tsCode}")
    public R<Map<String, Object>> predict(@PathVariable String tsCode) {
        try {
            List<KlineDailyEntity> klines = dataPrepareService.getRecentKlines(tsCode, 120);
            PredictionService.TradeSignal signal = predictionService.generateSignal(klines);

            PredictionEntity prediction = new PredictionEntity();
            prediction.setModelVersionId(0L);
            prediction.setTsCode(tsCode);
            prediction.setPredictDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            prediction.setPredCloseChg1(signal.getPrediction());
            prediction.setSignal(signal.getSignal());
            prediction.setConfidence(signal.getConfidence());
            if (!klines.isEmpty()) {
                KlineDailyEntity latest = klines.get(klines.size() - 1);
                prediction.setCurrentClose(latest.getClose());
            }
            predictionRepository.save(prediction);

            return R.ok(Map.of(
                    "symbol", tsCode,
                    "signal", signal.getSignal(),
                    "confidence", signal.getConfidence(),
                    "prediction", signal.getPrediction(),
                    "direction", signal.getDirection(),
                    "magnitude", signal.getMagnitude(),
                    "downProb", signal.getDownProb(),
                    "flatProb", signal.getFlatProb(),
                    "upProb", signal.getUpProb()
            ));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/history/{tsCode}")
    public R<List<PredictionEntity>> getPredictionHistory(@PathVariable String tsCode) {
        return R.ok(predictionRepository.findByTsCodeOrderByCreatedAtDesc(tsCode));
    }

    @GetMapping("/loss-history")
    public R<String> getLossHistory() {
        try {
            String content = Files.readString(Paths.get("models/loss_history.json"));
            return R.ok(content);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }
}
