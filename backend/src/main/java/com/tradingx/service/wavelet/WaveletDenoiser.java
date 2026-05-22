package com.tradingx.service.wavelet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WaveletDenoiser {

    private static final double[] LO_D = {
             0.2303778133088964,
             0.7148465705529154,
             0.6308807679298587,
            -0.0279837694168599,
            -0.1870348117190931,
             0.0308413818355607,
             0.0328830116668852,
            -0.0105974017850690
    };

    private static final int FILTER_LEN = LO_D.length;

    private static final double[] HI_D;
    private static final double[] LO_R;
    private static final double[] HI_R;

    static {
        HI_D = new double[FILTER_LEN];
        LO_R = new double[FILTER_LEN];
        HI_R = new double[FILTER_LEN];

        for (int k = 0; k < FILTER_LEN; k++) {
            HI_D[k] = (k % 2 == 0 ? 1.0 : -1.0) * LO_D[FILTER_LEN - 1 - k];
            LO_R[k] = LO_D[FILTER_LEN - 1 - k];
            HI_R[k] = (k % 2 == 0 ? 1.0 : -1.0) * LO_D[k];
        }
    }

    public Map<String, Double> denoiseFactorMap(Map<String, Double> dateValueMap) {
        return denoiseFactorMap(dateValueMap, 1.0, true);
    }

    public Map<String, Double> denoiseFactorMap(Map<String, Double> dateValueMap,
                                                 double thresholdMult,
                                                 boolean useSoft) {
        if (dateValueMap == null || dateValueMap.isEmpty()) {
            return dateValueMap;
        }

        List<String> dates = new ArrayList<>(dateValueMap.keySet());
        int n = dates.size();
        double[] signal = new double[n];
        for (int i = 0; i < n; i++) {
            Double v = dateValueMap.get(dates.get(i));
            signal[i] = (v != null && !Double.isNaN(v) && !Double.isInfinite(v)) ? v : 0.0;
        }

        double[] denoised = denoiseSignal(signal, thresholdMult, useSoft);

        Map<String, Double> result = new LinkedHashMap<>(n);
        for (int i = 0; i < n; i++) {
            result.put(dates.get(i), denoised[i]);
        }
        return result;
    }

    public double[] denoiseSignal(double[] signal, double thresholdMult, boolean useSoft) {
        if (signal == null || signal.length == 0) {
            return signal;
        }
        int n = signal.length;
        if (n < FILTER_LEN * 2) {
            return signal.clone();
        }

        int maxLevel = calcMaxLevel(n);
        if (maxLevel < 1) {
            return signal.clone();
        }

        int extension = FILTER_LEN - 1;
        double[] extended = symmetricExtend(signal, extension);

        List<double[]> details = new ArrayList<>();
        double[] approx = extended;

        for (int level = 0; level < maxLevel; level++) {
            if (approx.length < FILTER_LEN * 2) {
                break;
            }
            double[][] result = dwtDecompose(approx);
            details.add(result[1]);
            approx = result[0];
        }

        double[] finestDetail = details.get(0);
        double noiseStd = estimateNoiseStd(finestDetail);
        double baseThreshold = noiseStd * Math.sqrt(2.0 * Math.log(n)) * thresholdMult;

        for (int level = 0; level < details.size(); level++) {
            double levelThreshold = baseThreshold / Math.pow(2.0, level * 0.5);
            double[] d = details.get(level);
            if (useSoft) {
                softThreshold(d, levelThreshold);
            } else {
                hardThreshold(d, levelThreshold);
            }
            details.set(level, d);
        }

        for (int level = details.size() - 1; level >= 0; level--) {
            approx = idwtReconstruct(approx, details.get(level));
        }

        double[] result = new double[n];
        System.arraycopy(approx, extension, result, 0, n);
        return result;
    }

    public double[] denoiseSignal(double[] signal) {
        return denoiseSignal(signal, 1.0, true);
    }

    private double[][] dwtDecompose(double[] signal) {
        int n = signal.length;
        int outLen = n / 2;

        double[] approx = convAndDownsample(signal, LO_D, outLen);
        double[] detail = convAndDownsample(signal, HI_D, outLen);

        return new double[][]{approx, detail};
    }

    private double[] convAndDownsample(double[] signal, double[] filter, int outLen) {
        int n = signal.length;
        int fl = filter.length;
        double[] output = new double[outLen];

        for (int m = 0; m < outLen; m++) {
            double sum = 0.0;
            int center = 2 * m + 1;
            for (int j = 0; j < fl; j++) {
                int idx = center - j;
                if (idx < 0) idx = -idx;
                if (idx >= n) idx = 2 * n - 2 - idx;
                if (idx < 0) idx = 0;
                if (idx >= n) idx = n - 1;

                sum += filter[j] * signal[idx];
            }
            output[m] = sum;
        }
        return output;
    }

    private double[] idwtReconstruct(double[] approx, double[] detail) {
        int coeffLen = approx.length;

        double[] upsampledApprox = upsample(approx);
        double[] upsampledDetail = upsample(detail);

        double[] filteredApprox = fullConvolve(upsampledApprox, LO_R);
        double[] filteredDetail = fullConvolve(upsampledDetail, HI_R);

        int len = Math.min(filteredApprox.length, filteredDetail.length);
        double[] result = new double[len];
        for (int i = 0; i < len; i++) {
            result[i] = filteredApprox[i] + filteredDetail[i];
        }
        return result;
    }

    private double[] upsample(double[] signal) {
        int n = signal.length;
        double[] up = new double[2 * n];
        for (int i = 0; i < n; i++) {
            up[2 * i] = signal[i];
        }
        return up;
    }

    private double[] fullConvolve(double[] signal, double[] filter) {
        int n = signal.length;
        int fl = filter.length;
        int outLen = n + fl - 1;
        double[] output = new double[outLen];

        for (int k = 0; k < outLen; k++) {
            double sum = 0.0;
            for (int j = 0; j < fl; j++) {
                int idx = k - j;
                if (idx >= 0 && idx < n) {
                    sum += filter[j] * signal[idx];
                }
            }
            output[k] = sum;
        }
        return output;
    }

    private void softThreshold(double[] coefficients, double threshold) {
        for (int i = 0; i < coefficients.length; i++) {
            double v = coefficients[i];
            if (v > threshold) {
                coefficients[i] = v - threshold;
            } else if (v < -threshold) {
                coefficients[i] = v + threshold;
            } else {
                coefficients[i] = 0.0;
            }
        }
    }

    private void hardThreshold(double[] coefficients, double threshold) {
        for (int i = 0; i < coefficients.length; i++) {
            if (Math.abs(coefficients[i]) <= threshold) {
                coefficients[i] = 0.0;
            }
        }
    }

    private double estimateNoiseStd(double[] detailCoefficients) {
        double[] absValues = new double[detailCoefficients.length];
        for (int i = 0; i < detailCoefficients.length; i++) {
            absValues[i] = Math.abs(detailCoefficients[i]);
        }
        double mad = median(absValues);
        return mad / 0.6745;
    }

    private double[] symmetricExtend(double[] signal, int extension) {
        int n = signal.length;
        double[] extended = new double[n + 2 * extension];

        System.arraycopy(signal, 0, extended, extension, n);

        for (int i = 0; i < extension; i++) {
            extended[i] = signal[extension - i];
        }

        for (int i = 0; i < extension; i++) {
            extended[n + extension + i] = signal[n - 2 - i];
        }

        return extended;
    }

    private int calcMaxLevel(int signalLength) {
        if (signalLength < FILTER_LEN * 2) {
            return 0;
        }
        double ratio = (double) signalLength / (FILTER_LEN * 2);
        int maxLevel = (int) Math.floor(Math.log(Math.max(ratio, 1)) / Math.log(2));
        return Math.max(1, Math.min(maxLevel, 6));
    }

    private double median(double[] values) {
        int n = values.length;
        if (n == 0) return 0.0;

        double[] copy = new double[n];
        System.arraycopy(values, 0, copy, 0, n);
        java.util.Arrays.sort(copy);

        if (n % 2 == 0) {
            return (copy[n / 2 - 1] + copy[n / 2]) / 2.0;
        } else {
            return copy[n / 2];
        }
    }

    public double[] getLowPassDecompositionFilter() {
        return LO_D.clone();
    }

    public double[] getHighPassDecompositionFilter() {
        return HI_D.clone();
    }

    public double[] getLowPassReconstructionFilter() {
        return LO_R.clone();
    }

    public double[] getHighPassReconstructionFilter() {
        return HI_R.clone();
    }

    public boolean verifyFilters() {
        double sumLo = 0, sumHi = 0;
        for (int i = 0; i < FILTER_LEN; i++) {
            sumLo += LO_D[i];
            sumHi += HI_D[i];
        }
        boolean sumCheck = Math.abs(sumLo - Math.sqrt(2.0)) < 1e-10
                        && Math.abs(sumHi) < 1e-10;

        double energy = 0;
        for (double v : LO_D) energy += v * v;
        boolean energyCheck = Math.abs(energy - 1.0) < 1e-10;

        boolean orthogCheck = true;
        for (int m = 1; m < FILTER_LEN / 2; m++) {
            double dot = 0;
            for (int k = 0; k < FILTER_LEN; k++) {
                int idx = k - 2 * m;
                if (idx >= 0 && idx < FILTER_LEN) {
                    dot += LO_D[k] * LO_D[idx];
                }
            }
            if (Math.abs(dot) > 1e-10) {
                orthogCheck = false;
                break;
            }
        }

        boolean allPass = sumCheck && energyCheck && orthogCheck;
        if (!allPass) {
            System.err.println("db4 滤波器验证失败: sum=" + sumCheck
                    + ", energy=" + energyCheck + ", orthog=" + orthogCheck);
        }
        return allPass;
    }

    public double[][] getMultiLevelCoeffs(double[] signal, int levels) {
        if (signal == null || signal.length < FILTER_LEN * 2) {
            return new double[][]{signal.clone()};
        }

        int n = signal.length;
        List<double[]> coeffsList = new ArrayList<>();
        int extension = FILTER_LEN - 1;
        double[] extended = symmetricExtend(signal, extension);
        double[] approx = extended;

        for (int level = 0; level < levels; level++) {
            if (approx.length < FILTER_LEN * 2) {
                break;
            }
            double[][] result = dwtDecompose(approx);
            coeffsList.add(interpolateToLength(result[1], n));
            approx = result[0];
        }

        coeffsList.add(interpolateToLength(approx, n));

        double[][] result = new double[coeffsList.size()][];
        for (int i = 0; i < coeffsList.size(); i++) {
            result[i] = coeffsList.get(coeffsList.size() - 1 - i);
        }

        return result;
    }

    private double[] interpolateToLength(double[] coeffs, int targetLength) {
        if (coeffs.length == targetLength) {
            return coeffs.clone();
        }

        double[] result = new double[targetLength];
        double scale = (double) coeffs.length / targetLength;

        for (int i = 0; i < targetLength; i++) {
            double pos = i * scale;
            int idx1 = (int) pos;
            double frac = pos - idx1;
            int idx2 = Math.min(idx1 + 1, coeffs.length - 1);
            idx1 = Math.max(0, idx1);
            result[i] = coeffs[idx1] * (1 - frac) + coeffs[idx2] * frac;
        }

        return result;
    }
}
