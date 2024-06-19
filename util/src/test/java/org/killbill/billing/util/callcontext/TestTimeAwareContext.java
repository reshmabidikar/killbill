/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.callcontext;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.TimeAwareContext;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.account.AccountDateTimeUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

//
// There are two categories of tests, one that test the offset calculation and one that calculates
// how to get a DateTime from a LocalDate (in account time zone)
//
// Tests {1, 2, 3} use an account timezone with a negative offset (-8) and tests {A, B, C} use an account timezone with a positive offset (+8)
//
public class TestTimeAwareContext extends UtilTestSuiteNoDB {

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();
    private final String effectiveDateTime1 = "2012-01-20T07:30:42.000Z";
    private final String effectiveDateTime2 = "2012-01-20T08:00:00.000Z";
    private final String effectiveDateTime3 = "2012-01-20T08:45:33.000Z";

    private final String effectiveDateTimeA = "2012-01-20T16:30:42.000Z";
    private final String effectiveDateTimeB = "2012-01-20T16:00:00.000Z";
    private final String effectiveDateTimeC = "2012-01-20T15:30:42.000Z";

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDate1() {
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime1);
        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        refreshCallContext(effectiveDateTime, timeZone);

        final LocalDate endDate = new LocalDate(2013, 01, 19);
        final DateTime endDateTimeInUTC = internalCallContext.toUTCDateTime(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDate2() {
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime2);
        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        refreshCallContext(effectiveDateTime, timeZone);

        final LocalDate endDate = new LocalDate(2013, 01, 20);
        final DateTime endDateTimeInUTC = internalCallContext.toUTCDateTime(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDate3() {
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime3);
        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        refreshCallContext(effectiveDateTime, timeZone);

        final LocalDate endDate = new LocalDate(2013, 01, 20);
        final DateTime endDateTimeInUTC = internalCallContext.toUTCDateTime(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateA() {
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeA);
        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        refreshCallContext(effectiveDateTime, timeZone);

        final LocalDate endDate = new LocalDate(2013, 01, 21);
        final DateTime endDateTimeInUTC = internalCallContext.toUTCDateTime(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateB() {
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeB);
        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        refreshCallContext(effectiveDateTime, timeZone);

        final LocalDate endDate = new LocalDate(2013, 01, 21);
        final DateTime endDateTimeInUTC = internalCallContext.toUTCDateTime(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateC() {
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeC);
        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        refreshCallContext(effectiveDateTime, timeZone);

        final LocalDate endDate = new LocalDate(2013, 01, 20);
        final DateTime endDateTimeInUTC = internalCallContext.toUTCDateTime(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeTargetDateWithDayLightSaving() {
        final DateTime dateTime1 = new DateTime("2015-01-01T08:01:01.000Z");
        final DateTime dateTime2 = new DateTime("2015-09-01T08:01:01.000Z");
        final DateTime dateTime3 = new DateTime("2015-12-01T08:01:01.000Z");

        // Alaska Standard Time
        final DateTimeZone timeZone = DateTimeZone.forID("America/Juneau");

        // Time zone is AKDT (UTC-8h) between March and November
        final DateTime referenceDateTimeWithDST = new DateTime("2015-09-01T08:01:01.000Z");
        refreshCallContext(referenceDateTimeWithDST, timeZone);

        assertEquals(internalCallContext.toLocalDate(dateTime1), new LocalDate("2015-01-01"));
        assertEquals(internalCallContext.toLocalDate(dateTime2), new LocalDate("2015-09-01"));
        assertEquals(internalCallContext.toLocalDate(dateTime3), new LocalDate("2015-12-01"));

        // Time zone is AKST (UTC-9h) otherwise
        final DateTime referenceDateTimeWithoutDST = new DateTime("2015-02-01T08:01:01.000Z");
        refreshCallContext(referenceDateTimeWithoutDST, timeZone);

        assertEquals(internalCallContext.toLocalDate(dateTime1), new LocalDate("2014-12-31"));
        assertEquals(internalCallContext.toLocalDate(dateTime2), new LocalDate("2015-08-31"));
        assertEquals(internalCallContext.toLocalDate(dateTime3), new LocalDate("2015-11-30"));
    }

    @Test(groups = "fast")
    public void testIdempotencyOfDatesManipulation() throws Exception {
        final List<DateTimeZone> dateTimeZoneBuilder = new ArrayList<>();
        dateTimeZoneBuilder.add(DateTimeZone.forID("HST"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("PST8PDT"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("MST"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("CST6CDT"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("EST"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("Brazil/DeNoronha"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("UTC"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("CET"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("Europe/Istanbul"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("Singapore"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("Japan"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("Australia/Sydney"));
        dateTimeZoneBuilder.add(DateTimeZone.forID("Pacific/Tongatapu"));
        final Iterable<DateTimeZone> dateTimeZones = List.copyOf(dateTimeZoneBuilder);

        final List<DateTime> referenceDateTimeBuilder = new ArrayList<>();
        referenceDateTimeBuilder.add(new DateTime(2012, 1, 1, 1, 1, 1, DateTimeZone.UTC));
        referenceDateTimeBuilder.add(new DateTime(2012, 3, 15, 12, 42, 0, DateTimeZone.forID("PST8PDT")));
        referenceDateTimeBuilder.add(new DateTime(2012, 11, 15, 12, 42, 0, DateTimeZone.forID("PST8PDT")));
        final Iterable<DateTime> referenceDateTimes = List.copyOf(referenceDateTimeBuilder);

        DateTime currentDateTime = new DateTime(2015, 1, 1, 1, 1, DateTimeZone.UTC);
        final DateTime endDateTime = new DateTime(2020, 1, 1, 1, 1, DateTimeZone.UTC);
        while (currentDateTime.compareTo(endDateTime) <= 0) {
            for (final DateTimeZone dateTimeZone : dateTimeZones) {
                for (final DateTime referenceDateTime : referenceDateTimes) {
                    final TimeAwareContext timeAwareContext = new TimeAwareContext(dateTimeZone, dateTimeZone, referenceDateTime);

                    final LocalDate computedLocalDate = timeAwareContext.toLocalDate(currentDateTime);
                    final DateTime computedDateTime = timeAwareContext.toUTCDateTime(computedLocalDate);
                    final LocalDate computedLocalDate2 = timeAwareContext.toLocalDate(computedDateTime);

                    final String msg = String.format("currentDateTime=%s, localDate=%s, dateTime=%s, dateTimeZone=%s, referenceDateTime=%s", currentDateTime, computedLocalDate, computedDateTime, dateTimeZone, referenceDateTime);
                    Assert.assertEquals(computedLocalDate2, computedLocalDate, msg);
                }
            }

            currentDateTime = currentDateTime.plusHours(1);
        }
    }

    private void refreshCallContext(final DateTime effectiveDateTime, final DateTimeZone timeZone) {
        final Account account = new MockAccountBuilder().timeZone(timeZone)
                                                        .createdDate(effectiveDateTime)
                                                        .referenceTime(effectiveDateTime)
                                                        .build();
        internalCallContext.setFixedOffsetTimeZone(AccountDateTimeUtils.getFixedOffsetTimeZone(account));
        internalCallContext.setReferenceTime(account.getReferenceTime());
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateAndViceVersaUTCTimezone() {

        final DateTime referenceTime = new DateTime("2023-01-01T3:00:00.000Z");
        final DateTimeZone timeZone = DateTimeZone.UTC;
        refreshCallContext(referenceTime, timeZone);
        LocalTime referenceLocalTime = internalCallContext.getReferenceLocalTime();
        assertEquals(referenceLocalTime, new LocalTime("3:00:00.000"));

        LocalDate inputDate = new LocalDate("2023-07-28");
        DateTime utcDateTime = internalCallContext.toUTCDateTime(inputDate);
        assertEquals(utcDateTime.compareTo(new DateTime("2023-07-28T3:00")), 0);

        DateTime inputDateTime = new DateTime("2023-04-03T8:00Z");
        LocalDate localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-04-03")), 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateAndViceVersaPSTTimezone() {

        //without DST
        DateTime referenceTime = new DateTime("2023-01-01T9:00:00.000"); //reference time in UTC
        DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");
        refreshCallContext(referenceTime, timeZone);

        LocalTime referenceLocalTime = internalCallContext.getReferenceLocalTime(); //local time in America/Los_Angeles TZ
        assertEquals(referenceLocalTime, new LocalTime("1:00:00.000"));

        DateTimeZone fixedOffsetTimezone = internalCallContext.getFixedOffsetTimeZone(); //fixed offset from UTC
        assertEquals(fixedOffsetTimezone, DateTimeZone.forOffsetHours(-8));

        LocalDate inputDate = new LocalDate("2023-07-28");
        DateTime utcDateTime = internalCallContext.toUTCDateTime(inputDate);
        assertEquals(utcDateTime.compareTo(new DateTime("2023-07-28T9:00")), 0);

        DateTime inputDateTime = new DateTime("2023-07-28T9:00Z");
        LocalDate localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-28")), 0);

        inputDateTime = new DateTime("2023-07-28T7:00Z");
        localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-27")), 0);

        //with DST
        referenceTime = new DateTime("2023-04-01T9:00:00.000");//reference time in UTC
        timeZone = DateTimeZone.forID("America/Los_Angeles");
        refreshCallContext(referenceTime, timeZone);

        referenceLocalTime = internalCallContext.getReferenceLocalTime();//local time in America/Los_Angeles TZ
        assertEquals(referenceLocalTime, new LocalTime("2:00:00.000"));

        fixedOffsetTimezone = internalCallContext.getFixedOffsetTimeZone(); //fixed offset from UTC
        assertEquals(fixedOffsetTimezone, DateTimeZone.forOffsetHours(-7));

        inputDate = new LocalDate("2023-07-28");
        utcDateTime = internalCallContext.toUTCDateTime(inputDate);
        assertEquals(utcDateTime.compareTo(new DateTime("2023-07-28T9:00")), 0);

        inputDateTime = new DateTime("2023-07-28T9:00Z");
        localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-28")), 0);

        inputDateTime = new DateTime("2023-07-28T6:00Z");
        localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-27")), 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateAndViceVersaNewYorkTimezone() {
        //without DST
        DateTime referenceTime = new DateTime("2019-03-04T06:00:00Z"); //reference time in UTC
        DateTimeZone timeZone = DateTimeZone.forID("America/New_York");
        refreshCallContext(referenceTime, timeZone);

        LocalTime referenceLocalTime = internalCallContext.getReferenceLocalTime(); //local time in America/New_York TZ
        assertEquals(referenceLocalTime, new LocalTime("1:00:00.000"));

        DateTimeZone fixedOffsetTimezone = internalCallContext.getFixedOffsetTimeZone(); //fixed offset from UTC
        assertEquals(fixedOffsetTimezone, DateTimeZone.forOffsetHours(-5));

        //date to datetime
        LocalDate inputDate = new LocalDate("2023-07-28");
        DateTime utcDateTime = internalCallContext.toUTCDateTime(inputDate);
        assertEquals(utcDateTime.compareTo(new DateTime("2023-07-28T6:00")), 0);

        //datetime to date
        DateTime inputDateTime = new DateTime("2023-07-28T8:00Z");
        LocalDate localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-28")), 0);

        inputDateTime = new DateTime("2023-07-28T4:00Z");
        localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-27")), 0);

        //with DST
        referenceTime = new DateTime("2019-03-11T06:00:00Z");
        timeZone = DateTimeZone.forID("America/New_York");
        refreshCallContext(referenceTime, timeZone);

        referenceLocalTime = internalCallContext.getReferenceLocalTime(); //local time in America/New_York TZ
        assertEquals(referenceLocalTime, new LocalTime("2:00:00.000"));

        fixedOffsetTimezone = internalCallContext.getFixedOffsetTimeZone(); //fixed offset from UTC
        assertEquals(fixedOffsetTimezone, DateTimeZone.forOffsetHours(-4));

        //date to datetime
        inputDate = new LocalDate("2023-07-28");
        utcDateTime = internalCallContext.toUTCDateTime(inputDate);
        assertEquals(utcDateTime.compareTo(new DateTime("2023-07-28T6:00")), 0);

        //datetime to date
        inputDateTime = new DateTime("2023-07-28T8:00Z");
        localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-28")), 0);

        inputDateTime = new DateTime("2023-07-28T3:00Z");
        localDateInUsersTimezone = internalCallContext.toLocalDate(inputDateTime);
        assertEquals(localDateInUsersTimezone.compareTo(new LocalDate("2023-07-27")), 0);
    }

}
