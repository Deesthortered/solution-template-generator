package org.thingsboard.trendz.generator.service;

import com.github.sh0nk.matplotlib4j.Plot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.trendz.generator.model.Telemetry;
import org.thingsboard.trendz.generator.model.Timestamp;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VisualizationService {

    public void visualize(String title, List<Telemetry<? extends Number>> telemetryList) {
        try {
            Plot plt = Plot.create();
            for (Telemetry<? extends Number> telemetry : telemetryList) {
                List<Long> ts = telemetry.getPoints().stream().map(Telemetry.Point::getTs).map(Timestamp::get).collect(Collectors.toList());
                List<? extends Number> value = telemetry.getPoints().stream().map(Telemetry.Point::getValue).collect(Collectors.toList());

                plt.plot()
                        .add(ts, value)
                        .label(telemetry.getName())
                        .linestyle("--");
            }
            plt.xlabel("Time (ms)");
            plt.ylabel("Value");
            plt.title(title);
            plt.legend();
            plt.show();

        } catch (Exception e) {
            throw new RuntimeException("Error during visualization.", e);
        }
    }
}
