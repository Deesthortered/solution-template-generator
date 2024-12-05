package org.thingsboard.trendz.generator.model.tb;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.thingsboard.trendz.generator.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Getter
@ToString
@EqualsAndHashCode
public class Telemetry<T> {

    @Data
    @AllArgsConstructor
    public static class Point<T> implements Comparable<Point<T>> {

        private final Timestamp ts;
        private final T value;

        public Point(Point<T> that) {
            this.ts = that.ts;
            this.value = that.value;
        }

        @Override
        public int compareTo(Point point) {
            return this.ts.compareTo(point.ts);
        }
    }

    private final String name;
    private final Set<Point<T>> points;


    public Telemetry(Telemetry<T> that) {
        this.name = that.name;
        this.points = that.points == null
                ? null
                : that.points.stream()
                .map(Point::new)
                .collect(Collectors.toSet());
    }

    public Telemetry(String name) {
        this.name = name;
        this.points = new TreeSet<>();
    }

    public Telemetry(String name, Set<Point<T>> points) {
        this.name = name;
        this.points = new TreeSet<>(points);
    }


    public void add(Point<T> point) {
        this.points.add(point);
    }

    public void add(long ts, T value) {
        this.points.add(new Point<T>(Timestamp.of(ts), value));
    }

    public List<Telemetry<T>> partition(int size) {
        return Lists.partition(new ArrayList<>(this.points), size)
                .stream()
                .map(part -> new Telemetry<>(this.name, new TreeSet<>(part)))
                .collect(Collectors.toList());
    }

    public String toJson() {
        ArrayNode nodePointList = JsonUtils.getObjectMapper().createArrayNode();
        for (Point<T> point : this.points) {
            ObjectNode valueNode = JsonUtils.getObjectMapper().createObjectNode();
            if (point.getValue() instanceof Number) {
                valueNode.put(this.name, Double.parseDouble(point.getValue().toString()));
            } else {
                valueNode.put(this.name, point.getValue().toString());
            }

            ObjectNode nodePoint = JsonUtils.getObjectMapper().createObjectNode();
            nodePoint.put("ts", point.getTs().get());
            nodePoint.set("values", valueNode);
            nodePointList.add(nodePoint);
        }

        return nodePointList.toString();
    }
}
