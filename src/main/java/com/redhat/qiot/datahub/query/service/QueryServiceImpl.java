package com.redhat.qiot.datahub.query.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;

import com.redhat.qiot.datahub.query.domain.MeasurementDataSet;
import com.redhat.qiot.datahub.query.domain.MeasurementType;
import com.redhat.qiot.datahub.query.domain.measurement.Measurement;
import com.redhat.qiot.datahub.query.domain.measurement.MeasurementHistory;
import com.redhat.qiot.datahub.query.domain.measurement.MeasurementHistoryType;
import com.redhat.qiot.datahub.query.domain.measurement.MeasurementId;
import com.redhat.qiot.datahub.query.domain.station.MeasurementStation;
import com.redhat.qiot.datahub.query.domain.station.OtherMeasurementStation;
import com.redhat.qiot.datahub.query.persistence.QIoTRepository;

@ApplicationScoped
public class QueryServiceImpl implements QueryService {
    /**
     * Logger for this class
     */
    @Inject
    Logger LOGGER;

    @Inject
    @RestClient
    RegistrationServiceClient registrationServiceClient;

    @Inject
    QIoTRepository qIoTRepository;

    @Override
    public Map<MeasurementType, MeasurementDataSet> getSnapshot(int stationId) {
        Map<MeasurementType, MeasurementDataSet> dataSets = new HashMap<>();
        for (MeasurementType specie : MeasurementType.values())
            dataSets.put(specie, getDataset(stationId, specie));
        return dataSets;
    }

    private MeasurementDataSet getDataset(int stationId,
            MeasurementType specie) {
        MeasurementDataSet dataSet = new MeasurementDataSet();
        MeasurementHistoryType historySpecie = specie.getHistorySpecie();
        // measurement station
        MeasurementStation ms = qIoTRepository
                .queryMeasurementStation(stationId);
        // third party measurement station
        OtherMeasurementStation oms = qIoTRepository
                .getClosestStation(ms.location);
        // third party historical data
        if (historySpecie != null) {
            getSixMonthsHistory(stationId, specie, oms);
            getOneYearHistory(stationId, specie, oms);
        }
        // own historical date
        dataSet.last = qIoTRepository.getLastMeasurement(stationId, specie);
        dataSet.lastHourByMinute = qIoTRepository.getLastHourByMinute(stationId,
                specie);
        dataSet.lastDayByHour = qIoTRepository.getLastDayByHour(stationId,
                specie);
        dataSet.lastMonthByDay = qIoTRepository.getLastMonthByDay(stationId,
                specie);
        dataSet.allMonths = qIoTRepository.getAllMonths(stationId, specie);
        return dataSet;
    }

    private Measurement getSixMonthsHistory(int stationId,
            MeasurementType specie, OtherMeasurementStation oms) {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minus(6,
                ChronoUnit.MONTHS);
        MeasurementHistory h = qIoTRepository.getSixMonthsAgo(oms.country,
                oms.city, specie.getHistorySpecie());

        return historyToMeasurement(stationId, specie, utc, h);
    }

    private Measurement getOneYearHistory(int stationId, MeasurementType specie,
            OtherMeasurementStation oms) {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minus(1,
                ChronoUnit.YEARS);
        MeasurementHistory h = qIoTRepository.getOneYearAgo(oms.country,
                oms.city, specie.getHistorySpecie());

        return historyToMeasurement(stationId, specie, utc, h);

    }

    private Measurement historyToMeasurement(int stationId,
            MeasurementType specie, OffsetDateTime utc, MeasurementHistory h) {
        MeasurementId mId = new MeasurementId();
        Measurement m = new Measurement();

        mId.stationId = stationId;
        mId.year = utc.getYear();
        mId.month = utc.getMonthValue();
        mId.specie = specie.toString();
        m.id = mId;

        m.avg = h.median;
        m.min = h.min;
        m.max = h.max;
        m.count = h.count;

        return m;
    }

}