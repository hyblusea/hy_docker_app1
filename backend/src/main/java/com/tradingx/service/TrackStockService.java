package com.tradingx.service;

import com.tradingx.model.KlineDailyEntity;
import com.tradingx.model.TrackStockEntity;
import com.tradingx.model.TrackStockVO;
import com.tradingx.repository.KlineDailyRepository;
import com.tradingx.repository.TrackStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TrackStockService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TrackStockRepository trackStockRepository;
    private final KlineDailyRepository klineDailyRepository;

    public TrackStockService(TrackStockRepository trackStockRepository,
                             KlineDailyRepository klineDailyRepository) {
        this.trackStockRepository = trackStockRepository;
        this.klineDailyRepository = klineDailyRepository;
    }

    public boolean addTrack(String username, String tsCode, String stockName,
                            Long strategyId, String strategyName) {
        if (trackStockRepository.existsByUsernameAndTsCodeAndStrategyId(username, tsCode, strategyId)) {
            return false;
        }

        String today = LocalDate.now().format(DATE_FMT);

        TrackStockEntity entity = new TrackStockEntity();
        entity.setUsername(username);
        entity.setTsCode(tsCode);
        entity.setStockName(stockName);
        entity.setStrategyId(strategyId);
        entity.setStrategyName(strategyName);
        entity.setAddDate(today);
        trackStockRepository.save(entity);
        return true;
    }

    public List<TrackStockVO> listTracksWithChange(String username) {
        List<TrackStockEntity> tracks = trackStockRepository.findByUsernameOrderByCreatedAtDesc(username);
        return tracks.stream().map(entity -> {
            Double addPrice = getPriceByDate(entity.getTsCode(), entity.getAddDate());
            Double[] latestData = getLatestPriceWithPrev(entity.getTsCode());
            Double currentPrice = latestData[0];
            Double prevClose = latestData[1];
            return TrackStockVO.fromEntity(entity, addPrice, currentPrice, prevClose);
        }).toList();
    }

    private Double getPriceByDate(String tsCode, String tradeDate) {
        if (tradeDate == null || tradeDate.isBlank()) return null;
        List<KlineDailyEntity> records = klineDailyRepository
                .findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(tsCode, tradeDate, tradeDate);
        if (!records.isEmpty()) {
            return records.get(records.size() - 1).getClose().doubleValue();
        }
        return null;
    }

    private Double[] getLatestPriceWithPrev(String tsCode) {
        Double[] result = new Double[]{null, null};
        String lastDate = klineDailyRepository.findLastSyncDate(tsCode);
        if (lastDate != null && !lastDate.isBlank()) {
            List<KlineDailyEntity> recent = klineDailyRepository
                    .findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(tsCode, lastDate, lastDate);
            if (!recent.isEmpty()) {
                KlineDailyEntity latest = recent.get(recent.size() - 1);
                result[0] = latest.getClose().doubleValue();
                result[1] = latest.getPreClose() != null ? latest.getPreClose().doubleValue() : null;
                return result;
            }
        }

        List<KlineDailyEntity> all = klineDailyRepository.findByTsCodeOrderByTradeDateAsc(tsCode);
        if (!all.isEmpty()) {
            KlineDailyEntity latest = all.get(all.size() - 1);
            result[0] = latest.getClose().doubleValue();
            result[1] = latest.getPreClose() != null ? latest.getPreClose().doubleValue() : null;
        }

        return result;
    }

    @Transactional
    public boolean removeTrack(Long id, String username) {
        trackStockRepository.deleteByIdAndUsername(id, username);
        return true;
    }
}
