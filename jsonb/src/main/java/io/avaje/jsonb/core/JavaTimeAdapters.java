package io.avaje.jsonb.core;

import io.avaje.jsonb.JsonAdapter;
import io.avaje.jsonb.JsonReader;
import io.avaje.jsonb.JsonWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Adds support for java time types. */
final class JavaTimeAdapters {

  static final JsonAdapter.Factory FACTORY =
      (type, jsonb) -> {
        if (type == Instant.class) return JavaTimeAdapters.INSTANT_ADAPTER.nullSafe();
        if (type == LocalDate.class) return JavaTimeAdapters.LOCAL_DATE_ADAPTER.nullSafe();
        if (type == LocalDateTime.class) return JavaTimeAdapters.LOCAL_DATE_TIME_ADAPTER.nullSafe();
        if (type == LocalTime.class) return JavaTimeAdapters.LOCAL_TIME_ADAPTER.nullSafe();
        if (type == MonthDay.class) return JavaTimeAdapters.MONTH_DAY_ADAPTER.nullSafe();
        if (type == OffsetDateTime.class)
          return JavaTimeAdapters.OFFSET_DATE_TIME_ADAPTER.nullSafe();
        if (type == OffsetTime.class) return JavaTimeAdapters.OFFSET_TIME_ADAPTER.nullSafe();
        if (type == Period.class) return JavaTimeAdapters.PERIOD_ADAPTER.nullSafe();
        if (type == Year.class) return JavaTimeAdapters.YEAR_ADAPTER.nullSafe();
        if (type == YearMonth.class) return JavaTimeAdapters.YEAR_MONTH_ADAPTER.nullSafe();
        if (type == ZonedDateTime.class) return JavaTimeAdapters.ZONED_DATE_TIME_ADAPTER.nullSafe();
        if (type == ZoneId.class) return JavaTimeAdapters.ZONE_ID_ADAPTER.nullSafe();
        if (type == ZoneOffset.class) return JavaTimeAdapters.ZONE_OFFSET_ADAPTER.nullSafe();
        return null;
      };

  /** Using ISO-8601 */
  private static final JsonAdapter<Instant> INSTANT_ADAPTER =
      new JsonAdapter<Instant>() {
        @Override
        public Instant fromJson(JsonReader reader) {
          return Instant.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, Instant value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(Instant)";
        }
      };

  private static final JsonAdapter<OffsetDateTime> OFFSET_DATE_TIME_ADAPTER =
      new JsonAdapter<OffsetDateTime>() {
        @Override
        public OffsetDateTime fromJson(JsonReader reader) {
          return OffsetDateTime.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, OffsetDateTime value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(OffsetDateTime)";
        }
      };

  private static final JsonAdapter<OffsetTime> OFFSET_TIME_ADAPTER =
      new JsonAdapter<OffsetTime>() {
        @Override
        public OffsetTime fromJson(JsonReader reader) {
          return OffsetTime.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, OffsetTime value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(OffsetTime)";
        }
      };

  private static final JsonAdapter<ZonedDateTime> ZONED_DATE_TIME_ADAPTER =
      new JsonAdapter<ZonedDateTime>() {
        @Override
        public ZonedDateTime fromJson(JsonReader reader) {
          return ZonedDateTime.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, ZonedDateTime value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(ZonedDateTime)";
        }
      };

  private static final JsonAdapter<ZoneOffset> ZONE_OFFSET_ADAPTER =
      new JsonAdapter<ZoneOffset>() {
        @Override
        public ZoneOffset fromJson(JsonReader reader) {
          return ZoneOffset.of(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, ZoneOffset value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(ZoneOffset)";
        }
      };

  private static final JsonAdapter<ZoneId> ZONE_ID_ADAPTER =
      new JsonAdapter<ZoneId>() {
        @Override
        public ZoneId fromJson(JsonReader reader) {
          return ZoneId.of(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, ZoneId value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(ZoneOffset)";
        }
      };

  private static final JsonAdapter<LocalDate> LOCAL_DATE_ADAPTER =
      new JsonAdapter<LocalDate>() {
        @Override
        public LocalDate fromJson(JsonReader reader) {
          return LocalDate.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, LocalDate value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(LocalDate)";
        }
      };

  private static final JsonAdapter<LocalDateTime> LOCAL_DATE_TIME_ADAPTER =
      new JsonAdapter<LocalDateTime>() {
        @Override
        public LocalDateTime fromJson(JsonReader reader) {
          return LocalDateTime.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, LocalDateTime value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(LocalDateTime)";
        }
      };

  private static final JsonAdapter<LocalTime> LOCAL_TIME_ADAPTER =
      new JsonAdapter<LocalTime>() {
        @Override
        public LocalTime fromJson(JsonReader reader) {
          return LocalTime.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, LocalTime value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(LocalTime)";
        }
      };

  private static final JsonAdapter<MonthDay> MONTH_DAY_ADAPTER =
      new JsonAdapter<MonthDay>() {
        @Override
        public MonthDay fromJson(JsonReader reader) {
          return MonthDay.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, MonthDay value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(MonthDay)";
        }
      };

  private static final JsonAdapter<Period> PERIOD_ADAPTER =
      new JsonAdapter<Period>() {
        @Override
        public Period fromJson(JsonReader reader) {
          return Period.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, Period value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(Period)";
        }
      };

  private static final JsonAdapter<Year> YEAR_ADAPTER =
      new JsonAdapter<Year>() {
        @Override
        public Year fromJson(JsonReader reader) {
          return Year.of(reader.readInt());
        }

        @Override
        public void toJson(JsonWriter writer, Year value) {
          writer.value(value.getValue());
        }

        @Override
        public String toString() {
          return "JsonAdapter(Year)";
        }
      };

  private static final JsonAdapter<YearMonth> YEAR_MONTH_ADAPTER =
      new JsonAdapter<YearMonth>() {
        @Override
        public YearMonth fromJson(JsonReader reader) {
          return YearMonth.parse(reader.readString());
        }

        @Override
        public void toJson(JsonWriter writer, YearMonth value) {
          writer.value(value.toString());
        }

        @Override
        public String toString() {
          return "JsonAdapter(YearMonth)";
        }
      };
}
