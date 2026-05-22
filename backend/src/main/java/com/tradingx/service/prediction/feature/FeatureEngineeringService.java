
package com.tradingx.service.prediction.feature;

import com.tradingx.model.KlineDailyEntity;

import java.util.List;

public class FeatureEngineeringService {

    public static final int TECH_FEATURES = 15;
    public static final int WAVELET_FEATURES = 8;
    public static final int EXTRA_FEATURES = 14;
    public static final int TOTAL_FEATURES = TECH_FEATURES + WAVELET_FEATURES + EXTRA_FEATURES;

    public static double[][] extractFeatures(List<KlineDailyEntity> klines) {
        int n = klines.size();
        if (n == 0) return new double[0][0];

        double[] opens = new double[n];
        double[] highs = new double[n];
        double[] lows = new double[n];
        double[] closes = new double[n];
        double[] volumes = new double[n];

        for (int i = 0; i < n; i++) {
            KlineDailyEntity k = klines.get(i);
            opens[i] = k.getOpen() != null ? k.getOpen().doubleValue() : 0;
            highs[i] = k.getHigh() != null ? k.getHigh().doubleValue() : 0;
            lows[i] = k.getLow() != null ? k.getLow().doubleValue() : 0;
            closes[i] = k.getClose() != null ? k.getClose().doubleValue() : 0;
            volumes[i] = k.getVol() != null ? k.getVol().doubleValue() : 0;
        }

        double[][] techFeatures = TechnicalIndicatorService.computeAll(opens, highs, lows, closes, volumes);
        double[][] waveletFeatures = WaveletFeatureService.extractWaveletFeatures(closes);

        double[][] features = new double[n][TOTAL_FEATURES];

        for (int i = 0; i < n; i++) {
            int idx = 0;

            for (int j = 0; j < TECH_FEATURES; j++) {
                features[i][idx++] = techFeatures[i][j];
            }

            for (int j = 0; j < WAVELET_FEATURES; j++) {
                double val = j < waveletFeatures[i].length ? waveletFeatures[i][j] : 0;
                features[i][idx++] = Math.tanh(val);
            }

            double basePrice = closes[i] != 0 ? closes[i] : 1;
            double prevClose = i > 0 ? closes[i - 1] : closes[i];

            features[i][idx++] = (rsi6(closes, i) - 50) / 50;
            features[i][idx++] = (rsi24(closes, i) - 50) / 50;
            features[i][idx++] = Math.tanh(atr(highs, lows, closes, i, 7) / basePrice);
            features[i][idx++] = Math.tanh(atr(highs, lows, closes, i, 28) / basePrice);
            features[i][idx++] = Math.tanh(roc(closes, i, 6));
            features[i][idx++] = Math.tanh(roc(closes, i, 24));
            features[i][idx++] = Math.tanh(obvChange(closes, volumes, i, 5) - 1);
            features[i][idx++] = Math.tanh(volRatio(volumes, i, 5) - 1);
            features[i][idx++] = (mfi(highs, lows, closes, volumes, i) - 50) / 50;
            features[i][idx++] = adx(highs, lows, closes, i) / 100;
            features[i][idx++] = Math.tanh((opens[i] - closes[i]) / basePrice);
            features[i][idx++] = Math.tanh((closes[i] - prevClose) / basePrice);
            features[i][idx++] = Math.tanh(Math.log(volumes[i] + 1) / 15);
        }

        return features;
    }

    public static double[][][] createSequences(double[][] features, int seqLength) {
        int n = features.length;
        if (n < seqLength) return new double[0][0][0];

        int numSeqs = n - seqLength + 1;
        double[][][] sequences = new double[numSeqs][seqLength][TOTAL_FEATURES];

        for (int i = 0; i < numSeqs; i++) {
            for (int j = 0; j < seqLength; j++) {
                System.arraycopy(features[i + j], 0, sequences[i][j], 0, TOTAL_FEATURES);
            }
        }

        return sequences;
    }

    private static double rsi6(double[] closes, int i) {
        if (i < 7) return 50;
        double avgGain = 0, avgLoss = 0;
        for (int j = i - 5; j <= i; j++) {
            double c = closes[j] - closes[j - 1];
            if (c > 0) avgGain += c;
            else avgLoss += -c;
        }
        avgGain /= 6;
        avgLoss /= 6;
        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private static double rsi24(double[] closes, int i) {
        if (i < 25) return 50;
        double avgGain = 0, avgLoss = 0;
        for (int j = i - 23; j <= i; j++) {
            double c = closes[j] - closes[j - 1];
            if (c > 0) avgGain += c;
            else avgLoss += -c;
        }
        avgGain /= 24;
        avgLoss /= 24;
        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private static double atr(double[] highs, double[] lows, double[] closes, int i, int period) {
        if (i < period + 1) return 0;
        double sum = 0;
        for (int j = i - period + 1; j <= i; j++) {
            double hl = highs[j] - lows[j];
            double hc = Math.abs(highs[j] - closes[j - 1]);
            double lc = Math.abs(lows[j] - closes[j - 1]);
            sum += Math.max(Math.max(hl, hc), lc);
        }
        return sum / period;
    }

    private static double roc(double[] closes, int i, int period) {
        if (i < period || closes[i - period] == 0) return 0;
        return (closes[i] - closes[i - period]) / closes[i - period];
    }

    private static double obvChange(double[] closes, double[] volumes, int i, int period) {
        if (i < period) return 0;
        double obv = 0, obvPrev = 0;
        for (int j = 1; j <= i; j++) {
            if (closes[j] > closes[j - 1]) obv += volumes[j];
            else if (closes[j] < closes[j - 1]) obv -= volumes[j];
            if (j == i - period) obvPrev = obv;
        }
        return obvPrev == 0 ? 0 : (obv - obvPrev) / Math.abs(obvPrev);
    }

    private static double volRatio(double[] volumes, int i, int period) {
        if (i < period - 1) return 1;
        double sum = 0;
        for (int j = i - period + 1; j <= i; j++) {
            sum += volumes[j];
        }
        double mean = sum / period;
        return mean == 0 ? 1 : volumes[i] / mean;
    }

    private static double mfi(double[] highs, double[] lows, double[] closes, double[] volumes, int i) {
        if (i < 15) return 50;
        double posMf = 0, negMf = 0;
        for (int j = i - 13; j <= i; j++) {
            double tp = (highs[j] + lows[j] + closes[j]) / 3;
            double prevTp = (highs[j - 1] + lows[j - 1] + closes[j - 1]) / 3;
            double mf = tp * volumes[j];
            if (tp > prevTp) posMf += mf;
            else negMf += mf;
        }
        if (negMf == 0) return 100;
        return 100 - (100 / (1 + posMf / negMf));
    }

    private static double adx(double[] highs, double[] lows, double[] closes, int i) {
        if (i < 29) return 0;
        double plusDm = 0, minusDm = 0, tr = 0;
        for (int j = i - 13; j <= i; j++) {
            double upMove = highs[j] - highs[j - 1];
            double downMove = lows[j - 1] - lows[j];
            if (upMove > downMove && upMove > 0) plusDm += upMove;
            if (downMove > upMove && downMove > 0) minusDm += downMove;
            double hl = highs[j] - lows[j];
            double hc = Math.abs(highs[j] - closes[j - 1]);
            double lc = Math.abs(lows[j] - closes[j - 1]);
            tr += Math.max(Math.max(hl, hc), lc);
        }
        if (tr == 0) return 0;
        double plusDi = 100 * plusDm / tr;
        double minusDi = 100 * minusDm / tr;
        double dx = Math.abs(plusDi - minusDi) / (plusDi + minusDi + 0.0001) * 100;
        return dx;
    }
}
