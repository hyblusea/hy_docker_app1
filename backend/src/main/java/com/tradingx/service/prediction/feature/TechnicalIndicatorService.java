
package com.tradingx.service.prediction.feature;

public class TechnicalIndicatorService {
    
    public static double[][] computeAll(double[] opens, double[] highs, double[] lows, double[] closes, double[] volumes) {
        int n = closes.length;
        double[][] features = new double[n][15];
        
        double[] rsi = computeRSI(closes, 14);
        double[][] macd = computeMACD(closes);
        double[][] kdj = computeKDJ(highs, lows, closes);
        double[][] bollinger = computeBollingerBands(closes, 20);
        double[] atr = computeATR(highs, lows, closes, 14);
        double[] roc = computeROC(closes, 12);
        double[] williamsR = computeWilliamsR(highs, lows, closes, 14);
        double[] obvChange = computeOBVChange(closes, volumes, 5);
        double[] volumeRatio = computeVolumeRatio(volumes, 20);
        double[] volatility = computeVolatility(closes, 20);
        
        for (int i = 0; i < n; i++) {
            features[i][0] = (rsi[i] - 50) / 50;
            features[i][1] = macd[i][0] / (closes[i] != 0 ? closes[i] : 1);
            features[i][2] = macd[i][1] / (closes[i] != 0 ? closes[i] : 1);
            features[i][3] = macd[i][2] / (closes[i] != 0 ? closes[i] : 1);
            features[i][4] = kdj[i][0] / 100;
            features[i][5] = kdj[i][1] / 100;
            features[i][6] = kdj[i][2] / 100;
            features[i][7] = bollinger[i][2];
            features[i][8] = bollinger[i][3];
            features[i][9] = atr[i] / (closes[i] != 0 ? closes[i] : 1);
            features[i][10] = roc[i];
            features[i][11] = williamsR[i] / 100;
            features[i][12] = obvChange[i];
            features[i][13] = volumeRatio[i];
            features[i][14] = volatility[i];
        }
        
        return features;
    }

    private static double[] computeRSI(double[] prices, int period) {
        int n = prices.length;
        double[] rsi = new double[n];
        if (n < period + 1) return rsi;
        
        double avgGain = 0, avgLoss = 0;
        
        for (int j = 1; j <= period; j++) {
            double c = prices[j] - prices[j - 1];
            if (c > 0) avgGain += c;
            else avgLoss += -c;
        }
        avgGain /= period;
        avgLoss /= period;
        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        rsi[period] = 100 - (100 / (1 + rs));
        
        for (int i = period + 1; i < n; i++) {
            double change = prices[i] - prices[i - 1];
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? -change : 0;
            
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            
            rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            rsi[i] = 100 - (100 / (1 + rs));
        }
        return rsi;
    }

    private static double[][] computeMACD(double[] prices) {
        int n = prices.length;
        double[][] macd = new double[n][3];
        if (n < 26) return macd;
        
        double[] ema12 = computeEMA(prices, 12);
        double[] ema26 = computeEMA(prices, 26);
        double[] diff = new double[n];
        for (int i = 0; i < n; i++) {
            diff[i] = ema12[i] - ema26[i];
        }
        
        double[] signal = computeEMA(diff, 9);
        
        for (int i = 0; i < n; i++) {
            macd[i][0] = diff[i];
            macd[i][1] = signal[i];
            macd[i][2] = diff[i] - signal[i];
        }
        return macd;
    }

    private static double[] computeEMA(double[] values, int period) {
        double[] ema = new double[values.length];
        double k = 2.0 / (period + 1);
        
        ema[0] = values[0];
        for (int i = 1; i < values.length; i++) {
            ema[i] = values[i] * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }

    private static double[][] computeKDJ(double[] highs, double[] lows, double[] closes) {
        int n = closes.length;
        double[][] kdj = new double[n][3];
        if (n < 9) return kdj;
        double[] k = new double[n];
        double[] d = new double[n];
        double[] j = new double[n];
        
        for (int i = 8; i < n; i++) {
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (int jIdx = i - 8; jIdx <= i; jIdx++) {
                if (lows[jIdx] < low) low = lows[jIdx];
                if (highs[jIdx] > high) high = highs[jIdx];
            }
            double rsv = (high - low == 0) ? 50 : (closes[i] - low) / (high - low) * 100;
            k[i] = (i == 8 ? 50 : k[i - 1]) * 2/3 + rsv * 1/3;
            d[i] = (i == 8 ? 50 : d[i - 1]) * 2/3 + k[i] * 1/3;
            j[i] = 3 * k[i] - 2 * d[i];
            kdj[i][0] = k[i];
            kdj[i][1] = d[i];
            kdj[i][2] = j[i];
        }
        return kdj;
    }

    private static double[][] computeBollingerBands(double[] prices, int period) {
        int n = prices.length;
        double[][] bands = new double[n][4];
        if (n < period) return bands;
        
        for (int i = period - 1; i < n; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += prices[j];
            }
            double mean = sum / period;
            double sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sumSq += (prices[j] - mean) * (prices[j] - mean);
            }
            double std = Math.sqrt(sumSq / period);
            double lower = mean - 2 * std;
            double upper = mean + 2 * std;
            bands[i][0] = upper;
            bands[i][1] = mean;
            bands[i][2] = (upper - lower == 0 ? 0.5 : (prices[i] - lower) / (upper - lower));
            bands[i][3] = mean == 0 ? 0 : (upper - lower) / mean;
        }
        return bands;
    }

    private static double[] computeATR(double[] highs, double[] lows, double[] closes, int period) {
        int n = closes.length;
        double[] atr = new double[n];
        if (n < period + 1) return atr;
        
        double[] tr = new double[n];
        for (int i = 1; i < n; i++) {
            double hl = highs[i] - lows[i];
            double hc = Math.abs(highs[i] - closes[i - 1]);
            double lc = Math.abs(lows[i] - closes[i - 1]);
            tr[i] = Math.max(Math.max(hl, hc), lc);
        }
        
        for (int i = 1; i <= period; i++) {
            atr[period] += tr[i];
        }
        atr[period] /= period;
        
        for (int i = period + 1; i < n; i++) {
            atr[i] = (atr[i - 1] * (period - 1) + tr[i]) / period;
        }
        return atr;
    }

    private static double[] computeROC(double[] prices, int period) {
        int n = prices.length;
        double[] roc = new double[n];
        for (int i = period; i < n; i++) {
            roc[i] = prices[i - period] == 0 ? 0 : (prices[i] - prices[i - period]) / prices[i - period];
        }
        return roc;
    }

    private static double[] computeWilliamsR(double[] highs, double[] lows, double[] closes, int period) {
        int n = closes.length;
        double[] wr = new double[n];
        for (int i = period - 1; i < n; i++) {
            double high = -Double.MAX_VALUE;
            double low = Double.MAX_VALUE;
            for (int j = i - period + 1; j <= i; j++) {
                if (highs[j] > high) high = highs[j];
                if (lows[j] < low) low = lows[j];
            }
            wr[i] = high - low == 0 ? -50 : -100 * (high - closes[i]) / (high - low);
        }
        return wr;
    }

    private static double[] computeOBVChange(double[] closes, double[] volumes, int period) {
        int n = closes.length;
        double[] obv = new double[n];
        double[] obvChange = new double[n];
        obv[0] = volumes[0];
        for (int i = 1; i < n; i++) {
            if (closes[i] > closes[i - 1]) obv[i] = obv[i - 1] + volumes[i];
            else if (closes[i] < closes[i - 1]) obv[i] = obv[i - 1] - volumes[i];
            else obv[i] = obv[i - 1];
        }
        for (int i = period; i < n; i++) {
            obvChange[i] = obv[i - period] == 0 ? 0 : (obv[i] - obv[i - period]) / obv[i - period];
        }
        return obvChange;
    }

    private static double[] computeVolumeRatio(double[] volumes, int period) {
        int n = volumes.length;
        double[] ratio = new double[n];
        for (int i = period - 1; i < n; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += volumes[j];
            }
            double mean = sum / period;
            ratio[i] = mean == 0 ? 1 : volumes[i] / mean;
        }
        return ratio;
    }

    private static double[] computeVolatility(double[] prices, int period) {
        int n = prices.length;
        double[] vol = new double[n];
        for (int i = period; i < n; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double ret = prices[j - 1] == 0 ? 0 : (prices[j] - prices[j - 1]) / prices[j - 1];
                sum += ret * ret;
            }
            vol[i] = Math.sqrt(sum / period);
        }
        return vol;
    }
}

