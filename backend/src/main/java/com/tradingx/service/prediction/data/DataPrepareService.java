
package com.tradingx.service.prediction.data;

import com.tradingx.model.KlineDailyEntity;
import com.tradingx.repository.KlineDailyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataPrepareService {

    private final KlineDailyRepository klineDailyRepository;

    public DataPrepareService(KlineDailyRepository klineDailyRepository) {
        this.klineDailyRepository = klineDailyRepository;
    }

    public List<KlineDailyEntity> getRecentKlines(String tsCode, int limit) {
        List<KlineDailyEntity> all = klineDailyRepository.findByTsCodeOrderByTradeDateAsc(tsCode);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(all.size() - limit, all.size());
    }

    public List<KlineDailyEntity> getKlinesByDateRange(String tsCode, String startDate, String endDate) {
        return klineDailyRepository.findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(tsCode, startDate, endDate);
    }

    public List<String> getAllTsCodes() {
        return klineDailyRepository.findAll().stream()
                .map(KlineDailyEntity::getTsCode)
                .distinct()
                .sorted()
                .toList();
    }
}
