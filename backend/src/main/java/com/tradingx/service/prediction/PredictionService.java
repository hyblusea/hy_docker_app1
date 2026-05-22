
package com.tradingx.service.prediction;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.tradingx.model.KlineDailyEntity;
import com.tradingx.service.prediction.data.DataPrepareService;
import com.tradingx.service.prediction.feature.FeatureEngineeringService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Service
public class PredictionService {

    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);
    private static final int SEQ_LENGTH = 60;

    @Value("${ml.prediction.model-path:models/prediction_model.pt}")
    private String modelPath;

    private final DataPrepareService dataPrepareService;

    private Model model;
    private Predictor<float[], float[]> predictor;
    private volatile boolean modelLoaded = false;

    public PredictionService(DataPrepareService dataPrepareService) {
        this.dataPrepareService = dataPrepareService;
    }

    public synchronized void loadModel() {
        if (modelLoaded) return;

        try {
            if (!Files.exists(Paths.get(modelPath))) {
                logger.warn("Model file not found: {}, skip loading", modelPath);
                return;
            }

            model = Model.newInstance("kline-prediction");
            model.load(Paths.get(modelPath));

            Translator<float[], float[]> translator = new Translator<float[], float[]>() {
                @Override
                public NDList processInput(TranslatorContext ctx, float[] input) {
                    NDManager manager = ctx.getNDManager();
                    NDArray array = manager.create(input).toType(DataType.FLOAT32, false)
                            .reshape(new Shape(1, SEQ_LENGTH, FeatureEngineeringService.TOTAL_FEATURES));
                    return new NDList(array);
                }

                @Override
                public float[] processOutput(TranslatorContext ctx, NDList list) {
                    NDArray dirOutput = list.get(0);
                    NDArray magOutput = list.get(1);
                    float[] dirProbs = dirOutput.softmax(1).toFloatArray();
                    float magValue = magOutput.toFloatArray()[0];
                    return new float[]{dirProbs[0], dirProbs[1], dirProbs[2], magValue};
                }

                @Override
                public Batchifier getBatchifier() {
                    return Batchifier.STACK;
                }
            };

            predictor = model.newPredictor(translator);
            modelLoaded = true;
            logger.info("Model loaded successfully from {}", modelPath);
        } catch (IOException e) {
            logger.warn("Failed to load model: {}", e.getMessage());
            close();
        } catch (Exception e) {
            logger.warn("Failed to initialize DJL engine: {}", e.getMessage());
            close();
        }
    }

    public PredictionResult predict(List<KlineDailyEntity> klines) {
        if (!modelLoaded) {
            loadModel();
        }

        if (!modelLoaded || predictor == null || klines.size() < SEQ_LENGTH) {
            return new PredictionResult("HOLD", 0.5f, 0.0f, new float[]{0.33f, 0.34f, 0.33f});
        }

        try {
            double[][] features = FeatureEngineeringService.extractFeatures(klines);
            double[][][] sequences = FeatureEngineeringService.createSequences(features, SEQ_LENGTH);

            if (sequences.length == 0) {
                return new PredictionResult("HOLD", 0.5f, 0.0f, new float[]{0.33f, 0.34f, 0.33f});
            }

            double[][] latestSeq = sequences[sequences.length - 1];
            float[] flatInput = new float[SEQ_LENGTH * FeatureEngineeringService.TOTAL_FEATURES];
            int idx = 0;
            for (int i = 0; i < SEQ_LENGTH; i++) {
                for (int j = 0; j < FeatureEngineeringService.TOTAL_FEATURES; j++) {
                    flatInput[idx++] = (float) latestSeq[i][j];
                }
            }

            float[] result = predictor.predict(flatInput);
            float downProb = result[0];
            float flatProb = result[1];
            float upProb = result[2];
            float magnitude = result[3];

            String direction;
            float confidence;
            if (upProb > downProb && upProb > flatProb) {
                direction = "UP";
                confidence = upProb;
            } else if (downProb > upProb && downProb > flatProb) {
                direction = "DOWN";
                confidence = downProb;
            } else {
                direction = "FLAT";
                confidence = flatProb;
            }

            return new PredictionResult(direction, confidence, magnitude, new float[]{downProb, flatProb, upProb});
        } catch (TranslateException e) {
            logger.error("Prediction failed: {}", e.getMessage());
            return new PredictionResult("HOLD", 0.5f, 0.0f, new float[]{0.33f, 0.34f, 0.33f});
        }
    }

    public TradeSignal generateSignal(List<KlineDailyEntity> klines) {
        PredictionResult result = predict(klines);

        TradeSignal signal = new TradeSignal();
        signal.setDirection(result.direction);
        signal.setConfidence(result.confidence);
        signal.setMagnitude(result.magnitude);
        signal.setDownProb(result.probs[0]);
        signal.setFlatProb(result.probs[1]);
        signal.setUpProb(result.probs[2]);

        double magPct = result.magnitude * 100;

        if ("UP".equals(result.direction) && result.confidence > 0.4) {
            signal.setSignal("BUY");
            signal.setPrediction(magPct);
        } else if ("DOWN".equals(result.direction) && result.confidence > 0.4) {
            signal.setSignal("SELL");
            signal.setPrediction(magPct);
        } else {
            signal.setSignal("HOLD");
            signal.setPrediction(magPct);
        }

        return signal;
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    @PreDestroy
    public void close() {
        modelLoaded = false;
        if (predictor != null) {
            try { predictor.close(); } catch (Exception ignored) {}
        }
        if (model != null) {
            try { model.close(); } catch (Exception ignored) {}
        }
    }

    public static class PredictionResult {
        public final String direction;
        public final float confidence;
        public final float magnitude;
        public final float[] probs;

        public PredictionResult(String direction, float confidence, float magnitude, float[] probs) {
            this.direction = direction;
            this.confidence = confidence;
            this.magnitude = magnitude;
            this.probs = probs;
        }
    }

    public static class TradeSignal {
        private String signal;
        private String direction;
        private double confidence;
        private double magnitude;
        private double prediction;
        private double downProb;
        private double flatProb;
        private double upProb;

        public String getSignal() { return signal; }
        public void setSignal(String signal) { this.signal = signal; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public double getMagnitude() { return magnitude; }
        public void setMagnitude(double magnitude) { this.magnitude = magnitude; }
        public double getPrediction() { return prediction; }
        public void setPrediction(double prediction) { this.prediction = prediction; }
        public double getDownProb() { return downProb; }
        public void setDownProb(double downProb) { this.downProb = downProb; }
        public double getFlatProb() { return flatProb; }
        public void setFlatProb(double flatProb) { this.flatProb = flatProb; }
        public double getUpProb() { return upProb; }
        public void setUpProb(double upProb) { this.upProb = upProb; }
    }
}
