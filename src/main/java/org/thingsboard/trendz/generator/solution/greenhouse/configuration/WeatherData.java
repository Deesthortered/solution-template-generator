package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData implements Comparable<WeatherData> {

    private long ts;

    private double pressure;
    private double temperatureFahrenheit;
    private double temperatureCelsius;
    private double dewPointFahrenheit;
    private double dewPointCelsius;
    private double humidity;
    private double windSpeed;
    private double windGust;
    private double windDirectionDegrees;
    private String windDirectionWords;
    private String condition;

    @Override
    public int compareTo(WeatherData that) {
        return Long.compare(this.ts, that.ts);
    }

    @Override
    public String toString() {
        return String.format(
                "WeatherData[ Time = %s, Temperature = %s °F / %.2f °C, Dew Point = %s °F / %.2f °C, Humidity = %s %%, Wind = %s (%s), Wind Speed = %s mph, Wind Gust = %s mph, Pressure = %s, Condition = %s]",
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(this.ts), ZoneId.of("UTC")),
                this.temperatureFahrenheit,
                this.temperatureCelsius,
                this.dewPointFahrenheit,
                this.dewPointCelsius,
                this.humidity,
                this.windDirectionWords,
                this.windDirectionDegrees,
                this.windSpeed,
                this.windGust,
                this.pressure,
                this.condition
        );
    }
}
