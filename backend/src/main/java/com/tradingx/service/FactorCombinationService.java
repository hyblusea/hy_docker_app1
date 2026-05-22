package com.tradingx.service;

import com.tradingx.model.FactorCombinationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FactorCombinationService {

    private static final Logger log = LoggerFactory.getLogger(FactorCombinationService.class);

    public FactorCombinationResult combineFactors(
            Map<String, Map<String, Map<String, Double>>> allFactorValues,
            List<String> factorNames,
            int forwardDays) {

        Set<String> allDates = new TreeSet<>();
        for (Map<String, Map<String, Double>> stockData : allFactorValues.values()) {
            allDates.addAll(stockData.keySet());
        }
        List<String> sortedDates = new ArrayList<>(allDates);

        log.info("Fama-MacBeth回归开始: factors={}, dates={}, stocks={}",
                factorNames.size(), sortedDates.size(), allFactorValues.size());

        Map<String, Map<String, Double>> forwardReturnCache = precomputeForwardReturns(
                allFactorValues, sortedDates, forwardDays);

        List<double[]> periodCoefficients = new ArrayList<>();
        List<Double> periodRSquared = new ArrayList<>();
        int totalSampleSize = 0;

        Map<String, Double> globalFactorMeans = new LinkedHashMap<>();
        Map<String, Double> globalFactorStds = new LinkedHashMap<>();
        Map<String, List<Double>> globalFactorValues = new LinkedHashMap<>();
        for (String fn : factorNames) {
            globalFactorValues.put(fn, new ArrayList<>());
        }

        for (String currentDate : sortedDates) {
            List<Map<String, Double>> rows = new ArrayList<>();
            List<Double> returns = new ArrayList<>();

            for (Map.Entry<String, Map<String, Map<String, Double>>> entry : allFactorValues.entrySet()) {
                String tsCode = entry.getKey();
                Map<String, Map<String, Double>> stockData = entry.getValue();
                Map<String, Double> dateData = stockData.get(currentDate);
                if (dateData == null) continue;

                Double forwardReturn = forwardReturnCache.get(tsCode) != null
                        ? forwardReturnCache.get(tsCode).get(currentDate) : null;
                if (!isValidDouble(forwardReturn)) continue;

                Map<String, Double> row = new LinkedHashMap<>();
                boolean allPresent = true;
                for (String fn : factorNames) {
                    Double val = dateData.get(fn);
                    if (!isValidDouble(val)) {
                        allPresent = false;
                        break;
                    }
                    row.put(fn, val);
                    globalFactorValues.get(fn).add(val);
                }
                if (!allPresent) continue;

                rows.add(row);
                returns.add(forwardReturn);
            }

            if (rows.size() < factorNames.size() + 2) continue;

            Map<String, Double> means = new LinkedHashMap<>();
            Map<String, Double> stds = new LinkedHashMap<>();
            for (String fn : factorNames) {
                double[] vals = rows.stream().mapToDouble(r -> r.get(fn)).toArray();
                double mean = Arrays.stream(vals).average().orElse(0);
                double std = calcStd(vals, mean);
                means.put(fn, mean);
                stds.put(fn, std);
            }

            int n = rows.size();
            int k = factorNames.size();
            double[][] X = new double[n][k + 1];
            double[] y = new double[n];

            for (int i = 0; i < n; i++) {
                X[i][0] = 1.0;
                for (int j = 0; j < k; j++) {
                    String fn = factorNames.get(j);
                    double std = stds.get(fn);
                    X[i][j + 1] = std > 1e-10 ? (rows.get(i).get(fn) - means.get(fn)) / std : 0;
                }
                y[i] = returns.get(i);
            }

            OLSResult ols = olsRegression(X, y);
            if (ols == null) continue;

            periodCoefficients.add(ols.coefficients);
            periodRSquared.add(ols.rSquared);
            totalSampleSize += n;
        }

        if (periodCoefficients.isEmpty()) {
            throw new RuntimeException("Fama-MacBeth回归失败: 没有有效的截面数据");
        }

        int T = periodCoefficients.size();
        int k = factorNames.size();
        double[] avgCoeffs = new double[k + 1];
        double[] stdCoeffs = new double[k + 1];

        for (int j = 0; j <= k; j++) {
            double[] series = new double[T];
            for (int t = 0; t < T; t++) {
                series[t] = periodCoefficients.get(t)[j];
            }
            avgCoeffs[j] = Arrays.stream(series).average().orElse(0);
            stdCoeffs[j] = calcStd(series, avgCoeffs[j]);
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        Map<String, Double> tStats = new LinkedHashMap<>();
        Map<String, Double> pValues = new LinkedHashMap<>();

        for (int j = 0; j < k; j++) {
            String fn = factorNames.get(j);
            weights.put(fn, avgCoeffs[j + 1]);
            double se = stdCoeffs[j + 1] / Math.sqrt(T);
            double tStat = se > 1e-10 ? avgCoeffs[j + 1] / se : 0;
            tStats.put(fn, tStat);
            pValues.put(fn, tDistPValue(Math.abs(tStat), T - 1));
        }

        for (String fn : factorNames) {
            List<Double> vals = globalFactorValues.get(fn);
            if (!vals.isEmpty()) {
                double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double std = calcStd(vals.stream().mapToDouble(Double::doubleValue).toArray(), mean);
                globalFactorMeans.put(fn, mean);
                globalFactorStds.put(fn, std);
            }
        }

        double avgRSquared = periodRSquared.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double adjRSquared = 1 - (1 - avgRSquared) * ((double) (totalSampleSize / T - 1) / (totalSampleSize / T - k - 1));

        double entryThreshold = 0.5;
        double exitThreshold = -0.5;

        FactorCombinationResult result = new FactorCombinationResult();
        result.setFactorNames(factorNames);
        result.setWeights(weights);
        result.setIntercept(avgCoeffs[0]);
        result.setRSquared(avgRSquared);
        result.setAdjRSquared(adjRSquared);
        result.setTStats(tStats);
        result.setPValues(pValues);
        result.setSamplePeriods(T);
        result.setAvgSampleSize(T > 0 ? totalSampleSize / T : 0);
        result.setFactorMeans(globalFactorMeans);
        result.setFactorStds(globalFactorStds);
        result.setEntryThreshold(entryThreshold);
        result.setExitThreshold(exitThreshold);

        log.info("Fama-MacBeth回归完成: periods={}, avgR²={}, weights={}", T, String.format("%.4f", avgRSquared), weights);

        return result;
    }

    private Map<String, Map<String, Double>> precomputeForwardReturns(
            Map<String, Map<String, Map<String, Double>>> allFactorValues,
            List<String> sortedDates,
            int forwardDays) {

        Map<String, Map<String, Double>> cache = new HashMap<>();

        for (Map.Entry<String, Map<String, Map<String, Double>>> entry : allFactorValues.entrySet()) {
            String tsCode = entry.getKey();
            Map<String, Map<String, Double>> stockData = entry.getValue();

            List<String> stockDates = new ArrayList<>(stockData.keySet());
            Collections.sort(stockDates);

            Map<String, Double> dateReturns = new HashMap<>();

            for (int i = 0; i < stockDates.size(); i++) {
                String currentDate = stockDates.get(i);
                if (i + forwardDays >= stockDates.size()) continue;

                double totalReturn = 0;
                boolean valid = true;
                for (int j = 1; j <= forwardDays; j++) {
                    String futureDate = stockDates.get(i + j);
                    Map<String, Double> futureData = stockData.get(futureDate);
                    if (futureData == null) { valid = false; break; }
                    Double pctChg = futureData.get("pct_chg");
                    if (!isValidDouble(pctChg)) { valid = false; break; }
                    totalReturn += pctChg;
                }

                if (valid) {
                    dateReturns.put(currentDate, totalReturn);
                }
            }

            cache.put(tsCode, dateReturns);
        }

        return cache;
    }

    OLSResult olsRegression(double[][] X, double[] y) {
        int n = X.length;
        int p = X[0].length;

        if (n <= p) return null;

        double[][] XtX = new double[p][p];
        double[] Xty = new double[p];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                for (int l = 0; l < p; l++) {
                    XtX[j][l] += X[i][j] * X[i][l];
                }
                Xty[j] += X[i][j] * y[i];
            }
        }

        double[] beta = solveLinearSystem(XtX, Xty);
        if (beta == null) return null;

        double yMean = Arrays.stream(y).average().orElse(0);
        double ssTot = 0;
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double predicted = 0;
            for (int j = 0; j < p; j++) {
                predicted += beta[j] * X[i][j];
            }
            ssRes += (y[i] - predicted) * (y[i] - predicted);
            ssTot += (y[i] - yMean) * (y[i] - yMean);
        }

        double rSquared = ssTot > 0 ? 1 - ssRes / ssTot : 0;

        OLSResult result = new OLSResult();
        result.coefficients = beta;
        result.rSquared = rSquared;
        result.residualSumSquares = ssRes;
        return result;
    }

    double[] solveLinearSystem(double[][] A, double[] b) {
        int n = A.length;
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) {
                    maxRow = row;
                }
            }

            double[] temp = aug[col];
            aug[col] = aug[maxRow];
            aug[maxRow] = temp;

            if (Math.abs(aug[col][col]) < 1e-12) return null;

            for (int row = col + 1; row < n; row++) {
                double factor = aug[row][col] / aug[col][col];
                for (int j = col; j <= n; j++) {
                    aug[row][j] -= factor * aug[col][j];
                }
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = aug[i][n];
            for (int j = i + 1; j < n; j++) {
                x[i] -= aug[i][j] * x[j];
            }
            x[i] /= aug[i][i];
        }

        return x;
    }

    private double calcStd(double[] values, double mean) {
        if (values.length < 2) return 0;
        double variance = 0;
        for (double v : values) {
            variance += (v - mean) * (v - mean);
        }
        variance /= (values.length - 1);
        return Math.sqrt(variance);
    }

    private double tDistPValue(double tStat, double df) {
        double x = df / (df + tStat * tStat);
        return incompleteBeta(df / 2.0, 0.5, x);
    }

    private double incompleteBeta(double a, double b, double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        if (x < (a + 1) / (a + b + 2)) {
            return Math.pow(x, a) * Math.pow(1 - x, b) / (a * betaFunc(a, b))
                    * continuedFraction(a, b, x);
        } else {
            return 1 - incompleteBeta(b, a, 1 - x);
        }
    }

    private double continuedFraction(double a, double b, double x) {
        double am = 1.0;
        double bm = 1.0;
        double az = 1.0;
        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;
        double bz = 1.0 - qab * x / qap;

        for (int m = 1; m <= 100; m++) {
            double em = m;
            double tem = em + em;
            double d = em * (b - m) * x / ((qam + tem) * (a + tem));
            double ap = az + d * am;
            double bp = bz + d * bm;
            d = -(a + em) * (qab + em) * x / ((a + tem) * (qap + tem));
            double app = ap + d * az;
            double bpp = bp + d * bz;
            double aold = az;
            am = ap / bpp;
            bm = bp / bpp;
            az = app / bpp;
            bz = 1.0;
            if (Math.abs(az - aold) < 1e-10 * Math.abs(az)) break;
        }

        return az;
    }

    private double betaFunc(double a, double b) {
        return Math.exp(logGamma(a) + logGamma(b) - logGamma(a + b));
    }

    private double logGamma(double x) {
        double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double ci : c) {
            y += 1.0;
            ser += ci / y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    private static boolean isValidDouble(Double value) {
        return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
    }

    static class OLSResult {
        double[] coefficients;
        double rSquared;
        double residualSumSquares;
    }
}
