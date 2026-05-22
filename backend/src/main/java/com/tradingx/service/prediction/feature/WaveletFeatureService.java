
package com.tradingx.service.prediction.feature;

import com.tradingx.service.wavelet.WaveletDenoiser;

public class WaveletFeatureService {
    
    private static final int WAVELET_LEVELS = 3;
    private static final int COEFF_LENGTH = 32;
    
    public static double[][] extractWaveletFeatures(double[] prices) {
        int n = prices.length;
        double[][] features = new double[n][WAVELET_LEVELS + 1];
        
        WaveletDenoiser wavelet = new WaveletDenoiser();
        
        for (int i = COEFF_LENGTH - 1; i < n; i++) {
            double[] window = new double[COEFF_LENGTH];
            for (int j = 0; j < COEFF_LENGTH; j++) {
                window[j] = prices[i - COEFF_LENGTH + 1 + j];
            }
            
            double[][] coeffs = wavelet.getMultiLevelCoeffs(window, WAVELET_LEVELS);
            
            for (int level = 0; level <= WAVELET_LEVELS; level++) {
                if (level < coeffs.length) {
                    double[] levelCoeffs = coeffs[level];
                    double energy = 0;
                    double mean = 0;
                    double std = 0;
                    
                    for (double c : levelCoeffs) {
                        energy += c * c;
                        mean += c;
                    }
                    mean /= levelCoeffs.length;
                    
                    for (double c : levelCoeffs) {
                        std += (c - mean) * (c - mean);
                    }
                    std = Math.sqrt(std / levelCoeffs.length);
                    
                    features[i][level] = std;
                }
            }
        }
        
        return features;
    }
}

