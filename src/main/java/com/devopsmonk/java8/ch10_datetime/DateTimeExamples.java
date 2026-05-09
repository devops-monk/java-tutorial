package com.devopsmonk.java8.ch10_datetime;

import com.devopsmonk.java8.model.Employee;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/**
 * Chapter 10 — Date and Time API (JSR-310)
 * Tutorial: https://devops-monk.com/tutorials/java8/date-time-api/
 *
 * Covers:
 *  - LocalDate, LocalTime, LocalDateTime — no timezone
 *  - ZonedDateTime, ZoneId — with timezone
 *  - Instant — machine time (epoch)
 *  - Duration (time-based) and Period (date-based)
 *  - DateTimeFormatter — parsing and formatting
 *  - TemporalAdjusters — next Monday, last day of month, etc.
 *  - Migrating from java.util.Date
 */
public class DateTimeExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 10: Date and Time API ===\n");

        localDateExamples();
        localTimeExamples();
        localDateTimeExamples();
        zonedDateTimeExamples();
        instantExamples();
        durationAndPeriod();
        dateTimeFormatting();
        temporalAdjusters();
        realWorldHRExamples();
        migrationFromLegacyDate();
    }

    // -------------------------------------------------------------------------
    // 1. LocalDate — date only, no time, no timezone
    // -------------------------------------------------------------------------
    static void localDateExamples() {
        System.out.println("--- 1. LocalDate ---");

        LocalDate today     = LocalDate.now();
        LocalDate specific  = LocalDate.of(2024, 3, 15);
        LocalDate parsed    = LocalDate.parse("2024-03-15");          // ISO-8601
        LocalDate fromYear  = LocalDate.ofYearDay(2024, 100);        // 100th day of 2024

        System.out.println("Today:         " + today);
        System.out.println("Specific:      " + specific);
        System.out.println("Parsed:        " + parsed);
        System.out.println("Day 100/2024:  " + fromYear);

        // Arithmetic — LocalDate is immutable, operations return new instances
        LocalDate nextWeek      = today.plusWeeks(1);
        LocalDate lastMonth     = today.minusMonths(1);
        LocalDate nextYear      = today.plusYears(1);
        System.out.println("Next week:     " + nextWeek);
        System.out.println("Last month:    " + lastMonth);

        // Comparison
        System.out.println("Is before?     " + specific.isBefore(today));
        System.out.println("Is after?      " + specific.isAfter(today));
        System.out.println("Is equal?      " + specific.isEqual(LocalDate.of(2024, 3, 15)));

        // Component access
        System.out.println("Year: " + today.getYear() + "  Month: " + today.getMonth()
                + "  Day: " + today.getDayOfMonth()
                + "  DayOfWeek: " + today.getDayOfWeek());
        System.out.println("Is leap year:  " + today.isLeapYear());
        System.out.println("Days in month: " + today.lengthOfMonth());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. LocalTime — time only, no date, no timezone
    // -------------------------------------------------------------------------
    static void localTimeExamples() {
        System.out.println("--- 2. LocalTime ---");

        LocalTime now     = LocalTime.now();
        LocalTime standup = LocalTime.of(9, 30);
        LocalTime noon    = LocalTime.NOON;
        LocalTime midnight= LocalTime.MIDNIGHT;

        System.out.println("Now:      " + now);
        System.out.println("Standup:  " + standup);
        System.out.println("Noon:     " + noon);

        // Is it time for the standup?
        System.out.println("Before standup? " + now.isBefore(standup));

        // Arithmetic
        LocalTime inTwoHours = standup.plusHours(2);
        LocalTime withMinutes= standup.plusMinutes(45);
        System.out.println("Standup + 2h:    " + inTwoHours);
        System.out.println("Standup + 45min: " + withMinutes);

        // Truncation — useful for time-window comparisons
        LocalTime truncated = now.truncatedTo(ChronoUnit.HOURS);
        System.out.println("Truncated to hour: " + truncated);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. LocalDateTime — date + time, no timezone
    // -------------------------------------------------------------------------
    static void localDateTimeExamples() {
        System.out.println("--- 3. LocalDateTime ---");

        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime specific = LocalDateTime.of(2026, Month.MAY, 1, 9, 0, 0);
        LocalDateTime combined = LocalDate.of(2026, 5, 1).atTime(LocalTime.of(9, 30));

        System.out.println("Now:      " + now);
        System.out.println("Specific: " + specific);
        System.out.println("Combined: " + combined);

        // Convert to LocalDate / LocalTime
        System.out.println("Date part: " + now.toLocalDate());
        System.out.println("Time part: " + now.toLocalTime());

        // Arithmetic
        LocalDateTime inThreeDays = now.plusDays(3).withHour(9).withMinute(0).withSecond(0);
        System.out.println("In 3 days at 9am: " + inThreeDays);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. ZonedDateTime — date + time + timezone
    // -------------------------------------------------------------------------
    static void zonedDateTimeExamples() {
        System.out.println("--- 4. ZonedDateTime ---");

        ZoneId london    = ZoneId.of("Europe/London");
        ZoneId newYork   = ZoneId.of("America/New_York");
        ZoneId tokyo     = ZoneId.of("Asia/Tokyo");

        ZonedDateTime londonNow = ZonedDateTime.now(london);
        System.out.println("London:   " + londonNow);

        // Convert between zones
        ZonedDateTime newYorkTime = londonNow.withZoneSameInstant(newYork);
        ZonedDateTime tokyoTime   = londonNow.withZoneSameInstant(tokyo);
        System.out.println("New York: " + newYorkTime);
        System.out.println("Tokyo:    " + tokyoTime);

        // Schedule a meeting: 3pm London → what time in New York?
        LocalDateTime meetingLocal = LocalDateTime.of(2026, 5, 15, 15, 0);
        ZonedDateTime meetingLondon = meetingLocal.atZone(london);
        ZonedDateTime meetingNY     = meetingLondon.withZoneSameInstant(newYork);
        System.out.printf("3pm London = %s New York%n",
                meetingNY.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));

        // Available zone IDs
        System.out.println("Available zones count: " + ZoneId.getAvailableZoneIds().size());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Instant — machine time (UTC epoch nanoseconds)
    // -------------------------------------------------------------------------
    static void instantExamples() {
        System.out.println("--- 5. Instant ---");

        Instant now   = Instant.now();
        Instant epoch = Instant.EPOCH;                         // 1970-01-01T00:00:00Z
        Instant from  = Instant.ofEpochSecond(1_700_000_000L);

        System.out.println("Now:   " + now);
        System.out.println("Epoch: " + epoch);
        System.out.println("From epoch seconds: " + from);

        // Instant for logging / timestamps
        Instant start = Instant.now();
        // ... simulate some work ...
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        Instant end = Instant.now();
        Duration elapsed = Duration.between(start, end);
        System.out.println("Elapsed: " + elapsed.toMillis() + " ms");

        // Convert Instant to ZonedDateTime for human display
        ZonedDateTime humanReadable = now.atZone(ZoneId.of("UTC"));
        System.out.println("Instant as ZonedDateTime: " + humanReadable);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Duration and Period
    // -------------------------------------------------------------------------
    static void durationAndPeriod() {
        System.out.println("--- 6. Duration and Period ---");

        // Duration — time-based (hours, minutes, seconds, nanos)
        Duration twoHours    = Duration.ofHours(2);
        Duration ninetyMins  = Duration.ofMinutes(90);
        Duration fromParts   = Duration.of(1, ChronoUnit.DAYS);

        LocalTime start = LocalTime.of(9, 0);
        LocalTime end   = start.plus(twoHours);
        System.out.println("Meeting: " + start + " → " + end + " (" + twoHours.toMinutes() + " min)");

        Duration between = Duration.between(
                LocalTime.of(9, 0), LocalTime.of(17, 30));
        System.out.printf("Working day: %dh %dm%n", between.toHours(), between.toMinutesPart());

        // Period — date-based (years, months, days)
        Period twoYears   = Period.ofYears(2);
        Period threeMonths= Period.ofMonths(3);
        Period tenDays    = Period.ofDays(10);

        LocalDate joinDate = LocalDate.of(2019, 3, 15);
        LocalDate today    = LocalDate.now();
        Period tenure      = Period.between(joinDate, today);
        System.out.printf("Tenure: %d years, %d months, %d days%n",
                tenure.getYears(), tenure.getMonths(), tenure.getDays());

        // ChronoUnit.between — count in a single unit
        long daysBetween  = ChronoUnit.DAYS.between(joinDate, today);
        long monthsBetween= ChronoUnit.MONTHS.between(joinDate, today);
        System.out.printf("That's %d days (%d complete months)%n", daysBetween, monthsBetween);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. DateTimeFormatter — parsing and formatting
    // -------------------------------------------------------------------------
    static void dateTimeFormatting() {
        System.out.println("--- 7. DateTimeFormatter ---");

        LocalDate date = LocalDate.of(2026, 5, 9);
        LocalDateTime dateTime = LocalDateTime.of(2026, 5, 9, 14, 30);

        // Pre-built formatters
        System.out.println(date.format(DateTimeFormatter.ISO_LOCAL_DATE));       // 2026-05-09
        System.out.println(date.format(DateTimeFormatter.BASIC_ISO_DATE));       // 20260509

        // Custom patterns
        DateTimeFormatter ukDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter usDate = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter full   = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy HH:mm", Locale.UK);

        System.out.println("UK:   " + date.format(ukDate));
        System.out.println("US:   " + date.format(usDate));
        System.out.println("Full: " + dateTime.format(full));

        // Locale-sensitive formatting
        DateTimeFormatter localised = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.FRANCE);
        System.out.println("FR:   " + date.format(localised));

        // Parsing — string → LocalDate
        LocalDate parsed1 = LocalDate.parse("09/05/2026", ukDate);
        LocalDate parsed2 = LocalDate.parse("2026-05-09");  // ISO default
        System.out.println("Parsed (UK):  " + parsed1);
        System.out.println("Parsed (ISO): " + parsed2);

        // DateTimeFormatter is thread-safe — safe to use as a static constant
        System.out.println("DateTimeFormatter is immutable and thread-safe.");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 8. TemporalAdjusters — next Monday, last day of month, etc.
    // -------------------------------------------------------------------------
    static void temporalAdjusters() {
        System.out.println("--- 8. TemporalAdjusters ---");

        LocalDate today = LocalDate.now();

        LocalDate nextMonday    = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate lastDayMonth  = today.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate firstDayMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate firstMonInMonth = today.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));

        System.out.println("Today:              " + today);
        System.out.println("Next Monday:        " + nextMonday);
        System.out.println("Last day of month:  " + lastDayMonth);
        System.out.println("First day of month: " + firstDayMonth);
        System.out.println("1st Monday in month:" + firstMonInMonth);

        // Custom adjuster — next working day (skip weekends)
        LocalDate nextWorkingDay = today.with(date -> {
            LocalDate candidate = ((LocalDate) date).plusDays(1);
            while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY ||
                   candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                candidate = candidate.plusDays(1);
            }
            return candidate;
        });
        System.out.println("Next working day:   " + nextWorkingDay);

        // Payroll: calculate last Friday of month (typical UK pay day)
        LocalDate lastFriday = today.with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY));
        System.out.println("Pay day (last Fri): " + lastFriday);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 9. Real-World HR Scenarios
    // -------------------------------------------------------------------------
    static void realWorldHRExamples() {
        System.out.println("--- 9. Real-World HR Scenarios ---");

        List<Employee> employees = Employee.SampleData.employees();
        LocalDate today = LocalDate.now();

        // How many complete years has each engineer worked?
        System.out.println("Engineering tenure:");
        employees.stream()
                 .filter(e -> e.getDepartment() == com.devopsmonk.java8.model.Department.ENGINEERING)
                 .filter(Employee::isActive)
                 .forEach(e -> {
                     long years = ChronoUnit.YEARS.between(e.getJoinDate(), today);
                     System.out.printf("  %-15s joined %s → %d year(s)%n",
                             e.getName(), e.getJoinDate(), years);
                 });

        // Who has an anniversary this month?
        System.out.println("\nWork anniversaries this month:");
        employees.stream()
                 .filter(e -> e.getJoinDate().getMonth() == today.getMonth())
                 .forEach(e -> {
                     long years = ChronoUnit.YEARS.between(e.getJoinDate(), today);
                     System.out.printf("  %-15s — %d year(s) on %s%n",
                             e.getName(), years,
                             e.getJoinDate().withYear(today.getYear())
                              .format(DateTimeFormatter.ofPattern("dd MMM")));
                 });

        // Next performance review cycle — first Monday of next quarter
        YearMonth nextQuarter = YearMonth.now().plusMonths(3 - (today.getMonthValue() % 3));
        LocalDate firstOfNextQuarter = nextQuarter.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        System.out.println("\nNext review cycle starts: " + firstOfNextQuarter);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 10. Migrating from java.util.Date
    // -------------------------------------------------------------------------
    static void migrationFromLegacyDate() {
        System.out.println("--- 10. Migrating from java.util.Date ---");

        // java.util.Date → Instant → LocalDateTime
        java.util.Date legacyDate = new java.util.Date();
        Instant instant = legacyDate.toInstant();
        LocalDateTime modern = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        System.out.println("Legacy Date:  " + legacyDate);
        System.out.println("As Instant:   " + instant);
        System.out.println("As LocalDT:   " + modern);

        // LocalDateTime → java.util.Date (for legacy APIs that need it)
        java.util.Date backToLegacy = java.util.Date.from(
                modern.atZone(ZoneId.systemDefault()).toInstant());
        System.out.println("Back to Date: " + backToLegacy);

        // java.sql.Date → LocalDate
        java.sql.Date sqlDate = java.sql.Date.valueOf("2026-05-09");
        LocalDate localDate   = sqlDate.toLocalDate();
        System.out.println("SQL Date → LocalDate: " + localDate);

        // LocalDate → java.sql.Date (for JDBC)
        java.sql.Date backToSql = java.sql.Date.valueOf(LocalDate.now());
        System.out.println("LocalDate → SQL Date: " + backToSql);

        System.out.println();
        System.out.println("Key migration rules:");
        System.out.println("  java.util.Date  → Instant");
        System.out.println("  java.util.Calendar → ZonedDateTime");
        System.out.println("  java.sql.Date   → LocalDate");
        System.out.println("  java.sql.Timestamp → LocalDateTime");
    }
}
